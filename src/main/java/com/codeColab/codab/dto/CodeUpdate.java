package com.codeColab.codab.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CodeUpdate {
    private String sessionId;
    private String username;
    private String content;
    private String updatedBy;
    private Date timestamp;
    private String filePath; // or filePath
    // Add getter and setter for fileName/filePath
}