package com.genzenglish.partner.placement;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.genzenglish.partner.ai.llm.LlmClient;
import com.genzenglish.partner.ai.llm.LlmMessage;
import com.genzenglish.partner.ai.llm.LlmRequest;
import com.genzenglish.partner.ai.llm.LlmResponse;
import com.genzenglish.partner.ai.llm.LlmTask;
import com.genzenglish.partner.common.Json;

/**
 * Asks the model for a CEFR verdict; falls back to {@link HeuristicLevelEstimator} if the call fails or the JSON
 * can't be trusted, so onboarding never dead-ends.
 */
@Component
class PlacementAssessor {

    private static final Logger log = LoggerFactory.getLogger(PlacementAssessor.class);

    static final String SYSTEM_PROMPT = """
            You are an experienced CEFR speaking examiner for Indian learners of English.
            You will receive transcripts of a learner's spoken answers to four questions that get harder, with \
            their speaking pace (words per minute) and filler-word counts. Transcripts come from speech \
            recognition, so ignore punctuation and capitalisation. Judge communication, range and accuracy, not \
            accent. Don't penalise Indian English that is widely understood.
            Very short or missing answers suggest a lower level.

            Reply with only one JSON object, no other text:
            {"level":"A1|A2|B1|B2|C1","fluency":0-100,"grammar":0-100,"vocabulary":0-100,\
            "strengths":["up to 3 short strings"],"focusAreas":["exactly 3 short strings"],"summary":"..."}
            Write strengths, focusAreas and summary in plain, friendly English, speaking to the learner as "you". \
            Make focus areas specific and doable, and quote the learner's own words where it helps. The summary is \
            one or two encouraging sentences.
            """;

    private final LlmClient llm;
    private final Json json;

    PlacementAssessor(LlmClient llm, Json json) {
        this.llm = llm;
        this.json = json;
    }

    /** The user message sent to the model; also parsed by the mock responder. */
    record Input(List<Answer> answers) {
        record Answer(String question, String transcript, Long durationMs, Integer wordsPerMinute, int fillerCount) {
        }
    }

    PlacementAssessment assess(Input input) {
        try {
            LlmResponse response = llm.complete(LlmRequest.json(LlmTask.PLACEMENT_ASSESSMENT, SYSTEM_PROMPT,
                    List.of(LlmMessage.user(json.write(input))), 700));
            PlacementAssessment assessment = PlacementAssessment.fromRaw(
                    json.readLenient(response.text(), PlacementAssessment.Raw.class));
            if (assessment != null) {
                return assessment;
            }
            log.warn("Placement assessment JSON was unusable; using heuristic estimate");
        } catch (RuntimeException e) {
            log.warn("Placement assessment failed ({}); using heuristic estimate", llm.providerName(), e);
        }
        return HeuristicLevelEstimator.estimate(samples(input));
    }

    static List<HeuristicLevelEstimator.Sample> samples(Input input) {
        return input.answers().stream()
                .map(a -> new HeuristicLevelEstimator.Sample(a.question(), a.transcript(), a.durationMs()))
                .toList();
    }
}
