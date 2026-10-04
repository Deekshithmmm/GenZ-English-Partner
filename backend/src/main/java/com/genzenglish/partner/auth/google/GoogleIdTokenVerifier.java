package com.genzenglish.partner.auth.google;

/**
 * Verifies the ID token the app receives from Google Sign-In. Chosen by {@code app.auth.google.provider}.
 */
public interface GoogleIdTokenVerifier {

    /**
     * @throws com.genzenglish.partner.common.ApiException with status 401 if the token isn't valid for this app.
     */
    GoogleIdentity verify(String idToken);
}
