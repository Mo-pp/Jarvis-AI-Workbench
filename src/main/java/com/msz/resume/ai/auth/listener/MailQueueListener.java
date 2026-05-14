/**
 * 邮件队列监听器
 *
 * 作用：监听 RabbitMQ 邮件队列，异步发送验证码邮件
 *
 * 流程：
 * 1. MailService 将验证码信息发送到 "mail" 队列
 * 2. 本监听器消费队列消息
 * 3. 根据验证码类型生成不同邮件内容
 * 4. 调用 JavaMailSender 发送邮件
 *
 * 优势：邮件发送不阻塞 HTTP 请求，提高响应速度
 */
package com.msz.resume.ai.auth.listener;

import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

@RabbitListener(queuesToDeclare = @Queue("mail"), concurrency = "3")
@Component
public class MailQueueListener {

    @Resource
    private JavaMailSender mailSender;

    /** 发件人邮箱，从配置读取 */
    @Value("${spring.mail.username}")
    private String from;

    /**
     * 处理邮件发送消息
     * @param data 包含 type(验证码类型)、email(目标邮箱)、code(验证码)
     */
    @RabbitHandler
    public void sendMailMessage(Map<String, Object> data) {
        String email = String.valueOf(data.get("email"));
        Number code = (Number) data.get("code");
        String type = String.valueOf(data.get("type"));

        // 根据验证码类型生成邮件内容
        SimpleMailMessage message = switch (type) {
            case "register" -> createMessage(
                    "【JARVIS】注册验证码",
                    "您好，您的注册验证码是：" + code.intValue() + "，有效期 3 分钟。为保证账户安全，请勿泄露给他人。",
                    email);
            case "reset" -> createMessage(
                    "【JARVIS】重置密码验证码",
                    "您好，您正在重置密码，验证码是：" + code.intValue() + "，有效期 3 分钟。如非本人操作，请忽略此邮件。",
                    email);
            case "modify" -> createMessage(
                    "【JARVIS】修改邮箱验证码",
                    "您好，您正在修改邮箱，验证码是：" + code.intValue() + "，有效期 3 分钟。如非本人操作，请忽略此邮件。",
                    email);
            default -> null;
        };

        if (message != null) {
            mailSender.send(message);
        }
    }

    /** 创建简单邮件消息对象 */
    private SimpleMailMessage createMessage(String subject, String content, String to) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setSubject(subject);
        message.setText(content);
        message.setTo(to);
        message.setFrom(from);
        return message;
    }
}
