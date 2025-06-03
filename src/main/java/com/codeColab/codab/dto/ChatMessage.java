package com.codeColab.codab.dto;

import com.codeColab.codab.entity.WsChatMessageType;
import lombok.*;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor@NoArgsConstructor
@Builder
public class ChatMessage {
    //private String sessionId;
    private String sender;
    private String text;
    private Date timestamp;

    public void setType(WsChatMessageType wsChatMessageType) {
    }
    // getters, setters
}
