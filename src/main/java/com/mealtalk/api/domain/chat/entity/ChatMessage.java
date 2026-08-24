package com.mealtalk.api.domain.chat.entity;

import com.mealtalk.api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Getter @Entity @Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_room_created", columnList = "room_id, created_at")) @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id", nullable = false) private ChatRoom room;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ChatRole role;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(length = 50) private String action;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "analysis_result") private Map<String, Object> analysisResult;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private MessageStatus status;

    public static ChatMessage create(ChatRoom room, ChatRole role, String content, String action, Map<String, Object> analysisResult, MessageStatus status) {
        ChatMessage message = new ChatMessage();
        message.room = room;
        message.role = role;
        message.content = content;
        message.action = action;
        message.analysisResult = analysisResult;
        message.status = status;
        return message;
    }
}
