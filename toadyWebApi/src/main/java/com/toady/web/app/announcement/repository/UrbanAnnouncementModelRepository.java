package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.UrbanAnnouncementModelEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 도시형·오피스텔 공고의 주택형 상세 정보를 저장하고 조회합니다. */
public interface UrbanAnnouncementModelRepository extends JpaRepository<UrbanAnnouncementModelEntity, Long> {
    @Query("""
            select model from UrbanAnnouncementModelEntity model
            where model.houseManageNo = :houseManageNo and model.pblancNo = :pblancNo
            order by model.modelNo, model.id
            """)
    List<UrbanAnnouncementModelEntity> findByHouseManageNoAndPblancNoOrderByModelNoAsc(@Param("houseManageNo") String houseManageNo, @Param("pblancNo") String pblancNo);
}
