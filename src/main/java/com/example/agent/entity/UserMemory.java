package com.example.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 用户长期记忆实体。
 *
 * <p>对应需求文档 4.4 长期记忆 MySQL 表 {@code user_memory}：
 * <pre>
 * id            主键
 * user_id       用户ID
 * memory_key    记忆类型（如 name / skill / direction）
 * memory_value  记忆内容
 * create_time   创建时间
 * </pre>
 */
@Entity
@Table(name = "user_memory")
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "memory_key", nullable = false, length = 128)
    private String memoryKey;

    @Column(name = "memory_value", length = 1024)
    private String memoryValue;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    public UserMemory() {
    }

    public UserMemory(String userId, String memoryKey, String memoryValue) {
        this.userId = userId;
        this.memoryKey = memoryKey;
        this.memoryValue = memoryValue;
    }

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMemoryKey() {
        return memoryKey;
    }

    public void setMemoryKey(String memoryKey) {
        this.memoryKey = memoryKey;
    }

    public String getMemoryValue() {
        return memoryValue;
    }

    public void setMemoryValue(String memoryValue) {
        this.memoryValue = memoryValue;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
