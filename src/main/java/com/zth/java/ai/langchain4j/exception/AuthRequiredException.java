package com.zth.java.ai.langchain4j.exception;

/** 表示用户尚未登录或登录已失效，由全局异常处理器返回 HTTP 401。 */
public class AuthRequiredException extends RuntimeException {

    public AuthRequiredException(String message) {
        super(message);
    }
}
