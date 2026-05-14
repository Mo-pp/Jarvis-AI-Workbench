package com.msz.resume.ai.chat.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 折叠状态实体
 *
 * <p>用于 L4 ContextCollapse 投影式折叠。
 * 存储折叠的元数据（哪些消息被折叠），而不是消息内容本身。
 *
 * <p>架构说明：
 * <ul>
 *   <li>原始消息始终保留在状态中不变</li>
 *   <li>折叠只记录"哪些消息被折叠了"的元数据</li>
 *   <li>发送给 API 时，根据折叠状态生成投影视图</li>
 *   <li>PTL 恢复时，直接删除折叠记录，原始消息自动恢复</li>
 * </ul>
 */
@Data
@TableName("folded_message_blob")
public class FoldedMessageBlob {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 折叠组ID（同一次折叠的消息共享同一组ID） */
    private Integer foldGroupId;

    /** 起始消息索引（包含） */
    private Integer startIndex;

    /** 结束消息索引（不包含） */
    private Integer endIndex;

    /** 折叠的消息数量 */
    private Integer messageCount;

    /** 折叠时的 Token 数 */
    private Integer tokensFolded;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public FoldedMessageBlob() {
        this.createdAt = LocalDateTime.now();
    }

    public FoldedMessageBlob(String sessionId, Integer foldGroupId, Integer startIndex,
                             Integer endIndex, Integer messageCount, Integer tokensFolded) {
        this.sessionId = sessionId;
        this.foldGroupId = foldGroupId;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.messageCount = messageCount;
        this.tokensFolded = tokensFolded;
        this.createdAt = LocalDateTime.now();
    }
}
