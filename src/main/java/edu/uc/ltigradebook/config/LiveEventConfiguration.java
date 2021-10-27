package edu.uc.ltigradebook.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSetUri;

@Configuration
public class LiveEventConfiguration {

    @Value("${datastreams.jwt.issuer}")
    private String issuer;

    @Value("${datastreams.jwt.jwks.uri}")
    private String jwksUri;

    @Bean(name="liveEvents")
    public JwtDecoder jwtDecoder() {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new JwtIssuerValidator(issuer));
        DelegatingOAuth2TokenValidator<Jwt> jwtValidators = new DelegatingOAuth2TokenValidator<>(validators);
        NimbusJwtDecoder jwtDecoder = withJwkSetUri(jwksUri).build();
        jwtDecoder.setJwtValidator(jwtValidators);
        return jwtDecoder;
    }
}
