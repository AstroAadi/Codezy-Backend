package com.codeColab.codab.Controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VideoCallController {

    @GetMapping("/health")
    public String healthCheck() {
        return "Video Call Backend is running.";
    }
}
