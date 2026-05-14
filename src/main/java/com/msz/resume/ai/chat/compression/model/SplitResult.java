package com.msz.resume.ai.chat.compression.model;

/**
 * 消息分割结果
 *
 * <p>描述 L5 Autocompact 分割消息列表的计算结果。
 *
 * @param splitIndex       分割索引，[0, splitIndex) 压缩，[splitIndex, n) 保留
 * @param preservedTokens  保留部分的 token 数
 * @param preservedCount   保留部分的消息数
 * @param shouldSplit      是否需要分割（消息数不足时为 false）
 */
public record SplitResult(
    int splitIndex,
    int preservedTokens,
    int preservedCount,
    boolean shouldSplit
) {

    /** 不分割的结果（消息数不足） */
    private static final SplitResult NO_SPLIT = new SplitResult(0, 0, 0, false);

    /**
     * 返回不需要分割的结果
     *
     * @return 不分割的结果实例
     */
    public static SplitResult noSplit() {
        return NO_SPLIT;
    }

    /**
     * 创建需要分割的结果
     *
     * @param splitIndex      分割索引
     * @param preservedTokens 保留部分的 token 数
     * @param preservedCount  保留部分的消息数
     * @return 分割结果实例
     */
    public static SplitResult of(int splitIndex, int preservedTokens, int preservedCount) {
        return new SplitResult(splitIndex, preservedTokens, preservedCount, true);
    }

    /**
     * 获取压缩部分的消息数
     *
     * @param totalMessages 总消息数
     * @return 压缩部分的消息数
     */
    public int getCompressedCount(int totalMessages) {
        if (!shouldSplit) {
            return 0;
        }
        return splitIndex;
    }

    /**
     * 获取压缩部分的 token 估算
     *
     * @param totalTokens 总 token 数
     * @return 压缩部分的 token 数（估算）
     */
    public int getCompressedTokens(int totalTokens) {
        if (!shouldSplit) {
            return 0;
        }
        return totalTokens - preservedTokens;
    }
}
