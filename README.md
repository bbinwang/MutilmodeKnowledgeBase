<h1 align="center">全模态知识库</h1>

<p align="center">
  <strong>Android 端本地 RAG 知识库 —— 支持文档、图片、音频、视频全格式接入</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android" alt="Platform" />
  <img src="https://img.shields.io/badge/minSDK-26_(Android_8.0)-blue?style=flat-square" alt="minSDK" />
  <img src="https://img.shields.io/badge/targetSDK-34-blue?style=flat-square" alt="targetSDK" />
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk" alt="Java" />
</p>

<p align="center">
  对标智谱 GLM 多模态知识库的开源 Android 实现，基于 Clean Architecture + MVVM，<br>
  提供 PDF / DOCX / PPTX / 图片 / 音频 / 视频的统一解析、混合检索与 RAG 问答。
</p>

---

## 截图

| 文档列表 | 混合搜索 | RAG 问答 | 文档查看器 | 设置 |
|:---:|:---:|:---:|:---:|:---:|
| ![文档列表](docs/screenshots/document_list.png) | ![混合搜索](docs/screenshots/search.png) | ![RAG问答](docs/screenshots/chat.png) | ![查看器](docs/screenshots/viewer.png) | ![设置](docs/screenshots/settings.png) |

---

## 核心特性

### 全格式统一接入
- **PDF**（最大 150MB）：PDFBox 解析，临时文件内存模式 + 分批处理；支持扫描件 OCR（云端多模态模型）
- **DOCX / PPTX**：Apache POI 解析，保留表格与幻灯片结构
- **图片**：云端多模态模型描述（JPG / PNG / WebP，自动压缩至 2048px）
- **音频**：云端多模态模型转写（WAV / MP3）
- **视频**（最大 500MB）：关键帧提取（每 10s 一帧）+ 音轨提取，分别送入云端模型

### 混合检索
- **向量语义搜索**：ObjectBox HNSW 索引（`@HnswIndex(dimensions = 1536)`）
- **关键词搜索**：SQLite FTS5 + BM25 排序
- **RRF 融合**：Reciprocal Rank Fusion（K=60）合并双路结果
- **查询改写**：LLM 自动改写用户查询以提升召回率
- **重排序**：RRF 融合后 LLM 二次精排
- **文档范围搜索**：支持限定在单个文档内检索

### RAG 问答
- 多轮对话（历史消息上下文）
- 回答自动标注引用来源 `[1][2]...`
- 来源卡片展示：文档名、页码 / 时间戳、媒体类型图标
- 点击来源卡片跳转至 PDF 指定页 / 视频时间点 / 原始图片

### 工程特性
- 后台接入：WorkManager 驱动的文档解析与向量生成流水线
- 内存安全：PDF 分批处理（50 页/批）、视频帧分批（5 帧/批）、大文件使用临时文件模式
- RxJava 3 全链路异步，避免 UI 阻塞
- 手动依赖注入（AppComponent），无 Dagger/Hilt 开销

---

