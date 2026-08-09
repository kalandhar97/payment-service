package com.paymentprocessor.paymentservice.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Security wiring for the Payment Service.
 *
 * <p><b>Where this sits in the platform flow.</b> A user signs in at the
 * <i>authentication-service</i> (port 8081) either with a password or through a social
 * provider (Google/GitHub/Microsoft). That service mints an RS256-signed JWT and
 * publishes its public keys at {@code /.well-known/jwks.json}. Callers then present that
 * token as {@code Authorization: Bearer ...}. This class turns the Payment Service into
 * an OAuth2 <b>resource server</b>: it fetches the JWKS once, caches it, and verifies
 * each token's signature, issuer, expiry and {@code purpose} claim locally before any
 * controller runs. No per-request call back to the auth service is made.
 *
 * <p><b>Why this service in particular needs it.</b> Payment intents, authorizations,
 * captures and refunds move money. Before this class existed, every endpoint under
 * {@code /v1/payments} was reachable by anyone who could open a socket to the service —
 * the gateway validated tokens at the edge, but nothing stopped a direct call that
 * bypassed it. The resource server closes that gap in depth.
 *
 * <p><b>Why two filter chains.</b> Exactly one of the two {@link SecurityFilterChain}
 * beans below is active, selected by {@code security.jwt.enabled}. The enforcing chain is
 * the default; the permit-all chain exists so the {@code local} profile — which seeds
 * sample payment intents through {@code data.sql} — can be exercised with plain
 * {@code curl} before the authentication service is running.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Paths served without a token: operational probes and API documentation. These carry
     * no customer data and are needed by infrastructure (load balancers, Prometheus) that
     * holds no platform identity.
     */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    /**
     * Asynchronous callbacks from the UPI rail and the 3-D Secure directory server.
     *
     * <p><b>These must remain unauthenticated, and that is a deliberate, uncomfortable
     * trade-off.</b> The payment provider and the card scheme's ACS call these endpoints
     * to report the outcome of a payment the customer completed on <i>their</i> screen.
     * They are third parties with no account on this platform and cannot obtain or present
     * one of our JWTs. Requiring a bearer token here would mean payments never reach a
     * terminal state.
     *
     * <p>The correct protection is <i>provider signature verification</i>: the PSP signs
     * its callback body with a shared secret or its own key, and this service verifies
     * that signature before trusting a word of the payload. {@code CallbackController}
     * explicitly notes this is <b>not yet implemented</b> — meaning that today an attacker
     * who can reach these paths can assert an arbitrary payment outcome. This is the most
     * serious open security gap in the service. Until signature verification lands, these
     * two paths must be restricted to the providers' published IP ranges at the
     * gateway/ingress, and should never be exposed on a public interface.
     */
    private static final String[] PROVIDER_CALLBACK_PATHS = {
            "/v1/callbacks/upi",
            "/v1/callbacks/3ds"
    };

    /** Claim carrying the space-delimited OAuth2 scopes granted to the token. */
    private static final String SCOPE_CLAIM = "scope";

    /** Claim carrying the caller's principal type: USER, MERCHANT, ADMIN or SERVICE. */
    private static final String PRINCIPAL_TYPE_CLAIM = "principal_type";

    /** Claim distinguishing access tokens from refresh/step-up tokens. */
    private static final String PURPOSE_CLAIM = "purpose";

    /** The only {@code purpose} value this API accepts. */
    private static final String PURPOSE_ACCESS = "access";

    /** JWKS endpoint of the authentication service; keys are cached and rotated automatically. */
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /** Expected {@code iss} claim. Rejecting foreign issuers stops token-confusion attacks. */
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /**
     * The enforcing filter chain: every request except {@link #PUBLIC_PATHS} and
     * {@link #PROVIDER_CALLBACK_PATHS} needs a valid JWT.
     *
     * <p>Design notes, i.e. the <i>why</i>:
     * <ul>
     *   <li><b>CSRF disabled</b> — CSRF defends browser flows that authenticate with an
     *       ambient credential (a cookie). This API authenticates with a bearer token a
     *       browser never attaches on its own, so a CSRF token adds ceremony and no
     *       protection.</li>
     *   <li><b>STATELESS sessions</b> — the token <em>is</em> the session. Creating an
     *       {@code HttpSession} would pin a caller to one instance, break horizontal
     *       scaling, and silently keep a caller authenticated past token expiry.</li>
     *   <li><b>Callback paths first</b> — matcher order matters. The provider callback
     *       rules are declared before {@code anyRequest()} so they are not swallowed by
     *       the catch-all.</li>
     *   <li><b>Method security</b> — {@code @EnableMethodSecurity} lets controllers layer
     *       {@code @PreAuthorize("hasAuthority('SCOPE_payments:write')")} on top of this
     *       coarse gate. Money-moving operations (capture, refund, void) are the obvious
     *       candidates for that second, finer check.</li>
     * </ul>
     *
     * @param http                       the chain builder supplied by Spring Security
     * @param jwtAuthenticationConverter converts a verified token into authorities
     * @return the configured security filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        log.info("JWT resource-server security ENABLED (issuer={}, jwks={})", issuerUri, jwkSetUri);
        log.warn("Provider callback endpoints {} are exposed WITHOUT authentication; "
                + "callback signature verification is not yet implemented - restrict them "
                + "at the network edge.", String.join(", ", PROVIDER_CALLBACK_PATHS));
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Third-party callbacks: see PROVIDER_CALLBACK_PATHS javadoc.
                        .requestMatchers(PROVIDER_CALLBACK_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    /**
     * Development-only escape hatch: permits every request without a token.
     *
     * <p><b>Never enable this outside a developer laptop or an ephemeral CI container.</b>
     * It exists so the {@code local} profile — which seeds sample payment intents through
     * {@code data.sql} — can be exercised with plain {@code curl} before the
     * authentication service is running. The toggle lives in configuration rather than
     * code so the production artifact stays byte-identical to the one developers run.
     *
     * @param http the chain builder supplied by Spring Security
     * @return a permissive filter chain, active only when {@code security.jwt.enabled=false}
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "false")
    public SecurityFilterChain permitAllSecurityFilterChain(HttpSecurity http) throws Exception {
        log.warn("JWT validation is DISABLED (security.jwt.enabled=false). "
                + "All endpoints are open - local/dev use only.");
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Translates a cryptographically verified token into the authorities Spring Security
     * checks.
     *
     * <p>Two distinct mappings are produced, and the distinction is intentional:
     * <ul>
     *   <li><b>{@code scope} to {@code SCOPE_*}</b> — scopes describe <i>what the token is
     *       allowed to do</i>, and are delegated to {@link JwtGrantedAuthoritiesConverter},
     *       which already knows how to split the space-delimited string. The conventional
     *       {@code SCOPE_} prefix means {@code @PreAuthorize} rules read the same here as
     *       in every other service on the platform.</li>
     *   <li><b>{@code principal_type} to {@code ROLE_*}</b> — the principal type describes
     *       <i>who the caller is</i> (USER, MERCHANT, ADMIN, SERVICE). Mapping it into the
     *       {@code ROLE_} namespace lets rules use {@code hasRole('ADMIN')}. Keeping
     *       identity in {@code ROLE_} and delegated permission in {@code SCOPE_} prevents a
     *       broad scope from ever being mistaken for administrator identity — which for a
     *       money-moving service is exactly the confusion worth designing out.</li>
     * </ul>
     *
     * @return a converter producing {@code SCOPE_*} authorities plus a single {@code ROLE_*} one
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthoritiesClaimName(SCOPE_CLAIM);
        scopeConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // "sub" and "identity_id" carry the same value; "sub" is standard and always present.
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));
            String principalType = jwt.getClaimAsString(PRINCIPAL_TYPE_CLAIM);
            if (StringUtils.hasText(principalType)) {
                authorities.add(new SimpleGrantedAuthority(
                        "ROLE_" + principalType.trim().toUpperCase(Locale.ROOT)));
            }
            return authorities;
        });
        return converter;
    }

    /**
     * Builds the decoder that validates tokens against the authentication service's JWKS.
     *
     * <p>Declaring this bean explicitly (rather than relying on Boot's auto-configuration)
     * buys one thing the defaults do not give: enforcement of the {@code purpose} claim.
     * The authentication service issues several token types from the same key pair, and a
     * refresh or step-up (MFA) ticket must never be accepted as an API credential — least
     * of all by the service that captures funds — so anything other than
     * {@code purpose=access} is rejected here alongside the standard expiry/not-before and
     * issuer checks. The signature algorithm is pinned to RS256 so a token cannot downgrade
     * itself through its own header.
     *
     * @return a {@link NimbusJwtDecoder} that caches and refreshes JWKS keys automatically
     */
    @Bean
    @ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "true", matchIfMissing = true)
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> purposeIsAccess =
                new JwtClaimValidator<String>(PURPOSE_CLAIM, PURPOSE_ACCESS::equals);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri), purposeIsAccess));
        return decoder;
    }
}
