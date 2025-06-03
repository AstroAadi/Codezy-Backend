package com.codeColab.codab.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Entity
@Table(name = "collaborators")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Collaborator {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String projectName;

    @Column(nullable = false)
    private String permission;

    @Transient
    private boolean isValid;

    @Column(nullable = false)
    private boolean isOnline;

    // Constructor for WebSocket verification
    public Collaborator(String email, String sessionId, boolean isValid) {
        this.email = email;
        this.sessionId = sessionId;
        this.isValid = isValid;
    }

    // Method to set permission from boolean
    public void setPermissionFromBoolean(boolean canEdit) {
        this.permission = canEdit ? "EDIT" : "READ";
    }

    // Method to get permission as boolean
    public boolean getPermissionAsBoolean() {
        return "EDIT".equals(this.permission);
    }


}

