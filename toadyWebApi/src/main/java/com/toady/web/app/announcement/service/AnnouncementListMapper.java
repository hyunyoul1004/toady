package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.entity.*;

/** 외부 데이터 유형별 필드 차이를 통합 목록 계약으로 변환합니다. */
final class AnnouncementListMapper {
    private AnnouncementListMapper() {
    }

    static AnnouncementListItem toListItem(AptAnnouncementEntity item) {
        return new AnnouncementListItem(
                AnnouncementType.APT.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                item.getHssplyAdres(), item.getHssplyZip(),
                item.getRceptBgnde(), item.getRceptEndde(),
                item.getGnrlRnk1CrspareaRcptde(), item.getGnrlRnk1CrspareaEndde(),
                item.getGnrlRnk1EtcGgRcptde(), item.getGnrlRnk1EtcGgEndde(),
                item.getGnrlRnk1EtcAreaRcptde(), item.getGnrlRnk1EtcAreaEndde(),
                item.getGnrlRnk2CrspareaRcptde(), item.getGnrlRnk2CrspareaEndde(),
                item.getGnrlRnk2EtcGgRcptde(), item.getGnrlRnk2EtcGgEndde(),
                item.getGnrlRnk2EtcAreaRcptde(), item.getGnrlRnk2EtcAreaEndde(),
                item.getTotSuplyHshldco()
        );
    }

    static AnnouncementListItem toListItem(UrbanAnnouncementEntity item) {
        return new AnnouncementListItem(
                AnnouncementType.URBAN.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                item.getHssplyHshldco(), item.getHssplyZip(),
                item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                null, null, null, null, null, null, null, null, null, null, null, null,
                item.getTotSuplyHshldco()
        );
    }

    static AnnouncementListItem toListItem(RemainingAnnouncementEntity item) {
        return new AnnouncementListItem(
                AnnouncementType.REMAINING.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                item.getHssplyAdres(), item.getHssplyZip(),
                item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                null, null, null, null, null, null, null, null, null, null, null, null,
                item.getTotSuplyHshldco()
        );
    }

    static AnnouncementListItem toListItem(PublicRentalAnnouncementEntity item) {
        return new AnnouncementListItem(
                AnnouncementType.PUBLIC_RENTAL.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                item.getHssplyAdres(), item.getHssplyZip(),
                item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                null, null, null, null, null, null, null, null, null, null, null, null,
                item.getTotSuplyHshldco()
        );
    }

    static AnnouncementListItem toListItem(OptionalAnnouncementEntity item) {
        return new AnnouncementListItem(
                AnnouncementType.OPTIONAL.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                item.getHssplyAdres(), item.getHssplyZip(),
                item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                null, null, null, null, null, null, null, null, null, null, null, null,
                item.getTotSuplyHshldco()
        );
    }
}
