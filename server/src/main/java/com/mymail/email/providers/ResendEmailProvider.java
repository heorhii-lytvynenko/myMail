package com.mymail.email.providers;

import com.mymail.email.entities.Email;
import com.mymail.email.exception.EmailSendingException;
import com.mymail.email.mappers.EmailMapper;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResendEmailProvider implements EmailProvider {

    private final Resend resend;
    private final EmailMapper emailMapper;

    @Override
    public void send(String to, String subject, String html) {
        CreateEmailOptions options = emailMapper.toSendRequest(to, subject, html);

        try {
            resend.emails().send(options);
        } catch (ResendException exception) {
            throw new EmailSendingException("Failed to send email", exception);
        }
    }

    @Override
    public List<Email> getEmails() {
        return List.of();
    }
}
