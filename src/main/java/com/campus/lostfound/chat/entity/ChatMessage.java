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
 * 持久化聊天消息，对应 {@code chat_message}。
 *
 * <p>{@code senderId + requestId} 由数据库唯一索引保证幂等，在线连接 Map 不是消息存储。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long senderId;

    private String requestId;

    private String content;

    private LocalDateTime createdAt;
}
