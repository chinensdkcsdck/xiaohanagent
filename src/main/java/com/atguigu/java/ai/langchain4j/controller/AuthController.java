package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.auth.AuthConstants;
import com.atguigu.java.ai.langchain4j.auth.LoginUser;
import com.atguigu.java.ai.langchain4j.service.UserAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "登录鉴权")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserAuthService userAuthService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request, HttpSession session) {
        LoginUser user = userAuthService.authenticate(request.account(), request.password());
        if (user == null) {
            return Map.of("success", false, "message", "账号或密码错误");
        }
        session.setAttribute(AuthConstants.SESSION_USER_KEY, user);
        return Map.of("success", true, "message", "登录成功", "user", user);
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest request, HttpSession session) {
        LoginUser user = userAuthService.register(
                request.account(),
                request.password(),
                request.username(),
                request.idCard()
        );
        session.setAttribute(AuthConstants.SESSION_USER_KEY, user);
        return Map.of("success", true, "message", "注册成功", "user", user);
    }

    @Operation(summary = "当前登录信息")
    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        LoginUser user = (LoginUser) session.getAttribute(AuthConstants.SESSION_USER_KEY);
        if (user == null) {
            return Map.of("success", false, "message", "未登录");
        }
        return Map.of("success", true, "user", user);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("success", true, "message", "已退出登录");
    }

    public record LoginRequest(String account, String password) {
    }

    public record RegisterRequest(String account, String password, String username, String idCard) {
    }
}
