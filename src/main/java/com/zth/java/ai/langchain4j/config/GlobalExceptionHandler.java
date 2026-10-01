package com.zth.java.ai.langchain4j.config;

import com.zth.java.ai.langchain4j.bean.ApiResponse;
import com.zth.java.ai.langchain4j.exception.AuthRequiredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一把参数校验、登录失效和业务异常转换成前端可读的响应。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不合法");
        return ApiResponse.failure(message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ApiResponse.failure(exception.getMessage());
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AuthRequiredException.class)
    public ApiResponse<Void> handleAuthRequiredException(AuthRequiredException exception) {
        return ApiResponse.failure(exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleHttpMessageNotReadableException() {
        return ApiResponse.failure("请求体格式不正确");
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Void> handleNoResourceFoundException(NoResourceFoundException exception) {
        return ApiResponse.failure("资源不存在");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("Unhandled application exception", exception);
        return ApiResponse.failure(resolveMessage(exception));
    }

    private String resolveMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return "系统内部错误，请查看后端日志";
        }
        if (message.contains("Authentication Fails") || message.contains("auth header")) {
            return "大模型 API Key 未配置或格式不正确，请设置 DEEPSEEK_API_KEY 后重启服务";
        }
        return message;
    }
}
