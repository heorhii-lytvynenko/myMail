package com.mymail.email.repository;

import com.mymail.email.entities.GoogleEmailMessage;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleEmailRepository extends JpaRepository<GoogleEmailMessage, UUID> {
    Page<GoogleEmailMessage> findByGoogleAccount_Id(UUID accountId, Pageable pageable);

    // Native parent deletion also includes soft-deleted emails; the FK cascades to google_emails.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            delete from emails where id in (
                select id from google_emails where google_account_id = :accountId
            )
            """, nativeQuery = true)
    void deleteCachedEmails(@Param("accountId") UUID accountId);
}
