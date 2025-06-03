package com.codeColab.codab.dto;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CodeResponse {
    private String output;

    public CodeResponse() {}

    public CodeResponse(String output) {
        this.output = output;
    }

}

