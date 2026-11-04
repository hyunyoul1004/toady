package com.toady.web.app.announcement.entity;

import com.toady.web.app.announcement.entity.id.HouseTypeAnnouncementId;
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

/** APT 공고의 주택형별 특별공급 신청현황을 매핑합니다. */
@Entity
@Table(name = "TB_APT_SPSPLY_REQST_STUS")
@Comment("APT 특별공급 신청현황")
@IdClass(HouseTypeAnnouncementId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AptSpecialSupplyStatusEntity {
    @Id @Column(name = "HOUSE_MANAGE_NO", length = 40) private String houseManageNo;
    @Id @Column(name = "PBLANC_NO", length = 40) private String pblancNo;
    @Id @Column(name = "HOUSE_TY", length = 17) private String houseTy;
    @Column(name = "SPSPLY_HSHLDCO", length = 10) private String spsplyHshldco;
    @Column(name = "MNYCH_HSHLDCO", length = 10) private String mnychHshldco;
    @Column(name = "NWWDS_NMTW_HSHLDCO", length = 10) private String nwwdsNmtwHshldco;
    @Column(name = "LFE_FRST_HSHLDCO", length = 10) private String lfeFrstHshldco;
    @Column(name = "YGMN_HSHLDCO", length = 10) private String ygmnHshldco;
    @Column(name = "OLD_PARNTS_SUPORT_HSHLDCO", length = 10) private String oldParntsSuportHshldco;
    @Column(name = "NWBB_NWBBSHR_HSHLDCO", length = 10) private String nwbbNwbbshrHshldco;
    @Column(name = "INSTT_RECOMEND_HSHLDCO", length = 10) private String insttRecomendHshldco;
    @Column(name = "TRANSR_INSTT_ENFSN_HSHLDCO", length = 10) private String transrInsttEnfsnHshldco;
    @Column(name = "CRSPAREA_MNYCH_CNT", length = 10) private String crspareaMnychCnt;
    @Column(name = "CTPRVN_MNYCH_CNT", length = 10) private String ctprvnMnychCnt;
    @Column(name = "ETC_AREA_MNYCH_CNT", length = 10) private String etcAreaMnychCnt;
    @Column(name = "CRSPAREA_NWWDS_NMTW_CNT", length = 10) private String crspareaNwwdsNmtwCnt;
    @Column(name = "CTPRVN_NWWDS_NMTW_CNT", length = 10) private String ctprvnNwwdsNmtwCnt;
    @Column(name = "ETC_AREA_NWWDS_NMTW_CNT", length = 10) private String etcAreaNwwdsNmtwCnt;
    @Column(name = "CRSPAREA_LFE_FRST_CNT", length = 10) private String crspareaLfeFrstCnt;
    @Column(name = "CTPRVN_LFE_FRST_CNT", length = 10) private String ctprvnLfeFrstCnt;
    @Column(name = "ETC_AREA_LFE_FRST_CNT", length = 10) private String etcAreaLfeFrstCnt;
    @Column(name = "CRSPAREA_YGMN_CNT", length = 10) private String crspareaYgmnCnt;
    @Column(name = "CTPRVN_YGMN_CNT", length = 10) private String ctprvnYgmnCnt;
    @Column(name = "ETC_AREA_YGMN_CNT", length = 10) private String etcAreaYgmnCnt;
    @Column(name = "CRSPAREA_OPS_CNT", length = 10) private String crspareaOpsCnt;
    @Column(name = "CTPRVN_OPS_CNT", length = 10) private String ctprvnOpsCnt;
    @Column(name = "ETC_AREA_OPS_CNT", length = 10) private String etcAreaOpsCnt;
    @Column(name = "CRSPAREA_NWBB_NWBBSHR_CNT", length = 10) private String crspareaNwbbNwbbshrCnt;
    @Column(name = "CTPRVN_NWBB_NWBBSHR_CNT", length = 10) private String ctprvnNwbbNwbbshrCnt;
    @Column(name = "ETC_AREA_NWBB_NWBBSHR_CNT", length = 10) private String etcAreaNwbbNwbbshrCnt;
    @Column(name = "INSTT_RECOMEND_DCSN_CNT", length = 10) private String insttRecomendDcsnCnt;
    @Column(name = "INSTT_RECOMEND_PREPAR_CNT", length = 10) private String insttRecomendPreparCnt;
    @Column(name = "TRANSR_INSTT_ENFSN_CNT", length = 10) private String transrInsttEnfsnCnt;
    @Column(name = "SUBSCRPT_RESULT_NM", length = 10) private String subscrptResultNm;
    @Column(name = "REG_DT", insertable = false, updatable = false) private LocalDateTime regDt;
}
