package com.genzenglish.partner.auth.google;

public record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {
}
