package com.toady.web.app.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.push.PushMessage;
import com.toady.web.app.push.PushSubscriptions;
import com.toady.web.app.push.WebPushSender;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 새 쪽지 웹 푸시. 받는 회원이 로그인 상태로 알림을 켠 기기에만 보낸다(이메일은 보내지 않는다).
 * 잠금 화면에 내용이 보이지 않게 보낸 사람 닉네임만 담는다. 쪽지 저장이 확정된 뒤 별도 스레드에서 보내, 푸시 서비스가 느려도 보내기 응답을 붙잡지 않는다.
 */
@Slf4j
@Component
public class DmNotifier {
    private final JdbcTemplate jdbc;
    private final WebPushSender sender;
    private final PushSubscriptions subscriptions;
    private final ObjectMapper mapper;
    /** 한 줄로 보내고, 밀리면(100건) 알림을 버린다. 쪽지 자체는 쪽지함에 남는다. */
    private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100), task -> {
        Thread thread = new Thread(task, "dm-push"); thread.setDaemon(true); return thread;
    });

    public DmNotifier(JdbcTemplate jdbc, WebPushSender sender, PushSubscriptions subscriptions, ObjectMapper mapper) {
        this.jdbc = jdbc; this.sender = sender; this.subscriptions = subscriptions; this.mapper = mapper;
    }

    record Device(String hash, String endpoint, String p256dh, String auth) { }

    public void notify(long receiver, long from, long threadSeq) {
        if (!sender.enabled()) return;
        try { worker.execute(() -> deliver(receiver, from, threadSeq)); }
        catch (RejectedExecutionException busy) { log.warn("쪽지 알림이 밀려 건너뜀: threadSeq={}", threadSeq); }
    }

    void deliver(long receiver, long from, long threadSeq) {
        try {
            List<Device> devices = jdbc.query("SELECT ENDPOINT_HASH, ENDPOINT, P256DH, AUTH FROM TB_PUSH_SUBSCRIPTION WHERE USER_SEQ=?",
                    (rs, n) -> new Device(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), receiver);
            if (devices.isEmpty()) return;
            String nickname = jdbc.query("SELECT USER_NIC_NAME FROM TB_USER WHERE USER_SEQ=?", rs -> rs.next() ? rs.getString(1) : null, from);
            String json = message(nickname, threadSeq).json(mapper);
            for (Device device : devices) {
                switch (sender.send(new WebPushSender.Target(device.endpoint(), device.p256dh(), device.auth()), json, "dm")) {
                    case GONE -> subscriptions.deleteHash(device.hash());
                    default -> { }
                }
            }
        } catch (RuntimeException failure) {
            log.warn("쪽지 알림을 보내지 못함: threadSeq={}, {}", threadSeq, failure.getMessage());
        }
    }

    static PushMessage message(String nickname, long threadSeq) {
        String name = nickname == null || nickname.isBlank() ? "회원" : nickname.length() > 15 ? nickname.substring(0, 15) : nickname;
        return new PushMessage("새 쪽지가 왔어요", name + "님이 쪽지를 보냈어요.", "/messages?thread=" + threadSeq, "dm-" + threadSeq, List.of());
    }

    @PreDestroy
    void stop() { worker.shutdown(); }
}
