-- =============================================================
-- Java 智能 Agent 系统 —— 数据库初始化脚本
-- 对应需求文档 4.4 长期记忆 user_memory 表
-- =============================================================

CREATE DATABASE IF NOT EXISTS agent_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE agent_db;

-- 用户长期记忆表
CREATE TABLE IF NOT EXISTS user_memory (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      VARCHAR(64)  NOT NULL                COMMENT '用户ID',
    memory_key   VARCHAR(128) NOT NULL                COMMENT '记忆类型（如 name/skill/direction）',
    memory_value VARCHAR(1024)                        COMMENT '记忆内容',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_key (user_id, memory_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户长期记忆表';
