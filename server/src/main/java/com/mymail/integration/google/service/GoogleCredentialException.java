package com.mymail.integration.google.service;

/** Safe internal signal for definitively rejected credentials/permissions, never provider bodies. */
public class GoogleCredentialException extends RuntimeException {
    public GoogleCredentialException() { super("Google credentials or permissions rejected"); }
}
