package com.codeColab.codab.repository;
import com.codeColab.codab.entity.CollaborationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollaborationSessionRepository extends JpaRepository<CollaborationSession, Long> {
    Optional<CollaborationSession> findBySessionId(String sessionId);

    Optional<CollaborationSession> findByProjectName(String projectName);




}


