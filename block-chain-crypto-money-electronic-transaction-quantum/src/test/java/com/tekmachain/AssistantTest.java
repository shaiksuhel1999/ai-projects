package com.tekmachain;

import com.tekmachain.assistant.AssistantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/** The embedded RAG assistant retrieves the right guidance for common questions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AssistantTest {

    @Autowired AssistantService assistant;

    @Test
    void routesWalletQuestionToWalletGuidance() {
        var a = assistant.answer("which wallet should I use?");
        assertTrue(a.confident());
        assertEquals("wallet", a.action());
        assertTrue(a.answer().toLowerCase().contains("custodial"), "should explain wallet types");
    }

    @Test
    void routesTradingQuestionToExchange() {
        var a = assistant.answer("limit or market order, which is better?");
        assertEquals("exchange", a.action());
        assertTrue(a.answer().toLowerCase().contains("market") && a.answer().toLowerCase().contains("limit"));
        assertFalse(a.sources().isEmpty(), "cites the topics it used");
    }

    @Test
    void routesMiningAndQuantumQuestions() {
        assertTrue(assistant.answer("how do I get coins?").answer().toLowerCase().contains("min"));
        assertEquals("security", assistant.answer("is my wallet quantum safe?").action());
    }

    @Test
    void unknownQuestionFallsBackGracefully() {
        var a = assistant.answer("zxqw plkjh mnbv");
        assertFalse(a.confident(), "no strong match -> fallback");
        assertFalse(a.suggestions().isEmpty(), "still offers next steps");
    }
}
