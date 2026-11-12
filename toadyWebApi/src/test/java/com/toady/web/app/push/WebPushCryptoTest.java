package com.toady.web.app.push;

import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import static com.toady.web.app.push.WebPushCrypto.*;
import static org.junit.jupiter.api.Assertions.*;

class WebPushCryptoTest {
    /** RFC 8291 부록 A의 예시 값. 고정 키·salt로 같은 암호문이 나와야 한다. */
    @Test void matchesRfc8291AppendixA() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        ECPrivateKey asPrivate = privateKey(b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));
        byte[] asPublic = b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] uaPublic = b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] auth = b64("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = b64("DGv6ra1nlYgDCS1FRnbzlw");
        byte[] body = encrypt(plaintext, uaPublic, auth, asPrivate, asPublic, salt);
        assertEquals("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN", b64(body));
    }

    /** 브라우저(수신자) 입장에서 복호화해 원문이 나오는지 확인한다. */
    @Test void receiverCanDecryptRandomizedMessage() throws Exception {
        KeyPair receiver = ephemeral();
        byte[] uaPublic = uncompressed((ECPublicKey) receiver.getPublic());
        byte[] auth = new byte[16]; new java.security.SecureRandom().nextBytes(auth);
        byte[] message = "{\"title\":\"내일 접수 마감\"}".getBytes(StandardCharsets.UTF_8);
        byte[] body = encrypt(message, uaPublic, auth);
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        assertEquals(4096, java.nio.ByteBuffer.wrap(body, 16, 4).getInt());
        int idLength = body[20];
        byte[] asPublic = Arrays.copyOfRange(body, 21, 21 + idLength);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(receiver.getPrivate()); agreement.doPhase(publicKey(asPublic), true);
        byte[] ikm = hkdf(auth, agreement.generateSecret(), concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic), 32);
        byte[] cek = hkdf(salt, ikm, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 16);
        byte[] nonce = hkdf(salt, ikm, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(Arrays.copyOfRange(body, 21 + idLength, body.length));
        assertEquals(2, padded[padded.length - 1]);
        assertArrayEquals(message, Arrays.copyOf(padded, padded.length - 1));
        assertThrows(IllegalArgumentException.class, () -> encrypt(new byte[MAX_PLAINTEXT + 1], uaPublic, auth));
    }

    @Test void vapidJwtIsVerifiableEs256AndKeyPairCheckDetectsMismatch() throws Exception {
        KeyPair pair = ephemeral(), other = ephemeral();
        String jwt = vapidJwt("https://fcm.googleapis.com", "mailto:push@example.test", 1_900_000_000L, (ECPrivateKey) pair.getPrivate());
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        assertTrue(new String(b64(parts[1]), StandardCharsets.UTF_8).contains("\"aud\":\"https://fcm.googleapis.com\""));
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(pair.getPublic()); verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertEquals(64, b64(parts[2]).length);
        assertTrue(verifier.verify(b64(parts[2])));
        assertTrue(matches((ECPrivateKey) pair.getPrivate(), (ECPublicKey) pair.getPublic()));
        assertFalse(matches((ECPrivateKey) pair.getPrivate(), (ECPublicKey) other.getPublic()));
    }
}
