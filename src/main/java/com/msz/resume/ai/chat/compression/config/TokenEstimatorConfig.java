package com.msz.resume.ai.chat.compression.config;

import com.msz.resume.ai.chat.session.converter.ChatMessageTextExtractor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TokenCountEstimator Bean 配置
 *
 * 为什么需要这个配置？
 * - UsageStatNode（用量统计节点）依赖 TokenCountEstimator 来估算 Token 用量
 * - LangChain4j 的 DashScope Spring Boot Starter 不会自动注册这个 Bean
 * - 如果不手动提供，Spring 启动时会报 "No qualifying bean of type TokenCountEstimator" 错误
 *
 * 估算原理（简化版）：
 * - 中文约 1 个字 ≈ 1~2 个 Token
 * - 英文约 1 个单词 ≈ 1~2 个 Token，平均 4 个字符 ≈ 1 个 Token
 * - 综合来看，粗略估算：字符数 / 2 ≈ Token 数
 * - 这不是精确计算，但足够用于用量统计和成本估算
 *
 * 精确版替代方案：
 * - OpenAI 模型：new OpenAiTokenCountEstimator("gpt-4o-mini")（基于 tiktoken 算法）
 * - 其他模型：参考对应模型提供商的 Token 计算工具
 */
@Configuration
public class  TokenEstimatorConfig {

@Bean
public TokenCountEstimator tokenCountEstimator() {
    return new TokenCountEstimator() {

        /**
         * 估算纯文本的 Token 数
         *
         * 逻辑：字符数 / 2
         * 例："Hello World"（11字符）→ 5 Token
         * 例："你好世界"（4字符）→ 2 Token
         *
         * @param text 待估算的文本
         * @return 估算的 Token 数
         */
        @Override
        public int estimateTokenCountInText(String text) {
            return text.length() / 2;
        }

        /**
         * 估算单条消息的 Token 数
         *
         * 逻辑：先提取消息的文本内容长度，再除以 2
         * 不同消息类型提取文本的方式不同（见 extractTextLength）
         *
         * @param message 单条聊天消息
         * @return 估算的 Token 数
         */
        @Override
        public int estimateTokenCountInMessage(ChatMessage message) {
            return extractTextLength(message) / 2;
        }

        /**
         * 估算多条消息的总 Token 数
         *
         * 逻辑：遍历所有消息，累加文本长度，最后除以 2
         * UsageStatNode 就是用这个方法来统计输入/输出 Token 用量的
         *
         * @param messages 聊天消息列表
         * @return 估算的总 Token 数
         */
        @Override
        public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
            int charCount = 0;
            for (var msg : messages) {
                charCount += extractTextLength(msg);
            }
            return charCount / 2;
        }

        /**
         * 从 ChatMessage 中提取文本内容的字符长度
         *
         * 为什么不能直接用 ChatMessage.text()？
         * - LangChain4j 1.0+ 移除了 ChatMessage 接口的 text() 方法
         * - 每种消息子类型有自己的获取文本方式：
         *   - AiMessage.text()              → AI 回复的文本
         *   - UserMessage.singleText()       → 用户消息的文本
         *   - SystemMessage.text()           → 系统提示词的文本
         *   - ToolExecutionResultMessage.text() → 工具执行结果的文本
         *
         * @param msg 聊天消息
         * @return 文本字符长度，无法提取时返回 0
         */
        private int extractTextLength(ChatMessage msg) {
            if (msg instanceof AiMessage aiMsg && aiMsg.text() != null) {
                return aiMsg.text().length();
            } else if (msg instanceof UserMessage userMsg) {
                return ChatMessageTextExtractor.userText(userMsg).length();
            } else if (msg instanceof SystemMessage sysMsg) {
                return sysMsg.text().length();
            } else if (msg instanceof ToolExecutionResultMessage toolMsg) {
                return toolMsg.text().length();
            }
            return 0;
        }
    };
}
}
