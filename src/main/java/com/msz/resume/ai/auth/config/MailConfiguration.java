/**
 * RabbitMQ 邮件队列配置
 *
 * 作用：配置异步邮件发送的消息队列
 *
 * 流程：
 * 1. MailService 发送验证码 → 消息放入 "mail" 队列
 * 2. MailQueueListener 监听队列 → 异步发送邮件
 *
 * 优势：邮件发送不阻塞请求，提高响应速度
 */
package com.msz.resume.ai.auth.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailConfiguration {

    /** 创建邮件队列（持久化） */
    @Bean
    public Queue mailQueue() {
        return new Queue("mail", true);
    }

    /** 创建 JSON 消息转换器，用于序列化/反序列化队列消息 */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
