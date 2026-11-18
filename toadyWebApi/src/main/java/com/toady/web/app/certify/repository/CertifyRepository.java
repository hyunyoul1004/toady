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
 *
 * TB_CERTIFY용 Spring Data JPA 리포지토리. 메서드 이름 규칙(findBy...And...OrderBy...)으로 쿼리가 자동 생성된다.
 * CertifyService만 쓴다.
 */
public interface CertifyRepository extends JpaRepository<CertifyEntity, Long> {

    /**
     * 인증값 조회.
     * @param certifyType
     * @param certifySendType
     * @param certifyCode
     * @param userSeq
     */
    // @Lock(PESSIMISTIC_WRITE): SELECT ... FOR UPDATE. 같은 코드를 동시에 두 번 쓰지 못하게 행을 잠근다(트랜잭션 안에서만 의미 있다)
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
    // @Modifying: @Query가 SELECT가 아니라 UPDATE/DELETE라는 표시. 반환값은 바뀐 행 수
    // flushAutomatically = true: 실행 전에 아직 DB에 안 보낸 엔티티 변경을 먼저 flush한다
    // @Query: JPQL. 테이블·컬럼이 아니라 엔티티·필드 이름으로 쓴다
    @Modifying(flushAutomatically = true)
    @Query("update CertifyEntity c set c.certifyYn = 'Y', c.updateDt = :now, c.updateUserSeq = :userSeq where c.createUserSeq = :userSeq and c.certifyType = 'FIND_PW' and c.certifyYn = 'N'")
    int invalidatePasswordCodes(@Param("userSeq") Long userSeq,
            @Param("now") LocalDateTime now);


    /** 가입 인증 메일을 다시 보낼 때 이전에 보낸 가입 인증 코드를 모두 폐기한다. */
    @Modifying(flushAutomatically = true)
    @Query("update CertifyEntity c set c.certifyYn = 'Y', c.updateDt = :now, c.updateUserSeq = :userSeq where c.createUserSeq = :userSeq and c.certifyType = 'ACCOUNT' and c.certifyYn = 'N'")
    int invalidateAccountCodes(@Param("userSeq") Long userSeq,
            @Param("now") LocalDateTime now);
    /** 하루 비밀번호 재설정 메일 수(계정별 상한 확인용). */
    long countByCertifyTypeAndCreateUserSeqAndCreateDtGreaterThanEqual(String certifyType, Long createUserSeq, java.time.LocalDateTime from);
}