## 架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        Presentation (MVVM)                       │
│  DocumentList  Search  Chat  DocumentViewer  Settings            │
│  (ViewModel + LiveData / RxJava)                                 │
├──────────────────────────────────────────────────────────────────┤
│                          Domain Layer                            │
│  Repository Interfaces · Entity · Service Interfaces             │
│  (DocumentRepository, SearchRepository, IngestionRepository)     │
│  (HybridSearchService, EmbeddingService, FileParserService)      │
├──────────────────────────────────────────────────────────────────┤
│                           Data Layer                             │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │ ObjectBox (HNSW) │  │ SQLite FTS5      │  │ LLM Clients    │  │
│  │ 向量语义搜索      │  │ 全文关键词搜索    │  │ LlmClient      │  │
│  └─────────────────┘  └──────────────────┘  │ OmniClient     │  │
│           │                    │             │ EmbeddingClient │  │
│           └──────┬─────────────┘             └────────────────┘  │
│         HybridSearchDataSource (RRF 融合)                        │
├──────────────────────────────────────────────────────────────────┤
│                      Background Workers                          │
│  DocumentIngestionWorker  →  EmbeddingGenerationWorker           │
│  (WorkManager + KbWorkerFactory)                                 │
└──────────────────────────────────────────────────────────────────┘
```

---

## 技术栈

| 类别 | 库 | 版本 | 用途 |
|:---|:---|:---|:---|
| 向量数据库 | ObjectBox | 4.1.0 | 文档段落存储 + HNSW 向量索引 |
| 全文搜索 | Requery SQLite (FTS5) | 3.45.0 | BM25 关键词搜索，unicode61 分词 |
| 网络请求 | OkHttp | 4.12.0 | REST API 调用 |
| JSON | Moshi | 1.15.2 | API 响应解析 |
| PDF 解析 | PDFBox (Android) | 2.0.32.0 | PDF 文本提取 + 页面渲染 |
| Office 解析 | Apache POI | 5.3.0 | DOCX / PPTX 文本提取 |
| 异步框架 | RxJava 3 | 3.1.10 | 全链路异步调度 |
| 后台任务 | WorkManager | 2.10.0 | 文档接入 / 向量生成后台流水线 |
| 构建工具 | Gradle (AGP) | 8.7.3 | Android 构建系统 |

### LLM 服务

| 服务 | 默认模型 | 用途 |
|:---|:---|:---|
| 文本对话 | `LongCat-Flash-Chat` | RAG 问答、查询改写、重排序 |
| 多模态 | `LongCat-Flash-Omni-2603` | 图片描述、音频转写、视频关键帧分析、扫描 PDF OCR |
| 向量嵌入 | 用户配置 | OpenAI 兼容 Embedding 接口（默认维度 1536） |

---

## 项目结构

```
app/src/main/java/com/multimode/kb/
├── KbApplication.java                  # Application 入口
├── di/
│   └── AppComponent.java               # 手动 DI 容器
├── domain/
│   ├── entity/
│   │   ├── KbDocument.java             # 文档领域模型
│   │   ├── DocumentSegment.java        # 段落领域模型
│   │   ├── SearchResult.java           # 搜索结果（含媒体类型、来源路径）
│   │   └── DocumentStatus.java         # 文档状态枚举
│   ├── repository/
│   │   ├── DocumentRepository.java
│   │   ├── IngestionRepository.java
│   │   └── SearchRepository.java
│   └── service/
│       ├── FileParserService.java      # 文件解析接口
│       ├── EmbeddingService.java       # 向量嵌入接口
│       ├── HybridSearchService.java    # 混合搜索接口
│       └── RagService.java             # RAG 问答接口
├── data/
│   ├── local/
│   │   ├── objectbox/
│   │   │   ├── KbDocumentEntity.java   # @Entity 文档元数据
│   │   │   ├── DocumentSegmentEntity.java  # @Entity 段落 + @HnswIndex
│   │   │   └── ObjectBoxDataSource.java
│   │   ├── fts/
│   │   │   ├── FtsDatabase.java        # FTS5 建表 + 触发器
│   │   │   ├── FtsDataSource.java      # FTS5 查询
│   │   │   └── FtsSearchResult.java
│   │   └── HybridSearchDataSource.java # RRF 融合（向量 + 关键词）
│   ├── remote/
│   │   ├── FileParserServiceImpl.java  # PDF/DOCX/PPTX 本地解析 + OCR
│   │   ├── CloudProcessingClient.java  # 图片/音频/视频云端处理
│   │   └── MediaUtils.java            # 压缩、关键帧提取、音轨提取
│   └── repository/
│       ├── DocumentRepositoryImpl.java
│       ├── IngestionRepositoryImpl.java
│       ├── SearchRepositoryImpl.java
│       ├── EmbeddingServiceImpl.java
│       └── HybridSearchServiceImpl.java    # 改写 → 向量化 → RRF → 重排序
├── llm/
│   ├── LlmClient.java                 # 文本 LLM 客户端
│   ├── OmniClient.java                # 多模态 LLM 客户端
│   ├── EmbeddingClient.java           # 向量嵌入客户端
│   ├── RagPipeline.java               # RAG 流水线（多轮对话）
│   ├── QueryRewriter.java             # 查询改写
│   └── RerankService.java             # LLM 重排序
├── worker/
│   ├── KbWorkerFactory.java           # WorkManager 工厂
│   ├── DocumentIngestionWorker.java   # 文档解析 Worker
│   └── EmbeddingGenerationWorker.java # 向量生成 Worker
└── presentation/
    ├── documentlist/                   # 文档列表 + SAF 文件选择
    ├── search/                         # 混合搜索结果页
    ├── chat/                           # RAG 多轮问答页
    ├── viewer/                         # PDF/图片/视频/音频查看器
    └── settings/                       # API Key 与模型配置
