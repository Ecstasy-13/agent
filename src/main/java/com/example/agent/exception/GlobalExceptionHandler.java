package com.example.agent.exception;

import com.example.agent.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * 全局异常处理，统一返回 ApiResponse 结构。
 *
 * <p>处理顺序（Spring 会按"最具体匹配优先"选择 Handler，
 * 与这里声明的先后顺序无关，但仍然按"从具体到通用"排列，方便阅读）：
 *
 * <pre>
 * 1. 参数校验失败          -> 400
 * 2. Agent 业务语义异常     -> 400 + 具体业务错误码
 * 3. 调用大模型失败         -> 502
 * 4. 其他未预期异常         -> 500
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.PARAM_INVALID.getCode(), message));
    }

    /**
     * Agent 业务语义异常。
     *
     * <p>例如：
     * Token 预算超限、
     * 工具调用轮次超限、
     * 知识库为空等。
     *
     * <p>这类异常属于"预期内的业务边界情况"，
     * 不需要打印完整堆栈，warn 级别记录关键信息即可。
     */
    @ExceptionHandler(AgentBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(AgentBusinessException e) {
        log.warn("Agent 业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getErrorCode().getCode(), e.getMessage()));
    }

    /** 调用大模型失败 */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiResponse<Void>> handleRestClient(RestClientException e) {
        log.error("大模型调用失败", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(ErrorCode.LLM_CALL_FAILED.getCode(), ErrorCode.LLM_CALL_FAILED.getMessage() + ": " + e.getMessage()));
    }

    /** 其他未预期异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage() + ": " + e.getMessage()));
    }
}