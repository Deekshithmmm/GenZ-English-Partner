package com.genzenglish.partner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The grammar path through the real HTTP API: lessons open one by one from the learner's level, spoken answers are
 * checked as they're given, the whole lesson is marked on the server with a report, and a finished lesson counts
 * towards today's plan and XP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class GrammarFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper mapper;

    @Test
    void lessonsOpenOneByOneFromTheLearnersLevel() throws Exception {
        String token = learnerAt("B1", "KANNADA");

        mvc.perform(authed(get("/api/v1/grammar"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(178))
                .andExpect(jsonPath("$.done").value(0))
                .andExpect(jsonPath("$.currentNodeId").value("learn.past-simple"))
                .andExpect(jsonPath("$.weeks.length()").value(46))
                .andExpect(jsonPath("$.weeks[0].title").value("First steps"))
                .andExpect(jsonPath("$.weeks[0].level").value("A1"))
                .andExpect(jsonPath("$.weeks[0].nodes[0].status").value("OPEN"))
                .andExpect(jsonPath("$.weeks[18].number").value(19))
                .andExpect(jsonPath("$.weeks[18].title").value("Telling stories"))
                .andExpect(jsonPath("$.weeks[18].nodes[0].id").value("learn.past-simple"))
                .andExpect(jsonPath("$.weeks[18].nodes[0].status").value("CURRENT"))
                .andExpect(jsonPath("$.weeks[18].nodes[0].number").value(66))
                .andExpect(jsonPath("$.weeks[18].nodes[0].title").value("Learn: What happened: the past simple"))
                .andExpect(jsonPath("$.weeks[18].nodes[0].topicTitle").value("What happened: the past simple"))
                .andExpect(jsonPath("$.weeks[18].nodes[1].kind").value("BOOST"))
                .andExpect(jsonPath("$.weeks[18].nodes[1].status").value("LOCKED"))
                .andExpect(jsonPath("$.weeks[18].nodes[2].kind").value("REVIEW"))
                .andExpect(jsonPath("$.weeks[18].nodes[2].id").value("review.comparatives"))
                .andExpect(jsonPath("$.weeks[18].nodes[3].kind").value("MASTER"))
                .andExpect(jsonPath("$.weeks[18].nodes[3].id").value("master.some-any"));

        // An earlier topic's revision is open to review, with Luna's lines in the learner's language.
        mvc.perform(authed(get("/api/v1/grammar/lessons/review.be-verb"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("REVIEW"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.intro[0].nativeText").value(org.hamcrest.Matchers.startsWith("ಪುನರಾವರ್ತನೆಯ")))
                .andExpect(jsonPath("$.exercises.length()").value(5))
                .andExpect(jsonPath("$.speaking.length()").value(3));

        mvc.perform(authed(get("/api/v1/grammar/lessons/boost.past-simple"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("lesson_locked"));
        mvc.perform(authed(get("/api/v1/grammar/lessons/no-such-lesson"), token))
                .andExpect(status().isNotFound());
        mvc.perform(authed(get("/api/v1/grammar/lessons/learn.introduce-yourself"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(authed(get("/api/v1/grammar/lessons/learn.past-simple"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("LEARN"))
                .andExpect(jsonPath("$.week").value(19))
                .andExpect(jsonPath("$.intro.length()").value(4))
                .andExpect(jsonPath("$.intro[0].nativeText").value(org.hamcrest.Matchers.startsWith("ಇಂದು ನಾವು")))
                .andExpect(jsonPath("$.chart.length()").value(3))
                .andExpect(jsonPath("$.exercises.length()").value(6))
                .andExpect(jsonPath("$.exercises[4].type").value("BUILD"))
                .andExpect(jsonPath("$.speaking.length()").value(3))
                .andExpect(jsonPath("$.phrases.right.text").value("Perfect! You got it right."));

        mvc.perform(authed(get("/api/v1/today"), token))
                .andExpect(jsonPath("$.plan[1].action").value("lesson:learn.past-simple"))
                .andExpect(jsonPath("$.plan[1].done").value(false));
    }

    @Test
    void spokenAnswersAreCheckedAsTheyreGiven() throws Exception {
        String token = learnerAt("B1", "ENGLISH");
        JsonNode lesson = lesson(token, "learn.past-simple");
        JsonNode fix = lesson.get("speaking").get(0);
        assertThat(fix.get("type").asString()).isEqualTo("FIX");

        mvc.perform(speak(token, "learn.past-simple", 0, fix.get("sample").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.expected").value(fix.get("sample").asString()));
        mvc.perform(speak(token, "learn.past-simple", 0, fix.get("prompt").asString()))
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.feedback.corrections").isArray());
        mvc.perform(speak(token, "learn.past-simple", 2, "I ate dosa and chutney last night."))
                .andExpect(jsonPath("$.correct").value(true));
        mvc.perform(speak(token, "learn.past-simple", 9, "Anything"))
                .andExpect(status().isNotFound());
        mvc.perform(speak(token, "boost.past-simple", 0, "Anything"))
                .andExpect(status().isConflict());

        MockMultipartFile audio = new MockMultipartFile("audio", "answer.m4a", "audio/mp4", new byte[4096]);
        mvc.perform(authed(multipart("/api/v1/grammar/lessons/learn.past-simple/speaking/2/voice").file(audio)
                        .param("durationMs", "6000"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").isNotEmpty())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.feedback.mispronounced").isArray());
    }

    @Test
    void finishingALessonGivesAReportAndOpensTheNext() throws Exception {
        String token = learnerAt("B1", "ENGLISH");
        JsonNode lesson = lesson(token, "learn.past-simple");

        // Mostly wrong: a report with tips, but nothing is recorded and the lesson stays current.
        mvc.perform(complete(token, "learn.past-simple", wrongAnswers(lesson), List.of(), UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.xp").value(0))
                .andExpect(jsonPath("$.improvements.length()").value(3))
                .andExpect(jsonPath("$.nextNodeId").value("learn.past-simple"));

        UUID attempt = UUID.randomUUID();
        List<String> spoken = new ArrayList<>();
        lesson.get("speaking").forEach(item -> spoken.add(item.get("sample").asString()));
        mvc.perform(complete(token, "learn.past-simple", rightAnswers(lesson), spoken, attempt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.correct").value(9))
                .andExpect(jsonPath("$.total").value(9))
                .andExpect(jsonPath("$.stars").value(3))
                .andExpect(jsonPath("$.xp").value(35))
                .andExpect(jsonPath("$.firstTime").value(true))
                .andExpect(jsonPath("$.wordsUsed").value(org.hamcrest.Matchers.greaterThan(10)))
                .andExpect(jsonPath("$.seconds").value(240))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.startsWith("Excellent work")))
                .andExpect(jsonPath("$.improvements.length()").value(0))
                .andExpect(jsonPath("$.nextNodeId").value("boost.past-simple"))
                .andExpect(jsonPath("$.progress.xpToday").value(35))
                .andExpect(jsonPath("$.progress.streak").value(1));
        // A retry of the same attempt is answered from what was stored, without counting twice.
        mvc.perform(complete(token, "learn.past-simple", rightAnswers(lesson), spoken, attempt))
                .andExpect(jsonPath("$.xp").value(35))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.progress.xpToday").value(35));

        mvc.perform(authed(get("/api/v1/grammar"), token))
                .andExpect(jsonPath("$.done").value(1))
                .andExpect(jsonPath("$.currentNodeId").value("boost.past-simple"))
                .andExpect(jsonPath("$.weeks[18].nodes[0].status").value("DONE"))
                .andExpect(jsonPath("$.weeks[18].nodes[0].stars").value(3))
                .andExpect(jsonPath("$.weeks[18].nodes[1].status").value("CURRENT"));
        // Today's plan keeps the lesson finished today, ticked off, instead of moving on to the next one.
        mvc.perform(authed(get("/api/v1/today"), token))
                .andExpect(jsonPath("$.plan[1].action").value("lesson:learn.past-simple"))
                .andExpect(jsonPath("$.plan[1].title").value("Learn: What happened: the past simple"))
                .andExpect(jsonPath("$.plan[1].done").value(true))
                .andExpect(jsonPath("$.xp.today").value(35));

        // A fluency booster is all speaking; skipping half of it still passes with fewer stars.
        JsonNode boost = lesson(token, "boost.past-simple");
        List<String> half = new ArrayList<>();
        boost.get("speaking").forEach(item -> half.add(half.size() % 2 == 0 ? item.get("sample").asString() : null));
        mvc.perform(complete(token, "boost.past-simple", List.of(), half, UUID.randomUUID()))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.score").value(56))
                .andExpect(jsonPath("$.stars").value(1))
                .andExpect(jsonPath("$.xp").value(25));
        // With two lessons done today, the plan shows the latest.
        mvc.perform(authed(get("/api/v1/today"), token))
                .andExpect(jsonPath("$.plan[1].action").value("lesson:boost.past-simple"))
                .andExpect(jsonPath("$.plan[1].done").value(true));
    }

    private List<Map<String, Object>> rightAnswers(JsonNode lesson) {
        List<Map<String, Object>> answers = new ArrayList<>();
        for (JsonNode exercise : lesson.get("exercises")) {
            if (exercise.get("type").asString().equals("CHOICE")) {
                answers.add(Map.of("choice", exercise.get("answer").asInt()));
            } else {
                List<String> words = new ArrayList<>();
                exercise.get("words").forEach(w -> words.add(w.asString()));
                answers.add(Map.of("words", words));
            }
        }
        return answers;
    }

    private List<Map<String, Object>> wrongAnswers(JsonNode lesson) {
        List<Map<String, Object>> answers = new ArrayList<>();
        for (JsonNode exercise : lesson.get("exercises")) {
            if (exercise.get("type").asString().equals("CHOICE")) {
                answers.add(Map.of("choice", exercise.get("answer").asInt() == 0 ? 1 : 0));
            } else {
                answers.add(Map.of("words", List.of("wrong", "order")));
            }
        }
        return answers;
    }

    private JsonNode lesson(String token, String id) throws Exception {
        return body(mvc.perform(authed(get("/api/v1/grammar/lessons/" + id), token)).andExpect(status().isOk())
                .andReturn());
    }

    private MockHttpServletRequestBuilder speak(String token, String lessonId, int index, String text) {
        return authed(post("/api/v1/grammar/lessons/" + lessonId + "/speaking/" + index + "/text"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("text", text)));
    }

    private MockHttpServletRequestBuilder complete(String token, String lessonId, List<Map<String, Object>> answers,
            List<String> spoken, UUID attemptId) {
        Map<String, Object> body = new HashMap<>();
        body.put("answers", answers);
        body.put("spoken", spoken);
        body.put("seconds", 240);
        body.put("attemptId", attemptId);
        return authed(post("/api/v1/grammar/lessons/" + lessonId + "/complete"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));
    }

    private String learnerAt(String level, String language) throws Exception {
        String email = "grammar-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String token = body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"speakup123\",\"displayName\":\"Kiran Rao\"}"
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asString();
        mvc.perform(authed(put("/api/v1/profile/preferences"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"explanationLanguage":"%s","goal":"JOB_INTERVIEWS","dailyMinutes":10,
                                 "timeZone":"Asia/Kolkata"}
                                """.formatted(language)))
                .andExpect(status().isOk());
        mvc.perform(authed(put("/api/v1/profile/level"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"%s\"}".formatted(level)))
                .andExpect(status().isOk());
        return token;
    }

    private static <T extends AbstractMockHttpServletRequestBuilder<T>> T authed(T request, String token) {
        request.header("Authorization", "Bearer " + token);
        return request;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
