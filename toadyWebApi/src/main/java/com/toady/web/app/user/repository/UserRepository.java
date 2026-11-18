package com.toady.web.app.user.repository;

import com.toady.web.app.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/**
 * packageName    : com.toady.web.app.user.repository
 * fileName       : UserRepository
 * author         : youn
 * date           : 2022/06/09
 * description    : 유저 repo
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/09        youn       최초 생성
 *
 * TB_USER 조회·저장용 Spring Data JPA 리포지토리.
 * 인터페이스만 선언하면 스프링이 구현을 자동으로 만든다. save·findById 등 기본 메서드는 JpaRepository에서 물려받고,
 * findByUserEmail처럼 메서드 이름 규칙으로 쿼리를 만든다.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    //사용자 정보 조회.
    /** 이메일 주소로 사용자 계정을 조회합니다. */
    public UserEntity findByUserEmail(String userEmail);

    /** 인증 코드 발급·검증과 비밀번호 변경을 계정 단위로 직렬화한다. 반드시 트랜잭션 안에서 호출한다. */
    // @Lock(PESSIMISTIC_WRITE): SELECT ... FOR UPDATE로 행을 잠근다. 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다
    // @Query: 메서드 이름 대신 직접 쓴 JPQL(엔티티 기준 쿼리)을 실행한다. @Param("email")이 :email 자리에 들어간다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.userEmail = :email")
    UserEntity findByEmailForUpdate(@Param("email") String email);

    //닉네임 조회.
    /** 가입 시 닉네임 중복 여부를 확인합니다. */
    public UserEntity findByUserNicName(String userNicName);

}