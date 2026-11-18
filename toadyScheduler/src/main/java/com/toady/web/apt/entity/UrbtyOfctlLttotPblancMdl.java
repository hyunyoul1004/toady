package com.toady.web.apt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Comment;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 오피스텔/도시형/민간임대 주택형별 상세 엔티티. 테이블: TB_URBTY_OFCTL_LTTOT_PBLANC_MDL.
 * 청약홈 getUrbtyOfctlLttotPblancMdl 응답 한 행이다. 이 API는 같은 키의 주택형이 여러 번 올 수 있어
 * 다른 주택형 테이블과 달리 복합키 대신 DB가 매기는 일련번호(ID)를 기본키로 쓴다.
 * 저장은 ApplyhomeDataPersistenceService.upsertUrbanModels가 공고 단위로 최신 응답과 비교해 바뀐 행만 교체한다.
 */
@Entity
@Table(name = "TB_URBTY_OFCTL_LTTOT_PBLANC_MDL")
@Comment("오피스텔/도시형/민간임대 주택형별 상세")
@Getter
@Setter
@NoArgsConstructor
public class UrbtyOfctlLttotPblancMdl {

	// @GeneratedValue(IDENTITY): 값은 DB의 AUTO_INCREMENT가 정한다.
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Comment("내부 식별자")
	@Column(name = "ID", nullable = false)
	private Long id;

	@Comment("주택관리번호")
	@Column(name = "HOUSE_MANAGE_NO", length = 40, nullable = false)
	private String houseManageNo;

	@Comment("공고번호")
	@Column(name = "PBLANC_NO", length = 40, nullable = false)
	private String pblancNo;

	@Comment("모델번호")
	@Column(name = "MODEL_NO", length = 4)
	private String modelNo;

	@Comment("군")
	@Column(name = "GP", length = 30)
	private String gp;
	@Comment("타입")
	@Column(name = "TP", length = 10)
	private String tp;
	@Comment("전용면적")
	@Column(name = "EXCLUSE_AR", length = 17)
	private String excluseAr;
	@Comment("공급세대수")
	@Column(name = "SUPLY_HSHLDCO", length = 10)
	private String suplyHshldco;
	@Comment("공급금액(분양최고급액)")
	@Column(name = "SUPLY_AMOUNT", length = 20)
	private String suplyAmount;
	@Comment("청약신청금")
	@Column(name = "SUBSCRPT_REQST_AMOUNT", length = 20)
	private String subscrptReqstAmount;

	@Comment("등록일시")
	@Column(name = "REG_DT", insertable = false, updatable = false)
	private LocalDateTime regDt;
}
