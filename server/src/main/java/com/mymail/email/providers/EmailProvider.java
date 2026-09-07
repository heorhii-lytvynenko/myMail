package com.mymail.email.providers;


public interface EmailProvider {

    void send(
            String to,
            String subject,
            String html
    );
}