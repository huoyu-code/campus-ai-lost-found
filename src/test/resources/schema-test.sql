-- 测试库使用 H2 的 MySQL 兼容模式；生产结构以根目录 sql/schema.sql 为准。
-- 每个测试上下文启动时重建表，保证测试可重复运行。
DROP TABLE IF EXISTS sys_operation_log;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    nickname        VARCHAR(50)  NOT NULL,
    avatar_url      VARCHAR(500) NULL,
    phone           VARCHAR(20)  NULL,
    email           VARCHAR(100) NULL,
    role            VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMP    NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_phone UNIQUE (phone),
    CONSTRAINT uk_user_email UNIQUE (email)
);

DROP TABLE IF EXISTS lost_found_post_image;
DROP TABLE IF EXISTS claim_application;
DROP TABLE IF EXISTS lost_found_post;

CREATE TABLE lost_found_post (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    publisher_id            BIGINT       NOT NULL,
    post_type               VARCHAR(10)  NOT NULL,
    title                   VARCHAR(100) NOT NULL,
    description             VARCHAR(5000) NULL,
    category                VARCHAR(30)  NOT NULL,
    location                VARCHAR(120) NOT NULL,
    occurrence_time         TIMESTAMP    NOT NULL,
    contact_text            VARCHAR(100) NULL,
    contact_visibility      VARCHAR(20)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    hidden_previous_status  VARCHAR(20)  NULL,
    resolved_at             TIMESTAMP    NULL,
    closed_at               TIMESTAMP    NULL,
    hidden_at               TIMESTAMP    NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lost_found_post_image (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id     BIGINT       NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    sort_order  INT          NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_post_image_sort UNIQUE (post_id, sort_order)
);

CREATE TABLE claim_application (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id                  BIGINT        NOT NULL,
    applicant_id             BIGINT        NOT NULL,
    application_type         VARCHAR(10)   NOT NULL,
    description              VARCHAR(2000) NOT NULL,
    status                   VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    processed_by             BIGINT        NULL,
    process_note             VARCHAR(500)  NULL,
    processed_at             TIMESTAMP     NULL,
    completion_requested_at  TIMESTAMP     NULL,
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 生成列模拟 MySQL 唯一约束：未结束申请才参与 active 唯一键。
    active_flag TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('PENDING', 'ACCEPTED') THEN 1 ELSE NULL END
    ),
    accepted_post_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACCEPTED' THEN post_id ELSE NULL END
    ),
    CONSTRAINT uk_application_active
        UNIQUE (post_id, applicant_id, active_flag),
    CONSTRAINT uk_application_one_accepted UNIQUE (accepted_post_id)
);

CREATE TABLE sys_operation_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id  BIGINT       NOT NULL,
    action       VARCHAR(50)  NOT NULL,
    target_type  VARCHAR(30)  NOT NULL,
    target_id    BIGINT       NULL,
    result       VARCHAR(20)  NOT NULL,
    detail_json  VARCHAR(4000) NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);


