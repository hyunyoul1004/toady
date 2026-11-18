package com.toady.web.app.planner;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.operation.AnnouncementOverrides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 운영자가 숨긴 공고(AnnouncementOverrides, database.sql 26번)를 공개 페이지·알림 쪽에서 한 기준으로 확인한다.
 * 공개 주소의 유형 경로값(apt, public-rental)과 운영 테이블의 유형 이름(APT, PUBLIC_RENTAL)을 여기서 맞춘다.
 * 숨김 목록이 바뀌면(AnnouncementOverrides가 1분마다 다시 읽음) 공개 HTML 캐시를 비우고 Changed 이벤트를 알려
 * 사이트맵·지역 페이지·이번 주 청약·RSS 등 각자의 캐시를 비우게 한다. 숨긴 공고가 최대 30분 동안 남아 보이지 않게 하기 위해서다.
 */
// @Component: 스프링 빈. 숨김 기능 빈(AnnouncementOverrides)이 없으면 아무것도 숨기지 않는다.
@Component
public class HiddenAnnouncements {
    private static final Logger log = LoggerFactory.getLogger(HiddenAnnouncements.class);
    /** 숨김 목록이 바뀌었다는 알림. 받는 쪽은 자기 캐시를 비운다. */
    public record Changed() { }

    private final AnnouncementOverrides overrides;
    private final ApplicationEventPublisher events;
    /** 마지막으로 확인한 숨김 목록(정렬한 키 문자열). null이면 아직 한 번도 확인하지 않았다. */
    private volatile String lastSignature;

    // @Autowired: 스프링이 쓸 생성자. ObjectProvider로 빈이 없을 수도 있는 의존성을 선택적으로 받는다. 아래 생성자는 테스트용.
    @org.springframework.beans.factory.annotation.Autowired
    public HiddenAnnouncements(ObjectProvider<AnnouncementOverrides> overrides, ApplicationEventPublisher events) {
        this(overrides.getIfAvailable(), events);
    }
    HiddenAnnouncements(AnnouncementOverrides overrides, ApplicationEventPublisher events) { this.overrides = overrides; this.events = events; }

    /** 유형 경로값('apt', 'public-rental')이나 이름('APT')을 운영 테이블의 유형 이름으로 바꾼다. */
    static String typeName(String type) { return type == null ? "" : type.trim().toUpperCase(Locale.ROOT).replace('-', '_'); }

    public boolean hidden(String type, String house, String notice) {
        return overrides != null && type != null && house != null && notice != null && overrides.hidden(typeName(type), house, notice);
    }
    /** 공개 주소용 id('apt/주택관리번호/공고번호')로 확인한다. 형식이 다르면 숨기지 않은 것으로 본다. */
    public boolean hiddenId(String id) {
        String[] parts = id == null ? new String[0] : id.split("/", 3);
        return parts.length == 3 && hidden(parts[0], parts[1], parts[2]);
    }

    /** 숨긴 공고 전체를 정렬해 이은 문자열. 목록이 같으면 같은 값이다. */
    String signature() {
        if (overrides == null) return "";
        return Arrays.stream(AnnouncementType.values()).flatMap(type -> overrides.hiddenOf(type.name()).stream().map(key -> type.name() + "/" + String.join("/", key)))
            .sorted().collect(Collectors.joining("|"));
    }

    /** 직전 Changed 뒤 한 번 더 비워야 하는지(아래 watch 설명 참고). */
    private volatile boolean repeatPending;

    /**
     * 서버가 요청을 받기 시작할 때 기준값을 잡는다. 숨긴 공고가 하나라도 있으면 Changed를 한 번 알린다.
     * 시작 중에 먼저 채워진 캐시가 숨김 목록을 읽기 전 상태로 만들어졌을 수 있어서다(캐시를 비우는 비용은 작다).
     */
    // @EventListener(ApplicationReadyEvent): 스프링 부트가 시작을 모두 마친 뒤 한 번 불린다.
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    void onReady() { check(); }

    /**
     * 30초마다 숨김 목록을 비교해 바뀌었으면 공개 캐시를 비운다. 처음 확인할 때(시작 기준값을 못 잡은 경우)는 숨긴 공고가 있으면 한 번 비운다.
     * 운영 DB를 다시 읽는 일은 AnnouncementOverrides가 1분 간격으로만 하므로 여기서 추가 조회는 생기지 않는다.
     * Guava invalidateAll은 이미 계산 중이던 값까지 막지 못한다(비운 직후 예전 숨김 목록으로 만든 값이 다시 들어갈 수 있다).
     * 그래서 Changed를 알린 다음 확인(30초 뒤)에 한 번 더 알린다. 각 캐시에 세대 번호를 두는 것보다 단순하고,
     * 계산은 길어야 몇 초라 30초 뒤 두 번째 비우기로 남은 값이 모두 사라진다.
     */
    // @Scheduled(fixedDelay): 이전 실행이 끝나고 30초 뒤에 다시 실행한다(숨김 반영은 길어야 약 1분 30초).
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void watch() { check(); }

    synchronized void check() {
        String now;
        try { now = signature(); }
        catch (RuntimeException unavailable) { log.debug("숨김 목록 확인 실패: {}", unavailable.getClass().getSimpleName()); return; }
        String before = lastSignature;
        lastSignature = now;
        boolean changed = before == null ? !now.isEmpty() : !Objects.equals(before, now);
        if (changed) {
            log.info("숨긴 공고 목록이 바뀌어 공개 페이지 캐시를 비웁니다");
            announce();
            repeatPending = true;
        } else if (repeatPending) {
            repeatPending = false;
            announce();
        }
    }
    private void announce() {
        PublicHtml.clearCache();
        events.publishEvent(new Changed());
    }

    /** 목록에서 숨긴 공고를 뺀다. idOf: 항목의 공개 id('apt/H/N'). */
    public <T> List<T> visible(List<T> items, java.util.function.Function<T, String> idOf) {
        if (overrides == null) return items;
        return items.stream().filter(item -> !hiddenId(idOf.apply(item))).toList();
    }
}
