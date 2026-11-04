package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.PublicRentalAnnouncementModelEntity;
import com.toady.web.app.announcement.entity.id.AnnouncementModelId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 공공지원 민간임대 공고의 주택형별 상세 정보를 조회하는 저장소입니다.
 * 주택형은 주택관리번호, 공고번호, 모델번호를 복합키로 사용합니다.
 */
public interface PublicRentalAnnouncementModelRepository extends JpaRepository<PublicRentalAnnouncementModelEntity, AnnouncementModelId> {

    /**
     * 공고에 속한 주택형을 모델번호 오름차순으로 조회합니다.
     *
     * @param houseManageNo 주택관리번호
     * @param pblancNo 공고번호
     * @return 모델번호 순으로 정렬된 주택형 목록
     */
    List<PublicRentalAnnouncementModelEntity> findByHouseManageNoAndPblancNoOrderByModelNoAsc(
            String houseManageNo,
            String pblancNo
    );
}
