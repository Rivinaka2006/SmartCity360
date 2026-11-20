package com.smartcity360.web.security;
import com.smartcity360.common.security.CurrentUserView; import org.junit.jupiter.api.Test; import java.util.Arrays; import static org.junit.jupiter.api.Assertions.*;
class SecurityResponseContractTest { @Test void authenticatedViewContainsNoPasswordOrSecretProperties(){var names=Arrays.stream(CurrentUserView.class.getRecordComponents()).map(c->c.getName().toLowerCase()).toList();assertFalse(names.stream().anyMatch(n->n.contains("password")||n.contains("hash")||n.contains("token")||n.contains("secret")));}}
