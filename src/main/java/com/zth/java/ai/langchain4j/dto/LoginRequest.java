package com.zth.java.ai.langchain4j.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录请求：接收账号和密码。 */
@Data
public class LoginRequest {

    @NotBlank(message = "请输入账号")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;
}
