package com.toady.web.app.planner;

import com.toady.web.app.operation.AnnouncementOverrides;
import com.toady.web.app.planner.research.AddressLocator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 운영자가 관리자 '위치 확인'으로 넣은 공고 주소(AnnouncementOverrides.locationAddress, database.sql 27번)를 공개 id로 찾아 준다.
 * 공고 상세 화면(resolveLocation.js)과 같은 순서(운영자 확인 주소 → 공급 주소)로 위치를 확정하도록
 * 주변 시세 미리 계산(PriceGapSnapshots)과 분양 지도(SaleMapService)가 이 클래스를 거쳐 AddressLocator를 부른다.
 * 운영자 주소도 AddressLocator의 지번 일치·같은 시·군·구 검증을 통과해야만 쓰이고, 못 찾으면 공급 주소 판정으로 이어진다.
 */
// @Component: 스프링 빈. 운영 안내 빈(AnnouncementOverrides)이 없으면 확인 주소가 없는 것으로 본다.
@Component
public class ConfirmedAddresses {
    /** 확인 주소를 쓰지 않는 기본값(단위 테스트·옛 생성자용). */
    static final ConfirmedAddresses NONE = new ConfirmedAddresses((AnnouncementOverrides) null);
    private final AnnouncementOverrides overrides;

    // @Autowired: 스프링이 쓸 생성자. ObjectProvider로 없을 수도 있는 빈을 선택적으로 받는다. 아래 생성자는 테스트용.
    @Autowired
    public ConfirmedAddresses(ObjectProvider<AnnouncementOverrides> overrides) { this(overrides.getIfAvailable()); }
    ConfirmedAddresses(AnnouncementOverrides overrides) { this.overrides = overrides; }

    /** 공개 id('apt/주택관리번호/공고번호')의 운영자 확인 주소. 없거나 형식이 다르면 null. */
    public String of(String id) {
        String[] parts = id == null ? new String[0] : id.split("/", 3);
        if (overrides == null || parts.length != 3) return null;
        var value = overrides.get(HiddenAnnouncements.typeName(parts[0]), parts[1], parts[2]);
        return value == null ? null : value.locationAddress();
    }

    /** 운영자 확인 주소를 먼저, 다음으로 공급 주소를 확인한다(AddressLocator.locate 두 인자 판정). */
    public Optional<AddressLocator.Location> locate(AddressLocator locator, String id, String supplyAddress) throws Exception {
        return locator.locate(supplyAddress, of(id));
    }
}
