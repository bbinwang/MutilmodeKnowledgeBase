package com.multimode.kb.di;

import android.content.Context;
import android.content.SharedPreferences;

import com.multimode.kb.KbApplication;
import com.multimode.kb.data.local.HybridSearchDataSource;
import com.multimode.kb.data.local.fts.FtsDatabase;
import com.multimode.kb.data.local.fts.FtsDataSource;
import com.multimode.kb.data.local.objectbox.ObjectBoxDataSource;
import com.multimode.kb.data.remote.CloudProcessingClient;
import com.multimode.kb.data.remote.FileParserServiceImpl;
import com.multimode.kb.data.repository.DocumentRepositoryImpl;
import com.multimode.kb.data.repository.DirectoryRepositoryImpl;
import com.multimode.kb.data.repository.EmbeddingServiceImpl;
import com.multimode.kb.data.repository.HybridSearchServiceImpl;
import com.multimode.kb.data.repository.IngestionRepositoryImpl;
import com.multimode.kb.data.repository.SearchRepositoryImpl;
import com.multimode.kb.domain.repository.DocumentRepository;
import com.multimode.kb.domain.repository.DirectoryRepository;
import com.multimode.kb.domain.repository.IngestionRepository;
import com.multimode.kb.domain.repository.SearchRepository;
import com.multimode.kb.domain.service.EmbeddingService;
import com.multimode.kb.domain.service.FileParserService;
import com.multimode.kb.domain.service.HybridSearchService;
import com.multimode.kb.domain.service.RagService;
import com.multimode.kb.llm.EmbeddingClient;
import com.multimode.kb.llm.LlmClient;
import com.multimode.kb.llm.OmniClient;
import com.multimode.kb.llm.QueryRewriter;
import com.multimode.kb.llm.RagPipeline;
import com.multimode.kb.llm.RerankService;
import com.multimode.kb.worker.KbWorkerFactory;

import io.objectbox.BoxStore;

import com.multimode.kb.data.local.objectbox.MyObjectBox;

/**
 * Manual dependency injection component (no Dagger/Hilt).
 * Holds all singleton instances for the app.
 */
public class AppComponent {

    private static final String PREFS_NAME = "kb_prefs";
    private static final String KEY_LONGCAT_API_KEY = "longcat_api_key";
    private static final String KEY_LONGCAT_BASE_URL = "longcat_base_url";
    private static final String KEY_CHAT_MODEL = "chat_model";
    private static final String KEY_OMNI_MODEL = "omni_model";
    private static final String KEY_EMBEDDING_API_KEY = "embedding_api_key";
    private static final String KEY_EMBEDDING_BASE_URL = "embedding_base_url";
    private static final String KEY_EMBEDDING_MODEL = "embedding_model";
    private static final String KEY_EMBEDDING_DIMENSIONS = "embedding_dimensions";

    private final Context context;
    private SharedPreferences prefs;

    // Storage
    private BoxStore boxStore;
    private ObjectBoxDataSource objectBoxDataSource;
    private FtsDatabase ftsDatabase;
    private FtsDataSource ftsDataSource;
    private HybridSearchDataSource hybridSearchDataSource;

    // LLM clients
    private LlmClient llmClient;
    private EmbeddingClient embeddingClient;
    private OmniClient omniClient;

    // Repositories
    private DocumentRepository documentRepository;
    private DirectoryRepository directoryRepository;
    private IngestionRepository ingestionRepository;
    private SearchRepository searchRepository;

    // Services
    private EmbeddingService embeddingService;
    private HybridSearchService hybridSearchService;
    private RagService ragService;
    private FileParserService fileParserService;
    private CloudProcessingClient cloudProcessingClient;

    // Worker factory
    private KbWorkerFactory workerFactory;

    public AppComponent(Context context) {
        this.context = context.getApplicationContext();
    }

    public void initialize() {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // ObjectBox
        boxStore = MyObjectBox.builder().androidContext(context).build();
        objectBoxDataSource = new ObjectBoxDataSource(boxStore);

        // FTS5
        ftsDatabase = new FtsDatabase(context);
        ftsDataSource = new FtsDataSource(ftsDatabase);

        // Hybrid search
        hybridSearchDataSource = new HybridSearchDataSource(objectBoxDataSource, ftsDataSource);

        // Repositories
        documentRepository = new DocumentRepositoryImpl(objectBoxDataSource);
        directoryRepository = new DirectoryRepositoryImpl(objectBoxDataSource);
        ingestionRepository = new IngestionRepositoryImpl(objectBoxDataSource, ftsDataSource);
        searchRepository = new SearchRepositoryImpl(getHybridSearchService());

        // File parser
        fileParserService = new FileParserServiceImpl(context);
        // Note: OmniClient is lazily initialized, so we set it via getter when needed.
        // The DocumentIngestionWorker will call getFileParserService() and setOmniClient()
        // before calling parse().

        // Cloud processing (multimodal)
        cloudProcessingClient = new CloudProcessingClient(context);

        // Worker factory
        workerFactory = new KbWorkerFactory(this);

        // LLM clients (lazily initialized via getters)
    }

