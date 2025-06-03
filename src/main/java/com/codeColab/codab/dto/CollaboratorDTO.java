package com.codeColab.codab.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CollaboratorDTO {
    private String email;
    private String name;
    private boolean isOnline;

    // getters and setters
}
