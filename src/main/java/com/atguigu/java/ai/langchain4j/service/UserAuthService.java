package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.auth.LoginUser;

public interface UserAuthService {

    LoginUser authenticate(String account, String rawPassword);

    LoginUser register(String account, String rawPassword, String username, String idCard);
}
