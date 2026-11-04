package com.toady.web.apt.entity;

import com.toady.web.apt.entity.id.HouseManagePblancModelId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Comment;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "TB_PBL_PVT_RENT_LTTOT_PBLANC_MDL")
@Comment("공공지원 민간임대 주택형별 상세")
@IdClass(HouseManagePblancModelId.class)
@Getter
@Setter
@NoArgsConstructor
public class PblPvtRentLttotPblancMdl {

	@Id
	@Comment("주택관리번호")
	@Column(name = "HOUSE_MANAGE_NO", length = 40, nullable = false)
	private String houseManageNo;
	@Id
	@Comment("공고번호")
	@Column(name = "PBLANC_NO", length = 40, nullable = false)
	private String pblancNo;
	@Id
	@Comment("모델번호")
	@Column(name = "MODEL_NO", length = 4, nullable = false)
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
	@Comment("공급면적")
	@Column(name = "SUPPLY_AR", length = 17)
	private String supplyAr;
	@Comment("계약면적")
	@Column(name = "CNTRCT_AR", length = 17)
	private String cntrctAr;
	@Comment("공급세대수")
	@Column(name = "SUPLY_HSHLDCO", length = 10)
	private String suplyHshldco;
	@Comment("일반공급 세대수")
	@Column(name = "GNSPLY_HSHLDCO", length = 10)
	private String gnsplyHshldco;
	@Comment("특별공급 청년 세대수")
	@Column(name = "SPSPLY_YGMN_HSHLDCO", length = 10)
	private String spsplyYgmnHshldco;
	@Comment("특별공급 신혼 세대수")
	@Column(name = "SPSPLY_NEW_MRRG_HSHLDCO", length = 10)
	private String spsplyNewMrrgHshldco;
	@Comment("특별공급 고령자 세대수")
	@Column(name = "SPSPLY_AGED_HSHLDCO", length = 10)
	private String spsplyAgedHshldco;
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
