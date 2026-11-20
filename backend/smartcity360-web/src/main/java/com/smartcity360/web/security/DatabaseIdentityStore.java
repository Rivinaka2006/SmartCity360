package com.smartcity360.web.security;

import com.smartcity360.common.security.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.*;
import jakarta.security.enterprise.identitystore.*;

import java.util.Arrays;

@ApplicationScoped
public class DatabaseIdentityStore implements IdentityStore {
    @Inject
    AuthenticationGateway gateway;

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (!(credential instanceof UsernamePasswordCredential c))
            return CredentialValidationResult.NOT_VALIDATED_RESULT;
        char[] password = c.getPassword().getValue();
        try {
            AuthenticationResult r = gateway.authenticate(c.getCaller(), password);
            return r.authenticated() ? new CredentialValidationResult(r.principalName(), r.roles()) : CredentialValidationResult.INVALID_RESULT;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Override
    public int priority() {
        return 50;
    }
}
