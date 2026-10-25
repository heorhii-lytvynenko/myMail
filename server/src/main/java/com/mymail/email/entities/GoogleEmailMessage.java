package com.mymail.email.entities;

import com.mymail.integration.google.entities.GoogleAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "google_emails",
        uniqueConstraints = @UniqueConstraint(name = "uq_google_emails_account_message",
                columnNames = {"google_account_id", "google_message_id"}),
        indexes = @Index(name = "idx_google_emails_account_thread", columnList = "google_account_id, google_thread_id"))
@PrimaryKeyJoinColumn(name = "id", foreignKey = @ForeignKey(name = "fk_google_emails_email"))
@Getter
@Setter
@NoArgsConstructor
public class GoogleEmailMessage extends Email {
    // Opaque provider IDs: do not assume a fixed hexadecimal width or convert them to UUIDs.
    public static final int MAX_GOOGLE_ID_LENGTH = 255;

    @NotBlank
    @Size(max = MAX_GOOGLE_ID_LENGTH)
    @Column(name = "google_message_id", nullable = false, length = MAX_GOOGLE_ID_LENGTH)
    private String googleMessageId;

    @NotBlank
    @Size(max = MAX_GOOGLE_ID_LENGTH)
    @Column(name = "google_thread_id", nullable = false, length = MAX_GOOGLE_ID_LENGTH)
    private String googleThreadId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "google_account_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_google_emails_account"))
    private GoogleAccount googleAccount;
}
