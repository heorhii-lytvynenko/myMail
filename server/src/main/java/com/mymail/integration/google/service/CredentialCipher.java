package com.mymail.integration.google.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CredentialCipher {
    public static final String ENCRYPTED_PREFIX = "v1:";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int AUTHENTICATION_TAG_BITS = 128;
    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final byte[] ADDITIONAL_AUTHENTICATED_DATA =
            "mymail-google-v1".getBytes(StandardCharsets.UTF_8);

    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public CredentialCipher(@Value("${google.oauth.token-encryption-key:}") String encoded,
                            @Value("${google.oauth.enabled:false}") boolean enabled) {
        this.key = decodeKey(encoded, enabled);
    }

    public String encrypt(String value) {
        if (value == null) {
            return null;
        }
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = combine(nonce, encrypted);
            return ENCRYPTED_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Credential encryption failed");
        }
    }

    public String decrypt(String value) {
        if (value == null) {
            return null;
        }
        requireKey();
        try {
            if (!value.startsWith(ENCRYPTED_PREFIX)) {
                throw new IllegalArgumentException();
            }
            byte[] combined = Base64.getUrlDecoder().decode(value.substring(ENCRYPTED_PREFIX.length()));
            byte[] nonce = Arrays.copyOfRange(combined, 0, NONCE_BYTES);
            Cipher cipher = createCipher(Cipher.DECRYPT_MODE, nonce);
            byte[] decrypted = cipher.doFinal(combined, NONCE_BYTES, combined.length - NONCE_BYTES);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored credential cannot be decrypted");
        }
    }

    private byte[] decodeKey(String encoded, boolean enabled) {
        try {
            byte[] decoded = encoded.isBlank() ? null : Base64.getUrlDecoder().decode(encoded);
            if ((decoded != null && decoded.length != KEY_BYTES) || (enabled && decoded == null)) {
                throw new IllegalArgumentException();
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("A 32-byte Google token encryption key is required");
        }
    }

    private Cipher createCipher(int mode, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(mode, new SecretKeySpec(key, KEY_ALGORITHM),
                new GCMParameterSpec(AUTHENTICATION_TAG_BITS, nonce));
        cipher.updateAAD(ADDITIONAL_AUTHENTICATED_DATA);
        return cipher;
    }

    private byte[] combine(byte[] nonce, byte[] encrypted) {
        byte[] combined = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, combined, 0, nonce.length);
        System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
        return combined;
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("Google token encryption key is required");
        }
    }
}
