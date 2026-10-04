package com.genzenglish.partner.placement;

import java.io.IOException;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.genzenglish.partner.auth.CurrentUser;
import com.genzenglish.partner.common.ApiException;
import com.genzenglish.partner.common.AudioUploads;
import com.genzenglish.partner.placement.PlacementDtos.AnswerResponse;
import com.genzenglish.partner.placement.PlacementDtos.ResultResponse;
import com.genzenglish.partner.placement.PlacementDtos.StartResponse;
import com.genzenglish.partner.placement.PlacementDtos.TextAnswerRequest;

@RestController
@RequestMapping("/api/v1/placement")
public class PlacementController {

    private final PlacementService placement;

    public PlacementController(PlacementService placement) {
        this.placement = placement;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StartResponse start(@AuthenticationPrincipal Jwt jwt) {
        return placement.start(CurrentUser.id(jwt));
    }

    @PostMapping(path = "/{sessionId}/answers/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnswerResponse answerByVoice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
            @RequestParam int questionNumber, @RequestParam long durationMs,
            @RequestPart("audio") MultipartFile audio) throws IOException {
        checkQuestionNumber(questionNumber);
        return placement.answerByVoice(CurrentUser.id(jwt), sessionId, questionNumber,
                AudioUploads.read(audio, durationMs));
    }

    @PostMapping(path = "/{sessionId}/answers/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnswerResponse answerByText(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
            @Valid @RequestBody TextAnswerRequest request) {
        checkQuestionNumber(request.questionNumber());
        return placement.answerByText(CurrentUser.id(jwt), sessionId, request.questionNumber(), request.text());
    }

    @PostMapping("/{sessionId}/complete")
    public ResultResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return placement.complete(CurrentUser.id(jwt), sessionId);
    }

    private static void checkQuestionNumber(int questionNumber) {
        if (questionNumber < 1 || questionNumber > PlacementScript.TOTAL) {
            throw ApiException.badRequest("bad_question", "That question doesn't exist. Restart the placement chat.");
        }
    }
}
