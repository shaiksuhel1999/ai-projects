package com.tekmachain.assistant;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a retrieved knowledge entry into a helpful assistant reply — the answer
 * text, an optional deep-link into the right tab, the topics it drew on, and a few
 * related follow-up questions. Grounded entirely in the local knowledge base.
 */
@Service
public class AssistantService {

    private static final double MIN_SCORE = 0.6;   // BM25 units — filters no-match queries

    private final KnowledgeBase kb;

    public AssistantService(KnowledgeBase kb) {
        this.kb = kb;
    }

    public record AnswerDto(String answer, String action, String actionLabel,
                            List<String> sources, List<String> suggestions, boolean confident) {
    }

    public AnswerDto answer(String question) {
        if (question == null || question.isBlank()) {
            return fallback();
        }
        List<KnowledgeBase.Scored> top = kb.search(question, 4);
        if (top.isEmpty() || top.get(0).score() < MIN_SCORE) {
            return fallback();
        }
        KnowledgeBase.Entry best = top.get(0).entry();

        List<String> sources = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            String t = top.get(i).entry().title();
            if (i < 3) sources.add(t);
            if (i > 0 && suggestions.size() < 3) suggestions.add(t);
        }
        String action = best.action() == null || best.action().isBlank() ? null : best.action();
        return new AnswerDto(best.body(), action, actionLabel(action), sources, suggestions, true);
    }

    /** A few starter prompts shown when the assistant is opened. */
    public List<String> examples() {
        return List.of(
                "Which wallet should I use?",
                "How do I get TEK?",
                "Limit vs market order?",
                "How do I deposit to the exchange?",
                "Is it quantum-safe?");
    }

    private AnswerDto fallback() {
        String body = kb.all().stream()
                .filter(e -> e.title().toLowerCase().contains("assistant"))
                .map(KnowledgeBase.Entry::body).findFirst()
                .orElse("I'm not sure about that yet. Ask me about wallets, mining, sending TEK, trading, or security.");
        return new AnswerDto(body, null, null, List.of(), examples().subList(0, 3), false);
    }

    private String actionLabel(String action) {
        if (action == null) return null;
        return switch (action) {
            case "wallet" -> "Open Wallet";
            case "exchange" -> "Open Exchange";
            case "explorer" -> "Open Explorer";
            case "network" -> "Open Network";
            case "security" -> "Open Security lab";
            default -> "Open " + action;
        };
    }
}
