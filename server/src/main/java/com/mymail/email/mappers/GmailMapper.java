package com.mymail.email.mappers;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.mymail.email.entities.GoogleEmailMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = Instant.class)
public abstract class GmailMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "googleMessageId", source = "id")
    @Mapping(target = "googleThreadId", source = "threadId")
    @Mapping(target = "from", expression = "java(header(message.getPayload(), \"From\"))")
    @Mapping(target = "to", expression = "java(header(message.getPayload(), \"To\"))")
    @Mapping(target = "subject", expression = "java(header(message.getPayload(), \"Subject\"))")
    @Mapping(target = "receivedAt", expression = "java(message.getInternalDate() == null ? null : Instant.ofEpochMilli(message.getInternalDate()))")
    @Mapping(target = "body", expression = "java(body(message.getPayload()))")
    public abstract GoogleEmailMessage toEmail(Message message);

    protected String header(MessagePart payload, String name) {
        if (payload == null || payload.getHeaders() == null) return null;
        return payload.getHeaders().stream().filter(header -> name.equalsIgnoreCase(header.getName()))
                .map(MessagePartHeader::getValue).findFirst().orElse(null);
    }

    protected String body(MessagePart part) {
        if (part == null) return null;
        if (("text/plain".equalsIgnoreCase(part.getMimeType()) || "text/html".equalsIgnoreCase(part.getMimeType()))
                && part.getBody() != null && part.getBody().getData() != null) {
            return new String(Base64.getUrlDecoder().decode(part.getBody().getData()), StandardCharsets.UTF_8);
        }
        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                String result = body(child);
                if (result != null) return result;
            }
        }
        return null;
    }

}
