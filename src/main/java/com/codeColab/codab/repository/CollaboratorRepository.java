package com.codeColab.codab.repository;

import com.codeColab.codab.entity.CollaborationSession;
import com.codeColab.codab.entity.Collaborator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollaboratorRepository extends JpaRepository<Collaborator, Long> {
    boolean existsByEmail(String email);
    Optional<Collaborator> findByEmailAndSessionId(String email, String sessionId);

    Optional<CollaborationSession> findByProjectNameIgnoreCase(String projectName);

    Optional<Collaborator> findCollaboratorBySessionId(String sessionId);

    Optional<Collaborator> findBySessionId(String sessionId);
    List<Collaborator> findAllBySessionId(String sessionId);

    Optional<Collaborator> findBySessionIdAndEmail(String sessionId, String email);}
