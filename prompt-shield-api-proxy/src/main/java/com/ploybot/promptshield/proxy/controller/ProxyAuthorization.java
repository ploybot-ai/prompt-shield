package com.ploybot.promptshield.proxy.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether a client may use the proxy and which {@code Authorization} header is
 * sent upstream.
 *
 * <ul>
 *   <li>With client tokens configured, the client must present one of them; the
 *       provider API key (if any) is used upstream.</li>
 *   <li>Without client tokens, a client's own {@code Authorization} header is forwarded
 *       as is. A request without one may only use the provider API key when
 *       {@code allowUnauthenticated} is set; otherwise it is rejected, so the server key
 *       is never available to anyone who can reach the proxy.</li>
 * </ul>
 */
public class ProxyAuthorization {

    private static final String BEARER = "Bearer ";

    private final List<byte[]> clientTokens;
    private final String providerApiKey;
    private final boolean allowUnauthenticated;

    public ProxyAuthorization(List<String> clientTokens, String providerApiKey, boolean allowUnauthenticated) {
        this.clientTokens = clientTokens == null ? List.of() : clientTokens.stream()
                .filter(token -> token != null && !token.isBlank())
                .map(token -> token.strip().getBytes(StandardCharsets.UTF_8))
                .toList();
        this.providerApiKey = providerApiKey == null || providerApiKey.isBlank() ? null : providerApiKey.strip();
        this.allowUnauthenticated = allowUnauthenticated;
    }

    /** Upstream authorization: empty when the request is rejected. */
    public Optional<Upstream> resolve(String clientAuthorization) {
        boolean hasClientHeader = clientAuthorization != null && !clientAuthorization.isBlank();

        if (!clientTokens.isEmpty()) {
            String token = hasClientHeader && clientAuthorization.startsWith(BEARER)
                    ? clientAuthorization.substring(BEARER.length()).strip() : null;
            return token != null && isClientToken(token) ? Optional.of(new Upstream(providerHeader())) : Optional.empty();
        }
        if (hasClientHeader) {
            return Optional.of(new Upstream(clientAuthorization));
        }
        if (providerApiKey == null || allowUnauthenticated) {
            return Optional.of(new Upstream(providerHeader()));
        }
        return Optional.empty();
    }

    public boolean exposesProviderKeyWithoutAuthentication() {
        return clientTokens.isEmpty() && providerApiKey != null && allowUnauthenticated;
    }

    private String providerHeader() {
        return providerApiKey != null ? BEARER + providerApiKey : null;
    }

    private boolean isClientToken(String token) {
        byte[] candidate = token.getBytes(StandardCharsets.UTF_8);
        boolean match = false;
        for (byte[] expected : clientTokens) {
            // Constant-time comparison; keep checking every token
            match |= MessageDigest.isEqual(expected, candidate);
        }
        return match;
    }

    /** @param authorization header to send upstream, or {@code null} for none */
    public record Upstream(String authorization) {
    }
}
