package com.mymail.email.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class EmailMessage {
    private String id;
    private String threadId;
    private String from;
    private String to;
    private String subject;
    private String body;
    private Instant receivedAt;


}