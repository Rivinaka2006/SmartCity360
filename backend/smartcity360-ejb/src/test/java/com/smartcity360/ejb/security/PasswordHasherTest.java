package com.smartcity360.ejb.security;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class PasswordHasherTest {
 @Test void hashesAndVerifiesWithUniqueSalts(){var h=new PasswordHasher();char[] p="correct horse battery staple".toCharArray();String a=h.hash(p),b=h.hash(p);assertNotEquals(a,b);assertTrue(h.verify(p,a));assertTrue(h.verify(p,b));assertFalse(h.verify("wrong".toCharArray(),a));assertTrue(a.startsWith("sc360$1$pbkdf2-sha256$600000$"));}
 @Test void rejectsMalformedAndSignalsOldParameters(){var h=new PasswordHasher();assertFalse(h.verify("x".toCharArray(),"invalid"));String value=h.hash("secret".toCharArray());assertFalse(h.needsUpgrade(value));assertTrue(h.needsUpgrade(value.replace("$600000$","$599999$")));}
}
