package com.genzenglish.partner.profile;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genzenglish.partner.auth.CurrentUser;
import com.genzenglish.partner.profile.ProfileDtos.LevelRequest;
import com.genzenglish.partner.profile.ProfileDtos.PreferencesRequest;
import com.genzenglish.partner.profile.ProfileDtos.ProfileResponse;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return ProfileResponse.of(profiles.get(CurrentUser.id(jwt)));
    }

    /** Onboarding step one: explanation language, goal and daily time. */
    @PutMapping("/preferences")
    public ProfileResponse setPreferences(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PreferencesRequest request) {
        return ProfileResponse.of(profiles.setPreferences(CurrentUser.id(jwt), request));
    }

    /** For learners who skip the spoken placement and pick a level themselves. */
    @PutMapping("/level")
    public ProfileResponse setLevel(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LevelRequest request) {
        return ProfileResponse.of(profiles.setLevel(CurrentUser.id(jwt), request.level(), LevelSource.SELF_SELECTED));
    }
}
