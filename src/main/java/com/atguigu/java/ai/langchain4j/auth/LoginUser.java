package com.atguigu.java.ai.langchain4j.auth;

import java.io.Serializable;

public record LoginUser(Long userId, String account, String username, String idCard) implements Serializable {
}

