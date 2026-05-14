package com.msz.resume.ai.chat.runtime.state;
import com.msz.resume.ai.chat.prompt.model.UserProfile;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ！外层大循环状态实现！
 *
 *
 * 会话状态
 * 存储会话的变量
 */




public class SessionState extends AgentState{
    // ---------------------- 全局变量 ----------------------
    /** 会话的唯一ID：用来区分不同的用户，不同的会话，比如你这次聊天的ID */
    public static final String SESSION_ID = "sessionId";

    /** 内层的状态：把内层小循环的整个状态，存在外层里 */
    public static final String INNER_STATE = "innerState";

    /** 用户上下文：用户身份和偏好信息，传给内层用于构建系统提示词 */
    public static final String USER_CONTEXT = "userContext";

    /** OpenViking 租户身份：显式随状态传递，避免异步线程丢失 ThreadLocal 后串租户 */
    public static final String OPENVIKING_IDENTITY = "openVikingIdentity";

    /** 这个会话还在不在运行：是不是已经结束了 */
    public static final String IS_ACTIVE = "isActive";

    /** 总共输入了多少Token：整个会话，你给大模型发了多少字，用来计费的 */
    public static final String TOTAL_INPUT_TOKENS = "totalInputTokens";
    /** 总共输出了多少Token：大模型给你返回了多少字 */
    public static final String TOTAL_OUTPUT_TOKENS = "totalOutputTokens";

//----------------------------------新加------------------------------------------------------------------------------------
    /** 会话创建时间 */
    public static final String CREATED_AT = "createdAt";
    /** 最后活跃时间（用于超时判断） */
    public static final String LAST_ACTIVE_AT = "lastActiveAt";
    // ====================== 会话超时配置 ======================
    /** 默认超时时间：30分钟 */
    public static final long DEFAULT_TIMEOUT_MINUTES = 30;

    /** 会话初始化消息    会话恢复时的初始消息*/
    public static final String initialMessage = "initialMessage";







    // ---------------------- 更新规则 ----------------------
    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(

            // 会话ID，直接覆盖
            Map.entry(SESSION_ID, Channels.base(() -> "")),

            // 内层状态，直接覆盖（初始为空的内层状态）
            Map.entry(INNER_STATE, Channels.base(() -> new QueryLoopState(new java.util.HashMap<>()))),

            // 用户上下文，直接覆盖
            Map.entry(USER_CONTEXT, Channels.base(UserProfile::empty)),

            // OpenViking 租户身份，直接覆盖
            Map.entry(OPENVIKING_IDENTITY, Channels.base(OpenVikingIdentity::empty)),

            // 会话是不是活跃，直接覆盖
            Map.entry(IS_ACTIVE, Channels.base(() -> true)),

            // 会话初始化消息，用 ArrayList 存消息，每次都创建一个新的空 ArrayList
            Map.entry(initialMessage, Channels.appender(ArrayList::new)),

            // 累计的输入Token，直接覆盖，要最新的
            Map.entry(TOTAL_INPUT_TOKENS, Channels.base(() -> 0)),

            // 累计的输出Token，直接覆盖
            Map.entry(TOTAL_OUTPUT_TOKENS, Channels.base(() -> 0)),

            // 会话创建时间，直接覆盖
            Map.entry(CREATED_AT, Channels.base(Instant::now)),

            // 最后活跃时间，直接覆盖
            Map.entry(LAST_ACTIVE_AT, Channels.base(Instant::now))
    );


    // 构造函数 初始化数据
    public SessionState(Map<String, Object> initData) {
        super(initData);
    }




    // ====================== 便捷取值方法 ======================
    // 拿会话ID,if null 抛异常
    public String getSessionId() {
        return this.<String>value(SESSION_ID).orElseThrow();
    }

    // 拿会话是不是活跃
    public boolean isActive() {
        return this.<Boolean>value(IS_ACTIVE).orElse(true);
    }

    //拿会话initial聊天消息
    public List<ChatMessage> getInitMessages() {
        return this.<List<ChatMessage>>value(initialMessage).orElse(new ArrayList<>());
    }


    // 拿累计输入Token
    public int getTotalInputTokens() {
        return this.<Integer>value(TOTAL_INPUT_TOKENS).orElse(0);
    }

    // 拿累计输出Token
    public int getTotalOutputTokens() {
        return this.<Integer>value(TOTAL_OUTPUT_TOKENS).orElse(0);
    }

    // 拿会话创建时间
    public Instant getCreatedAt() {
        return this.<Instant>value(CREATED_AT).orElse(Instant.now());
    }
    // 拿会话最后活跃时间
    public Instant getLastActiveAt() {
        return this.<Instant>value(LAST_ACTIVE_AT).orElse(Instant.now());
    }
    /**
     * 获取内层状态
     */
    public QueryLoopState getInnerState() {
        return this.<QueryLoopState>value(INNER_STATE).orElse(null);
    }

    /**
     * 获取用户上下文
     * 如果没有设置，返回 empty()
     */
    public UserProfile getUserContext() {
        return this.<UserProfile>value(USER_CONTEXT).orElse(UserProfile.empty());
    }

    public OpenVikingIdentity getOpenVikingIdentity() {
        OpenVikingIdentity identity = this.<OpenVikingIdentity>value(OPENVIKING_IDENTITY).orElse(OpenVikingIdentity.empty());
        return identity.isComplete() ? identity : null;
    }
    /**
     * 检查会话是否超时
     */
    public boolean isTimeout() {
        Instant lastActive = getLastActiveAt();
        Instant timeoutThreshold = Instant.now().minusSeconds(DEFAULT_TIMEOUT_MINUTES * 60);
        return lastActive.isBefore(timeoutThreshold);
    }
    /**
     * 检查会话是否可继续运行（活跃且未超时）
     */
    public boolean canContinue() {
        return isActive() && !isTimeout();
    }

}
