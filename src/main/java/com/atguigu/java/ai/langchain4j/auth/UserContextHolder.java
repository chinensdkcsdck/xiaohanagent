package com.atguigu.java.ai.langchain4j.auth;

public final class UserContextHolder {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

