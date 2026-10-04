package com.genzenglish.partner.speech;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.genzenglish.partner.auth.CurrentUser;
import com.genzenglish.partner.common.ApiException;
import com.genzenglish.partner.profile.ProfileService;

/**
 * Luna's speech. Signed-in learners ask for a sentence in Luna's voice; the clip it makes is then fetched by its ID,
 * which is the capability, so audio players don't need auth headers.
 */
@RestController
@RequestMapping("/api/v1/speech")
public class SpeechController {

    private final SpeechClipStore clips;
    private final KaiVoice voice;
    private final ProfileService profiles;

    public SpeechController(SpeechClipStore clips, KaiVoice voice, ProfileService profiles) {
        this.clips = clips;
        this.voice = voice;
        this.profiles = profiles;
    }

    /**
     * @param text        one sentence or so, as the app will say it
     * @param languageTag BCP 47, e.g. en-IN
     * @param early       fetched ahead of time, so speech that's about to play goes first (optional, default false)
     */
    public record SpeakRequest(
            @NotBlank @Size(max = 300) String text,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*") String languageTag,
            Boolean early) {
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public SpeechDto speak(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SpeakRequest request) {
        return voice.voice(request.text().strip(), request.languageTag(), profiles.get(CurrentUser.id(jwt)),
                Boolean.TRUE.equals(request.early()));
    }

    /** @param languages the languages Luna's voice speaks, as primary subtags ("en", "kn"), or "*" for any */
    public record LanguagesResponse(List<String> languages) {
    }

    /** Which languages the server voices, so the app knows when to fall back to the phone's own voice. */
    @GetMapping("/languages")
    public LanguagesResponse languages() {
        return new LanguagesResponse(voice.languages());
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> clip(@PathVariable String id) {
        SpeechClipStore.Clip clip = clips.get(id)
                .orElseThrow(() -> ApiException.notFound("clip_expired", "That audio has expired. Tap replay again."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(clip.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(clip.audio());
    }
}
