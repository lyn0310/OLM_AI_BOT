package com.xbolt.mcp.rag.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId; // Foreign Key logic handled manually or via join, simplifying for now

    private String role; // "user" or "assistant"

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createdAt;

    public static Message create(String sessionId, String role, String content) {
        Message msg = new Message();
        msg.sessionId = sessionId;
        msg.role = role;
        msg.content = content;
        msg.createdAt = LocalDateTime.now();
        return msg;
    }
}
