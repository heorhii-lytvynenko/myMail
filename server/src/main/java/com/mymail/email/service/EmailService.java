package com.mymail.email.service;

import com.google.auth.oauth2.AccessToken;
import com.mymail.email.entities.Email;
import java.util.List;

public interface EmailService {
    List<Email> getEmails(AccessToken accessToken);
    Email getEmail(AccessToken accessToken, String messageId);
}
