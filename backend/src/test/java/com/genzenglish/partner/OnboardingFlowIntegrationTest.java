package com.genzenglish.partner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drives the real HTTP API against Postgres with every AI provider mocked: sign-up, onboarding, the spoken
 * placement chat, today's plan, token rotation, Google sign-in and account deletion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class OnboardingFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper mapper;

    @Test
    void newLearnerGoesFromSignUpThroughPlacementToTodaysPlan() throws Exception {
        String email = uniqueEmail();
        JsonNode auth = body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"speakup123","displayName":"Priya"}
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.me.email").value(email))
                .andExpect(jsonPath("$.me.onboardingStep").value("PREFERENCES"))
                .andReturn());
        String token = auth.get("accessToken").asString();

        mvc.perform(authed(put("/api/v1/profile/preferences"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"explanationLanguage":"KANNADA","goal":"JOB_INTERVIEWS","dailyMinutes":10,
                                 "timeZone":"Asia/Kolkata"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanationLanguage").value("KANNADA"))
                .andExpect(jsonPath("$.dailyMinutes").value(10));

        mvc.perform(authed(get("/api/v1/me"), token))
                .andExpect(jsonPath("$.onboardingStep").value("PLACEMENT"));

        JsonNode start = body(mvc.perform(authed(post("/api/v1/placement"), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questionNumber").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(4))
                .andExpect(jsonPath("$.kai.speech.mode").value("DEVICE"))
                .andReturn());
        String sessionId = start.get("sessionId").asString();

        for (int question = 1; question <= 4; question++) {
            mvc.perform(voiceAnswer(sessionId, question, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionNumber").value(question))
                    .andExpect(jsonPath("$.transcript").isNotEmpty())
                    .andExpect(jsonPath("$.done").value(question == 4))
                    .andExpect(jsonPath("$.kai.text").isNotEmpty());
        }

        // A retried upload (e.g. the response was lost on 4G) replays the stored answer instead of shifting questions.
        JsonNode retry = body(mvc.perform(voiceAnswer(sessionId, 2, token)).andExpect(status().isOk()).andReturn());
        assertThat(retry.get("questionNumber").asInt()).isEqualTo(2);
        assertThat(retry.get("transcript").asString()).contains("wake up");

        JsonNode result = body(mvc.perform(authed(post("/api/v1/placement/" + sessionId + "/complete"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.focusAreas.length()").value(3))
                .andExpect(jsonPath("$.path.goal").value("JOB_INTERVIEWS"))
                .andExpect(jsonPath("$.path.units[0].status").value("CURRENT"))
                .andReturn());
        String level = result.get("level").get("code").asString();
        assertThat(level).isIn("A1", "A2", "B1", "B2", "C1");

        // Completing again is idempotent.
        mvc.perform(authed(post("/api/v1/placement/" + sessionId + "/complete"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level.code").value(level));

        mvc.perform(authed(get("/api/v1/me"), token))
                .andExpect(jsonPath("$.onboardingStep").value("DONE"))
                .andExpect(jsonPath("$.profile.level.code").value(level))
                .andExpect(jsonPath("$.profile.levelSource").value("PLACEMENT"));

        mvc.perform(authed(get("/api/v1/today"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.length()").value(4))
                .andExpect(jsonPath("$.plan[0].kind").value("WARM_UP"))
                .andExpect(jsonPath("$.plan[2].action").value(org.hamcrest.Matchers.startsWith("roleplay:")))
                .andExpect(jsonPath("$.dailyMinutes").value(10));
    }

    @Test
    void typedAnswersWorkAndPlacementNeedsAtLeastTwoAnswers() throws Exception {
        String token = register(uniqueEmail());
        String sessionId = body(mvc.perform(authed(post("/api/v1/placement"), token)).andReturn())
                .get("sessionId").asString();

        mvc.perform(textAnswer(sessionId, 1, "My name is Kiran and I am from Hyderabad.", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kai.text").value(org.hamcrest.Matchers.startsWith("Hyderabad, nice!")))
                .andExpect(jsonPath("$.wordsPerMinute").doesNotExist());

        mvc.perform(authed(post("/api/v1/placement/" + sessionId + "/complete"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("answer_more"));

        mvc.perform(textAnswer(sessionId, 3, "Skipping ahead", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("out_of_order"));

        mvc.perform(textAnswer(sessionId, 2, "I go to college and then I study in the evening.", token))
                .andExpect(status().isOk());
        mvc.perform(authed(post("/api/v1/placement/" + sessionId + "/complete"), token))
                .andExpect(status().isOk());
    }

    @Test
    void learnerCanSkipPlacementAndPickALevel() throws Exception {
        String token = register(uniqueEmail());
        mvc.perform(authed(put("/api/v1/profile/level"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"A2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level.code").value("A2"))
                .andExpect(jsonPath("$.levelSource").value("SELF_SELECTED"));
    }

    @Test
    void refreshTokensRotateAndReplayingAnOldOneEndsTheSession() throws Exception {
        String email = uniqueEmail();
        JsonNode first = body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email))).andReturn());
        String original = first.get("refreshToken").asString();

        JsonNode second = body(mvc.perform(refresh(original)).andExpect(status().isOk()).andReturn());
        String rotated = second.get("refreshToken").asString();
        assertThat(rotated).isNotEqualTo(original);

        mvc.perform(refresh(original))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("session_expired"));
        // Reuse revoked the whole family, including the newest token.
        mvc.perform(refresh(rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordsAndDuplicateEmailsGetClearErrors() throws Exception {
        String email = uniqueEmail();
        register(email);

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" %s \",\"password\":\"speakup123\"}".formatted(email.toUpperCase())))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.fields.password").value("Use at least 8 characters for your password."));
    }

    @Test
    void googleSignInCreatesAnAccountOnceAndLinksToAnExistingEmail() throws Exception {
        String email = uniqueEmail();
        JsonNode first = body(mvc.perform(google("mock-google:" + email + ":Asha Rao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.displayName").value("Asha Rao"))
                .andExpect(jsonPath("$.me.hasGoogle").value(true))
                .andExpect(jsonPath("$.me.hasPassword").value(false))
                .andReturn());
        JsonNode again = body(mvc.perform(google("mock-google:" + email)).andExpect(status().isOk()).andReturn());
        assertThat(again.get("me").get("id").asString()).isEqualTo(first.get("me").get("id").asString());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"whatever1\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("use_google"));

        String passwordEmail = uniqueEmail();
        register(passwordEmail);
        mvc.perform(google("mock-google:" + passwordEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.hasPassword").value(true))
                .andExpect(jsonPath("$.me.hasGoogle").value(true));

        mvc.perform(google("forged-token")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value("mock"));
    }

    @Test
    void makingSpeechNeedsSignInButFetchingAClipDoesNot() throws Exception {
        String sentence = "{\"text\":\"Use am with I.\",\"languageTag\":\"en-IN\",\"early\":true}";
        mvc.perform(post("/api/v1/speech").contentType(MediaType.APPLICATION_JSON).content(sentence))
                .andExpect(status().isUnauthorized());

        String token = register(uniqueEmail());
        // With the mock voice there's no clip: the app speaks it with the phone's voice.
        mvc.perform(authed(post("/api/v1/speech"), token).contentType(MediaType.APPLICATION_JSON).content(sentence))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DEVICE"))
                .andExpect(jsonPath("$.voice").value("KAI_WARM"))
                .andExpect(jsonPath("$.languageTag").value("en-IN"));
        mvc.perform(authed(post("/api/v1/speech"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"%s\",\"languageTag\":\"en IN\"}".formatted("x".repeat(301))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.text").exists())
                .andExpect(jsonPath("$.fields.languageTag").exists());

        mvc.perform(get("/api/v1/speech/no-such-clip"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("clip_expired"));
        // The mock voice speaks no language itself: the app uses the phone's voice for everything.
        mvc.perform(get("/api/v1/speech/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languages.length()").value(0));
    }

    @Test
    void deletingTheAccountRemovesItsDataAndSessions() throws Exception {
        String email = uniqueEmail();
        JsonNode auth = body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email))).andReturn());
        String token = auth.get("accessToken").asString();
        String sessionId = body(mvc.perform(authed(post("/api/v1/placement"), token)).andReturn())
                .get("sessionId").asString();
        mvc.perform(textAnswer(sessionId, 1, "I am Dev from Delhi.", token)).andExpect(status().isOk());

        mvc.perform(authed(delete("/api/v1/me"), token)).andExpect(status().isNoContent());

        mvc.perform(authed(get("/api/v1/me"), token)).andExpect(status().isUnauthorized());
        mvc.perform(refresh(auth.get("refreshToken").asString())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"speakup123\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
        // The email is free to register again.
        register(email);
    }

    private String register(String email) throws Exception {
        return body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asString();
    }

    private static String registerJson(String email) {
        return "{\"email\":\"%s\",\"password\":\"speakup123\",\"displayName\":\"Test Learner\"}".formatted(email);
    }

    private RequestBuilder voiceAnswer(String sessionId, int question, String token) {
        MockMultipartFile audio = new MockMultipartFile("audio", "answer.m4a", "audio/mp4", new byte[4096]);
        return authed(multipart("/api/v1/placement/" + sessionId + "/answers/voice").file(audio)
                .param("questionNumber", String.valueOf(question))
                .param("durationMs", "15000"), token);
    }

    private MockHttpServletRequestBuilder textAnswer(String sessionId, int question, String text, String token) {
        return authed(post("/api/v1/placement/" + sessionId + "/answers/text"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("questionNumber", question, "text", text)));
    }

    private MockHttpServletRequestBuilder refresh(String refreshToken) {
        return post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken));
    }

    private MockHttpServletRequestBuilder google(String idToken) {
        return post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"%s\"}".formatted(idToken));
    }

    private static <T extends AbstractMockHttpServletRequestBuilder<T>> T authed(T request, String token) {
        request.header("Authorization", "Bearer " + token);
        return request;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private static String uniqueEmail() {
        return "learner-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
