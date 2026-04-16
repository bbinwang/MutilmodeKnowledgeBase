package com.multimode.kb.llm;

import com.multimode.kb.domain.entity.SearchResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LLM-based reranking service.
 * After RRF fusion, sends top candidates to LLM for relevance scoring,
 * then re-sorts by the LLM-assigned scores.
 */
public class RerankService {

    private static final String RERANK_PROMPT = """
            你是一个搜索结果相关性评分专家。给定用户问题和一组候选结果，请为每个结果打分（1-10分）。

            评分标准：
            - 10分：完全回答了用户问题
            - 7-9分：包含高度相关的信息
            - 4-6分：部分相关
            - 1-3分：几乎不相关

            用户问题：%s

            候选结果：
            %s

            请严格按以下格式输出，每行一个，不要输出其他内容：
            [编号] [分数]
            例如：
            1 8
            2 5
            3 9""";

    private static final int MAX_RERANK_CANDIDATES = 10; // Don't send too many to LLM

    private final LlmClient llmClient;

    public RerankService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Rerank search results using LLM relevance scoring.
     * Falls back to original order on error.
     *
     * @param query  the user's query
     * @param results  RRF-fused results to rerank
     * @return results re-sorted by LLM relevance score
     */
    public List<SearchResult> rerank(String query, List<SearchResult> results) {
        if (results == null || results.size() <= 1) return results;

        // Only rerank top N candidates
        List<SearchResult> candidates = results.size() > MAX_RERANK_CANDIDATES
                ? new ArrayList<>(results.subList(0, MAX_RERANK_CANDIDATES))
                : new ArrayList<>(results);

        try {
            String candidatesText = buildCandidatesText(candidates);
            String prompt = String.format(RERANK_PROMPT, query, candidatesText);

            String response = llmClient.chat("你是一个精准的相关性评分系统", prompt);
            int[] scores = parseScores(response, candidates.size());

            // Create scored entries and sort
            List<ScoredResult> scored = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                scored.add(new ScoredResult(candidates.get(i), scores[i]));
            }
            Collections.sort(scored, (a, b) -> Integer.compare(b.score, a.score));

            // Rebuild result list
            List<SearchResult> reranked = new ArrayList<>();
            for (ScoredResult sr : scored) {
                reranked.add(sr.result);
            }
            // Append remaining results that weren't reranked
            if (results.size() > MAX_RERANK_CANDIDATES) {
                reranked.addAll(results.subList(MAX_RERANK_CANDIDATES, results.size()));
            }

            return reranked;
        } catch (IOException e) {
            // Non-fatal: return original order
            return results;
        }
    }

    private String buildCandidatesText(List<SearchResult> candidates) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            String text = candidates.get(i).getText();
            // Truncate long texts to keep prompt manageable
            if (text.length() > 300) {
                text = text.substring(0, 300) + "...";
            }
            sb.append("[").append(i + 1).append("] ").append(text).append("\n");
        }
        return sb.toString();
    }

    /**
     * Parse LLM response into scores array.
     * Expected format: "1 8\n2 5\n3 9\n"
     */
    private int[] parseScores(String response, int count) {
        int[] scores = new int[count];
        // Default score = 5
        for (int i = 0; i < count; i++) {
            scores[i] = 5;
        }

        String[] lines = response.trim().split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            // Try to parse "N score" format
            try {
                // Remove brackets if present: "[1] 8" → "1 8"
                line = line.replaceAll("[\\[\\]]", "").trim();
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    int idx = Integer.parseInt(parts[0]) - 1;
                    int score = Integer.parseInt(parts[1]);
                    if (idx >= 0 && idx < count && score >= 1 && score <= 10) {
                        scores[idx] = score;
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return scores;
    }

    private static class ScoredResult {
        final SearchResult result;
        final int score;

        ScoredResult(SearchResult result, int score) {
            this.result = result;
            this.score = score;
        }
    }
}
