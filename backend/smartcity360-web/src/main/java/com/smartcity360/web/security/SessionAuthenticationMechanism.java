package com.smartcity360.web.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.*;
import jakarta.security.enterprise.authentication.mechanism.http.*;
import jakarta.security.enterprise.identitystore.*;
import jakarta.servlet.http.*;

@ApplicationScoped
@AutoApplySession
public class SessionAuthenticationMechanism implements HttpAuthenticationMechanism {
    @Inject
    IdentityStoreHandler stores;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response, HttpMessageContext context) {
        var credential = context.getAuthParameters().getCredential();
        if (credential == null) return context.doNothing();
        CredentialValidationResult result = stores.validate(credential);
        if (result.getStatus() != CredentialValidationResult.Status.VALID) return context.responseUnauthorized();
        context.setRegisterSession(result.getCallerPrincipal().getName(), result.getCallerGroups());
        return context.notifyContainerAboutLogin(result);
    }
}
