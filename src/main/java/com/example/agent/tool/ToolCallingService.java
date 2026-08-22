package com.example.agent.tool;

/**
 * Tool Calling 工具调用服务（第四阶段，后续版本）。
 *
 * <p>对应需求文档 4.6。目标：让 Agent 具备调用外部系统的能力（Function Calling）。
 *
 * <p>交互示例：
 * <pre>
 * 用户：查询订单10086状态
 * Agent 判断需要调用 queryOrder() -> 订单服务查询数据库 -> 返回结果 -> Agent 组织回复
 * </pre>
 */
public class ToolCallingService {

    /**
     * 查询订单状态（示例工具，待接入 Function Calling 框架）。
     *
     * <p>初期支持：查询数据库 / 查询订单 / 查询天气；
     * 后期支持：GitHub / 文件系统 / 企业系统。
     */
    public String queryOrder(String orderId) {
        throw new UnsupportedOperationException("Tool Calling 为后续版本功能，尚未实现。");
    }
}
