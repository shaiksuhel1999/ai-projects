package com.tekmachain;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Horizontal access control: a signed-in user must not be able to operate on another
 * account's custodial wallet — not spend from it, and not read its exchange account.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuthorizationTest {

    @Autowired MockMvc mvc;

    private MockHttpSession register(String username) throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/api/auth/register").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
        return s;
    }

    @Test
    void oneUserCannotActOnAnothersWallet() throws Exception {
        MockHttpSession alice = register("alice");
        String json = mvc.perform(post("/api/wallets").session(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Main\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String aliceAddr = JsonPath.parse(json).read("$.address");

        MockHttpSession bob = register("bob");

        // Bob tries to spend from Alice's server-held wallet -> forbidden
        mvc.perform(post("/api/transactions").session(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"" + aliceAddr + "\",\"to\":\"TEKsomewhere\",\"amount\":1,\"fee\":0}"))
                .andExpect(status().isForbidden());

        // Bob tries to read Alice's exchange account -> forbidden
        mvc.perform(get("/api/exchange/account/" + aliceAddr).session(bob))
                .andExpect(status().isForbidden());

        // Alice can read her own exchange account
        mvc.perform(get("/api/exchange/account/" + aliceAddr).session(alice))
                .andExpect(status().isOk());
    }

    @Test
    void publicMarketDataStaysOpenButPersonalDataNeedsAuth() throws Exception {
        mvc.perform(get("/api/exchange/ticker?pair=TEK/USD")).andExpect(status().isOk());
        mvc.perform(get("/api/exchange/account/TEKanything")).andExpect(status().isUnauthorized());
    }
}
