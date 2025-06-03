package com.codeColab.codab.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final Map<String, String> otpStorage = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendCollaboratorEmail(String email, String sessionId, String projectName, String permission) {
        String link = "https://codezy-e98c8.web.app/collaborate?sessionId=" + sessionId;
        String message = "You have been invited to collaborate on the project: " + projectName + ".\n\n"
                +"You are allowed to "+ permission+ "only. \n\n"
                + "Click the link below to join the session:\n" + link;

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(email);
        mailMessage.setSubject("Collaboration Invitation: " + projectName);
        mailMessage.setText(message);
        mailSender.send(mailMessage);
    }

    public void sendOtpEmail(String email) {
        String otp = String.format("%06d", random.nextInt(1000000));
        otpStorage.put(email, otp);
        String message = "Your OTP for registration is: " + otp + "\nIt is valid for 10 minutes.";
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(email);
        mailMessage.setSubject("Registration OTP");
        mailMessage.setText(message);
        mailSender.send(mailMessage);
    }

    public boolean checkOtp(String email, String otp) {
        String storedOtp = otpStorage.get(email);
        return storedOtp != null && storedOtp.equals(otp);
    }

    public void removeOtp(String email) {
        otpStorage.remove(email);
    }
}

