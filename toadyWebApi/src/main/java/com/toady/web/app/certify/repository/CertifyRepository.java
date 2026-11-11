package com.toady.web.app.certify.repository;

import com.toady.web.app.certify.entity.CertifyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * packageName    : com.toady.web.app.certify.repository
 * fileName       : CertifyRepository
 * author         : youn
 * date           : 2022/06/11
 * description    : 인증 repo
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/11        youn       최초 생성
 */
public interface CertifyRepository extends JpaRepository<CertifyEntity, Long> {

    /**
     * 인증값 조회.
     * @param CertifyType
     * @param CertifySendType
     * @param certifyCode
     * @param userSeq
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    public CertifyEntity findByCertifyTypeAndCertifySendTypeAndCertifyCodeAndCreateUserSeq(String CertifyType, String CertifySendType, String certifyCode, Long userSeq);


    /**
     * 최근 전송된 내역 조회.
     * 연속으로 보내지 못하도록 시간 체크 위함.
     * @param CertifyType
     * @param CertifySendType
     * @param userSeq
     * @return
     */
    public CertifyEntity findTop1ByCertifyTypeAndCertifySendTypeAndCreateUserSeqOrderByCreateDtDesc(String CertifyType, String CertifySendType, Long userSeq);

    /** 비밀번호 변경 후 같은 계정에 발급된 미사용 코드를 모두 폐기한다. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update CertifyEntity c set c.certifyYn = 'Y', c.updateDt = :now, c.updateUserSeq = :userSeq where c.createUserSeq = :userSeq and c.certifyType = 'FIND_PW' and c.certifyYn = 'N'")
    int invalidatePasswordCodes(@org.springframework.data.repository.query.Param("userSeq") Long userSeq,
            @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);


    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update CertifyEntity c set c.certifyYn = 'Y', c.updateDt = :now, c.updateUserSeq = :userSeq where c.createUserSeq = :userSeq and c.certifyType = 'ACCOUNT' and c.certifyYn = 'N'")
    int invalidateAccountCodes(@org.springframework.data.repository.query.Param("userSeq") Long userSeq,
            @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
}