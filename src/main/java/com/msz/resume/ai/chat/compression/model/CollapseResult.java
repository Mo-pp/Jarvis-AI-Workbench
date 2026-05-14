package com.msz.resume.ai.chat.compression.model;

/**
 * 折叠结果记录
 *
 * <p>描述 L4 ContextCollapse 折叠记录的结果。
 *
 * <p>注意：L4 是投影式折叠，不修改原始消息。
 * 此结果只记录折叠的元数据，消息列表不变。
 *
 * @param foldGroupId    折叠组ID
 * @param startIndex     起始消息索引（包含）
 * @param endIndex       结束消息索引（不包含）
 * @param messageCount   折叠的消息数量
 * @param tokensFolded   折叠的 Token 数
 * @param originalTokens 折叠前的总 Token 数
 */
public record CollapseResult(
    int foldGroupId,
    int startIndex,
    int endIndex,
    int messageCount,
    int tokensFolded,
    int originalTokens
) {

    /**
     * 创建未折叠的结果
     *
     * @return CollapseResult，messageCount=0
     */
    public static CollapseResult notCollapsed() {
        return new CollapseResult(0, 0, 0, 0, 0, 0);
    }

    /**
     * 判断是否执行了折叠
     *
     * @return messageCount > 0
     */
    public boolean wasCollapsed() {
        return messageCount > 0;
    }
}
