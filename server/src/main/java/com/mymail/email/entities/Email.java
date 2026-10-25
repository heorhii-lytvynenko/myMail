package com.mymail.email.entities;

import com.mymail.common.SoftDeletable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "emails", indexes = @Index(name = "idx_emails_received_at", columnList = "received_at, id"))
@Getter
@Setter
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.JOINED)
public class Email extends SoftDeletable {
    // Application storage limits, not SMTP or provider guarantees. Never silently truncate imported data.
    public static final int MAX_THREAD_ID_LENGTH = 255;
    public static final int MAX_HEADER_LENGTH = 65_535;
    public static final int MAX_SNIPPET_LENGTH = 4_096;

    @NotBlank
    @Size(max = MAX_THREAD_ID_LENGTH)
    @Column(name = "thread_id", nullable = false, length = MAX_THREAD_ID_LENGTH)
    private String threadId;

    // These are complete headers, which may include display names and multiple mailboxes.
    @Size(max = MAX_HEADER_LENGTH)
    @Column(name = "from_header", columnDefinition = "text")
    private String from;

    @Size(max = MAX_HEADER_LENGTH)
    @Column(name = "to_header", columnDefinition = "text")
    private String to;

    // Unfolded subjects are not limited to one physical RFC 5322 header line.
    @Size(max = MAX_HEADER_LENGTH)
    @Column(name = "subject", columnDefinition = "text")
    private String subject;

    @Size(max = MAX_SNIPPET_LENGTH)
    @Column(name = "snippet", length = MAX_SNIPPET_LENGTH)
    private String snippet;

    @Column(name = "received_at")
    private Instant receivedAt;
}
