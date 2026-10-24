package com.tekmachain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account login end-to-end through the real security filter chain: registration
 * opens a session, protected endpoints reject anonymous callers, and each user only
 * sees the wallets they own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuthTest {

    @Autowired MockMvc mvc;

    private static final String ALICE = "{\"username\":\"alice\",\"password\":\"secret123\"}";

    @Test
    void anonymousCannotUseProtectedEndpoints() throws Exception {
        mvc.perform(get("/api/wallets")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/wallets").contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerOpensASessionAndScopesWalletsToTheUser() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/register").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"));

        // with the session, a protected write succeeds
        mvc.perform(post("/api/wallets").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Main\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Main"));

        // the list is scoped to alice's own wallets
        mvc.perform(get("/api/wallets").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Main"))
                .andExpect(jsonPath("$.length()").value(1));

        // a second user sees none of alice's wallets
        MockHttpSession bobSession = new MockHttpSession();
        mvc.perform(post("/api/auth/register").session(bobSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/wallets").session(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(ALICE))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void publicMarketDataIsBrowsableWithoutLogin() throws Exception {
        mvc.perform(get("/api/market")).andExpect(status().isOk());
    }
}
