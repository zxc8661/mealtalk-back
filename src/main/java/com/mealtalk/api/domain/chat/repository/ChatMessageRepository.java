package com.mealtalk.api.domain.chat.repository;

import com.mealtalk.api.domain.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findAllByRoomIdOrderByCreatedAtAsc(Long roomId);
}
