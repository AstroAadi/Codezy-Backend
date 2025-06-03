package com.codeColab.codab.dto;

import lombok.*;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemMessage {
    private String type; // "JOIN" or "LEAVE"
    private String email;
    private String sessionId;
    private Date timestamp;
}