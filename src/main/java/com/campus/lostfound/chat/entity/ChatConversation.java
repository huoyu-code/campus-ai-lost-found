package com.campus.lostfound.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 帖子关联的双人聊天会话，对应 {@code chat_conversation}。
 *
 * <p>用户 ID 始终按低 ID、高 ID 排序保存，数据库唯一键才能可靠阻止同一帖子
 * 同一用户对创建多个会话。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long postId;

    private Long userLowId;

    private Long userHighId;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
