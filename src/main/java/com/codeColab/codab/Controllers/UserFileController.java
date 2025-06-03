package com.codeColab.codab.Controllers;

import com.codeColab.codab.entity.User;
import com.codeColab.codab.entity.UserFile;
import com.codeColab.codab.repository.UserRepository;
import com.codeColab.codab.service.UserFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "https://codezy-e98c8.web.app")
public class UserFileController {
    @Autowired
    private UserFileService userFileService;
    @Autowired
    private UserRepository userRepository;

    @PostMapping("/save")
    public UserFile saveFile(@RequestParam String fileName, @RequestParam String codeContent, @AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails.getUsername();
        User user = userRepository.findByEmail(email).orElseThrow();
        return userFileService.saveFile(user, fileName, codeContent);
    }

    @GetMapping
    public List<UserFile> getFiles(@AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails.getUsername();
        User user = userRepository.findByEmail(email).orElseThrow();
        return userFileService.getFilesByUser(user);
    }

    @GetMapping("/{fileName}")
    public UserFile getFile(@PathVariable String fileName, @AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails.getUsername();
        User user = userRepository.findByEmail(email).orElseThrow();
        return userFileService.getFileByUserAndName(user, fileName);
    }
}