package com.xbolt.mcp.rag.repository.jpa;

import com.xbolt.mcp.rag.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {
    List<Session> findAllByOrderByCreatedAtDesc();
}
