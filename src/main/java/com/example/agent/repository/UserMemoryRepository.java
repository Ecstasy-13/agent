package com.example.agent.repository;

import com.example.agent.entity.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 用户长期记忆数据访问层。
 */
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    /** 查询某用户的全部长期记忆 */
    List<UserMemory> findByUserId(String userId);

    /** 查询某用户某一类型的记忆 */
    Optional<UserMemory> findByUserIdAndMemoryKey(String userId, String memoryKey);

    /** 删除某用户的全部长期记忆 */
    void deleteByUserId(String userId);
}
