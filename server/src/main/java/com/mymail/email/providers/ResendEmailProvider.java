package com.mymail.email.providers;


import com.mymail.email.exception.EmailSendingException;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResendEmailProvider implements EmailProvider {

    private final Resend resend;

    @Override
    public void send(String to, String subject, String html) {
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from("myMail <onboarding@resend.dev>")
                .to(to)
                .subject(subject)
                .html(html)
                .build();

        try {
            resend.emails().send(options);
        } catch (ResendException exception) {
            throw new EmailSendingException("Failed to send email", exception);
        }
    }
}
