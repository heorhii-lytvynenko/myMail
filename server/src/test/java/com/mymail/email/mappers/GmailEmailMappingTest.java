package com.mymail.email.mappers;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.mymail.email.entities.GoogleEmailMessage;
import com.mymail.email.mappers.EmailMapper;
import com.mymail.email.mappers.GmailMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class GmailEmailMappingTest {
    @Test void internalBodyAndAccountMetadataAreExcludedFromApiResponse() {
        Message message = new Message().setId("provider-id").setThreadId("thread-id")
                .setSnippet("Public preview").setInternalDate(1_000L)
                .setPayload(new MessagePart().setMimeType("text/plain")
                        .setHeaders(List.of(new MessagePartHeader().setName("From").setValue("sender@example.com")))
                        .setBody(new MessagePartBody().setData(Base64.getUrlEncoder()
                                .encodeToString("Full private body".getBytes(StandardCharsets.UTF_8)))));
        GoogleEmailMessage internal = Mappers.getMapper(GmailMapper.class).toEmail(message);
        assertEquals("Full private body", internal.getBody());
        var response = Mappers.getMapper(EmailMapper.class).toResponse(internal);
        assertEquals("provider-id", response.getId());
        assertEquals("Public preview", response.getSnippet());
        assertNull(response.getSubject());
        var json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(response));
        assertFalse(json.has("body"));
        assertFalse(json.has("googleAccount"));
        assertFalse(json.has("deletedAt"));
        assertFalse(json.has("createdBy"));
    }

    @Test void missingProviderHeadersAndDateAreSafe() {
        var internal = Mappers.getMapper(GmailMapper.class).toEmail(new Message().setId("id").setThreadId("thread"));
        var response = Mappers.getMapper(EmailMapper.class).toResponse(internal);
        assertNull(response.getFrom());
        assertNull(response.getReceivedAt());
        assertNull(internal.getBody());
    }
}
