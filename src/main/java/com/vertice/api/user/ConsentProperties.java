package com.vertice.api.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code vertice.consent.terms-required-on-registration}: whether {@code CreateUser} refuses a
 * request without {@code accepted_terms_version} (P1-04). Off until vertice-bff sends the field, so
 * registration keeps working in between; the follow-up turns it on and then drops the switch.
 */
@ConfigurationProperties("vertice.consent")
public record ConsentProperties(boolean termsRequiredOnRegistration) {
}
