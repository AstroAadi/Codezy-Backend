package com.codeColab.codab.dto;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CodeRequest {
    private String code;
    private String language;
    private String input;
    private String fileName;

}

