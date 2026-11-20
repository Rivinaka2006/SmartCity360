package com.smartcity360.web.security;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class LoginThrottleTest { @Test void blocksAfterFiveFailuresAndSuccessClearsBucket(){var t=new LoginThrottle();String k="127.0.0.1|user@example.test";for(int i=0;i<5;i++){assertTrue(t.allowed(k));t.failed(k);}assertFalse(t.allowed(k));t.succeeded(k);assertTrue(t.allowed(k));}}
