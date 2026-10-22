package com.mymail.integration.google.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

@Component
public class CredentialCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public CredentialCipher(@Value("${google.oauth.token-encryption-key:}") String encoded,
                            @Value("${google.oauth.enabled:false}") boolean enabled) {
        try {
            key = encoded.isBlank() ? null : Base64.getUrlDecoder().decode(encoded);
            if ((key != null && key.length != 32) || (enabled && key == null)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) { throw new IllegalStateException("A 32-byte Google token encryption key is required"); }
    }
    public String encrypt(String value) {
        if (value == null) return null;
        requireKey();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD("mymail-google-v1".getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return "v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception ex) { throw new IllegalStateException("Credential encryption failed"); }
    }
    public String decrypt(String value) {
        if (value == null) return null;
        requireKey();
        try {
            if (!value.startsWith("v1:")) throw new IllegalArgumentException();
            byte[] combined = Base64.getUrlDecoder().decode(value.substring(3));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, java.util.Arrays.copyOfRange(combined, 0, 12)));
            cipher.updateAAD("mymail-google-v1".getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(combined, 12, combined.length - 12), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Stored credential cannot be decrypted"); }
    }
    private void requireKey() { if (key == null) throw new IllegalStateException("Google token encryption key is required"); }
}