```

---

## 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK：Compile 34, Min 26
- Gradle 8.x（项目自带 Gradle Wrapper）

### 克隆与构建

```bash
git clone https://github.com/<your-username>/MutilmodeKnowledgeBase.git
cd MutilmodeKnowledgeBase
./gradlew assembleDebug
```

### 安装运行

```bash
# ADB 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 或在 Android Studio 中直接运行
```

> 首次启动后需进入 **设置** 配置 API Key，否则文档接入与搜索功能不可用。

---

## 配置说明

进入 **SettingsActivity** 进行以下配置：

### LongCat API（必填）

| 配置项 | 说明 | 默认值 |
|:---|:---|:---|
| API Key | LongCat 平台密钥 | _(空)_ |
| Base URL | API 基础地址 | `https://api.longcat.chat` |
| Chat Model | 文本对话模型 | `LongCat-Flash-Chat` |
| Omni Model | 多模态理解模型 | `LongCat-Flash-Omni-2603` |

### Embedding 服务（必填）

| 配置项 | 说明 | 默认值 |
|:---|:---|:---|
| API Key | 向量服务密钥 | _(空)_ |
| Base URL | OpenAI 兼容 Embedding 地址 | _(空)_ |
| Model | 模型名称 | _(空)_ |
| Dimensions | 向量维度 | `1536` |

> 配置变更后自动清除缓存的 LLM 客户端，下次操作即使用新配置。如更换向量维度，需重新接入已有文档。

---

## 数据流

### 文档接入

```
SAF 文件选择
    │
    ▼
DocumentIngestionWorker (WorkManager)
    │
    ├── PDF / DOCX / PPTX → FileParserServiceImpl (本地解析)
    │       └── 扫描页 → OmniClient OCR
    ├── 图片 → OmniClient 描述
    ├── 音频 → OmniClient 转写
    └── 视频 → 关键帧描述 + 音轨转写
    │
    ▼
段落写入 ObjectBox + FTS5
    │
    ▼
EmbeddingGenerationWorker → 向量写入 @HnswIndex
```

### 混合检索

```
用户查询 → QueryRewriter 改写 → EmbeddingClient 向量化
    │
    ├── ObjectBox HNSW 向量搜索 (top-20)
    └── SQLite FTS5 关键词搜索 BM25 (top-20)
    │
    ▼
RRF 融合 (K=60) → RerankService LLM 重排序 → Top-N 结果
```

### RAG 问答

```
用户提问 + 历史对话
    │
    ▼
HybridSearchService.search() → Top-5 相关段落
    │
    ▼
RagPipeline 构建带引用标注的上下文
    │
    ▼
LlmClient.chatMultiTurn() 多轮对话生成
    │
    ▼
带 [1][2] 引用的回答 + 来源卡片（可点击跳转）
```

---

## 贡献

欢迎提交 Issue 和 Pull Request。提交 PR 前请确保：

1. 代码风格与现有代码一致
2. 新功能附带必要注释
3. 通过 `./gradlew assembleDebug` 构建

---

<p align="center">
  全模态知识库 · 让每份资料都可被精准提问与追溯
</p>
