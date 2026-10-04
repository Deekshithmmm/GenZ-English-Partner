package com.genzenglish.partner.user;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genzenglish.partner.common.ApiException;
import com.genzenglish.partner.profile.ProfileService;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository users;
    private final ProfileService profiles;

    public AccountService(UserRepository users, ProfileService profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Your session has ended. Sign in again."));
        return MeResponse.of(user, profiles.get(userId));
    }

    /**
     * Permanently removes the account and everything tied to it: profile, tokens, sessions, transcripts and scores.
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        int deleted = users.hardDelete(userId);
        if (deleted == 0) {
            throw ApiException.notFound("account_missing", "That account no longer exists.");
        }
        log.info("Deleted account {}", userId);
    }
}
