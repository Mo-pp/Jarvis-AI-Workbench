/**
 * 账户服务接口
 *
 * 作用：定义用户账户相关的业务操作
 *
 * 功能：
 * - 账户查询（按用户名/邮箱/ID）
 * - 账户存在性检查
 * - 注册、重置密码、修改密码
 */
package com.msz.resume.ai.auth.service;

import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.vo.ChangePasswordVO;
import com.msz.resume.ai.auth.vo.RegisterVO;
import com.msz.resume.ai.auth.vo.ResetPasswordVO;

public interface AccountService {

    /** 根据用户名或邮箱查询账户（支持两种登录方式） */
    Account findByUsernameOrEmail(String text);

    /** 根据ID查询账户 */
    Account findById(Integer id);

    /** 根据用户名查询账户 */
    Account findByUsername(String username);

    /** 检查用户名是否已存在 */
    boolean existsByUsername(String username);

    /** 检查邮箱是否已存在 */
    boolean existsByEmail(String email);

    /** 保存账户 */
    boolean save(Account account);

    /**
     * 用户注册
     * @return 成功返回 null，失败返回错误信息
     */
    String register(RegisterVO vo);

    /**
     * 重置密码
     * @return 成功返回 null，失败返回错误信息
     */
    String resetPassword(ResetPasswordVO vo);

    /**
     * 修改密码
     * @return 成功返回 null，失败返回错误信息
     */
    String changePassword(Integer userId, ChangePasswordVO vo);
}
