package com.genzenglish.partner.placement;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.genzenglish.partner.ai.llm.LlmRequest;
import com.genzenglish.partner.ai.llm.LlmTask;
import com.genzenglish.partner.ai.llm.MockLlmResponder;
import com.genzenglish.partner.common.Json;

/** What the mock LLM says during placement. */
@Configuration
@ConditionalOnProperty(prefix = "app.ai.llm", name = "provider", havingValue = "mock", matchIfMissing = true)
class PlacementMockResponders {

    private static final List<String> CITIES = List.of("Mysuru", "Bengaluru", "Bangalore", "Chennai", "Hyderabad",
            "Mumbai", "Pune", "Kolkata", "Delhi", "Kochi", "Jaipur", "Lucknow", "Indore", "Coimbatore", "Mangaluru",
            "Nagpur", "Patna", "Bhopal", "Ahmedabad", "Chandigarh", "Guwahati", "Thiruvananthapuram", "Visakhapatnam");
    private static final List<String> REACTIONS = List.of(
            "Nice, thanks for sharing that.",
            "Got it, that sounds real.",
            "Okay, I like that answer.",
            "That makes sense.",
            "Interesting, thanks for telling me.");

    @Bean
    MockLlmResponder placementReactionMock() {
        return new MockLlmResponder() {
            @Override
            public LlmTask task() {
                return LlmTask.PLACEMENT_REACTION;
            }

            @Override
            public String respond(LlmRequest request) {
                String said = request.lastUserMessage();
                String lower = said.toLowerCase(Locale.ROOT);
                for (String city : CITIES) {
                    if (lower.contains(city.toLowerCase(Locale.ROOT))) {
                        return city + ", nice!";
                    }
                }
                return REACTIONS.get(Math.floorMod(said.hashCode(), REACTIONS.size()));
            }
        };
    }

    @Bean
    MockLlmResponder placementAssessmentMock(Json json) {
        return new MockLlmResponder() {
            @Override
            public LlmTask task() {
                return LlmTask.PLACEMENT_ASSESSMENT;
            }

            @Override
            public String respond(LlmRequest request) {
                PlacementAssessor.Input input = json.read(request.lastUserMessage(), PlacementAssessor.Input.class);
                PlacementAssessment estimate = HeuristicLevelEstimator.estimate(PlacementAssessor.samples(input));
                return json.write(new PlacementAssessment.Raw(estimate.level().name(), estimate.fluency(),
                        estimate.grammar(), estimate.vocabulary(), estimate.strengths(), estimate.focusAreas(),
                        estimate.summary()));
            }
        };
    }
}
