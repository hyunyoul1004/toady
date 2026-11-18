package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.OptionalAnnouncementModelEntity;
import com.toady.web.app.announcement.entity.id.AnnouncementModelId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 임의공급 공고의 주택형(TB_OPT_LTTOT_PBLANC_MDL) 저장소. 키는 (주택관리번호, 공고번호, 모델번호) 복합키 AnnouncementModelId.
 * 인터페이스만 선언하면 Spring Data JPA가 구현체를 만들어 준다. API는 읽기만 하고, 쓰기는 스케줄러가 한다.
 * 호출: AnnouncementDetailService(공고 상세의 models 목록).
 */
public interface OptionalAnnouncementModelRepository extends JpaRepository<OptionalAnnouncementModelEntity, AnnouncementModelId> {
    // 메서드 이름을 해석해 쿼리를 만든다(파생 쿼리).
    // where HOUSE_MANAGE_NO = ? and PBLANC_NO = ? order by MODEL_NO asc 와 같다.
    List<OptionalAnnouncementModelEntity> findByHouseManageNoAndPblancNoOrderByModelNoAsc(String houseManageNo, String pblancNo);
}
