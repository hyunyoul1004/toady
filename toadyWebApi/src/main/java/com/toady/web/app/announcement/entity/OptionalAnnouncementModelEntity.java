package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.AnnouncementModelId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;

import java.time.LocalDateTime;

/**
 * 임의공급 공고에 속한 주택형별 상세 정보를 매핑합니다.
 * 테이블: TB_OPT_LTTOT_PBLANC_MDL, 키: (주택관리번호, 공고번호, 모델번호) = AnnouncementModelId.
 * 스케줄러(toadyScheduler)가 청약홈에서 모아 넣고, 이 API는 읽기만 한다.
 * AnnouncementDetailService가 공고 상세 응답의 models 목록으로 내보낸다.
 */
// @Entity: JPA가 관리하는 클래스. 객체 하나가 테이블 한 행이다.
@Entity
// @Table: 매핑할 테이블 이름.
@Table(name = "TB_OPT_LTTOT_PBLANC_MDL")
// @IdClass: 키가 컬럼 여러 개라 키 클래스를 따로 둔다. 아래 @Id가 붙은 필드들이 모여 하나의 키가 된다.
@IdClass(AnnouncementModelId.class)
// @Getter/@Setter(Lombok): getter·setter를 만든다. 상세 API는 엔티티를 그대로 JSON으로 내보내는데, 이때 Jackson이 getter로 값을 읽는다.
@Getter
@Setter
// @NoArgsConstructor(PROTECTED): JPA가 조회 결과로 객체를 만들 때 쓰는 빈 생성자. protected라 다른 코드에서 빈 객체를 함부로 만들지 못한다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionalAnnouncementModelEntity {

    // @Id: 키 컬럼. 주택관리번호·공고번호·모델번호 세 개가 모여 복합키가 된다.
    @Id
    // @Column: 필드와 컬럼 이름을 잇는다. length는 문자열 최대 길이(스키마 정보)다.
    @Column(name = "HOUSE_MANAGE_NO", length = 40)
    private String houseManageNo;

    @Id
    @Column(name = "PBLANC_NO", length = 40)
    private String pblancNo;

    @Id
    @Column(name = "MODEL_NO", length = 2)
    private String modelNo;

    @Column(name = "HOUSE_TY", length = 17)
    private String houseTy;

    @Column(name = "SUPLY_HSHLDCO", length = 10)
    private String suplyHshldco;

    @Column(name = "LTTOT_TOP_AMOUNT", length = 20)
    private String lttotTopAmount;

    // insertable·updatable = false: JPA는 이 컬럼에 값을 쓰지 않는다. 등록일시는 DB 기본값(CURRENT_TIMESTAMP)이 채운다.
    @Column(name = "REG_DT", insertable = false, updatable = false)
    private LocalDateTime regDt;
}
