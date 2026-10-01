# FinMate 项目目录与学习路线

先学一次普通业务请求如何从页面进入后端、保存到数据库并返回，再学习多轮聊天、PDF 分析和 RAG。不要从最长的 MarketService 或 app.js 第一行一直读到最后。

## 项目目录

```text
java-ai-langchain4j/
├── pom.xml                   Maven 依赖、Java 版本和构建插件
├── settings-local.xml        本机 Maven 依赖缓存位置
├── compose.rag.yaml          只启动 Qdrant，不会启动 Java/MySQL/MongoDB
├── README.md                 项目入口和简要调用流程
├── LEARNING.md               本文：目录解释和学习路线
├── RAG-SETUP.md              RAG 配置、接口与功能边界
├── src/
│   ├── main/
│   │   ├── java/com/zth/java/ai/langchain4j/
│   │   │   ├── APP.java      Spring Boot 启动入口
│   │   │   ├── controller/   HTTP 接口
│   │   │   ├── service/      业务处理
│   │   │   ├── repository/   MySQL 数据访问接口
│   │   │   ├── entity/       MySQL 表对应的实体
│   │   │   ├── dto/          请求和响应对象
│   │   │   ├── bean/         通用响应、聊天请求和消息存储对象
│   │   │   ├── assistant/    LangChain4j AI 服务声明
│   │   │   ├── tools/        模型可调用的 Java 方法
│   │   │   ├── store/        MongoDB 聊天记忆读写
│   │   │   ├── rag/          财报向量索引和检索问答
│   │   │   ├── config/       会话配置、异常处理、旧表兼容
│   │   │   └── exception/    登录异常
│   │   └── resources/
│   │       ├── application.properties  服务端配置
│   │       ├── finance-agent-prompt.txt 普通聊天系统提示词
│   │       └── static/       index.html、styles.css、app.js
│   └── test/
│       ├── java/.../service/ 会话和 PDF 分析回归测试
│       ├── java/.../rag/     分块、向量客户端、RAG 流程回归测试
│       └── js/              财报问答页面逻辑测试
├── target/                  编译生成的 class、JAR 和测试结果
├── .m2repo/                 Maven 下载的依赖缓存
├── .idea/                   IDEA 项目和本机运行配置
└── .git/                    版本历史
```

`src/main` 是学习主线。`src/test` 不会作为业务接口运行，第一遍可折叠。`target` 不是源代码，每次编译会重新生成文件；不要在里面改 Java。`.m2repo` 不是无用文件，删除后 Maven 需要重新下载依赖。`.idea` 可能保存你的启动配置，`.git` 保存历史，都不要当垃圾目录删除。

## 后端各目录具体负责什么

下面 Java 文件路径均相对于 `src/main/java/com/zth/java/ai/langchain4j/`。

### controller 接口入口

Controller 接收浏览器请求，解析入参，检查登录，调用 Service，再把结果返回。它不应承担大量数据库操作或模型提示词拼接。

| 文件 | 页面或功能 | 重点 |
| --- | --- | --- |
| AuthController | 注册、登录、当前用户、退出 | HttpSession 与请求校验 |
| FavoriteAssetController | 收藏标的 | 新增、查询、删除与用户归属 |
| DashboardController | 统计看板 | 汇总多个业务的数据 |
| FinanceController | 普通聊天、会话列表及消息 | 确定会话后调用 FinanceAgent |
| FinancialReportController | 股票财报列表、生成报告、报告历史 | 公告查询和 PDF 分析 |
| ReportRagController | 财报索引、索引状态、原文问答 | index 与 questions 接口的区别 |
| MarketController | 今日市场和市场追问 | 行情、新闻及页面快照 |

### service 业务逻辑

| 文件 | 职责 |
| --- | --- |
| AuthService | 查用户、注册、校验密码、读写登录身份 |
| PasswordService | 随机盐、PBKDF2 密码散列及密码校验 |
| FavoriteAssetService | 收藏去重、字段整理、保存、归属检查和统计 |
| FinanceKnowledgeService | 内置财经概念和面试题模板；不是向量知识库 |
| DashboardService | 汇总收藏和聊天会话数量及最近收藏 |
| ChatSessionService | 管理会话目录、标题、用户归属，读取和删除消息窗口 |
| FinancialReportService | 查询巨潮公告，协调 PDF 正文分析并保存报告 |
| FinancialReportPdfService | 限制下载来源，下载 PDF、逐页提取、关键词选段；向 RAG 提供选段前的页面正文 |
| MarketService | 获取指数/K 线/新闻，生成市场简报及回答，处理外部接口失败 |
| MarketQuoteService | 读取腾讯/东方财富真实行情，校验价格与日期，保留来源和行情时间 |

