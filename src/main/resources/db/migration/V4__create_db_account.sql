-- V2: 创建 db_account 表（用户账户表）

CREATE TABLE IF NOT EXISTS db_account (
    id            INT           NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username      VARCHAR(50)   NOT NULL COMMENT '用户名',
    password      VARCHAR(255)  NOT NULL COMMENT '密码（BCrypt加密）',
    email         VARCHAR(100)  NOT NULL COMMENT '邮箱地址',
    avatar        VARCHAR(255)  NULL     COMMENT '头像URL',
    register_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_username (username),
    UNIQUE INDEX uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账户表';
