package com.mymail.email.entities;

import com.mymail.MailApplication;
import com.mymail.integration.google.entities.GoogleAccount;
import com.mymail.user.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes = MailApplication.class, properties = {
        "auth0.domain=tenant.invalid", "auth0.audience=test-api", "auth0.client-id=test", "auth0.client-secret=test",
        "resend.api-key=test", "google.oauth.enabled=false",
        "google.oauth.token-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "logging.level.root=ERROR", "spring.main.banner-mode=off"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailPersistenceIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;
    @Autowired Validator validator;
    @Autowired PlatformTransactionManager transactionManager;
    TransactionTemplate transactions;

    @BeforeEach void setup() {
        transactions = new TransactionTemplate(transactionManager);
        jdbc.execute("truncate table google_emails, emails, google_oauth_states, google_accounts, users cascade");
    }

    private GoogleAccount account() {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setIdpId("auth0|" + suffix);
        user.setEmail(suffix + "@example.com");
        entities.persist(user);
        GoogleAccount account = new GoogleAccount();
        account.setUser(user);
        account.setGoogleUserId(suffix);
        account.setEmail(suffix + "@gmail.example");
        account.setAccessToken("test-provider-access");
        account.setRefreshToken("test-provider-refresh");
        account.setAccessTokenExpiresAt(Instant.now().plusSeconds(3600));
        entities.persist(account);
        return account;
    }

    private GoogleEmailMessage message(GoogleAccount account) {
        GoogleEmailMessage message = new GoogleEmailMessage();
        message.setThreadId("application-thread");
        message.setGoogleMessageId("provider-message");
        message.setGoogleThreadId("provider-thread");
        message.setGoogleAccount(account);
        message.setFrom("Sender <sender@example.com>");
        message.setTo("First <first@example.com>, Second <second@example.com>");
        message.setSubject("Subject");
        message.setSnippet("Preview");
        message.setReceivedAt(Instant.parse("2026-10-25T12:00:00Z"));
        return message;
    }

    private UUID persistMessage(Consumer<GoogleEmailMessage> customize) {
        return transactions.execute(status -> {
            GoogleEmailMessage message = message(account());
            customize.accept(message);
            entities.persist(message);
            entities.flush();
            return message.getId();
        });
    }

    @Test void maxLengthValuesRoundTripWithJoinedIdentityAuditAndVersion() {
        UUID id = persistMessage(message -> {
            message.setThreadId("t".repeat(Email.MAX_THREAD_ID_LENGTH));
            message.setFrom("f".repeat(Email.MAX_HEADER_LENGTH));
            message.setTo("t".repeat(Email.MAX_HEADER_LENGTH));
            message.setSubject("s".repeat(Email.MAX_HEADER_LENGTH));
            message.setSnippet("p".repeat(Email.MAX_SNIPPET_LENGTH));
            message.setGoogleMessageId("m".repeat(GoogleEmailMessage.MAX_GOOGLE_ID_LENGTH));
            message.setGoogleThreadId("g".repeat(GoogleEmailMessage.MAX_GOOGLE_ID_LENGTH));
        });
        transactions.executeWithoutResult(status -> {
            entities.clear();
            GoogleEmailMessage loaded = entities.find(GoogleEmailMessage.class, id);
            assertNotNull(loaded);
            assertEquals(Email.MAX_HEADER_LENGTH, loaded.getFrom().length());
            assertEquals(Email.MAX_HEADER_LENGTH, loaded.getTo().length());
            assertEquals(Email.MAX_HEADER_LENGTH, loaded.getSubject().length());
            assertEquals(Email.MAX_SNIPPET_LENGTH, loaded.getSnippet().length());
            assertEquals(GoogleEmailMessage.MAX_GOOGLE_ID_LENGTH, loaded.getGoogleMessageId().length());
            assertNotNull(loaded.getCreatedAt());
            assertEquals("system", loaded.getCreatedBy());
            assertNotNull(loaded.getVersion());
            assertInstanceOf(GoogleEmailMessage.class, entities.find(Email.class, id));
        });
        assertEquals(1, jdbc.queryForObject("select count(*) from emails where id = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select count(*) from google_emails where id = ?", Integer.class, id));
        assertEquals(8, jdbc.queryForObject("select count(*) from databasechangelog", Integer.class));
    }

    @Test void beanValidationRejectsOversizedValuesAndMissingProviderOwnership() {
        GoogleEmailMessage message = message(new GoogleAccount());
        Map<String, Consumer<GoogleEmailMessage>> invalid = Map.of(
                "threadId", value -> value.setThreadId("x".repeat(Email.MAX_THREAD_ID_LENGTH + 1)),
                "from", value -> value.setFrom("x".repeat(Email.MAX_HEADER_LENGTH + 1)),
                "to", value -> value.setTo("x".repeat(Email.MAX_HEADER_LENGTH + 1)),
                "subject", value -> value.setSubject("x".repeat(Email.MAX_HEADER_LENGTH + 1)),
                "snippet", value -> value.setSnippet("x".repeat(Email.MAX_SNIPPET_LENGTH + 1)),
                "googleMessageId", value -> value.setGoogleMessageId("x".repeat(GoogleEmailMessage.MAX_GOOGLE_ID_LENGTH + 1)),
                "googleThreadId", value -> value.setGoogleThreadId("x".repeat(GoogleEmailMessage.MAX_GOOGLE_ID_LENGTH + 1)),
                "googleAccount", value -> value.setGoogleAccount(null));
        for (var test : invalid.entrySet()) {
            GoogleEmailMessage candidate = message(message.getGoogleAccount());
            test.getValue().accept(candidate);
            assertTrue(validator.validate(candidate).stream()
                    .anyMatch(violation -> test.getKey().equals(violation.getPropertyPath().toString())), test.getKey());
        }
        message.setThreadId(" "); message.setGoogleMessageId(" "); message.setGoogleThreadId(" ");
        assertEquals(3, validator.validate(message).size());
    }

    @Test void databaseIndependentlyEnforcesAllLengthLimits() {
        UUID id = persistMessage(message -> { });
        Map<String, Integer> parentFields = Map.of("thread_id", 255, "from_header", 65535,
                "to_header", 65535, "subject", 65535, "snippet", 4096);
        for (var field : parentFields.entrySet()) {
            // Column names come only from the fixed test map; field values remain bound parameters.
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                    jdbc.update("update emails set " + field.getKey() + " = ? where id = ?",
                            "x".repeat(field.getValue() + 1), id));
        }
        for (String field : List.of("google_message_id", "google_thread_id")) {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                    jdbc.update("update google_emails set " + field + " = ? where id = ?", "x".repeat(256), id));
        }
    }

    @Test void providerIdentityIsUniquePerAccountButCanExistInDifferentAccounts() {
        UUID id = persistMessage(message -> { });
        UUID accountId = jdbc.queryForObject("select google_account_id from google_emails where id = ?", UUID.class, id);
        RuntimeException duplicate = assertThrows(RuntimeException.class, () -> transactions.executeWithoutResult(status -> {
            entities.persist(message(entities.find(GoogleAccount.class, accountId)));
            entities.flush();
        }));
        assertTrue(hasSqlState(duplicate, "23505"));
        persistMessage(message -> { });
        assertEquals(2, jdbc.queryForObject("select count(*) from emails", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from google_emails", Integer.class));
    }

    @Test void softDeletionHidesMessagesAndAccountDeletionRemovesBothJoinedTables() {
        UUID deleted = persistMessage(message -> { });
        UUID otherAccountMessage = persistMessage(message -> { });
        UUID accountId = jdbc.queryForObject("select google_account_id from google_emails where id = ?", UUID.class, deleted);
        UUID active = transactions.execute(status -> {
            GoogleEmailMessage message = message(entities.find(GoogleAccount.class, accountId));
            message.setGoogleMessageId("second-provider-message");
            entities.persist(message);
            entities.flush();
            entities.find(GoogleEmailMessage.class, deleted).delete("test");
            return message.getId();
        });
        transactions.executeWithoutResult(status -> {
            entities.clear();
            assertNull(entities.find(GoogleEmailMessage.class, deleted));
            assertNotNull(entities.find(GoogleEmailMessage.class, active));
            entities.remove(entities.find(GoogleAccount.class, accountId));
            entities.flush();
        });
        assertEquals(1, jdbc.queryForObject("select count(*) from google_accounts", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from google_emails", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from emails", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from emails where id = ?", Integer.class, otherAccountMessage));
    }

    @Test void deletingBaseRowCascadesChildWithoutAffectingOtherMessages() {
        UUID deleted = persistMessage(message -> { });
        UUID kept = persistMessage(message -> { });
        jdbc.update("delete from emails where id = ?", deleted);
        assertEquals(0, jdbc.queryForObject("select count(*) from google_emails where id = ?", Integer.class, deleted));
        assertEquals(1, jdbc.queryForObject("select count(*) from google_emails where id = ?", Integer.class, kept));
    }

    private static boolean hasSqlState(Throwable error, String state) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof SQLException sql && state.equals(sql.getSQLState())) return true;
        return false;
    }
}
