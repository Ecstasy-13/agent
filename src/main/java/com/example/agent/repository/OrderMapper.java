package com.example.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.agent.entity.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}