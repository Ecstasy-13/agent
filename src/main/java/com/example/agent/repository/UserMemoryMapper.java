package com.example.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.agent.entity.UserMemory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户长期记忆 Mapper。
 *
 * <p>继承 {@link BaseMapper} 后自动拥有：
 *
 * insert / deleteById / updateById /
 * selectById / selectOne / selectList
 *
 * 等常用方法，简单 CRUD 不需要再写 XML。
 */
@Mapper
public interface UserMemoryMapper extends BaseMapper<UserMemory> {
}
