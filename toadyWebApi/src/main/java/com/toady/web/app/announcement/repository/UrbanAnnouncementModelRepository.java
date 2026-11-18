package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.UrbanAnnouncementModelEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 도시형·오피스텔 공고의 주택형(TB_URBTY_OFCTL_LTTOT_PBLANC_MDL) 저장소.
 * 다른 주택형 테이블과 달리 자동 증가 ID(Long)를 키로 쓴다. 호출: AnnouncementDetailService.
 */
public interface UrbanAnnouncementModelRepository extends JpaRepository<UrbanAnnouncementModelEntity, Long> {
    // @Query: 메서드 이름 대신 여기 적은 JPQL(엔티티 기준 쿼리)을 실행한다.
    // 이름은 파생 쿼리 모양이지만 @Query가 우선한다. 모델번호가 같아도 순서가 흔들리지 않게 id로 한 번 더 정렬한다.
    // @Param: JPQL의 :houseManageNo 같은 이름 자리에 메서드 인자를 연결한다.
    @Query("""
            select model from UrbanAnnouncementModelEntity model
            where model.houseManageNo = :houseManageNo and model.pblancNo = :pblancNo
            order by model.modelNo, model.id
            """)
    List<UrbanAnnouncementModelEntity> findByHouseManageNoAndPblancNoOrderByModelNoAsc(@Param("houseManageNo") String houseManageNo, @Param("pblancNo") String pblancNo);
}
