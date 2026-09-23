package com.example.agent.exception;

/**
 * Agent 业务语义异常。
 *
 * <p>与通用异常（{@code IllegalArgumentException} 等）的区别：
 *
 * 它携带一个明确的 {@link ErrorCode}，
 * {@link GlobalExceptionHandler} 可以据此返回结构化的错误码给调用方，
 * 而不是只能返回一段拼接的英文/中文异常信息。
 */
public class AgentBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public AgentBusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AgentBusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}