package com.zth.java.ai.langchain4j.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 注册请求：接收账号、昵称和密码，并使用校验注解约束输入。 */
@Data
public class RegisterRequest {

    @NotBlank(message = "请输入账号")
    @Size(min = 3, max = 32, message = "账号长度需要在 3 到 32 位之间")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 72, message = "密码长度需要在 6 到 72 位之间")
    private String password;

    @Size(max = 32, message = "昵称不能超过 32 个字符")
    private String nickname;
}
