package com.mymail.email.providers;

import com.mymail.model.EmailMessage;

import java.util.List;

public interface EmailReceiver {
    List<EmailMessage> getEmails();
}
