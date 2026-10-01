package com.zth.java.ai.langchain4j.service;

import com.zth.java.ai.langchain4j.dto.CurrentUserResponse;
import com.zth.java.ai.langchain4j.dto.LoginRequest;
import com.zth.java.ai.langchain4j.dto.RegisterRequest;
import com.zth.java.ai.langchain4j.entity.AppUser;
import com.zth.java.ai.langchain4j.exception.AuthRequiredException;
import com.zth.java.ai.langchain4j.repository.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

/** 处理注册、登录和 Session 身份校验；密码散列交给 PasswordService。 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CURRENT_USER_ID = "CURRENT_USER_ID";

    private final AppUserRepository appUserRepository;
    private final PasswordService passwordService;

    /** 创建用户并建立登录状态。 */
    public CurrentUserResponse register(RegisterRequest request, HttpSession session) {
        String username = normalizeUsername(request.getUsername());
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("这个账号已经注册过了");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash(request.getPassword()));
        user.setNickname(resolveNickname(request.getNickname(), username));
        user.setCreatedAt(LocalDateTime.now());

        AppUser saved = appUserRepository.save(user);
        markLoggedIn(session, saved.getId());
        return toResponse(saved);
    }

    /** 校验账号密码，成功后将用户 ID 写入 Session。 */
    public CurrentUserResponse login(LoginRequest request, HttpSession session) {
        String username = normalizeUsername(request.getUsername());
        AppUser user = appUserRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("账号或密码不正确"));
        if (!passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("账号或密码不正确");
        }
        markLoggedIn(session, user.getId());
        return toResponse(user);
    }

    /** 读取当前登录用户的展示信息。 */
    public CurrentUserResponse currentUser(HttpSession session) {
        return toResponse(requireUser(session));
    }

    /** 取得已登录用户 ID，供业务接口按用户隔离数据。 */
    public Long requireUserId(HttpSession session) {
        return requireUser(session).getId();
    }

    /** 校验 Session 中的身份，失效时抛出登录异常。 */
    public AppUser requireUser(HttpSession session) {
        Object value = session.getAttribute(CURRENT_USER_ID);
        if (!(value instanceof Long userId)) {
            throw new AuthRequiredException("请先登录");
        }
        return appUserRepository.findById(userId)
                .orElseThrow(() -> {
                    session.invalidate();
                    return new AuthRequiredException("登录状态已失效，请重新登录");
                });
    }

    /** 销毁当前登录会话。 */
    public void logout(HttpSession session) {
        session.invalidate();
    }

    private void markLoggedIn(HttpSession session, Long userId) {
        session.setAttribute(CURRENT_USER_ID, userId);
    }

    private CurrentUserResponse toResponse(AppUser user) {
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getNickname());
    }

    private String normalizeUsername(String username) {
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{3,32}")) {
            throw new IllegalArgumentException("账号只能使用 3-32 位字母、数字、下划线或短横线");
        }
        return normalized;
    }

    private String resolveNickname(String nickname, String username) {
        if (nickname == null || nickname.isBlank()) {
            return username;
        }
        return nickname.trim();
    }
}
