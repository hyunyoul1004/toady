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


}