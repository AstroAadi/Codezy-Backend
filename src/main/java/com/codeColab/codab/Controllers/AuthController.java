package com.codeColab.codab.Controllers;
import com.codeColab.codab.entity.User;
import com.codeColab.codab.repository.UserRepository;
import com.codeColab.codab.service.JwtUtil;
import com.codeColab.codab.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private EmailService emailService;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody User user, @RequestParam String otp) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }
        if (!emailService.checkOtp(user.getEmail(), otp)) {
            throw new RuntimeException("Invalid or expired OTP");
        }
        User savedUser = userRepository.save(user);
        emailService.removeOtp(user.getEmail());
        String token = jwtUtil.generateToken(savedUser.getEmail());
        Map<String, Object> response = new HashMap<>();
        response.put("user", savedUser);
        response.put("token", token);
        return response;
    }

    @PostMapping("/send-otp")
    public Map<String, String> sendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        emailService.sendOtpEmail(email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "OTP sent");
        return response;
    }

    @PostMapping("/verify-otp")
    public Map<String, Boolean> verifyOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp = body.get("otp");
        boolean valid = emailService.checkOtp(email, otp);
        Map<String, Boolean> response = new HashMap<>();
        response.put("valid", valid);
        return response;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody User user) {
        User foundUser = userRepository.findByEmail(user.getEmail())
                .filter(u -> u.getPassword().equals(user.getPassword()))
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        String token = jwtUtil.generateToken(foundUser.getEmail());
        Map<String, Object> response = new HashMap<>();
        response.put("user", foundUser);
        response.put("token", token);
        return response;
    }
}