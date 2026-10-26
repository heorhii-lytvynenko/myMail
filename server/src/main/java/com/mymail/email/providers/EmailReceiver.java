package com.mymail.email.providers;

import com.mymail.email.entities.Email;
import java.util.List;

public interface EmailReceiver {
    List<Email> getEmails();
}
