package com.multimode.kb.llm;

import com.multimode.kb.domain.entity.SearchResult;
import com.multimode.kb.domain.service.EmbeddingService;
import com.multimode.kb.domain.service.HybridSearchService;
import com.multimode.kb.domain.service.RagService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * Message for multi-turn RAG conversation history.
 */
class ChatTurn {
    final String role;    // "user" or "assistant"
    final String content;

    ChatTurn(String role, String content) {
        this.role = role;
        this.content = content;
    }
}

/**
 * RAG (Retrieval-Augmented Generation) pipeline.
 * 1. Embed user query -> vector search
 * 2. Hybrid search (vector + keyword + RRF fusion)
 * 3. Build prompt with retrieved context
 * 4. Call LLM to generate answer with source citations
 */
public class RagPipeline implements RagService {

    private static final String SYSTEM_PROMPT = """
            你是一个知识库问答助手。根据提供的参考资料回答用户的问题。

            规则：
            1. 只根据提供的参考资料回答，不要编造信息
            2. 在回答中使用 [1], [2] 等标注引用来源
            3. 如果参考资料不足以回答问题，请如实说明
            4. 回答要简洁、准确、有条理
            """;

    private static final String CONTEXT_TEMPLATE = """
            参考资料：
            %s

            ---
            用户问题：%s

            请根据以上参考资料回答问题，并在回答中标注引用来源 [1][2]...
            """;

    private final HybridSearchService searchService;
    private final EmbeddingService embeddingService;
    private final LlmClient llmClient;

    public RagPipeline(HybridSearchService searchService,
                       EmbeddingService embeddingService,
                       LlmClient llmClient) {
        this.searchService = searchService;
        this.embeddingService = embeddingService;
        this.llmClient = llmClient;
    }

    @Override
    public Single<String> ask(String question, List<SearchResult> context) {
        return askWithHistory(question, context, null);
    }

    /**
     * Multi-turn RAG: search + generate with conversation history.
     *
     * @param question  current user question
     * @param context   pre-retrieved context (null = auto-search)
     * @param history   previous turns as [role, content] pairs
     */
    public Single<String> askWithHistory(String question, List<SearchResult> context,
                                          List<String[]> history) {
        return Single.fromCallable(() -> {
            if (context == null || context.isEmpty()) {
                context = searchService.search(question, 5).blockingGet();
            }

            String contextText = buildContextText(context);
            String userPrompt = String.format(CONTEXT_TEMPLATE, contextText, question);

            return llmClient.chatMultiTurn(SYSTEM_PROMPT, history, userPrompt);
        });
    }

    /**
     * Full RAG flow: search + generate (single-turn).
     */
    public Single<String> ask(String question) {
        return ask(question, null);
    }

    private String buildContextText(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("[").append(i + 1).append("] ");
            if (r.getDocumentName() != null) {
                sb.append("来源：").append(r.getDocumentName());
            }
            if (r.getSourceLocation() != null && !r.getSourceLocation().isEmpty()) {
                sb.append(" (").append(r.getSourceLocation()).append(")");
            }
            sb.append("\n");
            sb.append(r.getText());
            sb.append("\n\n");
        }
        return sb.toString();
    }
}
