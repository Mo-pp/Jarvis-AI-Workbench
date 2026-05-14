/**
 * 邮件服务接口
 *
 * 作用：定义验证码的发送、验证、删除操作
 *
 * 流程：
 * 1. 用户请求验证码 → sendVerificationCode() 发送到队列
 * 2. 验证码存入 Redis（3分钟有效）
 * 3. 用户提交验证码 → verifyCode() 验证
 * 4. 验证成功 → deleteCode() 删除验证码
 */
package com.msz.resume.ai.auth.service;

public interface MailService {

    /**
     * 发送验证码
     * 生成随机验证码，存入 Redis，通过 RabbitMQ 异步发送邮件
     * @param type 验证码类型：register/reset/modify
     * @param email 目标邮箱
     * @return 成功返回 null，失败返回错误信息
     */
    String sendVerificationCode(String type, String email);

    /**
     * 验证验证码
     * @return 成功返回 null，失败返回错误信息
     */
    String verifyCode(String type, String email, String code);

    /** 删除验证码（验证成功后调用） */
    void deleteCode(String type, String email);
}
