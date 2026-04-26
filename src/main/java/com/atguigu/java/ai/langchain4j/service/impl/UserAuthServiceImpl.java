package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.auth.LoginUser;
import com.atguigu.java.ai.langchain4j.entity.SysUser;
import com.atguigu.java.ai.langchain4j.exception.BusinessException;
import com.atguigu.java.ai.langchain4j.exception.ErrorCode;
import com.atguigu.java.ai.langchain4j.mapper.SysUserMapper;
import com.atguigu.java.ai.langchain4j.service.UserAuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserAuthServiceImpl implements UserAuthService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public LoginUser authenticate(String account, String rawPassword) {
        if (!StringUtils.hasText(account) || !StringUtils.hasText(rawPassword)) {
            return null;
        }
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getAccount, account.trim())
                        .eq(SysUser::getStatus, 1)
                        .last("limit 1")
        );
        if (user == null) {
            return null;
        }
        if (!rawPassword.trim().equals(user.getPasswordHash())) {
            return null;
        }
        return new LoginUser(user.getId(), user.getAccount(), user.getUsername(), user.getIdCard());
    }

    @Override
    public LoginUser register(String account, String rawPassword, String username, String idCard) {
        if (!StringUtils.hasText(account) || !StringUtils.hasText(rawPassword)
                || !StringUtils.hasText(username) || !StringUtils.hasText(idCard)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "注册参数不能为空");
        }
        String accountTrim = account.trim();
        String passwordTrim = rawPassword.trim();
        String usernameTrim = username.trim();
        String idCardTrim = idCard.trim();
        if (accountTrim.length() < 4 || accountTrim.length() > 32) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "账号长度需在4到32位之间");
        }
        if (passwordTrim.length() < 6 || passwordTrim.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "密码长度需在6到64位之间");
        }
        if (usernameTrim.length() > 32) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "姓名长度不能超过32位");
        }
        if (idCardTrim.length() > 32) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "身份证长度不能超过32位");
        }
        Long accountExists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getAccount, accountTrim)
                .last("limit 1"));
        if (accountExists != null && accountExists > 0) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "账号已存在");
        }
        Long idCardExists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIdCard, idCardTrim)
                .last("limit 1"));
        if (idCardExists != null && idCardExists > 0) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "身份证已注册");
        }
        SysUser sysUser = new SysUser();
        sysUser.setAccount(accountTrim);
        sysUser.setPasswordHash(passwordTrim);
        sysUser.setUsername(usernameTrim);
        sysUser.setIdCard(idCardTrim);
        sysUser.setStatus(1);
        int inserted = sysUserMapper.insert(sysUser);
        if (inserted <= 0 || sysUser.getId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "注册失败，请稍后重试");
        }
        return new LoginUser(sysUser.getId(), sysUser.getAccount(), sysUser.getUsername(), sysUser.getIdCard());
    }
}
