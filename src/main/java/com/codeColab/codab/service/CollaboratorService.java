package com.codeColab.codab.service;

import com.codeColab.codab.entity.CollaborationSession;
import com.codeColab.codab.entity.Collaborator;
import com.codeColab.codab.repository.CollaboratorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CollaboratorService {

    private final CollaboratorRepository collaboratorRepository;

    @Autowired
    public CollaboratorService(CollaboratorRepository collaboratorRepository) {
        this.collaboratorRepository = collaboratorRepository;
    }

    public Collaborator addCollaborator(Collaborator collaborator) {
        // Check if the collaborator already exists in the same session
        Optional<Collaborator> existingCollaborator = collaboratorRepository.findByEmailAndSessionId(
                collaborator.getEmail(), collaborator.getSessionId());

        if (existingCollaborator.isPresent()) {
            throw new RuntimeException("Collaborator with this email is already part of the session.");
        }

        return collaboratorRepository.save(collaborator);
    }

    public List<Collaborator> getAllCollaborators() {
        return collaboratorRepository.findAll();
    }

    public Collaborator getCollaboratorById(Long id) {
        return collaboratorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Collaborator not found"));
    }


    public void deleteCollaborator(Long id) {
        collaboratorRepository.deleteById(id);
    }

    public void updateCollaborator(Collaborator newCollaborator) {
        // Always update isOnline status
        Optional<Collaborator> existing = collaboratorRepository.findByEmailAndSessionId(newCollaborator.getEmail(), newCollaborator.getSessionId());
        if (existing.isPresent()) {
            Collaborator toUpdate = existing.get();
            toUpdate.setOnline(newCollaborator.isOnline());
            toUpdate.setPermission(newCollaborator.getPermission());
            collaboratorRepository.save(toUpdate);
        } else {
            throw new RuntimeException("Collaborator not found");
        }
    }


    public Optional<Collaborator> getCollaboratorByEmailAndSession(String email, String sessionId) {
        System.out.println("🔍 Checking Collaborator for Email: " + email + " | SessionId: " + sessionId);

        Optional<Collaborator> collaborator = collaboratorRepository.findByEmailAndSessionId(email, sessionId);

        System.out.println("🔍 Query Result: " + (collaborator.isPresent() ? "Collaborator found ✅" : "Collaborator NOT found ❌"));

        return collaborator;
    }
    public Optional<CollaborationSession> getSessionByProjectName(String projectName) {
        return collaboratorRepository.findByProjectNameIgnoreCase(projectName);
    }

    public Optional<Collaborator> getCollaboratorBySessionId(String sessionId) {
        return collaboratorRepository.findBySessionId(sessionId);
    }

    public Collaborator getCollaboratorBySessionId(String sessionId, String email) {
        List<Collaborator> collaborators = collaboratorRepository.findAllBySessionId(sessionId);
        return collaborators.stream()
                .filter(c -> c.getEmail().equals(email))
                .findFirst()
                .orElse(null);
    }

//    public boolean verifyCollaboratorAccess(String sessionId, String email) {
//        List<Collaborator> collaborators = collaboratorRepository.findAllBySessionId(sessionId);
//        return collaborators.stream()
//                .anyMatch(c -> c.getEmail().equals(email));
//    }

    public List<Collaborator> findAllBySessionId(String sessionId) {
        return collaboratorRepository.findAllBySessionId(sessionId);
    }

    public Collaborator findBySessionIdAndEmail(String sessionId, String email) {
        return collaboratorRepository.findBySessionIdAndEmail(sessionId, email)
                .orElse(null);
    }



   public boolean verifyCollaboratorAccess(String sessionId, String email) {
       return collaboratorRepository.findBySessionIdAndEmail(sessionId, email)
               .isPresent();
   }
}
