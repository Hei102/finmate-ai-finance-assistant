package com.zth.java.ai.langchain4j.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户信息响应：只返回 ID、账号和昵称，不返回密码散列。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserResponse {

    private Long id;
    private String username;
    private String nickname;
}
