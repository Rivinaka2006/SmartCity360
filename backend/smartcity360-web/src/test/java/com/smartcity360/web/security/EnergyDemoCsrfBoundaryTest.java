package com.smartcity360.web.security;

import com.smartcity360.web.energy.EnergyDemoRequestSizeFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EnergyDemoCsrfBoundaryTest {
    private static final URI BASE_URI = URI.create("https://city.example/smartcity360/api/v1/");

    @Test
    void demoPostRequiresValidSessionCsrfTokenAndSameOrigin() {
        Map<String, Object> sessionAttributes = new HashMap<>();
        HttpSession session = proxy(HttpSession.class, (method, arguments) -> {
            if ("getAttribute".equals(method)) {
                return sessionAttributes.get(arguments[0]);
            }
            if ("setAttribute".equals(method)) {
                sessionAttributes.put((String) arguments[0], arguments[1]);
            }
            return null;
        });
        CsrfTokenService tokens = new CsrfTokenService();
        String validToken = tokens.token(session);

        assertEquals(403, invokeCsrfFilter(tokens, session, null, "https://city.example").getStatus());
        assertEquals(403, invokeCsrfFilter(tokens, session, validToken, "https://attacker.example").getStatus());
        assertNull(invokeCsrfFilter(tokens, session, validToken, "https://city.example"));

        assertEquals(403, invokeCsrfFilter(
                tokens,
                session,
                null,
                "https://city.example",
                "energy/streams/demo/process-reading").getStatus());
        assertNull(invokeCsrfFilter(
                tokens,
                session,
                validToken,
                "https://city.example",
                "energy/streams/demo/process-reading"));
    }

    @Test
    void demoRequestBodyIsBoundedEvenWhenContentLengthIsUnknown() throws Exception {
        AtomicReference<Response> aborted = new AtomicReference<>();
        AtomicReference<InputStream> entityStream = new AtomicReference<>(
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        EnergyDemoRequestSizeFilter filter = new EnergyDemoRequestSizeFilter();

        filter.filter(requestContext(entityStream, aborted, -1));

        assertNull(aborted.get());
        assertEquals("{}", new String(entityStream.get().readAllBytes(), StandardCharsets.UTF_8));

        byte[] oversized = new byte[1_025];
        entityStream.set(new ByteArrayInputStream(oversized));
        filter.filter(requestContext(
                entityStream,
                aborted,
                -1,
                "energy/streams/demo/process-reading"));

        assertEquals(413, aborted.get().getStatus());
    }

    private static Response invokeCsrfFilter(
            CsrfTokenService tokens,
            HttpSession session,
            String token,
            String origin) {
        return invokeCsrfFilter(tokens, session, token, origin, "energy/streams/demo");
    }

    private static Response invokeCsrfFilter(
            CsrfTokenService tokens,
            HttpSession session,
            String token,
            String origin,
            String path) {
        AtomicReference<Response> aborted = new AtomicReference<>();
        CsrfRequestFilter filter = new CsrfRequestFilter();
        filter.tokens = tokens;
        filter.request = proxy(HttpServletRequest.class, (method, arguments) ->
                "getSession".equals(method) ? session : null);
        UriInfo requestUri = uriInfo(path);
        filter.uri = requestUri;
        ContainerRequestContext request = proxy(ContainerRequestContext.class, (method, arguments) -> {
            if ("getMethod".equals(method)) return "POST";
            if ("getUriInfo".equals(method)) return requestUri;
            if ("getHeaderString".equals(method)) {
                return switch ((String) arguments[0]) {
                    case "X-CSRF-Token" -> token;
                    case "Origin" -> origin;
                    case "Cookie", "Referer" -> null;
                    default -> null;
                };
            }
            if ("abortWith".equals(method)) {
                aborted.set((Response) arguments[0]);
            }
            return null;
        });

        filter.filter(request);
        return aborted.get();
    }

    private static ContainerRequestContext requestContext(
            AtomicReference<InputStream> entityStream,
            AtomicReference<Response> aborted,
            int contentLength) {
        return requestContext(entityStream, aborted, contentLength, "energy/streams/demo");
    }

    private static ContainerRequestContext requestContext(
            AtomicReference<InputStream> entityStream,
            AtomicReference<Response> aborted,
            int contentLength,
            String path) {
        return proxy(ContainerRequestContext.class, (method, arguments) -> {
            if ("getMethod".equals(method)) return "POST";
            if ("getUriInfo".equals(method)) return uriInfo(path);
            if ("getLength".equals(method)) return contentLength;
            if ("getEntityStream".equals(method)) return entityStream.get();
            if ("setEntityStream".equals(method)) {
                entityStream.set((InputStream) arguments[0]);
            }
            if ("abortWith".equals(method)) {
                aborted.set((Response) arguments[0]);
            }
            return null;
        });
    }

    private static UriInfo uriInfo() {
        return uriInfo("energy/streams/demo");
    }

    private static UriInfo uriInfo(String path) {
        return proxy(UriInfo.class, (method, arguments) -> {
            if ("getBaseUri".equals(method)) return BASE_URI;
            if ("getPath".equals(method)) return path;
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> invocation.invoke(method.getName(), arguments));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] arguments) throws Throwable;
    }
}