    // ---- Getters with lazy initialization for LLM clients ----

    public SharedPreferences getPrefs() { return prefs; }

    public ObjectBoxDataSource getObjectBoxDataSource() { return objectBoxDataSource; }
    public FtsDataSource getFtsDataSource() { return ftsDataSource; }
    public BoxStore getBoxStore() { return boxStore; }

    public DocumentRepository getDocumentRepository() { return documentRepository; }
    public DirectoryRepository getDirectoryRepository() { return directoryRepository; }
    public IngestionRepository getIngestionRepository() { return ingestionRepository; }
    public SearchRepository getSearchRepository() { return searchRepository; }

    public LlmClient getLlmClient() {
        if (llmClient == null) {
            llmClient = new LlmClient(
                    getLongcatBaseUrl(),
                    getLongcatApiKey(),
                    getChatModel()
            );
        }
        return llmClient;
    }

    public EmbeddingClient getEmbeddingClient() {
        if (embeddingClient == null) {
            embeddingClient = new EmbeddingClient(
                    getEmbeddingBaseUrl(),
                    getEmbeddingApiKey(),
                    getEmbeddingModel()
            );
        }
        return embeddingClient;
    }

    public OmniClient getOmniClient() {
        if (omniClient == null) {
            omniClient = new OmniClient(
                    getLongcatBaseUrl(),
                    getLongcatApiKey(),
                    getOmniModel()
            );
        }
        return omniClient;
    }

    public KbWorkerFactory getWorkerFactory() { return workerFactory; }

    public FileParserService getFileParserService() { return fileParserService; }

    public CloudProcessingClient getCloudProcessingClient() { return cloudProcessingClient; }

    public EmbeddingService getEmbeddingService() {
        if (embeddingService == null) {
            embeddingService = new EmbeddingServiceImpl(getEmbeddingClient());
        }
        return embeddingService;
    }

    public HybridSearchService getHybridSearchService() {
        if (hybridSearchService == null) {
            HybridSearchServiceImpl impl = new HybridSearchServiceImpl(getEmbeddingService(), hybridSearchDataSource);
            impl.setQueryRewriter(new QueryRewriter(getLlmClient()));
            impl.setRerankService(new RerankService(getLlmClient()));
            hybridSearchService = impl;
        }
        return hybridSearchService;
    }

    public RagService getRagService() {
        if (ragService == null) {
            ragService = new RagPipeline(getHybridSearchService(), getEmbeddingService(), getLlmClient());
        }
        return ragService;
    }

    // ---- Settings helpers ----

    public String getLongcatApiKey() {
        return prefs.getString(KEY_LONGCAT_API_KEY, "");
    }

    public String getLongcatBaseUrl() {
        return prefs.getString(KEY_LONGCAT_BASE_URL, "https://api.longcat.chat");
    }

    public String getChatModel() {
        return prefs.getString(KEY_CHAT_MODEL, "LongCat-Flash-Chat");
    }

    public String getOmniModel() {
        return prefs.getString(KEY_OMNI_MODEL, "LongCat-Flash-Omni-2603");
    }

    public String getEmbeddingApiKey() {
        return prefs.getString(KEY_EMBEDDING_API_KEY, "");
    }

    public String getEmbeddingBaseUrl() {
        return prefs.getString(KEY_EMBEDDING_BASE_URL, "");
    }

    public String getEmbeddingModel() {
        return prefs.getString(KEY_EMBEDDING_MODEL, "");
    }

    public int getEmbeddingDimensions() {
        return prefs.getInt(KEY_EMBEDDING_DIMENSIONS, 1536);
    }

    /**
     * Called when settings change to invalidate cached clients.
     */
    public void invalidateClients() {
        llmClient = null;
        embeddingClient = null;
        omniClient = null;
        // Reset services that depend on LLM clients
        embeddingService = null;
        hybridSearchService = null;
        ragService = null;
    }
}