### entity 与 repository 数据库映射

`entity` 描述数据长什么样，`repository` 描述怎么存取。Repository 通常是接口，具体实现由 Spring Data JPA 在运行时生成，因此你找不到手写实现类。

| 实体 | Repository | 保存什么 |
| --- | --- | --- |
| AppUser | AppUserRepository | 账号、密码散列、昵称 |
| FavoriteAsset | FavoriteAssetRepository | 用户收藏的标的 |
| FinanceChatSession | FinanceChatSessionRepository | 会话标题、用户归属和时间 |
| FinancialReportAnalysis | FinancialReportAnalysisRepository | 生成的财报分析报告 |
| rag/ReportDocument | rag/ReportDocumentRepository | 财报索引状态、配置版本、来源和页数 |

例如 `findByIdAndUserId` 同时限制资源 ID 和用户 ID；只用 `findById` 找到记录，不代表当前用户有权访问它。

### dto 与 bean 接口数据

DTO 是浏览器和后端之间传输的数据格式，不等于数据库表。不需要先逐个背 DTO；读某个接口时，打开它对应的请求和响应类即可。

| DTO 分组 | 例子 | 含义 |
| --- | --- | --- |
| 登录 | RegisterRequest、LoginRequest、CurrentUserResponse | 用户输入与可展示的用户信息 |
| 收藏 | FavoriteAssetRequest、FavoriteAssetResponse | 收藏入参与返回结果 |
| 聊天 | ChatSessionRequest/Response、ChatMessageResponse、ChatResponse | 会话信息和聊天回复 |
| 市场 | MarketTodayResponse、MarketChatRequest/Response | 市场看板与追问 |
| 财报 | FinancialReportResponse、FinancialReportListResponse、FinancialReportAnalysisRequest/Response | 公告与分析报告 |
| 看板 | DashboardResponse | 数量统计与最近记录 |

`bean/ApiResponse` 是接口外层统一包装；`bean/ChatForm` 是普通聊天入参；`bean/ChatMessages` 是 MongoDB 消息文档。命名没有完全统一，但这些文件各有实际用途。RAG 的小型 DTO 使用 Java record，放在 `ReportRagController` 和 `ReportRagService` 内部。

### assistant tools store 普通 AI 聊天

- `assistant/FinanceAgent` 用 `@AiService` 声明模型、记忆和工具，LangChain4j 生成实现。
- `tools/FinanceTools` 用 `@Tool` 暴露方法给模型；方法内部委托 `FinanceKnowledgeService`。
- `store/MongoChatMemoryStore` 实现框架的 `ChatMemoryStore`，序列化/反序列化消息窗口并写入 MongoDB。
- `config/FinanceAgentConfig` 为每个会话创建最多 20 条消息的窗口。20 条消息不等于 20 轮问答，也不是永久完整聊天历史。

### rag 财报检索问答

| 文件 | 作用 |
| --- | --- |
| RagProperties | 读取开关、模型地址、Key、维度、集合名和相似度阈值 |
| ReportDocument | 记录谁索引了哪份财报，以及 INDEXING/READY/FAILED 状态 |
| ReportDocumentRepository | 把上述记录保存到 MySQL，按用户查询 |
| ReportChunker | 页内切分正文，保留页码与重叠文本 |
| RagVectorClient | 通过 HTTP 调用 Embedding 和 Qdrant，检查维度、创建集合、写入和检索 |
| ReportRagService | 串起公告核验、解析、入库、权限校验、检索、模型回答和来源构造 |

向量存储和 Embedding 当前通过官方 REST 协议调用，生成回答使用 LangChain4j `ChatModel`。不要把这段实现误记成使用了框架的自动检索组件。

### config 与 exception

- `GlobalExceptionHandler` 把参数错误、登录失效和业务异常转换成接口响应。
- `FinanceAgentConfig` 管理普通聊天的消息窗口。
- `DatabaseSchemaMaintenance` 在启动时兼容旧收藏表索引，把全局唯一改为按用户唯一。它由 Spring 调用，不能因为找不到普通方法引用就当成无用类。
- `AuthRequiredException` 表示未登录或登录失效，对应 HTTP 401。

