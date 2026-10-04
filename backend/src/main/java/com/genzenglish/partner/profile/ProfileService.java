package com.genzenglish.partner.profile;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genzenglish.partner.common.ApiException;
import com.genzenglish.partner.config.AppProperties;
import com.genzenglish.partner.profile.ProfileDtos.PreferencesRequest;

@Service
public class ProfileService {

    static final Set<Integer> DAILY_MINUTE_OPTIONS = Set.of(5, 10, 20);

    private final LearnerProfileRepository profiles;
    private final AppProperties props;
    private final Clock clock;

    public ProfileService(LearnerProfileRepository profiles, AppProperties props, Clock clock) {
        this.profiles = profiles;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public LearnerProfile createFor(UUID userId) {
        return profiles.save(new LearnerProfile(userId, props.privacy().keepAudioByDefault()));
    }

    @Transactional(readOnly = true)
    public LearnerProfile get(UUID userId) {
        return profiles.findById(userId)
                .orElseThrow(() -> ApiException.notFound("profile_missing", "We couldn't find your profile. Sign in again."));
    }

    @Transactional
    public LearnerProfile setPreferences(UUID userId, PreferencesRequest request) {
        if (!DAILY_MINUTE_OPTIONS.contains(request.dailyMinutes())) {
            throw ApiException.badRequest("daily_minutes_invalid", "Pick 5, 10 or 20 minutes a day.");
        }
        LearnerProfile profile = get(userId);
        profile.setPreferences(request.explanationLanguage(), request.goal(), request.dailyMinutes(),
                validZone(request.timeZone()), clock.instant());
        return profile;
    }

    @Transactional
    public LearnerProfile setLevel(UUID userId, CefrLevel level, LevelSource source) {
        LearnerProfile profile = get(userId);
        profile.setLevel(level, source, clock.instant());
        return profile;
    }

    private static String validZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(zone).getId();
        } catch (DateTimeException e) {
            return null;
        }
    }
}
