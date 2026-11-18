package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.entity.*;

/**
 * 외부 데이터 유형별 필드 차이를 통합 목록 계약으로 변환합니다.
 * 예: 접수 시작일이 APT는 rceptBgnde, 나머지는 subscrptRceptBgnde. 특별공급·순위별 접수일은 APT에만 있어 다른 유형은 null.
 * 댓글 수는 0으로 두고, 나중에 AnnouncementCommentCountService가 채운다. 호출: AnnouncementSearchService.searchAll.
 * 상태가 없는 변환 함수 모음이라 빈이 아닌 static 메서드로 두었다.
 */
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
                item.getSpsplyRceptBgnde(), item.getSpsplyRceptEndde(),
                item.getGnrlRnk1CrspareaRcptde(), item.getGnrlRnk1CrspareaEndde(),
                item.getGnrlRnk1EtcGgRcptde(), item.getGnrlRnk1EtcGgEndde(),
                item.getGnrlRnk1EtcAreaRcptde(), item.getGnrlRnk1EtcAreaEndde(),
                item.getGnrlRnk2CrspareaRcptde(), item.getGnrlRnk2CrspareaEndde(),
                item.getGnrlRnk2EtcGgRcptde(), item.getGnrlRnk2EtcGgEndde(),
                item.getGnrlRnk2EtcAreaRcptde(), item.getGnrlRnk2EtcAreaEndde(),
                item.getTotSuplyHshldco(), 0L
        );
    }

    // 도시형·오피스텔은 주소가 HSSPLY_HSHLDCO 컬럼(hssplyHshldco)에 들어 있다.
    static AnnouncementListItem toListItem(UrbanAnnouncementEntity item) {
        return new AnnouncementListItem(
                AnnouncementType.URBAN.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                item.getHssplyHshldco(), item.getHssplyZip(),
                item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                item.getTotSuplyHshldco(), 0L
        );
    }

    static AnnouncementListItem toListItem(RemainingAnnouncementEntity item) {
        return new AnnouncementListItem(
                AnnouncementType.REMAINING.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                item.getHssplyAdres(), item.getHssplyZip(),
                item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                item.getTotSuplyHshldco(), 0L
        );
    }

    static AnnouncementListItem toListItem(PublicRentalAnnouncementEntity item) {
        return new AnnouncementListItem(
                AnnouncementType.PUBLIC_RENTAL.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                item.getHssplyAdres(), item.getHssplyZip(),
                item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                item.getTotSuplyHshldco(), 0L
        );
    }

    static AnnouncementListItem toListItem(OptionalAnnouncementEntity item) {
        return new AnnouncementListItem(
                AnnouncementType.OPTIONAL.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                item.getHssplyAdres(), item.getHssplyZip(),
                item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                item.getTotSuplyHshldco(), 0L
        );
    }
}