### resources 前端与配置

- `application.properties` 配置端口、数据库和聊天模型。学习配置项含义，不要把实际密码/Key复制到笔记或 Git。
- `finance-agent-prompt.txt` 是普通聊天的系统提示词。财报分析和 RAG 的提示词在各自 Service 中。
- `static/index.html` 定义页面区域、按钮、输入框及元素 ID。
- `static/styles.css` 决定颜色、布局、间距等视觉样式。
- `static/app.js` 负责事件、HTTP 请求、状态与渲染。`handle*` 处理操作，`load*` 加载数据，`render*` 更新页面，`request` 统一发请求。

## 学习路线

每一阶段只跟踪一个真实操作，写清楚：请求地址、入参、登录用户从哪来、调用哪个 Service、访问哪种数据库、返回什么。先看 public 方法的主流程，再看它用到的 private 方法。

### 第一步 看懂项目怎样启动

顺序：`pom.xml` → `APP.java` → `application.properties` → `static/index.html`。

目标：知道 Java 17、Spring Boot、JPA、MongoDB、LangChain4j、PDFBox 分别解决什么问题；知道 `APP.main` 启动 Spring 容器，浏览器访问同一个后端提供的静态页面。此阶段不用研究 Maven 内部机制。

自查：为什么没有手动 new Controller？为什么前端文件能被浏览器访问？数据库连接地址在哪里配置？

### 第二步 跟踪注册和登录

顺序：`AuthController` → `RegisterRequest/LoginRequest` → `AuthService.register/login` → `PasswordService` → `AppUser` → `AppUserRepository` → `CurrentUserResponse` → `ApiResponse`。

然后看 `AuthService.requireUserId`、`AuthRequiredException`、`GlobalExceptionHandler`。前端只看 `submitAuth` 和 `request`。

目标：理解 JSON 入参、`@Valid`、构造方法注入、查询数据库、Session 登录和响应 DTO。

自查：密码为什么不能直接入库？后续请求不再带账号密码时，后端如何知道是谁？未登录会发生什么？

### 第三步 完整跟踪一次添加收藏

顺序：`app.js/handleFavoriteSubmit` → `FavoriteAssetController.create` → `FavoriteAssetRequest` → `AuthService.requireUserId` → `FavoriteAssetService.create` → `FavoriteAssetRepository` → `FavoriteAsset` → `FavoriteAssetResponse`。

再看 list 与 delete，最后看 `DatabaseSchemaMaintenance`。

目标：把 Controller → Service → Repository → MySQL 的关系学扎实。

练习：在 IDEA 给 `create` 打断点，观察 symbol 整理前后、userId、保存后的 id。解释“用户 A 和 B 可以收藏同一股票，但 A 不应删除 B 的收藏”。

### 第四步 看统计聚合

顺序：DashboardController → DashboardService → FavoriteAssetService/ChatSessionService。

目标：理解统计接口如何组合收藏与会话数据。模板卡片及其历史已移除，旧表和数据保留但不再使用。

### 第五步 学普通聊天和会话记忆

顺序：`FinanceController.chat` → `ChatForm` → `ChatSessionService.resolveForMessage/requireOwned` → `FinanceChatSession/FinanceChatSessionRepository` → `FinanceAgent` → `finance-agent-prompt.txt` → `FinanceTools` → `FinanceKnowledgeService` → `FinanceAgentConfig` → `MongoChatMemoryStore` → `ChatMessages`。

把登录 Session 和聊天会话分开：前者回答“你是谁”，后者回答“这是哪个聊天窗口”。MySQL 存会话目录，MongoDB 存模型消息窗口。

自查：会话 ID 怎么传给 LangChain4j？模型如何知道有哪些工具？MongoDB 是追加全部历史还是覆盖窗口？删除会话为什么涉及两个数据库？

读完可看 `ChatSessionServiceTest`，用越权和删除场景检查自己的理解。

### 第六步 学财报正文分析

顺序：`FinancialReportController.reports/analyze` → `FinancialReportService.listReports/analyze` → `FinancialReportPdfService.read` → `extractPages` → `selectEvidence` → `FinancialReportService.analyzeEvidence` → `FinancialReportAnalysis/FinancialReportAnalysisRepository`。

先抓主线：收藏股票 → 公告列表 → PDF → 提取文字 → 选段 → 模型分析 → 保存报告。第二遍再研究机构 ID 查询、公告去重和链接构造。

