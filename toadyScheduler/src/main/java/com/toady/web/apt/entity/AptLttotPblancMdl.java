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
@Table(name = "TB_APT_LTTOT_PBLANC_MDL")
@Comment("APT 분양정보 주택형별 상세")
@IdClass(HouseManagePblancModelId.class)
@Getter
@Setter
@NoArgsConstructor
public class AptLttotPblancMdl {

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
	@Column(name = "MODEL_NO", length = 2, nullable = false)
	private String modelNo;

	@Comment("주택형")
	@Column(name = "HOUSE_TY", length = 17)
	private String houseTy;
	@Comment("공급면적")
	@Column(name = "SUPLY_AR", length = 17)
	private String suplyAr;
	@Comment("공급세대수")
	@Column(name = "SUPLY_HSHLDCO", length = 10)
	private String suplyHshldco;
	@Comment("특별공급세대수")
	@Column(name = "SPSPLY_HSHLDCO", length = 10)
	private String spsplyHshldco;
	@Comment("특별공급-다자녀가구 세대수")
	@Column(name = "MNYCH_HSHLDCO", length = 10)
	private String mnychHshldco;
	@Comment("특별공급-신혼부부 세대수")
	@Column(name = "NWWDS_HSHLDCO", length = 10)
	private String nwwdsHshldco;
	@Comment("특별공급-생애최초 세대수")
	@Column(name = "LFE_FRST_HSHLDCO", length = 10)
	private String lfeFrstHshldco;
	@Comment("특별공급-노부모부양 세대수")
	@Column(name = "OLD_PARNTS_SUPORT_HSHLDCO", length = 10)
	private String oldParntsSuportHshldco;
	@Comment("특별공급-기관추천 세대수")
	@Column(name = "INSTT_RECOMEND_HSHLDCO", length = 10)
	private String insttRecomendHshldco;
	@Comment("특별공급-기타 세대수")
	@Column(name = "ETC_HSHLDCO", length = 10)
	private String etcHshldco;
	@Comment("특별공급-이전기관 세대수")
	@Column(name = "TRANSR_INSTT_ENFSN_HSHLDCO", length = 10)
	private String transrInsttEnfsnHshldco;
	@Comment("특별공급-청년 세대수")
	@Column(name = "YGMN_HSHLDCO", length = 10)
	private String ygmnHshldco;
	@Comment("특별공급-신생아 세대수")
	@Column(name = "NWBB_HSHLDCO", length = 10)
	private String nwbbHshldco;
	@Comment("공급금액 (분양최고금액)")
	@Column(name = "LTTOT_TOP_AMOUNT", length = 20)
	private String lttotTopAmount;

	@Comment("등록일시")
	@Column(name = "REG_DT", insertable = false, updatable = false)
	private LocalDateTime regDt;
}
