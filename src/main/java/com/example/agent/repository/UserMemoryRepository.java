package com.example.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agent.entity.UserMemory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户长期记忆数据访问层。
 *
 * <p>V2 变化：
 *
 * 不再是继承 {@code JpaRepository} 的接口，
 * 而是一个持有 {@link UserMemoryMapper} 的普通类。
 *
 * <p>关键设计点：
 *
 * 对外暴露的方法签名（findByUserId / findByUserIdAndMemoryKey /
 * deleteByUserId / save）与迁移前完全一致，
 * 因此 {@code LongMemoryService} 不需要做任何改动，
 * 这正是"面向接口编程"带来的收益。
 */
@Repository
public class UserMemoryRepository {

    private final UserMemoryMapper mapper;

    public UserMemoryRepository(UserMemoryMapper mapper) {
        this.mapper = mapper;
    }

    /** 查询某用户的全部长期记忆 */
    public List<UserMemory> findByUserId(String userId) {
        LambdaQueryWrapper<UserMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMemory::getUserId, userId);
        return mapper.selectList(wrapper);
    }

    /** 查询某用户某一类型的记忆 */
    public Optional<UserMemory> findByUserIdAndMemoryKey(String userId, String memoryKey) {
        LambdaQueryWrapper<UserMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMemory::getUserId, userId)
                .eq(UserMemory::getMemoryKey, memoryKey);
        /*
         * selectOne 在查不到数据时返回 null，
         * 这里包装成 Optional，
         * 保持和迁移前 JpaRepository 一样的调用体验。
         *
         * 注意：selectOne 要求结果最多一条，
         * 如果表里因为脏数据出现同一 (userId, memoryKey) 多条记录，
         * MyBatis-Plus 会抛异常，
         * 这也倒逼我们保证业务上 (userId, memoryKey) 的唯一性
         * （对应 sql/init.sql 中的 uk_user_key 唯一索引）。
         */
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    /** 删除某用户的全部长期记忆 */
    public void deleteByUserId(String userId) {
        LambdaQueryWrapper<UserMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMemory::getUserId, userId);
        mapper.delete(wrapper);
    }

    /**
     * 保存一条长期记忆（新增或更新）。
     *
     * <p>与 JPA {@code save()} 不同，
     * MyBatis-Plus 的 insert / update 是分开的两个方法，
     * 需要我们自己根据主键是否存在来判断。
     */
    public void save(UserMemory memory) {
        if (memory.getId() == null) {
            mapper.insert(memory);
        } else {
            mapper.updateById(memory);
        }
    }
}