CREATE DATABASE annual_leave CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE annual_leave;






CREATE TABLE employee (
                          employee_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
                          employee_number   VARCHAR(20)   NOT NULL UNIQUE COMMENT '사번',
                          name              VARCHAR(50)   NOT NULL COMMENT '성명',
                          department        VARCHAR(50)   NULL COMMENT '부서',
                          position          VARCHAR(50)   NULL COMMENT '직급',
                          hire_date         DATE          NOT NULL COMMENT '입사일',

                          login_id          VARCHAR(50)   NULL UNIQUE COMMENT '로그인 아이디 (NULL일 경우 아직 회원가입 전)',
                          password          VARCHAR(255)  NULL,
                          role              VARCHAR(10)   NOT NULL DEFAULT 'EMPLOYEE' COMMENT 'EMPLOYEE / ADMIN',

                          total_leave_days  DECIMAL(4,1)  NOT NULL DEFAULT 0 COMMENT '올해 부여된 총 연차일수',

                          created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '인사정보 + 로그인 계정 + 배정 연차';






CREATE TABLE leave_request (
                               leave_request_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
                               employee_id        BIGINT       NOT NULL COMMENT '신청자',

                               leave_type         VARCHAR(10)  NOT NULL COMMENT 'FULL(연차) / AM_HALF(오전반차) / PM_HALF(오후반차)',
                               start_date         DATE         NOT NULL,
                               end_date           DATE         NOT NULL,
                               use_days           DECIMAL(4,1) NOT NULL COMMENT '차감될 일수 (연차 1.0 / 반차 0.5)',

                               status             VARCHAR(10)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING(대기) / APPROVED(승인 완료) / REJECTED(반려)',
                               approver_id        BIGINT       NULL COMMENT '처리한 관리자',
                               reject_reason      VARCHAR(200) NULL COMMENT '반려 사유',
                               processed_at       DATETIME     NULL COMMENT '승인/반려 처리 시각',

                               created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                               CONSTRAINT fk_request_employee FOREIGN KEY (employee_id) REFERENCES employee(employee_id),
                               CONSTRAINT fk_request_approver FOREIGN KEY (approver_id) REFERENCES employee(employee_id)
) COMMENT '연차/반차 신청 + 승인/반려 내역';