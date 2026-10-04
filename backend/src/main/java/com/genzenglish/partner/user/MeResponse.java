package com.genzenglish.partner.user;

import java.time.Instant;
import java.util.UUID;

import com.genzenglish.partner.profile.LearnerProfile;
import com.genzenglish.partner.profile.ProfileDtos.ProfileResponse;

/**
 * The signed-in learner plus the next onboarding step, so the app knows where to route after launch.
 */
public record MeResponse(
        UUID id,
        String email,
        String displayName,
        Instant createdAt,
        boolean hasPassword,
        boolean hasGoogle,
        ProfileResponse profile,
        OnboardingStep onboardingStep) {

    public enum OnboardingStep {
        PREFERENCES,
        PLACEMENT,
        DONE
    }

    public static MeResponse of(User user, LearnerProfile profile) {
        OnboardingStep step = !profile.hasPreferences() ? OnboardingStep.PREFERENCES
                : !profile.hasLevel() ? OnboardingStep.PLACEMENT
                : OnboardingStep.DONE;
        return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt(),
                user.getPasswordHash() != null, user.getGoogleSubject() != null, ProfileResponse.of(profile), step);
    }
}
