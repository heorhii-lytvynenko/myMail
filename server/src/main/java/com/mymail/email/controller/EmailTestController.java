package com.mymail.email.controller;


import com.mymail.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EmailTestController {

    private final EmailService emailService;

    @PostMapping("/test/email")
    public void sendTestEmail() {
        emailService.send(
                "heo.lytvynenko@gmail.com",
                "myMail test",
                "<h1>Hello from myMail</h1>"
        );
    }
}