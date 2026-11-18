package com.toady.web.app.announcement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;

import java.time.LocalDateTime;

/**
 * 도시형·오피스텔 공고에 속한 주택형별 상세 정보를 매핑합니다.
 * 테이블: TB_URBTY_OFCTL_LTTOT_PBLANC_MDL. 다른 주택형 테이블과 달리 자동 증가 ID 하나를 키로 쓴다.
 * 스케줄러(toadyScheduler)가 청약홈에서 모아 넣고, 이 API는 읽기만 한다.
 * AnnouncementDetailService가 공고 상세 응답의 models 목록으로 내보낸다.
 */
// @Entity: JPA가 관리하는 클래스. 객체 하나가 테이블 한 행이다.
@Entity
// @Table: 매핑할 테이블 이름.
@Table(name = "TB_URBTY_OFCTL_LTTOT_PBLANC_MDL")
// @Getter/@Setter(Lombok): getter·setter를 만든다. 상세 API는 엔티티를 그대로 JSON으로 내보내는데, 이때 Jackson이 getter로 값을 읽는다.
@Getter
@Setter
// @NoArgsConstructor(PROTECTED): JPA가 조회 결과로 객체를 만들 때 쓰는 빈 생성자. protected라 다른 코드에서 빈 객체를 함부로 만들지 못한다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UrbanAnnouncementModelEntity {

    // @Id + @GeneratedValue(IDENTITY): 키 값은 DB의 AUTO_INCREMENT가 정한다.
    // @Column: 필드와 컬럼 이름을 잇는다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;


    @Column(name = "HOUSE_MANAGE_NO", length = 40)
    private String houseManageNo;

    @Column(name = "PBLANC_NO", length = 40)
    private String pblancNo;

    @Column(name = "MODEL_NO", length = 4)
    private String modelNo;

    @Column(name = "GP", length = 30)
    private String gp;

    @Column(name = "TP", length = 10)
    private String tp;

    @Column(name = "EXCLUSE_AR", length = 17)
    private String excluseAr;

    @Column(name = "SUPLY_HSHLDCO", length = 10)
    private String suplyHshldco;

    @Column(name = "SUPLY_AMOUNT", length = 20)
    private String suplyAmount;

    @Column(name = "SUBSCRPT_REQST_AMOUNT", length = 20)
    private String subscrptReqstAmount;

    // insertable·updatable = false: JPA는 이 컬럼에 값을 쓰지 않는다. 등록일시는 DB 기본값(CURRENT_TIMESTAMP)이 채운다.
    @Column(name = "REG_DT", insertable = false, updatable = false)
    private LocalDateTime regDt;
}
