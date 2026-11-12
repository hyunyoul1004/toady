package com.toady.web.app.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;

/**
 * 웹 푸시 메시지 암호화(RFC 8291, aes128gcm)와 VAPID 서명(RFC 8292). JDK 기본 암호 기능만 사용한다.
 * 외부 라이브러리 대신 직접 구현한 이유: 필요한 연산이 P-256 ECDH·HKDF(HMAC-SHA256)·AES-GCM·ES256뿐이라
 * JDK 21에 모두 있고, 대표 Java 라이브러리는 BouncyCastle·비동기 HTTP 클라이언트 등 큰 의존성을 함께 끌어온다.
 * 정확성은 RFC 8291 부록 A 테스트 벡터와 같은 결과를 내는지로 검증한다.
 */
public final class WebPushCrypto {
    static final int RECORD_SIZE = 4096;
    /** 암호화 후 한 레코드(4096) 안에 들어가는 평문 최대 길이: 4096 - 헤더 86 - 태그 16 - 구분자 1. */
    public static final int MAX_PLAINTEXT = RECORD_SIZE - 86 - 16 - 1;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ECParameterSpec P256 = p256();
    private WebPushCrypto() { }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) { throw new IllegalStateException("P-256 곡선을 사용할 수 없습니다.", e); }
    }

    public static byte[] b64(String value) { return Base64.getUrlDecoder().decode(value.trim().replace('+', '-').replace('/', '_').replace("=", "")); }
    public static String b64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }

    /** 브라우저 PushSubscription.getKey('p256dh') 형식(0x04 || X || Y, 65바이트)의 공개키. */
    public static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) throw new InvalidKeyException("P-256 비압축 공개키가 아닙니다.");
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)), new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }
    public static ECPrivateKey privateKey(byte[] d) throws GeneralSecurityException {
        if (d.length != 32) throw new InvalidKeyException("P-256 개인키는 32바이트여야 합니다.");
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), P256));
    }
    public static byte[] uncompressed(ECPublicKey key) {
        byte[] out = new byte[65]; out[0] = 4;
        unsigned(key.getW().getAffineX(), out, 1); unsigned(key.getW().getAffineY(), out, 33);
        return out;
    }
    private static void unsigned(BigInteger value, byte[] out, int offset) {
        byte[] raw = value.toByteArray();
        int start = Math.max(0, raw.length - 32);
        System.arraycopy(raw, start, out, offset + 32 - (raw.length - start), raw.length - start);
    }
    static KeyPair ephemeral() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(P256, RANDOM);
        return generator.generateKeyPair();
    }

    /** 무작위 임시 키와 salt로 암호화한다. */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        KeyPair pair = ephemeral();
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, (ECPrivateKey) pair.getPrivate(), uncompressed((ECPublicKey) pair.getPublic()), salt);
    }

    /** RFC 8291 3·4장. 테스트 벡터 검증을 위해 임시 키와 salt를 주입할 수 있다. */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, ECPrivateKey asPrivate, byte[] asPublic, byte[] salt) throws GeneralSecurityException {
        if (plaintext.length > MAX_PLAINTEXT) throw new IllegalArgumentException("푸시 본문이 너무 깁니다.");
        if (authSecret.length != 16) throw new InvalidKeyException("auth 비밀값은 16바이트여야 합니다.");
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(asPrivate);
        agreement.doPhase(publicKey(uaPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();
        byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 16);
        byte[] nonce = hkdf(salt, ikm, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        // 단일 레코드이므로 마지막 레코드 구분자 0x02만 붙이고 추가 패딩은 넣지 않는다.
        byte[] body = cipher.doFinal(concat(plaintext, new byte[]{2}));
        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), body);
    }

    /** HKDF(RFC 5869) 추출·확장. 출력은 32바이트 이하만 필요해 확장 1회로 충분하다. */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        return Arrays.copyOf(mac.doFinal(concat(info, new byte[]{1})), length);
    }

    /** RFC 8292 VAPID JWT(ES256). aud는 푸시 서비스 출처, exp는 24시간 이내여야 한다. */
    public static String vapidJwt(String audience, String subject, long expiresEpochSeconds, ECPrivateKey key) throws GeneralSecurityException {
        String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64(("{\"aud\":\"" + json(audience) + "\",\"exp\":" + expiresEpochSeconds + ",\"sub\":\"" + json(subject) + "\"}").getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(key);
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + b64(signer.sign());
    }

    /** 환경변수로 받은 키 쌍이 서로 맞는지 서명·검증으로 확인한다. 값은 로그에 남기지 않는다. */
    static boolean matches(ECPrivateKey privateKey, ECPublicKey publicKey) {
        try {
            byte[] probe = "toady-vapid-check".getBytes(StandardCharsets.US_ASCII);
            Signature signer = Signature.getInstance("SHA256withECDSA"); signer.initSign(privateKey); signer.update(probe);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withECDSA"); verifier.initVerify(publicKey); verifier.update(probe);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) { return false; }
    }

    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.writeBytes(part);
        return out.toByteArray();
    }
}
