package com.example.agent.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

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
 *
 * <p>V2 起改用 MyBatis-Plus 而不是 JPA/Hibernate。
 * <p>
 * MyBatis-Plus 的字段映射默认按照
 * 驼峰 -> 下划线自动转换（userId -> user_id），
 * 与这张表的命名规则完全吻合，
 * 所以这里不需要额外写 {@code @TableField}，
 * 保留一个字段的写法作为示例说明。
 */
@TableName("user_memory")
public class UserMemory {

    /**
     * 主键。
     *
     * <p>{@code IdType.AUTO}
     * 表示由数据库自增生成，
     * 对应 JPA 时代的
     * {@code GenerationType.IDENTITY}。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID。
     *
     * <p>这里显式写 {@code @TableField}
     * 只是为了给你展示写法，
     * 实际上删掉它效果完全一样
     * （因为字段名本来就能自动映射到 user_id）。
     */
    @TableField("user_id")
    private String userId;

    private String memoryKey;

    private String memoryValue;

    private LocalDateTime createTime;

    public UserMemory() {
    }

    /**
     * 新增一条长期记忆时使用的构造器。
     *
     * <p>注意：
     * createTime 在这里主动赋值，
     * 因为 MyBatis-Plus 没有 JPA {@code @PrePersist} 这种生命周期回调，
     * 需要我们自己在业务代码里保证它不为空。
     */
    public UserMemory(String userId, String memoryKey, String memoryValue) {
        this.userId = userId;
        this.memoryKey = memoryKey;
        this.memoryValue = memoryValue;
        this.createTime = LocalDateTime.now();
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