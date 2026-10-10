package com.genzenglish.partner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Practice chats through the real HTTP API, with every AI provider mocked: warm-ups, roleplays, free talk and mock
 * interviews, answering as JSON and as a stream, feedback, hints, retries, the summary with scores and a scorecard, and
 * how finished practice shows up in today's plan, streak and XP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class PracticeFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void warmUpThenRoleplayFillTodaysPlanStreakAndXp() throws Exception {
        String token = onboardedLearner();

        mvc.perform(authed(get("/api/v1/today"), token))
                .andExpect(jsonPath("$.plan[0].done").value(false))
                .andExpect(jsonPath("$.streak.current").value(0))
                .andExpect(jsonPath("$.xp.total").value(0));

        JsonNode warmUp = body(mvc.perform(start(token, "WARM_UP", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("WARM_UP"))
                .andExpect(jsonPath("$.turnNumber").value(1))
                .andExpect(jsonPath("$.targetTurns").value(6))
                .andExpect(jsonPath("$.timeLimitSeconds").doesNotExist())
                .andExpect(jsonPath("$.brief").isNotEmpty())
                .andExpect(jsonPath("$.kai.text").value(org.hamcrest.Matchers.startsWith("Hey Meera, let's warm up.")))
                .andReturn());
        String warmUpId = warmUp.get("sessionId").asString();

        mvc.perform(textAnswer(warmUpId, 1, "Myself Meera. I am having two years experience in dance.", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback.corrections.length()").value(2))
                .andExpect(jsonPath("$.feedback.corrections[0].better").value("I'm Meera"))
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.nextTurnNumber").value(2))
                .andExpect(jsonPath("$.kai.text").isNotEmpty());
        mvc.perform(textAnswer(warmUpId, 2, "We went to the beach and had a lot of fun.", token))
                .andExpect(jsonPath("$.feedback.corrections.length()").value(0));

        // A retried answer replays what was stored instead of moving the chat on.
        mvc.perform(textAnswer(warmUpId, 2, "Something different this time", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").value("We went to the beach and had a lot of fun."));
        mvc.perform(textAnswer(warmUpId, 4, "Skipping ahead", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("out_of_order"));

        mvc.perform(textAnswer(warmUpId, 3, "My friends and I ate ice cream after that.", token))
                .andExpect(jsonPath("$.done").value(false));
        mvc.perform(textAnswer(warmUpId, 4, "Yes, I would definitely go again next month.", token))
                .andExpect(jsonPath("$.done").value(false));
        mvc.perform(textAnswer(warmUpId, 5, "My cousins want to come with us next time.", token))
                .andExpect(jsonPath("$.done").value(false));
        mvc.perform(textAnswer(warmUpId, 6, "We will take the early bus so we have the whole day.", token))
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.nextTurnNumber").doesNotExist());
        mvc.perform(textAnswer(warmUpId, 7, "One more thing", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session_done"));

        JsonNode summary = body(mvc.perform(authed(post("/api/v1/practice/" + warmUpId + "/complete"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.stats.answers").value(6))
                .andExpect(jsonPath("$.summary.xp").value(80))
                .andExpect(jsonPath("$.summary.corrections[0].original").value("Myself Meera"))
                .andExpect(jsonPath("$.summary.note").isNotEmpty())
                .andExpect(jsonPath("$.progress.streak").value(1))
                .andExpect(jsonPath("$.progress.xpToday").value(80))
                .andReturn());
        // Completing again is idempotent and doesn't credit XP twice.
        mvc.perform(authed(post("/api/v1/practice/" + warmUpId + "/complete"), token))
                .andExpect(jsonPath("$.summary.note").value(summary.get("summary").get("note").asString()))
                .andExpect(jsonPath("$.progress.xpTotal").value(80));
        mvc.perform(textAnswer(warmUpId, 1, "Too late", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("practice_missing"));

        String roleplayId = body(mvc.perform(start(token, "ROLEPLAY", "tell-me-about-yourself"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Tell me about yourself"))
                .andExpect(jsonPath("$.targetTurns").value(10))
                .andReturn()).get("sessionId").asString();
        mvc.perform(voiceAnswer(roleplayId, 1, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").isNotEmpty())
                .andExpect(jsonPath("$.wordsPerMinute").isNumber())
                .andExpect(jsonPath("$.feedback.corrections[0].better").value("in my final year"));
        // Finishing early is fine: no finish bonus, but the answer and speaking still count.
        mvc.perform(authed(post("/api/v1/practice/" + roleplayId + "/complete"), token))
                .andExpect(jsonPath("$.summary.xp").value(15))
                .andExpect(jsonPath("$.summary.stats.voiceAnswers").value(1));

        mvc.perform(authed(get("/api/v1/today"), token))
                .andExpect(jsonPath("$.plan[0].done").value(true))
                .andExpect(jsonPath("$.plan[2].action").value("roleplay:tell-me-about-yourself"))
                .andExpect(jsonPath("$.plan[2].done").value(true))
                .andExpect(jsonPath("$.streak.current").value(1))
                .andExpect(jsonPath("$.streak.longest").value(1))
                .andExpect(jsonPath("$.xp.today").value(95))
                .andExpect(jsonPath("$.xp.total").value(95));
    }

    @Test
    void startingAnotherChatEndsTheOpenOne() throws Exception {
        String token = onboardedLearner();
        String first = body(mvc.perform(start(token, "WARM_UP", null)).andReturn()).get("sessionId").asString();
        String second = body(mvc.perform(start(token, "ROLEPLAY", "ordering-at-cafe")).andReturn())
                .get("sessionId").asString();

        mvc.perform(textAnswer(first, 1, "Hello there", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("practice_missing"));
        mvc.perform(authed(post("/api/v1/practice/" + second + "/complete"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("answer_more"));
        mvc.perform(start(token, "ROLEPLAY", "no-such-scene"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("roleplay_missing"));
    }

    @Test
    void learnersCanOnlyReachTheirOwnChats() throws Exception {
        String owner = onboardedLearner();
        String sessionId = body(mvc.perform(start(owner, "WARM_UP", null)).andReturn()).get("sessionId").asString();
        String stranger = onboardedLearner();

        mvc.perform(textAnswer(sessionId, 1, "Not my chat", stranger)).andExpect(status().isNotFound());
        mvc.perform(authed(post("/api/v1/practice/" + sessionId + "/complete"), stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void freeTalkStreamsKaisLineBeforeTheFeedbackAndOffersHints() throws Exception {
        String token = onboardedLearner();
        mvc.perform(authed(get("/api/v1/practice/catalog"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[0].slug").value("anything"))
                .andExpect(jsonPath("$.roleplays.length()").value(12))
                .andExpect(jsonPath("$.roleplays[0].difficulty").value("EASY"));

        mvc.perform(start(token, "FREE_TALK", "no-such-topic"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("topic_missing"));
        mvc.perform(start(token, "FREE_TALK", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brief").value("Anything you like"));
        String sessionId = body(mvc.perform(start(token, "FREE_TALK", "music"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Talk with Luna"))
                .andExpect(jsonPath("$.brief").value("Music"))
                // Free talk runs on time; the answer count is only an upper bound.
                .andExpect(jsonPath("$.timeLimitSeconds").value(600))
                .andExpect(jsonPath("$.targetTurns").value(40))
                .andExpect(jsonPath("$.kai.text").value("Hey Meera! What have you been listening to on repeat lately?"))
                .andReturn()).get("sessionId").asString();

        mvc.perform(authed(post("/api/v1/practice/" + sessionId + "/hint"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ideas.length()").value(2));

        List<Map.Entry<String, JsonNode>> events = stream(textStream(sessionId, 1,
                "Yesterday night I listened to one of my friend playlists.", token));
        List<String> names = events.stream().map(Map.Entry::getKey).toList();
        assertThat(names.getFirst()).isEqualTo("transcript");
        assertThat(names).contains("reply");
        assertThat(names.indexOf("kai")).isLessThan(names.indexOf("feedback"));
        assertThat(names.getLast()).isEqualTo("done");
        assertThat(event(events, "kai").get("text").asString())
                .startsWith(event(events, "reply").get("text").asString());
        JsonNode done = events.getLast().getValue();
        assertThat(done.get("nextTurnNumber").asInt()).isEqualTo(2);
        assertThat(done.get("feedback").get("corrections").size()).isEqualTo(2);

        // A retried stream gets the stored answer straight back.
        List<Map.Entry<String, JsonNode>> replay = stream(textStream(sessionId, 1, "Different words", token));
        assertThat(replay).extracting(Map.Entry::getKey).containsExactly("transcript", "kai", "feedback", "done");
        assertThat(replay.getLast().getValue().get("transcript").asString()).startsWith("Yesterday night");

        List<Map.Entry<String, JsonNode>> spoken = stream(voiceStream(sessionId, 2, token));
        assertThat(spoken.getFirst().getValue().get("wordsPerMinute").isNumber()).isTrue();
        assertThat(spoken.getLast().getKey()).isEqualTo("done");

        // Problems found before streaming starts are ordinary error responses.
        mvc.perform(textStream(sessionId, 5, "Skipping ahead", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("out_of_order"));

        mvc.perform(authed(post("/api/v1/practice/" + sessionId + "/complete"), token))
                .andExpect(jsonPath("$.summary.kind").value("FREE_TALK"))
                .andExpect(jsonPath("$.summary.xp").value(25));
        mvc.perform(authed(post("/api/v1/practice/" + sessionId + "/hint"), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void roleplayFeedbackExplainsInTheLearnersLanguageAndTheSummaryScoresTheGoal() throws Exception {
        String token = onboardedLearner("HINDI");
        String sessionId = body(mvc.perform(start(token, "ROLEPLAY", "ordering-at-cafe"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kaiRole").value("a cheerful barista at a busy café near a college"))
                .andExpect(jsonPath("$.criteria.length()").value(3))
                .andExpect(jsonPath("$.criteria[1]").value("Ask a question about the menu"))
                .andReturn()).get("sessionId").asString();

        mvc.perform(textAnswer(sessionId, 1, "Yesterday night I ordered here and it was very tasty. Can I get one "
                + "cold coffee and one veg sandwich?", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback.corrections[0].original").value("Yesterday night"))
                .andExpect(jsonPath("$.feedback.corrections[0].better").value("Last night"))
                .andExpect(jsonPath("$.feedback.corrections[0].whyNative").value(
                        org.hamcrest.Matchers.startsWith("\"Yesterday night\" की जगह \"Last night\" बोलिए।")))
                .andExpect(jsonPath("$.feedback.betterPhrasing").value("Last night I ordered here and it was "
                        + "delicious. Can I get one cold coffee and one veg sandwich?"))
                .andExpect(jsonPath("$.feedback.newWords[0].word").value("delicious"))
                .andExpect(jsonPath("$.feedback.newWords[0].instead").value("very tasty"));

        mvc.perform(voiceAnswer(sessionId, 2, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback.mispronounced").isArray())
                .andExpect(jsonPath("$.feedback.fillerWords").isArray());
        mvc.perform(textAnswer(sessionId, 3, "Do you have any offers on cold coffee? Okay, I will pay by UPI.", token))
                .andExpect(status().isOk());

        // Three answers, one spoken, every goal reached; finishing early means no finish bonus.
        mvc.perform(authed(post("/api/v1/practice/" + sessionId + "/complete"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.scorecard.verdict").value("REACHED"))
                .andExpect(jsonPath("$.summary.scorecard.met").value(3))
                .andExpect(jsonPath("$.summary.scorecard.criteria[2].evidence").value("Okay, I will pay by UPI."))
                .andExpect(jsonPath("$.summary.xp").value(50))
                .andExpect(jsonPath("$.summary.xpItems[2].label").value("Reached the goal"))
                .andExpect(jsonPath("$.summary.scores.fluency").isNumber())
                .andExpect(jsonPath("$.summary.scores.grammar").isNumber())
                .andExpect(jsonPath("$.summary.scores.pronunciation").isNumber())
                .andExpect(jsonPath("$.summary.scores.vocabulary").isNumber())
                .andExpect(jsonPath("$.summary.focus.length()").value(3))
                .andExpect(jsonPath("$.summary.corrections[0].whyNative").isNotEmpty());

        Map<String, Object> stored = jdbc.queryForMap("select fluency_score, pronunciation_score from practice_session "
                + "where id = ?", UUID.fromString(sessionId));
        assertThat(stored.get("fluency_score")).isNotNull();
        assertThat(stored.get("pronunciation_score")).isNotNull();
    }

    @Test
    void mockInterviewIsTailoredToWhatWasPastedAndEndsWithAScorecard() throws Exception {
        String token = onboardedLearner();
        mvc.perform(interview(token, "Java developer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("interview_context"));

        String jobDescription = """
                About The Role
                We are hiring a Java Developer to join our payments team at Zentro Labs.
                Responsibilities: build REST APIs with Spring Boot, write SQL, review code.
                Requirements: Java, Spring Boot, SQL. Internship experience is a plus.
                """;
        String sessionId = body(mvc.perform(interview(token, jobDescription))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("INTERVIEW"))
                .andExpect(jsonPath("$.title").value("Mock interview"))
                .andExpect(jsonPath("$.brief").value("Java developer at Zentro Labs"))
                .andExpect(jsonPath("$.kaiRole").value("a friendly HR interviewer"))
                .andExpect(jsonPath("$.targetTurns").value(6))
                .andExpect(jsonPath("$.criteria.length()").value(4))
                .andExpect(jsonPath("$.kai.text").value("Hello Meera, thanks for coming in today for the Java "
                        + "developer role at Zentro Labs. Let's start with you. Tell me a little about yourself."))
                .andReturn()).get("sessionId").asString();

        // Only the plan is kept, never the pasted text.
        String plan = jdbc.queryForObject("select plan_json::text from practice_session where id = ?", String.class,
                UUID.fromString(sessionId));
        assertThat(plan).contains("Zentro Labs").doesNotContain("Responsibilities", "payments team");

        List<String> answers = List.of(
                "I'm Meera, a final-year computer science student from Pune, and I love building backend systems.",
                "I'm excited about payments, and I used Java and Spring Boot in my internship last summer.",
                "In my project I built REST APIs for a library app, for example a search that 300 students used.",
                "I wrote SQL reports during my internship, and I learnt to make slow queries faster.",
                "Once two teammates disagreed about the design, so I set up a short call and we agreed on a plan.",
                "In three years I want to grow into a senior developer. Can I ask what the team is working on?");
        for (int turn = 1; turn <= answers.size(); turn++) {
            JsonNode answer = body(mvc.perform(textAnswer(sessionId, turn, answers.get(turn - 1), token))
                    .andExpect(status().isOk())
                    .andReturn());
            String kai = answer.get("kai").get("text").asString();
            if (turn == 1) {
                assertThat(kai).endsWith("What made you apply for the Java developer role at Zentro Labs?");
            } else if (turn == answers.size()) {
                assertThat(answer.get("done").asBoolean()).isTrue();
                assertThat(kai).startsWith("Thank you, that's all from my side.");
            }
        }

        mvc.perform(authed(post("/api/v1/practice/" + sessionId + "/complete"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.kind").value("INTERVIEW"))
                .andExpect(jsonPath("$.summary.title").value("Mock interview"))
                .andExpect(jsonPath("$.summary.scores.pronunciation").doesNotExist())
                .andExpect(jsonPath("$.summary.scorecard.total").value(4))
                .andExpect(jsonPath("$.summary.scorecard.verdict").value("REACHED"))
                .andExpect(jsonPath("$.summary.scorecard.criteria[0].evidence").value(answers.getFirst()))
                .andExpect(jsonPath("$.summary.focus.length()").value(3))
                .andExpect(jsonPath("$.summary.xp").value(95));
    }

    private MockHttpServletRequestBuilder interview(String token, String context) {
        return authed(post("/api/v1/practice"), token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("kind", "INTERVIEW", "context", context)));
    }

    private String onboardedLearner() throws Exception {
        return onboardedLearner("ENGLISH");
    }

    private String onboardedLearner(String explanationLanguage) throws Exception {
        String email = "learner-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String token = body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"speakup123\",\"displayName\":\"Meera Iyer\"}"
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asString();
        mvc.perform(authed(put("/api/v1/profile/preferences"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"explanationLanguage":"%s","goal":"JOB_INTERVIEWS","dailyMinutes":10,
                                 "timeZone":"Asia/Kolkata"}
                                """.formatted(explanationLanguage)))
                .andExpect(status().isOk());
        mvc.perform(authed(put("/api/v1/profile/level"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"B1\"}"))
                .andExpect(status().isOk());
        return token;
    }

    private MockHttpServletRequestBuilder start(String token, String kind, String scenario) {
        Map<String, String> request = scenario == null ? Map.of("kind", kind) : Map.of("kind", kind, "scenario", scenario);
        return authed(post("/api/v1/practice"), token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request));
    }

    private MockHttpServletRequestBuilder textAnswer(String sessionId, int turn, String text, String token) {
        return authed(post("/api/v1/practice/" + sessionId + "/turns/text"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("turnNumber", turn, "text", text)));
    }

    private RequestBuilder voiceAnswer(String sessionId, int turn, String token) {
        MockMultipartFile audio = new MockMultipartFile("audio", "answer.m4a", "audio/mp4", new byte[4096]);
        return authed(multipart("/api/v1/practice/" + sessionId + "/turns/voice").file(audio)
                .param("turnNumber", String.valueOf(turn))
                .param("durationMs", "15000"), token);
    }

    private MockHttpServletRequestBuilder textStream(String sessionId, int turn, String text, String token) {
        return authed(post("/api/v1/practice/" + sessionId + "/turns/text/stream"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content(mapper.writeValueAsString(Map.of("turnNumber", turn, "text", text)));
    }

    private RequestBuilder voiceStream(String sessionId, int turn, String token) {
        MockMultipartFile audio = new MockMultipartFile("audio", "answer.m4a", "audio/mp4", new byte[4096]);
        return authed(multipart("/api/v1/practice/" + sessionId + "/turns/voice/stream").file(audio)
                .param("turnNumber", String.valueOf(turn))
                .param("durationMs", "12000")
                .accept(MediaType.TEXT_EVENT_STREAM), token);
    }

    /** Runs a streamed answer to the end and returns its server-sent events in order. */
    private List<Map.Entry<String, JsonNode>> stream(RequestBuilder call) throws Exception {
        MvcResult started = mvc.perform(call).andExpect(request().asyncStarted()).andReturn();
        started.getAsyncResult(10_000);
        String body = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map.Entry<String, JsonNode>> events = new ArrayList<>();
        for (String block : body.split("\n\n")) {
            String name = null;
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) {
                    name = line.substring("event:".length()).strip();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring("data:".length()));
                }
            }
            if (name != null) {
                events.add(Map.entry(name, mapper.readTree(data.toString())));
            }
        }
        return events;
    }

    private static JsonNode event(List<Map.Entry<String, JsonNode>> events, String name) {
        return events.stream().filter(e -> e.getKey().equals(name)).findFirst().orElseThrow().getValue();
    }

    private static <T extends AbstractMockHttpServletRequestBuilder<T>> T authed(T request, String token) {
        request.header("Authorization", "Bearer " + token);
        return request;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
