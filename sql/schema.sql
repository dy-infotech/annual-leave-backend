CREATE DATABASE annual_leave CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE annual_leave;


CREATE TABLE employee (
                          employee_id       	BIGINT			AUTO_INCREMENT PRIMARY KEY,
                          employee_number   	VARCHAR(20) 	NOT NULL UNIQUE COMMENT '사번',
                          password          	VARCHAR(255)	NULL COMMENT '비밀번호 (NULL일 경우 아직 회원가입 전)',
                          access_count			INT				NOT NULL DEFAULT 0 COMMENT '로그인 실패 횟수',
                          accessed_at			DATETIME		NULL COMMENT '로그인 실패 시각',
                          name              	VARCHAR(50)   	NOT NULL COMMENT '성명',
                          department        	VARCHAR(50)   	NULL COMMENT '부서',
                          team		        	VARCHAR(30) 	NOT NULL COMMENT '팀 // 배정되지 않은 경우 대표이사 팀 선택 및 approver_id도 대표이사의 id로 해야 한다',
                          position          	VARCHAR(50)		NULL COMMENT '직급',
                          email			 	    VARCHAR(100)		NULL COMMENT '이메일',
                          hire_date         	DATE        	NOT NULL COMMENT '입사일',
                          fire_date         	DATE   			NULL COMMENT '퇴사일',

                          curr_year				VARCHAR(4)  	NOT NULL COMMENT '올해 연도',
                          curr_total_leave_days	FLOAT  			NOT NULL DEFAULT 0 COMMENT '올해 부여된 총 연차일수',
                          prev_year				VARCHAR(4)  	NULL COMMENT '작년 연도',
                          prev_total_leave_days	FLOAT  			NULL DEFAULT 0 COMMENT '작년 부여된 총 연차일수',

                          created_at        	DATETIME      	NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          approver_id          	BIGINT			NULL COMMENT '승인한 관리자 번호',
                          updated_at        	DATETIME      	NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '인사정보 + 로그인 계정 + 배정 연차';



CREATE TABLE leave_request (
                               leave_request_id   BIGINT		AUTO_INCREMENT PRIMARY KEY,
                               employee_id        BIGINT		NOT NULL COMMENT '신청자',

                               leave_type         VARCHAR(50)	NOT NULL COMMENT 'FULL(연차) / AM_HALF(오전반차) / PM_HALF(오후반차) / ALTERNATE(대체) / PARENTAL(출산) / FAMILY(가족돌봄) / OTHER(기타)',
                               start_date         DATE			NOT NULL,
                               end_date           DATE			NOT NULL,
                               use_days           FLOAT			NOT NULL COMMENT '차감될 일수 (연차 1.0 / 반차 0.5)',
                               leave_reason       VARCHAR(200)	NULL COMMENT '신청 사유',

                               status             VARCHAR(10)	NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING(대기) / APPROVED(승인 완료) / REJECTED(반려) / CANCELLED(신청자 취소)',
                               manager_id         BIGINT		NULL COMMENT '처리한 관리자',
                               managed_at         DATETIME		NULL COMMENT '처리 시각',
                               reject_reason      VARCHAR(200)	NULL COMMENT '반려 사유',

                               created_at         DATETIME		NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_request_employee FOREIGN KEY (employee_id) REFERENCES employee(employee_id),
                               CONSTRAINT fk_request_manager FOREIGN KEY (manager_id) REFERENCES employee(employee_id)
) COMMENT '휴가 신청 + 승인/반려 내역';



CREATE TABLE team (
                               seq                BIGINT		AUTO_INCREMENT PRIMARY KEY,
                               team               VARCHAR(30)	NOT NULL UNIQUE COMMENT '팀',
                               project_manager_id BIGINT		NOT NULL COMMENT '프로젝트 담당자',
                               parent_team        VARCHAR(30)	NOT NULL COMMENT '상위 팀',

                               CONSTRAINT fk_project_manager FOREIGN KEY (project_manager_id) REFERENCES employee(employee_id)
) COMMENT '팀 정보 (결재라인 상급자 탐색용)';


CREATE TABLE basis_data (
                               year               VARCHAR(4)	NOT NULL,
                               seq                BIGINT		NOT NULL,
                               type               VARCHAR(2)	NOT NULL COMMENT '0: bool, 1: int, 2: long, 3: float, 4: double, 5: string, ...',
                               data               VARCHAR(100)	NOT NULL COMMENT '비고에 해당하는 값',
                               remark             VARCHAR(200)	NOT NULL COMMENT '비고',
                               
                               PRIMARY KEY (year, seq)
) COMMENT '기초데이터 (연도별 연차 수, N년당 추가 연차 발생, 추가연차 발생시 연차 수, 만근 출석 퍼센트 등)';



CREATE TABLE leave_adjustment (
                               employee_id        BIGINT		NOT NULL,
                               year               VARCHAR(4)	NOT NULL,
                               sign               VARCHAR(5)	NOT NULL COMMENT 'plus, minus',
                               leave_days         FLOAT			NOT NULL COMMENT '조정 일수',
                               reason             VARCHAR(200)	NOT NULL COMMENT '발생 사유',

                               created_at         DATETIME		NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                               
                               PRIMARY KEY (employee_id, year, created_at),
                               CONSTRAINT fk_employee FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
) COMMENT '휴가 조정 내역(특별 휴가 추가, 만근 실패 차감)';



CREATE TABLE holiday (
                               name               VARCHAR(50)	NOT NULL COMMENT '공휴일 이름',
                               year               VARCHAR(4)	NOT NULL COMMENT '공휴일 연도',
                               month			  VARCHAR(2)	NOT NULL COMMENT '공휴일 월',
                               day		          VARCHAR(2)	NOT NULL COMMENT '공휴일 일자',
                               
                               PRIMARY KEY (year, month, day)
) COMMENT '연간 공휴일 정보 // 국경일로 변경시 data_kind, is_holiday 컬럼 추가 필요';



CREATE TABLE fcm_token (
                               token_id			  BIGINT AUTO_INCREMENT PRIMARY KEY,
                               employee_id        BIGINT		NOT NULL COMMENT '근로자 인덱스',
                               fcm_token		  VARCHAR(255)	NOT NULL UNIQUE COMMENT 'FCM 디바이스 토큰',
                               device_os          VARCHAR(10)	NULL COMMENT 'ANDROID, IOS, WEB 등',
                               updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                               
                               CONSTRAINT fk_fcm_token_employee FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
) COMMENT 'FCM 디바이스 토큰';


