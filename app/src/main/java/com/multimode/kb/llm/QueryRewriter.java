package com.multimode.kb.llm;

import java.io.IOException;

/**
 * Rewrites user queries for better retrieval performance.
 * Uses LLM to expand ambiguous queries, extract keywords,
 * and generate alternative phrasings.
 */
public class QueryRewriter {

    private static final String REWRITE_PROMPT = """
            你是一个搜索查询优化专家。用户的原始问题可能过于口语化、模糊或不利于检索。
            请将用户的问题改写为更适合知识库检索的查询。

            规则：
            1. 提取核心关键词
            2. 补充同义词和相关术语
            3. 保持原意不变
            4. 输出格式：一行改写后的查询，直接输出，不要解释

            原始问题：%s""";

    private final LlmClient llmClient;

    public QueryRewriter(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Rewrite a user query for better retrieval.
     * Falls back to original query on error.
     */
    public String rewrite(String originalQuery) {
        try {
            String prompt = String.format(REWRITE_PROMPT, originalQuery);
            String rewritten = llmClient.chat("你是一个搜索查询优化助手", prompt);
            // Clean up: remove quotes, extra whitespace
            String cleaned = rewritten.trim()
                    .replaceAll("^[\"'`]+|[\"'`]+$", "")
                    .trim();
            return cleaned.isEmpty() ? originalQuery : cleaned;
        } catch (IOException e) {
            // Non-fatal: return original query
            return originalQuery;
        }
    }
}
