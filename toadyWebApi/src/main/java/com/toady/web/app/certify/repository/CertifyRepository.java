package com.toady.web.app.certify.repository;

import com.toady.web.app.certify.entity.CertifyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;

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
     * @param certifyType
     * @param certifySendType
     * @param certifyCode
     * @param userSeq
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    public CertifyEntity findByCertifyTypeAndCertifySendTypeAndCertifyCodeAndCreateUserSeq(String certifyType, String certifySendType, String certifyCode, Long userSeq);


    /**
     * 최근 전송된 내역 조회.
     * 연속으로 보내지 못하도록 시간 체크 위함.
     * @param certifyType
     * @param certifySendType
     * @param userSeq
     * @return
     */
    public CertifyEntity findTop1ByCertifyTypeAndCertifySendTypeAndCreateUserSeqOrderByCreateDtDesc(String certifyType, String certifySendType, Long userSeq);

    /** 비밀번호 변경 후 같은 계정에 발급된 미사용 코드를 모두 폐기한다. */
    @Modifying(flushAutomatically = true)
    @Query("update CertifyEntity c set c.certifyYn = 'Y', c.updateDt = :now, c.updateUserSeq = :userSeq where c.createUserSeq = :userSeq and c.certifyType = 'FIND_PW' and c.certifyYn = 'N'")
    int invalidatePasswordCodes(@Param("userSeq") Long userSeq,
            @Param("now") LocalDateTime now);


    @Modifying(flushAutomatically = true)
    @Query("update CertifyEntity c set c.certifyYn = 'Y', c.updateDt = :now, c.updateUserSeq = :userSeq where c.createUserSeq = :userSeq and c.certifyType = 'ACCOUNT' and c.certifyYn = 'N'")
    int invalidateAccountCodes(@Param("userSeq") Long userSeq,
            @Param("now") LocalDateTime now);
}