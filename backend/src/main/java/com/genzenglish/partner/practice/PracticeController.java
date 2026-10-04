package com.genzenglish.partner.practice;

import java.io.IOException;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.genzenglish.partner.auth.CurrentUser;
import com.genzenglish.partner.common.ApiException;
import com.genzenglish.partner.common.AudioUploads;
import com.genzenglish.partner.practice.PracticeDtos.AnswerResponse;
import com.genzenglish.partner.practice.PracticeDtos.CatalogResponse;
import com.genzenglish.partner.practice.PracticeDtos.HintResponse;
import com.genzenglish.partner.practice.PracticeDtos.StartRequest;
import com.genzenglish.partner.practice.PracticeDtos.StartResponse;
import com.genzenglish.partner.practice.PracticeDtos.SummaryResponse;
import com.genzenglish.partner.practice.PracticeDtos.TextAnswerRequest;

@RestController
@RequestMapping("/api/v1/practice")
public class PracticeController {

    private final PracticeService practice;

    public PracticeController(PracticeService practice) {
        this.practice = practice;
    }

    @GetMapping("/catalog")
    public CatalogResponse catalog() {
        return PracticeService.catalog();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StartResponse start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartRequest request) {
        return practice.start(CurrentUser.id(jwt), request);
    }

    @PostMapping(path = "/{sessionId}/turns/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnswerResponse answerByVoice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
            @RequestParam int turnNumber, @RequestParam long durationMs,
            @RequestPart("audio") MultipartFile audio) throws IOException {
        checkTurnNumber(turnNumber);
        return practice.answerByVoice(CurrentUser.id(jwt), sessionId, turnNumber,
                AudioUploads.read(audio, durationMs));
    }

    @PostMapping(path = "/{sessionId}/turns/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnswerResponse answerByText(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
            @Valid @RequestBody TextAnswerRequest request) {
        return practice.answerByText(CurrentUser.id(jwt), sessionId, request.turnNumber(), request.text());
    }

    /** Same as the voice answer, streamed as server-sent events (see {@link PracticeService#streamByVoice}). */
    @PostMapping(path = "/{sessionId}/turns/voice/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamByVoice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
            @RequestParam int turnNumber, @RequestParam long durationMs,
            @RequestPart("audio") MultipartFile audio) throws IOException {
        checkTurnNumber(turnNumber);
        return practice.streamByVoice(CurrentUser.id(jwt), sessionId, turnNumber,
                AudioUploads.read(audio, durationMs));
    }

    @PostMapping(path = "/{sessionId}/turns/text/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamByText(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
            @Valid @RequestBody TextAnswerRequest request) {
        return practice.streamByText(CurrentUser.id(jwt), sessionId, request.turnNumber(), request.text());
    }

    @PostMapping("/{sessionId}/hint")
    public HintResponse hint(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return practice.hint(CurrentUser.id(jwt), sessionId);
    }

    @PostMapping("/{sessionId}/complete")
    public SummaryResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return practice.complete(CurrentUser.id(jwt), sessionId);
    }

    private static void checkTurnNumber(int turnNumber) {
        if (turnNumber < 1 || turnNumber > PracticeDtos.MAX_TURNS) {
            throw ApiException.badRequest("bad_turn", "That answer doesn't fit this chat. Start it again.");
        }
    }
}
