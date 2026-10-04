package com.genzenglish.partner.path;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genzenglish.partner.auth.CurrentUser;
import com.genzenglish.partner.profile.LearnerProfile;
import com.genzenglish.partner.profile.ProfileService;

@RestController
@RequestMapping("/api/v1")
public class PathController {

    private final ProfileService profiles;
    private final LearningPathService paths;
    private final TodayService today;

    public PathController(ProfileService profiles, LearningPathService paths, TodayService today) {
        this.profiles = profiles;
        this.paths = paths;
        this.today = today;
    }

    @GetMapping("/path")
    public LearningPath path(@AuthenticationPrincipal Jwt jwt) {
        LearnerProfile profile = profiles.get(CurrentUser.id(jwt));
        return paths.pathFor(profile.getGoal(), profile.getLevel());
    }

    @GetMapping("/today")
    public TodayResponse today(@AuthenticationPrincipal Jwt jwt) {
        return today.today(CurrentUser.id(jwt));
    }
}
