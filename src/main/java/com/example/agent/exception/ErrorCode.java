package com.example.agent.exception;

/**
 * 统一业务错误码。
 *
 * <p>为什么要有这一层，而不是继续在各处抛
 * {@code IllegalArgumentException} / {@code IllegalStateException}：
 *
 * <pre>
 * 1. 前端 / 调用方可以根据 code 做精细化处理，
 *    而不是只能解析 message 里的中文文本；
 * 2. 新增一种业务异常场景时，
 *    只需要在这里加一行枚举，
 *    不需要在业务代码里现造一个新的字符串错误码；
 * 3. HTTP 状态码（400/502/500）描述的是"协议层面发生了什么"，
 *    业务错误码描述的是"具体发生了什么"，两者不是一回事，
 *    混用会导致以后很难扩展。
 * </pre>
 */
public enum ErrorCode {

    PARAM_INVALID(400, "参数校验失败"),
    LLM_CALL_FAILED(502, "大模型调用失败"),
    CONTEXT_BUDGET_EXCEEDED(4001, "当前输入超过上下文 Token 预算"),
    TOOL_EXECUTION_FAILED(4002, "工具执行失败"),
    TOOL_ROUNDS_EXCEEDED(4003, "工具调用轮次过多，未能在限定轮次内给出最终答案"),
    KNOWLEDGE_BASE_EMPTY(4004, "知识库为空或未检索到相关内容"),
    INTERNAL_ERROR(500, "系统内部异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}