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
@Table(name = "TB_REMNDR_LTTOT_PBLANC_MDL")
@Comment("APT 잔여세대 주택형별 상세")
@IdClass(HouseManagePblancModelId.class)
@Getter
@Setter
@NoArgsConstructor
public class RemndrLttotPblancMdl {

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
	@Comment("공급금액 (분양최고금액)")
	@Column(name = "LTTOT_TOP_AMOUNT", length = 20)
	private String lttotTopAmount;

	@Comment("등록일시")
	@Column(name = "REG_DT", insertable = false, updatable = false)
	private LocalDateTime regDt;
}
