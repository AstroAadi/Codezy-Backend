package com.codeColab.codab.Controllers;

import com.codeColab.codab.dto.ChatMessage;
import com.codeColab.codab.dto.CodeUpdate;
import com.codeColab.codab.dto.CollaboratorDTO;
import com.codeColab.codab.entity.Collaborator;
import com.codeColab.codab.service.CodeSharingService;

import com.codeColab.codab.service.CollaboratorService;
import com.codeColab.codab.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/collaboration")
public class CollaborationController {
    private final EmailService emailService;
    private final CollaboratorService collaboratorService;
    private final CodeSharingService codeSharingService; // New service for code management
    @Autowired
    private SimpMessagingTemplate messagingTemplate;


    @Autowired
    public CollaborationController(EmailService emailService,
                                   CollaboratorService collaboratorService,
                                   CodeSharingService codeSharingService) {
        this.emailService = emailService;
        this.collaboratorService = collaboratorService;
        this.codeSharingService = codeSharingService;
    }

    @PostMapping("/addCollaborator")
    public ResponseEntity<String> addCollaborator(@RequestBody Collaborator collaborator) {
        Collaborator newCollaborator = collaboratorService.addCollaborator(collaborator);
        emailService.sendCollaboratorEmail(
                collaborator.getEmail(),
                collaborator.getSessionId(),
                collaborator.getProjectName(),
                collaborator.getPermission()
        );
        return ResponseEntity.ok("Email sent to " + newCollaborator.getEmail());
    }

    @GetMapping("/verifySession")
    public ResponseEntity<?> verifySession(
            @RequestParam String sessionId,
            @RequestParam(required = false) String email) {
        try {
            List<Collaborator> collaborators = collaboratorService.findAllBySessionId(sessionId);
            if (collaborators.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("isValid", false));
            }

            // If email is provided, verify specific collaborator
            if (email != null && !email.isEmpty()) {
                Collaborator collaborator = collaborators.stream()
                        .filter(c -> c.getEmail().equals(email))
                        .findFirst()
                        .orElse(null);

                if (collaborator != null) {
                    return ResponseEntity.ok(Map.of(
                            "isValid", true,
                            "canEdit", "EDIT".equals(collaborator.getPermission()),
                            "email", collaborator.getEmail()
                    ));
                }
            }

            // If no email provided or not found, return first collaborator (owner)
            Collaborator owner = collaborators.get(0);
            return ResponseEntity.ok(Map.of(
                    "isValid", true,
                    "canEdit", "EDIT".equals(owner.getPermission()),
                    "email", owner.getEmail()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to verify session"));
        }
    }

    @MessageMapping("/code.update")
    @SendTo("/topic/code.changes")
    public CodeUpdate updateCode(@Payload CodeUpdate update) {
        // Store code update
        codeSharingService.saveCodeUpdate(update);
        return update;
    }

    @GetMapping("/code/{sessionId}")
    public ResponseEntity<String> getLatestCode(@PathVariable String sessionId, @PathVariable String filepath) {
        String code = codeSharingService.getLatestCode(sessionId, filepath);
        return ResponseEntity.ok(code);
    }

    @PostMapping("/joinSession")
    public ResponseEntity<?> joinSession(@RequestBody Map<String, String> payload) {
        String sessionId = payload.get("sessionId");
        String email = payload.get("email");
        String name = payload.get("name"); // <-- fetch name from payload
        List<Collaborator> collaborators = collaboratorService.findAllBySessionId(sessionId);
        Optional<Collaborator> collaboratorOpt = collaborators.stream().filter(c -> c.getEmail().equals(email)).findFirst();
        if (collaboratorOpt.isPresent()) {
            Collaborator collaborator = collaboratorOpt.get();
            collaborator.setOnline(true);
            collaboratorService.updateCollaborator(collaborator);
            // Broadcast updated list and joining user's name
            List<Collaborator> updatedList = collaboratorService.findAllBySessionId(sessionId);
            Map<String, Object> broadcastPayload = new HashMap<>();
            broadcastPayload.put("collaborators", updatedList);
            broadcastPayload.put("joinedUser", name); // <-- include name
            messagingTemplate.convertAndSend("/topic/collaborators/" + sessionId, broadcastPayload);
            return ResponseEntity.ok(Map.of("joined", true));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("joined", false));
        }
    }
}