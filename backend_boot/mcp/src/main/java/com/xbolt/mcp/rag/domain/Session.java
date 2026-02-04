package com.xbolt.mcp.rag.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor
public class Session {
    @Id
    private String id;
    private String title;
    private LocalDateTime createdAt;

    public static Session create(String title) {
        Session session = new Session();
        session.id = UUID.randomUUID().toString();
        session.title = title == null ? "새로운 대화" : title;
        session.createdAt = LocalDateTime.now();
        return session;
    }
}
