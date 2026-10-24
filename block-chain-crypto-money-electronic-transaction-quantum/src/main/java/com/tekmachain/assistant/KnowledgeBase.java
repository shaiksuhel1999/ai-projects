package com.tekmachain.assistant;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A fully local, embedded retrieval index over the guidance knowledge base
 * (`assistant-kb.md`). No cloud, no API keys, no LLM — it ranks knowledge entries
 * against a user's question with the standard <b>BM25</b> algorithm (the same
 * ranking search engines use), which is the "retrieval" half of a
 * retrieval-augmented assistant and handles varying entry lengths correctly.
 */
@Component
public class KnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);
    private static final double K1 = 1.2, B = 0.6;
    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "is", "are", "am", "be", "to", "of", "and", "or", "for", "in", "on", "it",
            "i", "my", "me", "you", "your", "do", "does", "did", "how", "what", "which", "when", "where",
            "should", "can", "could", "would", "with", "this", "that", "there", "if", "so", "as", "at",
            "by", "we", "us", "our", "they", "them", "want", "need", "use", "using", "get", "go", "into");

    public record Entry(String title, String body, String action, Map<String, Integer> tf, int len) {
    }

    public record Scored(Entry entry, double score) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Double> idf = new HashMap<>();
    private double avgLen = 1;

    @PostConstruct
    void load() {
        List<String[]> raw = parse();   // [title, keywords, action, body]
        if (raw.isEmpty()) {
            log.warn("Assistant knowledge base is empty");
            return;
        }
        Map<String, Integer> df = new HashMap<>();
        long totalLen = 0;
        for (String[] e : raw) {
            // weight titles and keywords more heavily than body
            String weighted = (e[0] + " ").repeat(3) + (e[1] + " ").repeat(3) + e[3];
            Map<String, Integer> tf = termFreq(weighted);
            int len = tf.values().stream().mapToInt(Integer::intValue).sum();
            totalLen += len;
            entries.add(new Entry(e[0], e[3], e[2], tf, len));
            for (String t : tf.keySet()) df.merge(t, 1, Integer::sum);
        }
        int n = entries.size();
        avgLen = (double) totalLen / n;
        for (var d : df.entrySet()) {
            idf.put(d.getKey(), Math.log(1 + (n - d.getValue() + 0.5) / (d.getValue() + 0.5)));
        }
        log.info("Assistant loaded {} knowledge entries (BM25)", n);
    }

    /** Rank entries against the query with BM25; returns up to {@code k}. */
    public List<Scored> search(String query, int k) {
        Set<String> qTerms = new HashSet<>(tokenize(query));
        List<Scored> scored = new ArrayList<>();
        for (Entry e : entries) {
            double score = 0;
            for (String t : qTerms) {
                Integer f = e.tf().get(t);
                if (f == null) continue;
                double d = idf.getOrDefault(t, 0.0);
                score += d * (f * (K1 + 1)) / (f + K1 * (1 - B + B * e.len() / avgLen));
            }
            if (score > 0) scored.add(new Scored(e, score));
        }
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return scored.subList(0, Math.min(k, scored.size()));
    }

    public List<Entry> all() {
        return Collections.unmodifiableList(entries);
    }

    // ---- helpers ----
    private Map<String, Integer> termFreq(String text) {
        Map<String, Integer> tf = new HashMap<>();
        for (String tok : tokenize(text)) tf.merge(tok, 1, Integer::sum);
        return tf;
    }

    private List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase().split("[^a-z0-9]+")) {
            if (raw.length() >= 2 && !STOP.contains(raw)) {
                out.add(raw.endsWith("s") && raw.length() > 3 ? raw.substring(0, raw.length() - 1) : raw); // crude stemming
            }
        }
        return out;
    }

    private List<String[]> parse() {
        List<String[]> blocks = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource("assistant-kb.md").getInputStream(), StandardCharsets.UTF_8))) {
            String title = null, keywords = "", action = "";
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("## ")) {
                    if (title != null) blocks.add(new String[]{title, keywords, action, body.toString().trim()});
                    title = line.substring(3).trim();
                    keywords = ""; action = ""; body.setLength(0);
                } else if (title == null) {
                    // skip preamble
                } else if (line.startsWith("keywords:")) {
                    keywords = line.substring("keywords:".length()).trim();
                } else if (line.startsWith("action:")) {
                    action = line.substring("action:".length()).trim();
                } else {
                    body.append(line).append('\n');
                }
            }
            if (title != null) blocks.add(new String[]{title, keywords, action, body.toString().trim()});
        } catch (Exception e) {
            log.error("Failed to load assistant knowledge base", e);
        }
        return blocks;
    }
}
