package com.msz.resume.ai.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.mapper.AccountMapper;
import com.msz.resume.ai.auth.service.impl.AccountServiceImpl;
import com.msz.resume.ai.auth.vo.ChangePasswordVO;
import com.msz.resume.ai.auth.vo.RegisterVO;
import com.msz.resume.ai.auth.vo.ResetPasswordVO;
import com.msz.resume.ai.integrations.openviking.core.config.OpenVikingProperties;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    private AccountMapper accountMapper;
    private MailService mailService;
    private PasswordEncoder passwordEncoder;
    private OpenVikingProvisioningService openVikingProvisioningService;
    private OpenVikingProperties openVikingProperties;
    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        accountMapper = Mockito.mock(AccountMapper.class);
        mailService = Mockito.mock(MailService.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        openVikingProvisioningService = Mockito.mock(OpenVikingProvisioningService.class);
        openVikingProperties = new OpenVikingProperties();
        accountService = new AccountServiceImpl(accountMapper, mailService, passwordEncoder, openVikingProvisioningService, openVikingProperties);
    }

    @Test
    @DisplayName("注册成功")
    void testRegister_Success() {
        RegisterVO vo = new RegisterVO();
        vo.setEmail("new@example.com");
        vo.setCode("123456");
        vo.setUsername("new_user");
        vo.setPassword("password123");

        when(mailService.verifyCode("register", "new@example.com", "123456")).thenReturn(null);
        when(accountMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
        when(accountMapper.insert(any(Account.class))).thenReturn(1);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(openVikingProvisioningService.createAdminAccountForUsername("new_user")).thenReturn("admin-key");

        assertNull(accountService.register(vo));
        verify(mailService).deleteCode("register", "new@example.com");
    }

    @Test
    @DisplayName("重置密码成功")
    void testResetPassword_Success() {
        ResetPasswordVO vo = new ResetPasswordVO();
        vo.setEmail("new@example.com");
        vo.setCode("123456");
        vo.setPassword("password123");

        Account account = new Account();
        account.setId(1);
        account.setEmail("new@example.com");
        account.setPassword("old");

        when(mailService.verifyCode("reset", "new@example.com", "123456")).thenReturn(null);
        when(accountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(accountMapper.updateById(any(Account.class))).thenReturn(1);

        assertNull(accountService.resetPassword(vo));
        verify(mailService).deleteCode("reset", "new@example.com");
    }

    @Test
    @DisplayName("修改密码成功")
    void testChangePassword_Success() {
        ChangePasswordVO vo = new ChangePasswordVO();
        vo.setOldPassword("oldpass");
        vo.setNewPassword("newpass123");

        Account account = new Account();
        account.setId(1);
        account.setPassword("hashed_old");

        when(accountMapper.selectById(1)).thenReturn(account);
        when(passwordEncoder.matches("oldpass", "hashed_old")).thenReturn(true);
        when(passwordEncoder.matches("newpass123", "hashed_old")).thenReturn(false);
        when(passwordEncoder.encode("newpass123")).thenReturn("hashed_new");
        when(accountMapper.updateById(any(Account.class))).thenReturn(1);

        assertNull(accountService.changePassword(1, vo));
    }
}
