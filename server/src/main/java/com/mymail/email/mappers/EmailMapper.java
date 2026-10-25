package com.mymail.email.mappers;

import com.mymail.api.model.EmailResponse;
import com.mymail.api.model.GetGoogleEmailsResponse;
import com.mymail.api.model.PageResponse;
import com.mymail.email.entities.Email;
import com.mymail.email.entities.GoogleEmailMessage;
import com.resend.services.emails.model.CreateEmailOptions;
import java.time.ZoneOffset;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring", imports = {ZoneOffset.class, List.class})
public interface EmailMapper {
    @Mapping(target = "id", expression = "java(identifier(email))")
    @Mapping(target = "receivedAt", expression = "java(email.getReceivedAt() == null ? null : email.getReceivedAt().atOffset(ZoneOffset.UTC))")
    EmailResponse toResponse(Email email);

    @Mapping(target = "content", source = "emails.content")
    @Mapping(target = "page", source = "emails")
    GetGoogleEmailsResponse toResponse(Page<Email> emails);

    PageResponse toPageResponse(Page<?> page);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "from", constant = "myMail <onboarding@resend.dev>")
    @Mapping(target = "to", expression = "java(List.of(to))")
    @Mapping(target = "subject", source = "subject")
    @Mapping(target = "html", source = "html")
    CreateEmailOptions toSendRequest(String to, String subject, String html);

    default String identifier(Email email) {
        String id = email.getId() == null ? null : email.getId().toString();
        if (id == null && email instanceof GoogleEmailMessage google) id = google.getGoogleMessageId();
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Email identifier is required");
        return id;
    }
}
