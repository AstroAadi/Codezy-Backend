package com.codeColab.codab.model;

import lombok.Data;

@Data
public class FileUpdate {
    private String sessionId; // The session ID for collaboration
    private String content;   // The updated file content

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
