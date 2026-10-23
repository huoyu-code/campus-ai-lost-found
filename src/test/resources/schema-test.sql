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


