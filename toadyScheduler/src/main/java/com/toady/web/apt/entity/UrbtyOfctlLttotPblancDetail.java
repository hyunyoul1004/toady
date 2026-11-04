package com.toady.web.apt.entity;

import com.toady.web.apt.entity.id.HouseManagePblancId;
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
@Table(name = "TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL")
@Comment("오피스텔/도시형/민간임대 분양정보 상세")
@IdClass(HouseManagePblancId.class)
@Getter
@Setter
@NoArgsConstructor
public class UrbtyOfctlLttotPblancDetail {

	@Id
	@Comment("주택관리번호")
	@Column(name = "HOUSE_MANAGE_NO", length = 40, nullable = false)
	private String houseManageNo;
	@Id
	@Comment("공고번호")
	@Column(name = "PBLANC_NO", length = 40, nullable = false)
	private String pblancNo;

	@Comment("주택명")
	@Column(name = "HOUSE_NM", length = 200)
	private String houseNm;
	@Comment("주택구분코드")
	@Column(name = "HOUSE_SECD", length = 2)
	private String houseSecd;
	@Comment("주택구분코드명")
	@Column(name = "HOUSE_SECD_NM", length = 4000)
	private String houseSecdNm;
	@Comment("주택상세구분코드")
	@Column(name = "HOUSE_DTL_SECD", length = 2)
	private String houseDtlSecd;
	@Comment("주택상세구분코드명")
	@Column(name = "HOUSE_DTL_SECD_NM", length = 4000)
	private String houseDtlSecdNm;
	@Comment("주택구분")
	@Column(name = "SEARCH_HOUSE_SECD", length = 4)
	private String searchHouseSecd;
	@Comment("공급지역코드")
	@Column(name = "SUBSCRPT_AREA_CODE", length = 3)
	private String subscrptAreaCode;
	@Comment("공급지역명")
	@Column(name = "SUBSCRPT_AREA_CODE_NM", length = 500)
	private String subscrptAreaCodeNm;
	@Comment("공급위치 우편번호")
	@Column(name = "HSSPLY_ZIP", length = 6)
	private String hssplyZip;
	@Comment("공급위치")
	@Column(name = "HSSPLY_HSHLDCO", length = 256)
	private String hssplyHshldco;
	@Comment("공급규모")
	@Column(name = "TOT_SUPLY_HSHLDCO", length = 10)
	private String totSuplyHshldco;
	@Comment("모집공고일")
	@Column(name = "RCRIT_PBLANC_DE", length = 10)
	private String rcritPblancDe;
	@Comment("신문사")
	@Column(name = "NSPRC_NM", length = 200)
	private String nsprcNm;
	@Comment("청약접수시작일")
	@Column(name = "SUBSCRPT_RCEPT_BGNDE", length = 10)
	private String subscrptRceptBgnde;
	@Comment("청약접수종료일")
	@Column(name = "SUBSCRPT_RCEPT_ENDDE", length = 10)
	private String subscrptRceptEndde;
	@Comment("당첨자발표일")
	@Column(name = "PRZWNER_PRESNATN_DE", length = 10)
	private String przwnerPresnatnDe;
	@Comment("계약시작일")
	@Column(name = "CNTRCT_CNCLS_BGNDE", length = 10)
	private String cntrctCnclsBgnde;
	@Comment("계약종료일")
	@Column(name = "CNTRCT_CNCLS_ENDDE", length = 10)
	private String cntrctCnclsEndde;
	@Comment("홈페이지주소")
	@Column(name = "HMPG_ADRES", length = 256)
	private String hmpgAdres;
	@Comment("사업주체명 (시행사)")
	@Column(name = "BSNS_MBY_NM", length = 200)
	private String bsnsMbyNm;
	@Comment("문의처")
	@Column(name = "MDHS_TELNO", length = 30)
	private String mdhsTelno;
	@Comment("입주예정월")
	@Column(name = "MVN_PREARNGE_YM", length = 6)
	private String mvnPrearngeYm;
	@Comment("모집공고 상세 URL")
	@Column(name = "PBLANC_URL", length = 300)
	private String pblancUrl;

	@Comment("등록일시")
	@Column(name = "REG_DT", insertable = false, updatable = false)
	private LocalDateTime regDt;
}
