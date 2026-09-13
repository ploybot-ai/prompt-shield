package com.ploybot.promptshield.proxy;

import com.ploybot.promptshield.proxy.controller.ProxyAuthorization;
import com.ploybot.promptshield.proxy.controller.ProxyAuthorization.Upstream;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProxyAuthorizationTest {

    @Test
    void clientTokensAreRequiredAndTheServerKeyIsUsedUpstream() {
        ProxyAuthorization auth = new ProxyAuthorization(List.of("t1", "t2"), "sk-server", false);

        assertEquals(Optional.of(new Upstream("Bearer sk-server")), auth.resolve("Bearer t2"));
        assertTrue(auth.resolve("Bearer nope").isEmpty());
        assertTrue(auth.resolve(null).isEmpty());
        assertTrue(auth.resolve("t1").isEmpty(), "the token must be sent as a bearer token");
    }

    @Test
    void withoutClientTokensTheClientKeyIsForwarded() {
        ProxyAuthorization auth = new ProxyAuthorization(List.of(), "sk-server", false);

        assertEquals(Optional.of(new Upstream("Bearer sk-client")), auth.resolve("Bearer sk-client"));
        assertTrue(auth.resolve(null).isEmpty(), "the server key must not be available anonymously");
        assertFalse(auth.exposesProviderKeyWithoutAuthentication());
    }

    @Test
    void anonymousUseOfTheServerKeyMustBeExplicit() {
        ProxyAuthorization auth = new ProxyAuthorization(List.of(), "sk-server", true);

        assertEquals(Optional.of(new Upstream("Bearer sk-server")), auth.resolve(null));
        assertTrue(auth.exposesProviderKeyWithoutAuthentication());
    }

    @Test
    void providersWithoutKeyAcceptAnonymousRequests() {
        ProxyAuthorization auth = new ProxyAuthorization(List.of(), null, false);

        assertEquals(Optional.of(new Upstream(null)), auth.resolve(null));
    }
}
