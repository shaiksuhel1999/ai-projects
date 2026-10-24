package com.tekmachain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The profile aggregates a user's holdings and linked accounts, and fiat funding
 * requires a linked bank account.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ProfileTest {

    @Autowired MockMvc mvc;

    private MockHttpSession registerAlice() throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/api/auth/register").session(s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"secret123\",\"displayName\":\"Alice\"}"))
                .andExpect(status().isOk());
        return s;
    }

    @Test
    void profileShowsAccountAndOwnedWallets() throws Exception {
        MockHttpSession s = registerAlice();
        mvc.perform(post("/api/wallets").session(s).contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Main\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/profile").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.username").value("alice"))
                .andExpect(jsonPath("$.account.displayName").value("Alice"))
                .andExpect(jsonPath("$.wallets[0].label").value("Main"))
                .andExpect(jsonPath("$.totals.onChainTek").exists());
    }

    @Test
    void linkedBankAccountNumberIsMasked() throws Exception {
        MockHttpSession s = registerAlice();
        mvc.perform(post("/api/profile/accounts").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"BANK\",\"holderName\":\"Alice\",\"institution\":\"HDFC Bank\",\"number\":\"123456789012\",\"code\":\"HDFC0001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("BANK"))
                .andExpect(jsonPath("$.maskedNumber").value("•••• 9012"));

        mvc.perform(get("/api/profile/accounts").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void fiatDepositRequiresALinkedBankAccount() throws Exception {
        MockHttpSession s = registerAlice();

        // no bank linked yet -> rejected
        mvc.perform(post("/api/exchange/deposit/fiat").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"TEKalicedemo\",\"asset\":\"USD\",\"amount\":100}"))
                .andExpect(status().isUnprocessableEntity());

        // link a bank, then it works and the response names the account
        mvc.perform(post("/api/profile/accounts").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"BANK\",\"holderName\":\"Alice\",\"institution\":\"HDFC Bank\",\"number\":\"123456789012\",\"code\":\"HDFC0001\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/exchange/deposit/fiat").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"TEKalicedemo\",\"asset\":\"USD\",\"amount\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("9012")));
    }

    @Test
    void dematAccountCanBeLinkedAndRemoved() throws Exception {
        MockHttpSession s = registerAlice();
        String body = "{\"kind\":\"DEMAT\",\"holderName\":\"Alice\",\"institution\":\"NSDL\",\"number\":\"IN30012345678901\",\"code\":\"IN300123\"}";
        String json = mvc.perform(post("/api/profile/accounts").session(s).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("DEMAT"))
                .andReturn().getResponse().getContentAsString();
        long id = com.jayway.jsonpath.JsonPath.parse(json).read("$.id", Integer.class);

        mvc.perform(delete("/api/profile/accounts/" + id).session(s))
                .andExpect(status().isOk());
        mvc.perform(get("/api/profile/accounts").session(s))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
