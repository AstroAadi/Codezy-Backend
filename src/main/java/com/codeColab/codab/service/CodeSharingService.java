package com.codeColab.codab.service;

import com.codeColab.codab.dto.CodeUpdate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CodeSharingService {
    // Map<sessionId, Map<filePath, content>>
    private final Map<String, Map<String, String>> sessionCode = new ConcurrentHashMap<>();

    //private static final Logger logger = LoggerFactory.getLogger(CodeSharingService.class);

    public void saveCodeUpdate(CodeUpdate update) {
        //logger.info("Storing code for session: {} | filePath: {}", update.getSessionId(), update.getFilePath());
        sessionCode
            .computeIfAbsent(update.getSessionId(), k -> new ConcurrentHashMap<>())
            .put(update.getFilePath(), update.getContent());
    }

    public String getLatestCode(String sessionId, String filePath) {
        return sessionCode.getOrDefault(sessionId, new ConcurrentHashMap<>())
                          .getOrDefault(filePath, "");
    }
}