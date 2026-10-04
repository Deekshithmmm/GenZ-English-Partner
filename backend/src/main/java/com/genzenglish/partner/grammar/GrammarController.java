package com.genzenglish.partner.grammar;

import java.io.IOException;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.genzenglish.partner.auth.CurrentUser;
import com.genzenglish.partner.common.AudioUploads;
import com.genzenglish.partner.grammar.GrammarDtos.CompleteRequest;
import com.genzenglish.partner.grammar.GrammarDtos.LessonReport;
import com.genzenglish.partner.grammar.GrammarDtos.LessonResponse;
import com.genzenglish.partner.grammar.GrammarDtos.PathResponse;
import com.genzenglish.partner.grammar.GrammarDtos.SpeakResult;
import com.genzenglish.partner.grammar.GrammarDtos.SpeakTextRequest;

@RestController
@RequestMapping("/api/v1/grammar")
public class GrammarController {

    private final GrammarService grammar;

    public GrammarController(GrammarService grammar) {
        this.grammar = grammar;
    }

    @GetMapping
    public PathResponse path(@AuthenticationPrincipal Jwt jwt) {
        return grammar.path(CurrentUser.id(jwt));
    }

    @GetMapping("/lessons/{lessonId}")
    public LessonResponse lesson(@AuthenticationPrincipal Jwt jwt, @PathVariable String lessonId) {
        return grammar.lesson(CurrentUser.id(jwt), lessonId);
    }

    @PostMapping(path = "/lessons/{lessonId}/speaking/{index}/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SpeakResult speakByText(@AuthenticationPrincipal Jwt jwt, @PathVariable String lessonId,
            @PathVariable int index, @Valid @RequestBody SpeakTextRequest request) {
        return grammar.speakByText(CurrentUser.id(jwt), lessonId, index, request.text());
    }

    @PostMapping(path = "/lessons/{lessonId}/speaking/{index}/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SpeakResult speakByVoice(@AuthenticationPrincipal Jwt jwt, @PathVariable String lessonId,
            @PathVariable int index, @RequestParam long durationMs, @RequestPart("audio") MultipartFile audio)
            throws IOException {
        return grammar.speakByVoice(CurrentUser.id(jwt), lessonId, index, AudioUploads.read(audio, durationMs));
    }

    @PostMapping(path = "/lessons/{lessonId}/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LessonReport complete(@AuthenticationPrincipal Jwt jwt, @PathVariable String lessonId,
            @Valid @RequestBody CompleteRequest request) {
        return grammar.complete(CurrentUser.id(jwt), lessonId, request);
    }
}
