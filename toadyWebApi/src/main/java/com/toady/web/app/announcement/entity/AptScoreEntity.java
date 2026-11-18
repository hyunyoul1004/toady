package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.AptScoreId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * APT 공고의 거주지역·주택형별 당첨가점을 매핑합니다.
 * 테이블: TB_APT_LTTOT_PBLANC_SCORE, 키: AptScoreId(주택관리번호·공고번호·모델번호·거주지역).
 * 스케줄러가 청약홈 결과를 매일 다시 받아 채운다. 이 API는 읽기만 한다.
 * AnnouncementCompetitionService가 읽고, 컨트롤러가 엔티티 목록을 그대로 JSON으로 내보낸다.
 * 건수·경쟁률도 숫자로 바꾸지 않고 청약홈 값 그대로 문자열로 둔다.
 */
// @Entity: JPA가 관리하는 클래스. 객체 하나가 테이블 한 행이다.
@Entity
// @Table: 매핑할 테이블 이름.
@Table(name = "TB_APT_LTTOT_PBLANC_SCORE")
// @Comment(Hibernate): 스키마를 자동 생성할 때 넣는 설명이다. 이 프로젝트는 ddl-auto: none이라 읽는 사람을 위한 메모 역할만 한다.
@Comment("APT 당첨가점 조회")
// @IdClass: 키가 컬럼 여러 개라 키 클래스를 따로 둔다. 아래 @Id가 붙은 필드들이 모여 하나의 키가 된다.
@IdClass(AptScoreId.class)
// @Getter(Lombok): getter만 만든다. 읽기 전용이라 setter는 두지 않았다. 응답 JSON도 getter로 만들어진다.
@Getter
// @NoArgsConstructor(PROTECTED): JPA가 조회 결과로 객체를 만들 때 쓰는 빈 생성자. protected라 다른 코드에서 빈 객체를 함부로 만들지 못한다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AptScoreEntity {
    // @Id가 붙은 필드들(주택관리번호·공고번호·모델번호·거주지역)이 모여 복합키가 된다. @Column은 필드와 컬럼 이름을 잇는다.
    @Id @Column(name = "HOUSE_MANAGE_NO", length = 40) private String houseManageNo;
    @Id @Column(name = "PBLANC_NO", length = 40) private String pblancNo;
    @Id @Column(name = "MODEL_NO", length = 2) private String modelNo;
    @Id @Column(name = "RESIDE_SECD", length = 2) private String resideSecd;
    @Column(name = "HOUSE_TY", length = 17) private String houseTy;
    @Column(name = "RESIDE_SENM", length = 200) private String resideSenm;
    @Column(name = "LWET_SCORE", length = 5) private String lwetScore;
    @Column(name = "TOP_SCORE", length = 5) private String topScore;
    @Column(name = "AVRG_SCORE", length = 5) private String avrgScore;
    // insertable·updatable = false: JPA는 이 컬럼에 값을 쓰지 않는다. 등록일시는 DB 기본값(CURRENT_TIMESTAMP)이 채운다.
    @Column(name = "REG_DT", insertable = false, updatable = false) private LocalDateTime regDt;
}
