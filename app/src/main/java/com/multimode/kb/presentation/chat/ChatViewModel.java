package com.multimode.kb.presentation.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.multimode.kb.domain.entity.SearchResult;
import com.multimode.kb.domain.service.RagService;
import com.multimode.kb.domain.repository.SearchRepository;
import com.multimode.kb.llm.RagPipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ChatViewModel extends ViewModel {

    private static final int MAX_HISTORY_TURNS = 10; // keep last N turns for context

    private final SearchRepository searchRepository;
    private final RagService ragService;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private long scopeDocumentId = -1; // -1 = global search
    private List<SearchResult> lastSearchResults; // captured during search for bot message

    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public ChatViewModel(SearchRepository searchRepository, RagService ragService) {
        this.searchRepository = searchRepository;
        this.ragService = ragService;
    }

    public LiveData<List<ChatMessage>> getMessages() { return messages; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void setScopeDocumentId(long documentId) {
        this.scopeDocumentId = documentId;
    }

    public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;

        // Add user message
        addMessage(new ChatMessage(text, false));

        loading.setValue(true);
        String query = text.trim();

        disposables.add(
                searchRepository.hybridSearch(query, 5, scopeDocumentId)
                        .flatMap(searchResults -> {
                            // Store results for the bot message
                            lastSearchResults = searchResults;

                            // Build history from previous messages
                            List<String[]> history = buildHistory();

                            if (ragService instanceof RagPipeline) {
                                return ((RagPipeline) ragService)
                                        .askWithHistory(query, searchResults, history);
                            }
                            return ragService.ask(query, searchResults);
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                answer -> {
                                    List<SearchResult> sources = lastSearchResults != null
                                            ? lastSearchResults : Collections.emptyList();
                                    addMessage(new ChatMessage(answer, true, sources));
                                    lastSearchResults = null;
                                    loading.setValue(false);
                                },
                                throwable -> {
                                    addMessage(new ChatMessage("Error: " + throwable.getMessage(), true));
                                    error.setValue(throwable.getMessage());
                                    loading.setValue(false);
                                }
                        )
        );
    }

    /**
     * Build conversation history from previous messages for multi-turn context.
     */
    private List<String[]> buildHistory() {
        List<ChatMessage> current = messages.getValue();
        if (current == null || current.size() <= 1) return null;

        List<String[]> history = new ArrayList<>();
        // Skip the last message (the one we just added)
        int start = Math.max(0, current.size() - 1 - MAX_HISTORY_TURNS * 2);
        for (int i = start; i < current.size() - 1; i++) {
            ChatMessage msg = current.get(i);
            String role = msg.isFromBot ? "assistant" : "user";
            // Truncate long messages to avoid exceeding context window
            String content = msg.text.length() > 500
                    ? msg.text.substring(0, 500) + "..."
                    : msg.text;
            history.add(new String[]{role, content});
        }
        return history;
    }

    private void addMessage(ChatMessage message) {
        List<ChatMessage> current = messages.getValue();
        if (current == null) current = new ArrayList<>();
        current.add(message);
        messages.setValue(new ArrayList<>(current));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }

    /**
     * Chat message with optional source references.
     */
    public static class ChatMessage {
        public final String text;
        public final boolean isFromBot;
        public final List<SearchResult> sources; // non-null only for bot messages

        public ChatMessage(String text, boolean isFromBot) {
            this(text, isFromBot, null);
        }

        public ChatMessage(String text, boolean isFromBot, List<SearchResult> sources) {
            this.text = text;
            this.isFromBot = isFromBot;
            this.sources = sources;
        }
    }
}