自查：为什么限制 PDF 下载域名？为什么要记录实际页码？正文读取失败为什么不保存模板报告？选段分析和全文审阅有什么区别？

### 第七步 学 RAG 入库 再学 RAG 问答

先读 `RagProperties`，搞清楚聊天模型、Embedding 模型和 Qdrant 是三个不同角色。然后按两条链路阅读。

索引链路：

```text
app.js/handleReportRagQuestion（首次提问）
  → ReportRagController.index
  → ReportRagService.index
  → FinancialReportService.listReports（核验所选公告）
  → FinancialReportPdfService.readPages（选段前的全部可提取正文）
  → ReportChunker.split
  → RagVectorClient.embed / upsert
  → ReportDocumentRepository（标记 READY）
```

问答链路：

```text
ReportRagController.ask
  → ReportRagService.ask / owned
  → RagVectorClient.embed（问题向量）
  → RagVectorClient.search（用户、文档、索引代次过滤）
  → 未命中：直接返回依据不足
  → 命中：ChatModel.chat
  → validateCitations / Source
  → app.js 展示回答、片段和原文链接
```

目标：能解释分块、重叠、向量维度、相似度阈值、Top-K、元数据过滤、索引复用和失败状态。注意 `ReportDocumentRepository` 保存的是索引管理信息，不是向量本身。

练习：拿一个你能在原文找到答案的问题，断点查看检索前后的数据；若检索为空，区分原文缺失、过滤范围、相似度阈值等原因。不要为了有回答就直接取消所有限制。

当前 RAG 是单份财报独立提问，不自动继承普通聊天的 MongoDB 记忆；“那上一年呢”并不会自动切换报告。没有 OCR、混合检索、重排序或自动追问改写。

### 第八步 最后读今日市场

顺序：`MarketController` → `MarketService.getTodayMarket → MarketQuoteService` → `indices/bars/safeFetchNews` → `buildAiInsight` → `answerMarketQuestion` → 各 fallback 方法。

这部分较长，是因为不同外部来源的数据格式和异常处理多。先理解输入输出与失败路径，再看 HTTP 请求和 JSON 字段细节。

自查：数据源失败和模型失败分别怎么处理？行情不可用时如何展示空状态？市场问答拿到的页面快照和实时重新查询有何区别？

## 测试怎么使用

测试统一放在 `src/test`。它们不是启动项目的前置步骤，第一遍学习可以跳过，第二遍用于检查边界情况。

| 测试 | 验证什么 |
| --- | --- |
| service/ChatSessionServiceTest | 会话用户隔离、消息读取与删除 |
| service/FinancialReportPdfServiceTest | PDF 来源限制、文字提取与选段 |
| service/FinancialReportServiceTest | 正文送入模型、失败不保存假报告 |
| rag/ReportChunkerTest | 分块边界、页码保留、引用编号校验 |
| rag/RagVectorClientTest | 向量响应、维度与集合检查、过滤条件 |
| rag/ReportRagServiceTest | 索引复用、归属、空检索、失败重试 |
| js/report-rag-ui.test.cjs | 页面索引复用、来源渲染、退出后的旧请求隔离 |
| service/MarketQuoteServiceTest | 行情解析、双源切换、空数据、无效价格、日期顺序及交易所代码 |
| js/market-ui.test.cjs | 行情空状态、来源和时间、请求失败及过期响应隔离 |

```powershell
mvn -s settings-local.xml test
node --test src/test/js/*.test.cjs
```

这些测试使用模拟依赖或本机模拟 HTTP 服务，不是实际模型回答质量评估。不要把测试通过当成财报分析准确率。

## 本次清理范围

- 删除 `target` 根目录下 18 个旧日志、一次性修改/验证脚本和历史 JSON 校验快照。
- 删除只断言固定模板文案的 `FinanceToolsTest`，以及对应旧编译类和测试报告。
- 将根目录的 JS 测试移入 `src/test/js`，修正它读取 app.js 的相对路径和文档命令。
- 删除空的 `scripts`、`tests`、`.mvn` 目录。
- 保留实际业务类、配置、数据库相关文件、有效回归测试、Maven 缓存及 IDEA/Git 信息。

先把第三步“添加收藏”从头到尾讲清楚，再进入 AI 部分。你需要理解的是数据和调用如何流动，而不是记住所有类名。
