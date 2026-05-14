/**
 * 账户服务实现类
 *
 * 作用：实现用户账户的增删改查和业务逻辑
 *
 * 核心功能：
 * - 注册：验证码验证 → 检查重名 → BCrypt 加密密码 → 保存
 * - 重置密码：验证码验证 → 加密新密码 → 更新
 * - 修改密码：验证旧密码 → 加密新密码 → 更新
 */
package com.msz.resume.ai.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.mapper.AccountMapper;
import com.msz.resume.ai.auth.service.AccountService;
import com.msz.resume.ai.auth.service.MailService;
import com.msz.resume.ai.auth.vo.ChangePasswordVO;
import com.msz.resume.ai.auth.vo.RegisterVO;
import com.msz.resume.ai.auth.vo.ResetPasswordVO;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingProvisioningService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountMapper accountMapper;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final OpenVikingProvisioningService openVikingProvisioningService;

    public AccountServiceImpl(AccountMapper accountMapper,
                              MailService mailService,
                              PasswordEncoder passwordEncoder,
                              OpenVikingProvisioningService openVikingProvisioningService) {
        this.accountMapper = accountMapper;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.openVikingProvisioningService = openVikingProvisioningService;
    }

    /** 根据用户名或邮箱查询（支持两种登录方式） */
    @Override
    public Account findByUsernameOrEmail(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return accountMapper.selectOne(
                Wrappers.<Account>lambdaQuery()
                        .eq(Account::getUsername, text)
                        .or()
                        .eq(Account::getEmail, text)
        );
    }

    /** 根据ID查询 */
    @Override
    public Account findById(Integer id) {
        if (id == null) {
            return null;
        }
        return accountMapper.selectById(id);
    }

    @Override
    public Account findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return accountMapper.selectOne(
                Wrappers.<Account>lambdaQuery()
                        .eq(Account::getUsername, username.trim())
        );
    }

    /** 检查用户名是否已存在 */
    @Override
    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return accountMapper.exists(
                Wrappers.<Account>lambdaQuery()
                        .eq(Account::getUsername, username)
        );
    }

    /** 检查邮箱是否已存在 */
    @Override
    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return accountMapper.exists(
                Wrappers.<Account>lambdaQuery()
                        .eq(Account::getEmail, email)
        );
    }

    /** 保存账户 */
    @Override
    public boolean save(Account account) {
        if (account == null) {
            return false;
        }
        return accountMapper.insert(account) > 0;
    }

    /**
     * 用户注册
     * 流程：验证码验证 → 检查重名重邮箱 → BCrypt加密密码 → 保存 → 删除验证码
     */
    @Override
    public String register(RegisterVO vo) {
        // 1. 验证验证码
        String verifyError = mailService.verifyCode("register", vo.getEmail(), vo.getCode());
        if (verifyError != null) {
            return verifyError;
        }
        // 2. 检查用户名是否已注册
        if (existsByUsername(vo.getUsername())) {
            return "用户名已被注册";
        }
        // 3. 检查邮箱是否已注册
        if (existsByEmail(vo.getEmail())) {
            return "邮箱已被注册";
        }

        String adminKey;
        try {
            adminKey = openVikingProvisioningService.createAdminAccountForUsername(vo.getUsername());
        } catch (OpenVikingClientException e) {
            return "注册失败: " + e.getMessage();
        } catch (Exception e) {
            return "注册失败: OpenViking 开通异常: " + e.getMessage();
        }

        // 4. 创建账户
        Account account = new Account();
        account.setUsername(vo.getUsername());
        account.setEmail(vo.getEmail());
        account.setPassword(passwordEncoder.encode(vo.getPassword()));  // BCrypt 加密
        account.setRegisterTime(LocalDateTime.now());
        account.setOpenvikingAdminKey(adminKey);

        // 5. 保存到数据库
        if (!save(account)) {
            return "注册失败，请稍后重试";
        }

        // 6. 删除已使用的验证码
        mailService.deleteCode("register", vo.getEmail());
        return null;
    }

    /**
     * 重置密码
     * 流程：验证码验证 → 查询用户 → 加密新密码 → 更新 → 删除验证码
     */
    @Override
    public String resetPassword(ResetPasswordVO vo) {
        // 1. 验证验证码
        String verifyError = mailService.verifyCode("reset", vo.getEmail(), vo.getCode());
        if (verifyError != null) {
            return verifyError;
        }

        // 2. 查询用户
        Account account = accountMapper.selectOne(
                Wrappers.<Account>lambdaQuery().eq(Account::getEmail, vo.getEmail())
        );
        if (account == null) {
            return "用户不存在";
        }

        // 3. 更新密码
        account.setPassword(passwordEncoder.encode(vo.getPassword()));
        if (accountMapper.updateById(account) <= 0) {
            return "重置密码失败，请稍后重试";
        }

        // 4. 删除已使用的验证码
        mailService.deleteCode("reset", vo.getEmail());
        return null;
    }

    /**
     * 修改密码
     * 流程：查询用户 → 验证旧密码 → 检查新旧密码不同 → 加密新密码 → 更新
     */
    @Override
    public String changePassword(Integer userId, ChangePasswordVO vo) {
        // 1. 查询用户
        Account account = findById(userId);
        if (account == null) {
            return "用户不存在";
        }
        // 2. 验证旧密码
        if (!passwordEncoder.matches(vo.getOldPassword(), account.getPassword())) {
            return "旧密码错误";
        }
        // 3. 新密码不能与旧密码相同
        if (passwordEncoder.matches(vo.getNewPassword(), account.getPassword())) {
            return "新密码不能与旧密码相同";
        }

        // 4. 更新密码
        account.setPassword(passwordEncoder.encode(vo.getNewPassword()));
        if (accountMapper.updateById(account) <= 0) {
            return "修改密码失败，请稍后重试";
        }
        return null;
    }
}
