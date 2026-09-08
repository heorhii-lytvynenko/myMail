package com.mymail.email.providers;

public interface EmailSender {
    void send(String to, String subject, String html);
}
