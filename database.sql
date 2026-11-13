-- TOADY 공유 DB 설정 (MariaDB)
-- 저장소 루트에서 필요한 DB를 선택해 직접 실행합니다. 앱·배포에서 자동 실행하지 않습니다.
-- 아래 생성문은 없는 테이블만 추가하며 기존 테이블의 열·데이터는 변경하지 않습니다.
-- 공고 상세·주택형·회원 등 서비스 전체의 초기 스키마는 별도로 준비되어 있어야 합니다.

-- ============================================================================
-- 1. 수집 관리자: 관리자 기능을 활성화하기 전에 필요한 테이블
-- ============================================================================
-- 관리자 화면 활성화 전에 직접 실행하세요. 기존 수집 테이블은 변경하지 않습니다.
-- 수집 데이터와 분리된 실행 이력이다. 과거 이력은 다시 실행할 때 덮어쓰지 않는다.
CREATE TABLE IF NOT EXISTS TB_SCHEDULER_ADMIN_JOB (
    JOB_ID VARCHAR(36) NOT NULL,
    JOB_KIND VARCHAR(24) NOT NULL,
    JOB_SOURCE VARCHAR(16) NOT NULL,
    START_DATE DATE NOT NULL,
    END_DATE DATE NOT NULL,
    STATUS VARCHAR(24) NOT NULL,
    -- 진행률은 월별 구간 수 기준이며 API 응답 행 수가 아니다.
    TOTAL_CHUNKS INT NOT NULL,
    COMPLETED_CHUNKS INT NOT NULL DEFAULT 0,
    CANCEL_REQUESTED BOOLEAN NOT NULL DEFAULT FALSE,
    REQUESTED_BY VARCHAR(100) NOT NULL,
    MESSAGE VARCHAR(1000) NOT NULL,
    CREATED_AT DATETIME NOT NULL,
    STARTED_AT DATETIME NULL,
    FINISHED_AT DATETIME NULL,
    -- 마지막 프로세스 생존 신호. 수집 진행률이나 원본 API의 정상 응답을 의미하지 않는다.
    HEARTBEAT_AT DATETIME NOT NULL,
    -- 활성 작업만 1을 사용하고 종료 시 NULL로 반환해 여러 종료 이력을 보관한다.
    ACTIVE_SLOT INT NULL,
    -- 자동 실행은 종류+분 단위 키로 중복 등록을 막고, 수동 실행은 NULL로 재실행을 허용한다.
    TRIGGER_KEY VARCHAR(64) NULL,
    PRIMARY KEY (JOB_ID),
    UNIQUE (ACTIVE_SLOT),
    UNIQUE (TRIGGER_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 앱은 없는 종류의 기본값만 최초 등록한다. 이후 화면에서 저장한 설정은 재시작해도 유지된다.
CREATE TABLE IF NOT EXISTS TB_SCHEDULER_ADMIN_SCHEDULE (
    JOB_KIND VARCHAR(24) NOT NULL,
    ENABLED BOOLEAN NOT NULL,
    CRON_EXPRESSION VARCHAR(100) NOT NULL,
    LOOKBACK_DAYS INT NOT NULL,
    UPDATED_AT DATETIME NOT NULL,
    PRIMARY KEY (JOB_KIND)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 최초 스케줄은 앱이 환경 설정을 기준으로 INSERT합니다.
-- ACTIVE_SLOT=1은 전체 관리자 인스턴스에서 하나의 작업만 실행되도록 보장합니다.

-- ============================================================================
-- 2. 공고 댓글
-- ============================================================================
CREATE TABLE IF NOT EXISTS TB_LTTOT_PBLANC_COMMENT (
    COMMENT_SEQ BIGINT NOT NULL AUTO_INCREMENT,
    ANNOUNCEMENT_TYPE VARCHAR(20) NOT NULL,
    HOUSE_MANAGE_NO VARCHAR(40) NOT NULL,
    PBLANC_NO VARCHAR(40) NOT NULL,
    PARENT_COMMENT_SEQ BIGINT NULL,
    CONTENT TEXT NOT NULL,
    RECOMMEND INT NOT NULL DEFAULT 0,
    OPPOSE INT NOT NULL DEFAULT 0,
    DELETE_YN VARCHAR(1) NOT NULL DEFAULT 'N',
    CREATE_USER_SEQ BIGINT NOT NULL,
    REG_DT DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATE_USER_SEQ BIGINT NULL,
    UPDATE_DT DATETIME NULL,
    PRIMARY KEY (COMMENT_SEQ),
    KEY IDX_LTTOT_COMMENT_ANNOUNCEMENT (ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO, DELETE_YN, REG_DT),
    KEY IDX_LTTOT_COMMENT_PARENT (PARENT_COMMENT_SEQ)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='신규 분양공고 댓글';

-- ============================================================================
-- 3. 경쟁률·당첨 가점·특별공급 신청현황
-- ============================================================================
CREATE TABLE IF NOT EXISTS TB_APT_LTTOT_PBLANC_CMPET (
    HOUSE_MANAGE_NO VARCHAR(40) NOT NULL COMMENT '주택관리번호',
    PBLANC_NO VARCHAR(40) NOT NULL COMMENT '공고번호',
    MODEL_NO VARCHAR(2) NOT NULL COMMENT '모델번호',
    SUBSCRPT_RANK_CODE VARCHAR(10) NOT NULL COMMENT '순위',
    RESIDE_SECD VARCHAR(2) NOT NULL COMMENT '거주코드',
    HOUSE_TY VARCHAR(17) COMMENT '주택형',
    SUPLY_HSHLDCO VARCHAR(10) COMMENT '공급세대수',
    RESIDE_SENM VARCHAR(200) COMMENT '거주지역',
    REQ_CNT VARCHAR(20) COMMENT '접수건수',
    CMPET_RATE VARCHAR(20) COMMENT '경쟁률',
    REG_DT DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO, SUBSCRPT_RANK_CODE, RESIDE_SECD)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='APT 분양정보/경쟁률';

CREATE TABLE IF NOT EXISTS TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET (
    HOUSE_MANAGE_NO VARCHAR(40) NOT NULL COMMENT '주택관리번호',
    PBLANC_NO VARCHAR(40) NOT NULL COMMENT '공고번호',
    MODEL_NO VARCHAR(2) NOT NULL COMMENT '모델번호',
    HOUSE_TY VARCHAR(17) COMMENT '주택형',
    SUPLY_HSHLDCO VARCHAR(10) COMMENT '공급세대수',
    RESIDNT_PRIOR_AT VARCHAR(1) COMMENT '거주자 우선여부',
    RESIDENT_PRIOR_SENM VARCHAR(200) COMMENT '공급구분명',
    REQ_CNT VARCHAR(20) COMMENT '접수건수',
    CMPET_RATE VARCHAR(20) COMMENT '경쟁률',
    REG_DT DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='오피스텔/도시형 등 경쟁률';

CREATE TABLE IF NOT EXISTS TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET (
    HOUSE_MANAGE_NO VARCHAR(40) NOT NULL COMMENT '주택관리번호',
    PBLANC_NO VARCHAR(40) NOT NULL COMMENT '공고번호',
    MODEL_NO VARCHAR(2) NOT NULL COMMENT '모델번호',
    SPSPLY_KND_CODE VARCHAR(2) NOT NULL COMMENT '공급유형코드',
    HOUSE_TY VARCHAR(17) COMMENT '주택형',
    SUPLY_HSHLDCO VARCHAR(10) COMMENT '공급세대수',
    SPSPLY_KND_NM VARCHAR(200) COMMENT '공급유형',
    SPSPLY_KND_HSHLDCO VARCHAR(10) COMMENT '배정세대수',
    REQ_CNT VARCHAR(20) COMMENT '접수건수',
    CMPET_RATE VARCHAR(20) COMMENT '경쟁률',
    REG_DT DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO, SPSPLY_KND_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='공공지원 민간임대 경쟁률';

CREATE TABLE IF NOT EXISTS TB_CANC_RESPL_LTTOT_PBLANC_CMPET (
    HOUSE_MANAGE_NO VARCHAR(40) NOT NULL COMMENT '주택관리번호',
    PBLANC_NO VARCHAR(40) NOT NULL COMMENT '공고번호',
    MODEL_NO VARCHAR(2) NOT NULL COMMENT '모델번호',
    HOUSE_TY VARCHAR(17) COMMENT '주택형',
    SUPLY_HSHLDCO VARCHAR(10) COMMENT '공급세대수',
    NORMAL_HSHLDCO VARCHAR(10) COMMENT '일반공급 배정세대수',
    MNYCH_HSHLDCO VARCHAR(10) COMMENT '다자녀가구 배정세대수',
    NWWDS_HSHLDCO VARCHAR(10) COMMENT '신혼부부 배정세대수',
    LFE_FRST_HSHLDCO VARCHAR(10) COMMENT '생애최초 배정세대수',
    OLD_PARNTS_SUPORT_HSHLDCO VARCHAR(10) COMMENT '노부모부양 배정세대수',
    INSTT_RECOMEND_HSHLDCO VARCHAR(10) COMMENT '기관추천 배정세대수',
    NORMAL_REQ_CNT VARCHAR(20) COMMENT '일반공급 접수건수',
    MNYCH_REQ_CNT VARCHAR(20) COMMENT '다자녀가구 접수건수',
    NWWDS_REQ_CNT VARCHAR(20) COMMENT '신혼부부 접수건수',
    LFE_FRST_REQ_CNT VARCHAR(20) COMMENT '생애최초 접수건수',
    OLD_PARNTS_SUPORT_REQ_CNT VARCHAR(20) COMMENT '노부모부양 접수건수',
    INSTT_RECOMEND_REQ_CNT VARCHAR(20) COMMENT '기관추천 접수건수',
    NORMAL_CMPET_RATE VARCHAR(20) COMMENT '일반공급 경쟁률',
    MNYCH_CMPET_RATE VARCHAR(20) COMMENT '다자녀가구 경쟁률',
    NWWDS_CMPET_RATE VARCHAR(20) COMMENT '신혼부부 경쟁률',
    LFE_FRST_CMPET_RATE VARCHAR(20) COMMENT '생애최초 경쟁률',
    OLD_PARNTS_SUPORT_CMPET_RATE VARCHAR(20) COMMENT '노부모부양 경쟁률',
    INSTT_RECOMEND_CMPET_RATE VARCHAR(20) COMMENT '기관추천 경쟁률',
    REG_DT DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='취소후재공급 경쟁률';

CREATE TABLE IF NOT EXISTS TB_REMNDR_LTTOT_PBLANC_CMPET (
    HOUSE_MANAGE_NO VARCHAR(40) NOT NULL COMMENT '주택관리번호',
    PBLANC_NO VARCHAR(40) NOT NULL COMMENT '공고번호',
    HOUSE_TY VARCHAR(17) NOT NULL COMMENT '주택형',
    REMNDR_HSHLD_PBLANC_TYCD VARCHAR(2) NOT NULL COMMENT '사전/사후 구분',
    SUPLY_HSHLDCO VARCHAR(10) COMMENT '공급세대수',
    REQ_CNT VARCHAR(20) COMMENT '접수건수',
    CMPET_RATE VARCHAR(20) COMMENT '경쟁률',
    REG_DT DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_TY, REMNDR_HSHLD_PBLANC_TYCD)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='잔여세대 경쟁률';

CREATE TABLE IF NOT EXISTS TB_APT_LTTOT_PBLANC_SCORE (
    HOUSE_MANAGE_NO VARCHAR(40) NOT NULL COMMENT '주택관리번호',
    PBLANC_NO VARCHAR(40) NOT NULL COMMENT '공고번호',
    MODEL_NO VARCHAR(2) NOT NULL COMMENT '모델번호',
    RESIDE_SECD VARCHAR(2) NOT NULL COMMENT '거주코드',
    HOUSE_TY VARCHAR(17) COMMENT '주택형',
    RESIDE_SENM VARCHAR(200) COMMENT '거주지역',
    LWET_SCORE VARCHAR(5) COMMENT '최저당첨가점',
    TOP_SCORE VARCHAR(5) COMMENT '최고당첨가점',
    AVRG_SCORE VARCHAR(5) COMMENT '평균당첨가점',
    REG_DT DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO, RESIDE_SECD)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='APT 당첨가점 조회';

CREATE TABLE IF NOT EXISTS TB_OPT_LTTOT_PBLANC_CMPET (
    HOUSE_MANAGE_NO VARCHAR(40) NOT NULL COMMENT '주택관리번호',
    PBLANC_NO VARCHAR(40) NOT NULL COMMENT '공고번호',
    HOUSE_TY VARCHAR(17) NOT NULL COMMENT '주택형',
    SUPLY_HSHLDCO VARCHAR(10) COMMENT '공급세대수',
    REQ_CNT VARCHAR(20) COMMENT '접수건수',
    CMPET_RATE VARCHAR(20) COMMENT '경쟁률',
    REG_DT DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_TY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='임의공급 경쟁률';

CREATE TABLE IF NOT EXISTS TB_APT_SPSPLY_REQST_STUS (
    HOUSE_MANAGE_NO VARCHAR(40) NOT NULL COMMENT '주택관리번호',
    PBLANC_NO VARCHAR(40) NOT NULL COMMENT '공고번호',
    HOUSE_TY VARCHAR(17) NOT NULL COMMENT '주택형',
    SPSPLY_HSHLDCO VARCHAR(10) COMMENT '공급세대수',
    MNYCH_HSHLDCO VARCHAR(10) COMMENT '다자녀 배정세대수',
    NWWDS_NMTW_HSHLDCO VARCHAR(10) COMMENT '신혼부부 배정세대수',
    LFE_FRST_HSHLDCO VARCHAR(10) COMMENT '생애최초 배정세대수',
    YGMN_HSHLDCO VARCHAR(10) COMMENT '청년 배정세대수',
    OLD_PARNTS_SUPORT_HSHLDCO VARCHAR(10) COMMENT '노부모 배정세대수',
    NWBB_NWBBSHR_HSHLDCO VARCHAR(10) COMMENT '신생아 배정세대수',
    INSTT_RECOMEND_HSHLDCO VARCHAR(10) COMMENT '기관추천배정세대수',
    TRANSR_INSTT_ENFSN_HSHLDCO VARCHAR(10) COMMENT '이전기관 배정세대수',
    CRSPAREA_MNYCH_CNT VARCHAR(10) COMMENT '해당지역 다자녀 접수건수',
    CTPRVN_MNYCH_CNT VARCHAR(10) COMMENT '기타경기 다자녀 접수건수',
    ETC_AREA_MNYCH_CNT VARCHAR(10) COMMENT '기타지역 다자녀 접수건수',
    CRSPAREA_NWWDS_NMTW_CNT VARCHAR(10) COMMENT '해당지역 신혼부부 접수건수',
    CTPRVN_NWWDS_NMTW_CNT VARCHAR(10) COMMENT '기타경기 신혼부부 접수건수',
    ETC_AREA_NWWDS_NMTW_CNT VARCHAR(10) COMMENT '기타지역 신혼부부 접수건수',
    CRSPAREA_LFE_FRST_CNT VARCHAR(10) COMMENT '해당지역 생애최초 접수건수',
    CTPRVN_LFE_FRST_CNT VARCHAR(10) COMMENT '기타경기 생애최초 접수건수',
    ETC_AREA_LFE_FRST_CNT VARCHAR(10) COMMENT '기타지역 생애최초 접수건수',
    CRSPAREA_YGMN_CNT VARCHAR(10) COMMENT '해당지역 청년 접수건수',
    CTPRVN_YGMN_CNT VARCHAR(10) COMMENT '기타경기 청년 접수건수',
    ETC_AREA_YGMN_CNT VARCHAR(10) COMMENT '기타지역 청년 접수건수',
    CRSPAREA_OPS_CNT VARCHAR(10) COMMENT '해당지역 노부모 접수건수',
    CTPRVN_OPS_CNT VARCHAR(10) COMMENT '기타경기 노부모 접수건수',
    ETC_AREA_OPS_CNT VARCHAR(10) COMMENT '기타지역 노부모 접수건수',
    CRSPAREA_NWBB_NWBBSHR_CNT VARCHAR(10) COMMENT '해당지역 신생아 접수건수',
    CTPRVN_NWBB_NWBBSHR_CNT VARCHAR(10) COMMENT '기타경기 신생아 접수건수',
    ETC_AREA_NWBB_NWBBSHR_CNT VARCHAR(10) COMMENT '기타지역 신생아 접수건수',
    INSTT_RECOMEND_DCSN_CNT VARCHAR(10) COMMENT '기관추천 접수건수',
    INSTT_RECOMEND_PREPAR_CNT VARCHAR(10) COMMENT '기관추천 예비자 접수건수',
    TRANSR_INSTT_ENFSN_CNT VARCHAR(10) COMMENT '이전기관 접수건수',
    SUBSCRPT_RESULT_NM VARCHAR(10) COMMENT '청약결과',
    REG_DT DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_TY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='APT 특별공급 신청현황';

-- ============================================================================
-- 4. 기존 도시형 주택형 PK 변경 (수동 적용 참고용, 자동 실행하지 않음)
-- 이미 ID PK로 변경한 DB에는 재실행하지 마세요. 백업 후 실제 구조를 확인해야 합니다.
-- ============================================================================
-- ALTER TABLE TB_URBTY_OFCTL_LTTOT_PBLANC_MDL
--     DROP PRIMARY KEY,
--     ADD COLUMN ID BIGINT NOT NULL AUTO_INCREMENT COMMENT '내부 식별자' FIRST,
--     ADD PRIMARY KEY (ID),
--     MODIFY COLUMN MODEL_NO VARCHAR(4) NULL COMMENT '모델번호';

-- ============================================================================
-- 5. 기존 권한 부여 명령 (참고용 보존, 자동 실행하지 않음)
-- 아래는 기존 파일의 내용이며 DB 범위·구문을 확인하고 관리 계정으로 별도 적용해야 합니다.
-- DB 전체 권한은 DB명.* 형태로 대상을 명시합니다. 앱 계정에는 필요한 권한만 부여하세요.
-- ============================================================================
-- 아래 원래 줄은 DB가 아니라 'toady'라는 테이블에 권한을 주는 구문이라 쓰지 않는다(참고용으로만 남김).
-- GRANT DELETE ON toady TO 'toady_sources'@'%';
-- 수집기(toady_sources)는 공고별 결과·주택형을 교체하면서 아래 테이블에서 DELETE를 실행한다. DB명을 실제 값으로 바꿔 확인한다.
-- 권한 확인: SHOW GRANTS FOR 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_APT_LTTOT_PBLANC_CMPET TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_APT_LTTOT_PBLANC_SCORE TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_APT_SPSPLY_REQST_STUS TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_CANC_RESPL_LTTOT_PBLANC_CMPET TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_REMNDR_LTTOT_PBLANC_CMPET TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_OPT_LTTOT_PBLANC_CMPET TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_APT_LTTOT_PBLANC_MDL TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_URBTY_OFCTL_LTTOT_PBLANC_MDL TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_REMNDR_LTTOT_PBLANC_MDL TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_PBL_PVT_RENT_LTTOT_PBLANC_MDL TO 'toady_sources'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON toady.TB_OPT_LTTOT_PBLANC_MDL TO 'toady_sources'@'%';

-- 6. 인증 이메일 본문 컬럼 확장 (운영 DB 적용 완료, 미적용 DB에만 수동 실행)
-- ALTER TABLE TB_CERTIFY MODIFY COLUMN CERTIFY_MESSAGE TEXT NOT NULL COMMENT '인증 이메일 본문';

-- 7. 익명 페이지 조회 로그 (API 배포 전에 이 CREATE TABLE만 수동 실행)
CREATE TABLE IF NOT EXISTS TB_PAGE_VIEW (
    EVENT_ID CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '중복 전송 방지 UUID',
    VISITOR_ID CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '브라우저 저장소 기반 익명 방문자 UUID',
    SESSION_ID CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '30분 미활동 기준 방문 세션 UUID',
    PAGE_PATH VARCHAR(100) NOT NULL COMMENT '쿼리 문자열을 제외한 허용된 페이지 경로',
    VISIT_DATE DATE NOT NULL COMMENT '서버 한국시간 집계 날짜',
    REG_DT DATETIME(6) NOT NULL COMMENT '서버 한국시간 수신 시각',
    PRIMARY KEY (EVENT_ID),
    INDEX IX_PAGE_VIEW_DATE (VISIT_DATE, PAGE_PATH),
    INDEX IX_PAGE_VIEW_VISITOR (VISIT_DATE, VISITOR_ID),
    INDEX IX_PAGE_VIEW_SESSION (VISIT_DATE, SESSION_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='익명 페이지 조회 로그';

-- 기간별 일간 통계 예시: 세션이 자정을 넘으면 날짜별로 각각 집계된다.
-- SELECT VISIT_DATE, COUNT(*) AS PAGE_VIEWS,
--        COUNT(DISTINCT VISITOR_ID) AS VISITORS, COUNT(DISTINCT SESSION_ID) AS SESSIONS
-- FROM TB_PAGE_VIEW WHERE VISIT_DATE >= '2026-11-01' AND VISIT_DATE < '2026-12-01'
-- GROUP BY VISIT_DATE ORDER BY VISIT_DATE;
-- 페이지별 조회 수: SELECT VISIT_DATE, PAGE_PATH, COUNT(*) FROM TB_PAGE_VIEW
-- WHERE VISIT_DATE >= '2026-11-01' GROUP BY VISIT_DATE, PAGE_PATH;
-- 보관: 스케줄러(DataRetentionJob)가 매일 04:40에 90일 지난 행을 소량씩 자동 삭제한다(14번 권한 필요). 아래는 수동 정리용.
-- DELETE FROM TB_PAGE_VIEW WHERE VISIT_DATE < DATE_SUB(DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00')), INTERVAL 90 DAY) LIMIT 10000;

-- 8. 관심 공고 / Gmail 발송량 / 일정 알림 이력 (API 배포 전에 모두 생성)
-- 앱 계정에는 아래 테이블의 SELECT, INSERT, UPDATE, DELETE 권한이 필요합니다.
CREATE TABLE IF NOT EXISTS TB_FAVORITE (
    USER_SEQ BIGINT NOT NULL COMMENT '회원 번호',
    ANNOUNCEMENT_TYPE VARCHAR(20) NOT NULL COMMENT '공고 유형 경로',
    HOUSE_MANAGE_NO VARCHAR(40) NOT NULL,
    PBLANC_NO VARCHAR(40) NOT NULL,
    NOTE VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '비공개 조사 메모',
    CHECKLIST INT NOT NULL DEFAULT 0 COMMENT '준비 체크리스트 5개 비트',
    EMAIL_ENABLED BOOLEAN NOT NULL DEFAULT FALSE COMMENT '회원이 선택한 일정 이메일 알림',
    REG_DT DATETIME(6) NOT NULL COMMENT '한국시간 저장 시각',
    PRIMARY KEY (USER_SEQ, ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO),
    INDEX IX_FAVORITE_EMAIL (EMAIL_ENABLED, USER_SEQ)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='회원별 관심 청약과 준비 상태';

CREATE TABLE IF NOT EXISTS TB_MAIL_BUDGET (
    ID INT NOT NULL PRIMARY KEY COMMENT 'Gmail 공통 발송 잠금 행'
) ENGINE=InnoDB;
INSERT IGNORE INTO TB_MAIL_BUDGET (ID) VALUES (1);

CREATE TABLE IF NOT EXISTS TB_MAIL_ATTEMPT (
    ID CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    MAIL_KIND VARCHAR(10) NOT NULL COMMENT 'AUTH 또는 REMINDER',
    REG_DT DATETIME(6) NOT NULL COMMENT 'UTC 발송 시도 시각',
    INDEX IX_MAIL_ATTEMPT_TIME (REG_DT, MAIL_KIND)
) ENGINE=InnoDB COMMENT='Gmail 최근 24시간 발송 시도량';

CREATE TABLE IF NOT EXISTS TB_REMINDER_DELIVERY (
    USER_SEQ BIGINT NOT NULL,
    SEND_DATE DATE NOT NULL COMMENT '한국시간 알림 날짜',
    STATUS VARCHAR(12) NOT NULL COMMENT 'SENDING / SENT / UNKNOWN / FAILED',
    UPDATED_AT DATETIME(6) NOT NULL COMMENT 'UTC 갱신 시각',
    PRIMARY KEY (USER_SEQ, SEND_DATE),
    INDEX IX_REMINDER_STATUS (STATUS, UPDATED_AT)
) ENGINE=InnoDB COMMENT='일별 묶음 알림 중복 발송 방지';
-- 알림 상태 점검: SELECT SEND_DATE,STATUS,COUNT(*) FROM TB_REMINDER_DELIVERY GROUP BY SEND_DATE,STATUS;
-- 보관: 스케줄러(DataRetentionJob)가 90일 지난 행을 자동 삭제한다(14번 권한 필요). 수동 정리: DELETE FROM TB_REMINDER_DELIVERY WHERE SEND_DATE < CURRENT_DATE - INTERVAL 90 DAY LIMIT 10000;

-- 9. 익명 행동 집계 (상세 열람·관심 저장·알림·공식 페이지 이동)
CREATE TABLE IF NOT EXISTS TB_USAGE_EVENT (
    EVENT_ID CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    VISITOR_ID CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ACTION_NAME VARCHAR(20) NOT NULL,
    SUBJECT_ID VARCHAR(110) NOT NULL COMMENT '유형/주택관리번호/공고번호',
    VISIT_DATE DATE NOT NULL COMMENT '한국시간 집계 날짜',
    REG_DT DATETIME(6) NOT NULL COMMENT '한국시간 수신 시각',
    INDEX IX_USAGE_DATE_ACTION (VISIT_DATE,ACTION_NAME),
    INDEX IX_USAGE_SUBJECT (SUBJECT_ID,VISIT_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='익명 청약 이용 행동';
-- SELECT VISIT_DATE,ACTION_NAME,COUNT(*),COUNT(DISTINCT VISITOR_ID) FROM TB_USAGE_EVENT GROUP BY VISIT_DATE,ACTION_NAME;
-- 공식 페이지 이동은 실제 청약 신청 완료를 의미하지 않습니다.

-- 10. 페이지 조회 유입 도메인 (API 배포 전 실행 권장, 미적용이어도 API는 유입 없이 조회를 저장)
-- 세션 첫 조회의 외부 도메인(예: www.google.com, m.search.naver.com)만 저장하고 전체 URL·검색어는 저장하지 않는다.
ALTER TABLE TB_PAGE_VIEW ADD COLUMN IF NOT EXISTS REFERRER_HOST VARCHAR(100) NULL COMMENT '세션 첫 조회의 외부 유입 도메인';
-- 주간 유입 경로 예시: SELECT REFERRER_HOST, COUNT(DISTINCT SESSION_ID) FROM TB_PAGE_VIEW
-- WHERE VISIT_DATE >= CURRENT_DATE - INTERVAL 7 DAY AND REFERRER_HOST IS NOT NULL GROUP BY REFERRER_HOST;

-- 11. 회원 캘린더 구독 주소 (API 배포 전에 생성)
-- 주소 자체가 열람 권한이므로 256비트 무작위 토큰을 쓰고, 다시 보여줄 수 있도록 원문으로 보관한다. 재발급 시 이전 주소는 즉시 무효.
CREATE TABLE IF NOT EXISTS TB_CALENDAR_FEED (
    USER_SEQ BIGINT NOT NULL COMMENT '회원 번호',
    FEED_TOKEN CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '구독 주소용 무작위 토큰(base64url)',
    REG_DT DATETIME(6) NOT NULL COMMENT '한국시간 발급 시각',
    LAST_READ_DT DATETIME(6) NULL COMMENT '캘린더 앱이 마지막으로 가져간 한국시간',
    PRIMARY KEY (USER_SEQ),
    UNIQUE (FEED_TOKEN)
) ENGINE=InnoDB COMMENT='회원별 캘린더 구독 주소';
-- 앱 계정에는 이 테이블의 SELECT, INSERT, UPDATE, DELETE 권한이 필요합니다.

-- 12. 경쟁률 예상 투표 (API 배포 전에 생성)
-- 아파트 공고의 '가장 인기 있는 주택형 1순위 경쟁률' 구간 예상. 접수 마감일까지 받고 경쟁률 공개 후 실제 구간과 함께 보여 준다.
-- VISITOR_ID는 익명 측정과 같은 브라우저 무작위 값이며 회원 정보와 연결하지 않는다.
CREATE TABLE IF NOT EXISTS TB_RATE_POLL (
    ANNOUNCEMENT_ID VARCHAR(110) NOT NULL COMMENT 'apt/주택관리번호/공고번호',
    VISITOR_ID CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '브라우저 익명 방문자 ID',
    CHOICE TINYINT NOT NULL COMMENT '경쟁률 구간 0~5',
    REG_DT DATETIME(6) NOT NULL COMMENT '한국시간 투표·변경 시각',
    PRIMARY KEY (ANNOUNCEMENT_ID, VISITOR_ID),
    INDEX IX_RATE_POLL_CHOICE (ANNOUNCEMENT_ID, CHOICE)
) ENGINE=InnoDB COMMENT='공고별 경쟁률 예상 투표';
-- 앱 계정에는 이 테이블의 SELECT, INSERT, UPDATE 권한이 필요합니다.

-- 13. 웹 푸시 구독 (API 배포 전에 생성, 미적용 시 알림 켜기 화면만 숨겨진다)
-- 엔드포인트 주소 자체가 발송 권한이라 원문은 서버에만 두고, 길이 제한 없이 고유 키로 쓰도록 SHA-256 해시를 PK로 쓴다.
-- 회원은 USER_SEQ의 관심 공고(TB_FAVORITE)를, 비회원은 FAVORITE_IDS(브라우저에 저장한 관심 공고, 최대 50개)를 쓴다.
-- AREA_SLUGS는 생활권 slug를 쉼표로 이은 값(예: unjeong,dongtan)이며, 그 생활권의 새 공고를 하루 1회 묶음에 넣는다.
CREATE TABLE IF NOT EXISTS TB_PUSH_SUBSCRIPTION (
    ENDPOINT_HASH CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ENDPOINT의 SHA-256(hex)',
    ENDPOINT VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '브라우저 푸시 서비스 주소',
    P256DH VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '브라우저 공개키(base64url)',
    AUTH VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '브라우저 인증 비밀값(base64url)',
    USER_SEQ BIGINT NULL COMMENT '로그인 상태로 구독한 회원 번호',
    VISITOR_ID CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '브라우저 익명 방문자 ID',
    FAVORITES_ENABLED BOOLEAN NOT NULL DEFAULT TRUE COMMENT '관심 공고 전날 일정 알림',
    FAVORITE_IDS TEXT NULL COMMENT '비회원 관심 공고 ID(쉼표 구분)',
    AREA_SLUGS VARCHAR(200) NOT NULL DEFAULT '' COMMENT '새 공고를 받을 생활권 slug(쉼표 구분)',
    REG_DT DATETIME(6) NOT NULL COMMENT '한국시간 최초 등록 시각',
    UPDATED_DT DATETIME(6) NOT NULL COMMENT '한국시간 범위 변경 시각',
    LAST_ATTEMPT_DATE DATE NULL COMMENT '하루 1회 묶음 발송을 시도한 한국 날짜',
    LAST_DIGEST_DT DATETIME(6) NULL COMMENT '묶음 알림을 마지막으로 전달한 한국시간(생활권 새 공고 기준점)',
    LAST_SUCCESS_DT DATETIME(6) NULL COMMENT '푸시 서비스가 마지막으로 수락한 한국시간',
    PRIMARY KEY (ENDPOINT_HASH),
    INDEX IX_PUSH_USER (USER_SEQ)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='웹 푸시 구독과 알림 범위';
-- 생활권별로 공고를 처음 확인한 시각. 구독자가 마지막으로 알림을 받은 뒤 처음 보인 공고만 '새 공고'로 보낸다.
CREATE TABLE IF NOT EXISTS TB_PUSH_AREA_SEEN (
    AREA_SLUG VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ANNOUNCEMENT_ID VARCHAR(110) NOT NULL COMMENT '유형/주택관리번호/공고번호',
    FIRST_SEEN_DT DATETIME(6) NOT NULL COMMENT '한국시간 최초 확인 시각',
    PRIMARY KEY (AREA_SLUG, ANNOUNCEMENT_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='생활권 새 공고 판정';
-- 앱 계정에는 두 테이블의 SELECT, INSERT, UPDATE, DELETE 권한이 필요합니다.
-- 구독 현황: SELECT USER_SEQ IS NOT NULL AS MEMBER, COUNT(*), SUM(LAST_SUCCESS_DT IS NOT NULL) FROM TB_PUSH_SUBSCRIPTION GROUP BY 1;

-- 14. 지표·푸시 조회 인덱스와 보관 기간 정리 권한 (스케줄러 배포 전 실행 권장, 미적용이어도 앱은 동작)
-- 관리자 유입 경로 집계(VISIT_DATE 범위 + REFERRER_HOST별 COUNT(DISTINCT SESSION_ID))를 인덱스만으로 처리한다.
-- 기존 IX_PAGE_VIEW_SESSION(VISIT_DATE, SESSION_ID)에는 REFERRER_HOST가 없어 기간 안의 행을 모두 읽었다. 10번 컬럼이 있어야 한다.
CREATE INDEX IF NOT EXISTS IX_PAGE_VIEW_SOURCE ON TB_PAGE_VIEW (VISIT_DATE, REFERRER_HOST, SESSION_ID);
-- 하루 1회 묶음 발송이 '오늘 아직 시도하지 않은 구독'을 고를 때 구독 전체를 훑지 않게 한다.
CREATE INDEX IF NOT EXISTS IX_PUSH_LAST_ATTEMPT ON TB_PUSH_SUBSCRIPTION (LAST_ATTEMPT_DATE);
-- 스케줄러의 보관 기간 정리(매일 04:40): 방문 로그 90일, 행동 집계 180일, 일정 알림 이력 90일, 생활권 새 공고 판정 365일.
-- 스케줄러 계정에 아래 권한이 없으면 해당 테이블만 건너뛰고 경고 로그를 남긴다. DB명·접속 호스트를 실제 값으로 확인한 뒤 관리 계정으로 실행하세요.
-- MariaDB는 WHERE 조건 컬럼을 읽기 위해 DELETE와 함께 SELECT 권한도 요구한다.
-- GRANT SELECT, DELETE ON toady.TB_PAGE_VIEW TO 'toady_sources'@'%';
-- GRANT SELECT, DELETE ON toady.TB_USAGE_EVENT TO 'toady_sources'@'%';
-- GRANT SELECT, DELETE ON toady.TB_REMINDER_DELIVERY TO 'toady_sources'@'%';
-- GRANT SELECT, DELETE ON toady.TB_PUSH_AREA_SEEN TO 'toady_sources'@'%';
-- 권한 확인: SHOW GRANTS FOR 'toady_sources'@'%';

-- 15. 생활권 새 공고 판정의 마지막 확인 시각 (API·스케줄러 배포 전 실행 권장, 미적용이어도 앱은 동작)
-- 웹 푸시 묶음 발송이 생활권의 접수 중·예정 공고를 볼 때마다 갱신한다. 보관 정리는 이 값이 1년 지난 행만 지워,
-- 1년 넘게 열려 있는 공고가 '새 공고'로 다시 알림되지 않게 한다. 미적용이면 API는 기록을 건너뛰고 스케줄러는 이 테이블 정리를 건너뛴다.
ALTER TABLE TB_PUSH_AREA_SEEN ADD COLUMN IF NOT EXISTS LAST_SEEN_DT DATETIME(6) NULL COMMENT '한국시간 마지막 확인 시각';
CREATE INDEX IF NOT EXISTS IX_PUSH_AREA_LAST_SEEN ON TB_PUSH_AREA_SEEN (LAST_SEEN_DT);
-- 컬럼을 추가한 직후에는 모든 행이 NULL이다. 적용 시점을 마지막 확인으로 채워, 첫 묶음 발송 전에 보관 정리가 돌아도 지워지지 않게 한다.
UPDATE TB_PUSH_AREA_SEEN SET LAST_SEEN_DT = CURRENT_TIMESTAMP(6) WHERE LAST_SEEN_DT IS NULL;

-- 16. 나의 청약 진행 단계 (API 배포 전 실행 권장, 미적용이어도 앱은 동작)
-- 관심 공고마다 '관심 → 신청 예정 → 신청 완료 → 당첨/미당첨 → 계약 완료'를 기록해 단계별 다음 할 일과 결과 확인을 안내한다.
-- 0 관심, 1 신청 예정, 2 신청 완료, 3 당첨, 4 미당첨, 5 계약 완료. 미적용이면 회원의 단계 저장만 건너뛴다(비회원은 브라우저에 저장).
ALTER TABLE TB_FAVORITE ADD COLUMN IF NOT EXISTS STAGE TINYINT NOT NULL DEFAULT 0 COMMENT '청약 진행 단계 0~5';

-- 17. 오피스텔·공공지원민간임대 경쟁률의 모델번호 길이 (스케줄러 배포 전 실행 권장)
-- 같은 유형의 주택형 테이블은 MODEL_NO가 4자리인데 경쟁률 테이블만 2자리라, 3~4자리 모델번호가 오면 MariaDB(STRICT)에서 저장이 실패한다.
-- 늘리기만 하므로 기존 값은 그대로이며 다시 실행해도 된다.
ALTER TABLE TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET MODIFY MODEL_NO VARCHAR(4) NOT NULL COMMENT '모델번호';
ALTER TABLE TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET MODIFY MODEL_NO VARCHAR(4) NOT NULL COMMENT '모델번호';

-- 18. 공고별 주변 시세 비교 요약 (API 배포 전 실행 권장, 미적용이어도 앱은 동작)
-- API가 매일 17:20(한국시간) 접수 중·예정 공고의 공급 주소를 카카오 로컬 REST API로 확인하고(정확한 지번만),
-- 국토교통부 실거래로 '주변 (신축) 시세 대비' 범위를 계산해 둔다. 맞춤 청약·줍줍 목록 뱃지, 나의 청약 비교표, 공개 페이지, 줍줍 알림이 쓴다.
-- STATUS: READY(요약 있음) · NO_LOCATION(정확한 위치 미확인) · NO_TRADES(신뢰할 거래 부족) · UNAVAILABLE(제공처 오류, 다음 날 다시 계산).
-- 금액은 만원, 시세 범위는 같은 면적(전용 ±5㎡) 거래의 1~3사분위다. 180일 지난 행은 API가 정리한다.
-- API 환경변수 KAKAO_REST_API_KEY가 있어야 계산하며, 앱 계정에 SELECT·INSERT·UPDATE·DELETE 권한이 필요하다.
CREATE TABLE IF NOT EXISTS TB_PRICE_GAP (
    ANNOUNCEMENT_ID VARCHAR(110) NOT NULL COMMENT '유형/주택관리번호/공고번호',
    STATUS VARCHAR(12) NOT NULL COMMENT 'READY·NO_LOCATION·NO_TRADES·UNAVAILABLE',
    BCODE CHAR(10) NULL COMMENT '법정동 코드',
    DONG VARCHAR(40) NULL COMMENT '법정동 이름',
    AREA SMALLINT NULL COMMENT '대표 주택형 전용면적(반올림)',
    SUPPLY_MANWON INT NULL COMMENT '대표 주택형 최고 공급금액',
    BASIS VARCHAR(4) NULL COMMENT 'new(준공 10년 이내) 또는 all',
    SCOPE VARCHAR(10) NULL COMMENT 'DONG 또는 DISTRICT',
    BASIS_COUNT INT NULL COMMENT '비교에 쓴 거래 수',
    CONFIDENCE VARCHAR(8) NULL COMMENT 'high·medium',
    Q1_MANWON INT NULL COMMENT '시세 1사분위',
    Q3_MANWON INT NULL COMMENT '시세 3사분위',
    MONTHS TINYINT NULL COMMENT '반영한 개월 수',
    COMPUTED_DT DATETIME(6) NOT NULL COMMENT '한국시간 계산 시각',
    PRIMARY KEY (ANNOUNCEMENT_ID),
    INDEX IX_PRICE_GAP_COMPUTED (COMPUTED_DT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='공고별 주변 시세 비교 요약';

-- 19. 관리자 사이트(toadyAdmin) 전용 DB 계정 (관리자 배포 전에 root로 수동 실행, 자동 실행하지 않음)
-- 조회는 화면에 쓰는 테이블·컬럼만, 변경은 회원 잠금(LOCK_YN·LOCK_DT)과 댓글 숨김(DELETE_YN)만 허용한다.
-- 비밀번호 해시(USER_PASSWORD)와 푸시 구독 키(P256DH·AUTH)는 읽지 못하게 컬럼 단위로 준다. 비밀번호는 실제 값으로 바꿔 실행한다.
-- CREATE USER 'toady_admin'@'%' IDENTIFIED BY '<관리자 DB 비밀번호>';
-- GRANT SELECT (USER_SEQ, USER_EMAIL, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, LOCK_DT, WITHDRAWAL_YN, CREATE_DT, UPDATE_DT), UPDATE (LOCK_YN, LOCK_DT, UPDATE_DT) ON toady.TB_USER TO 'toady_admin'@'%';
-- GRANT SELECT, UPDATE (DELETE_YN, UPDATE_DT) ON toady.TB_LTTOT_PBLANC_COMMENT TO 'toady_admin'@'%';
-- GRANT SELECT (USER_SEQ, AREA_SLUGS, LAST_ATTEMPT_DATE, LAST_DIGEST_DT, LAST_SUCCESS_DT) ON toady.TB_PUSH_SUBSCRIPTION TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_FAVORITE TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_PAGE_VIEW TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_USAGE_EVENT TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_PRICE_GAP TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_APT_LTTOT_PBLANC_DETAIL TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_REMNDR_LTTOT_PBLANC_DETAIL TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_OPT_LTTOT_PBLANC_DETAIL TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_APT_LTTOT_PBLANC_CMPET TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_APT_LTTOT_PBLANC_SCORE TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_SCHEDULER_ADMIN_JOB TO 'toady_admin'@'%';
-- 공고 상세(주택형·분양가)와 경쟁률 예상 투표 조회, 내 방문 제외 목록(20번) 관리
-- GRANT SELECT ON toady.TB_APT_LTTOT_PBLANC_MDL TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_URBTY_OFCTL_LTTOT_PBLANC_MDL TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_REMNDR_LTTOT_PBLANC_MDL TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_PBL_PVT_RENT_LTTOT_PBLANC_MDL TO 'toady_admin'@'%';
-- GRANT SELECT ON toady.TB_OPT_LTTOT_PBLANC_MDL TO 'toady_admin'@'%';
-- GRANT SELECT (ANNOUNCEMENT_ID, CHOICE) ON toady.TB_RATE_POLL TO 'toady_admin'@'%';
-- GRANT SELECT, INSERT, DELETE ON toady.TB_ANALYTICS_EXCLUDED_VISITOR TO 'toady_admin'@'%';
-- GRANT SELECT, INSERT, DELETE ON toady.TB_COMMENT_ADMIN_HIDE TO 'toady_admin'@'%';
-- 권한 확인: SHOW GRANTS FOR 'toady_admin'@'%';

-- 20. 지표에서 뺄 운영자 브라우저 (관리자 사이트 배포 전 실행 권장, 미적용이어도 지표는 모든 방문을 센다)
-- 방문자는 브라우저 저장소의 무작위 ID로 센다(IP는 저장하지 않는다). 운영자가 관리자 화면에서 자기 브라우저를 등록하면 과거 기록까지 지표에서 뺀다.
CREATE TABLE IF NOT EXISTS TB_ANALYTICS_EXCLUDED_VISITOR (
    VISITOR_ID CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    LABEL VARCHAR(50) NOT NULL COMMENT '구분용 이름(예: 맥북 크롬)',
    REG_DT DATETIME(6) NOT NULL COMMENT '한국시간 등록 시각'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='지표에서 제외할 운영자 브라우저';

-- ============================================================================
-- 21. 관리자가 숨긴 댓글 기록 (관리자 사이트 배포 전 실행 권장)
-- ============================================================================
-- 작성자가 지운 댓글과 관리자가 숨긴 댓글은 모두 DELETE_YN='Y'라 구분되지 않는다. 관리자가 숨긴 댓글만 여기에 남겨,
-- 관리자 화면의 '되살리기'가 작성자가 직접 지운 글을 다시 공개하지 않게 한다. 미적용이면 숨김은 되지만 되살리기는 막힌다.
CREATE TABLE IF NOT EXISTS TB_COMMENT_ADMIN_HIDE (
    COMMENT_SEQ BIGINT NOT NULL PRIMARY KEY,
    ADMIN_NAME VARCHAR(100) NOT NULL COMMENT '숨긴 관리자',
    REG_DT DATETIME(6) NOT NULL COMMENT '한국시간 숨긴 시각'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='관리자가 숨긴 댓글';

-- ============================================================================
-- 22. 실거래 단지 좌표 (API 배포 전 실행 권장, 미적용이어도 앱은 동작)
-- ============================================================================
-- 국토부 실거래 자료에는 좌표가 없어 위치 탭 지도에 단지별 가격을 놓으려면 카카오 주소 검색으로 지번을 좌표로 바꿔야 한다.
-- 한 번 확인한 결과를 남겨 카카오 호출을 줄인다. 찾지 못한 지번(NOT_FOUND)은 30일 뒤에 다시 확인한다.
-- 미적용이면 API가 메모리에만 보관하므로 재시작할 때마다 다시 확인한다.
CREATE TABLE IF NOT EXISTS TB_APT_COMPLEX_LOCATION (
    COMPLEX_KEY VARCHAR(120) NOT NULL PRIMARY KEY COMMENT '시군구코드|법정동|지번',
    LATITUDE DECIMAL(10,7) NULL,
    LONGITUDE DECIMAL(10,7) NULL,
    STATUS VARCHAR(12) NOT NULL COMMENT 'FOUND 또는 NOT_FOUND',
    CHECKED_DT DATETIME(6) NOT NULL COMMENT '한국시간 확인 시각'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='실거래 단지 좌표(카카오 주소 검색 결과)';
