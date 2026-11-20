package com.smartcity360.web.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.*;

import java.security.*;
import java.util.*;

@ApplicationScoped
public class CsrfTokenService {
    static final String ATTRIBUTE = CsrfTokenService.class.getName() + ".token";
    private final SecureRandom random = new SecureRandom();

    public String token(HttpSession session) {
        synchronized (session) {
            String token = (String) session.getAttribute(ATTRIBUTE);
            if (token == null) {
                byte[] bytes = new byte[32];
                random.nextBytes(bytes);
                token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                session.setAttribute(ATTRIBUTE, token);
            }
            return token;
        }
    }

    public boolean valid(HttpSession session, String supplied) {
        if (session == null || supplied == null) return false;
        String expected = (String) session.getAttribute(ATTRIBUTE);
        return expected != null && MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII), supplied.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
