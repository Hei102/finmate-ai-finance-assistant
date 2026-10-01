# FinMate 财报 RAG 使用说明

财报页面新增“基于原文提问”。首次提问核验公告来源、下载 PDF、提取全部可读页面、分块并建立向量索引；后续问题检索同一份财报，回答下方展示真实检索片段和 PDF 页码链接。

## 启动

1. 保持项目原有 MySQL、MongoDB 和聊天模型配置可用。
2. 如果应用在本机直接运行，只需要用 Docker 启动 Qdrant：

```powershell
docker compose -f compose.rag.yaml up -d
```

Qdrant 仅映射本机 6333 端口，数据保存在 Docker 命名卷。已有 Qdrant 服务可跳过本步骤。

如果希望应用、MySQL、MongoDB 和 Qdrant 全部由 Docker 管理，请按 [README 的 Docker 一键启动说明](README.md#docker-一键启动) 使用根目录的 `compose.yaml`，无需再单独执行本步骤。

3. 在启动后端的同一个 PowerShell 窗口设置：

```powershell
$env:FINMATE_RAG_ENABLED = 'true'
$ragKey = Read-Host '输入百炼 Embedding API Key' -AsSecureString
$env:FINMATE_RAG_EMBEDDING_API_KEY = [System.Net.NetworkCredential]::new('', $ragKey).Password
mvn spring-boot:run
```

密钥只进入当前进程环境，不写入源代码；关闭窗口后需重新配置。该 Key 必须有对应地域模型的调用权限。IDE 启动时应在 Run Configuration 中设置相同变量。

4. 登录 FinMate，收藏一只 A 股股票，打开“财报分析”，选中一份公告，在“基于原文提问”中提交具体问题。

首次准备可能耗时几分钟，期间不要重复提交。原有“生成分析报告”功能仍使用原来的全文选段分析流程，不依赖 Qdrant。

## 可选环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| FINMATE_RAG_QDRANT_URL | http://localhost:6333 | Qdrant REST 地址 |
| FINMATE_RAG_QDRANT_API_KEY | 空 | 云服务认证密钥 |
| FINMATE_RAG_COLLECTION | finmate_reports_v1 | 集合名称 |
| FINMATE_RAG_EMBEDDING_URL | https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings | 默认中国内地接口，跨地域时需同时调整地址和 Key |
| FINMATE_RAG_EMBEDDING_MODEL | text-embedding-v4 | 文档和问题共用此模型 |
| FINMATE_RAG_DIMENSIONS | 1024 | 必须与集合维度一致 |
| FINMATE_RAG_MIN_SCORE | 0.45 | 初始阈值，需通过实际财报问题校准 |

更换向量模型时请使用新的集合名称并重新索引。系统会检查索引配置版本；相同维度但不同模型的向量同样不能混用。

## 实现位置

- `rag/ReportChunker`：每页内最多 800 个字符，重叠 100 个字符，优先换行处切分。
- `rag/RagVectorClient`：通义 Embedding 与 Qdrant 官方 REST 协议，按批次写入并校验返回维度；问答生成复用现有 LangChain4j ChatModel。
- `rag/ReportRagService`：核验公告、建立索引、校验用户归属、检索和生成回答。
- `rag/ReportDocument`：MySQL 文档、内容哈希、索引配置与状态。现有 `ddl-auto=update` 会创建 `rag_report_document` 表。
- `controller/ReportRagController`：三个登录后使用的接口。

Java 路径前缀为 `src/main/java/com/zth/java/ai/langchain4j/`。

| 接口 | 请求 |
| --- | --- |
| POST /api/financial-reports/rag/index | `{"symbol":"600519","reportId":"服务端公告ID"}` |
| GET /api/financial-reports/rag/documents/{id} | 查询当前用户索引状态 |
| POST /api/financial-reports/rag/documents/{id}/questions | `{"question":"本报告期经营现金流发生了什么变化？"}` |

## 范围与限制

- 当前实现单份财报独立问题，暂不包含追问改写、跨财报检索、通用知识库上传、OCR、混合检索或重排序。
- PDF 的全部可提取正文入库，不使用原有 48,000 字符选段作为索引来源。每次最多检索 5 个片段，未命中时不调用生成模型。
- PDFBox 提取表格可能丢失列关系，片段切分也可能分离表头。回答展示核验提醒，不能视为结构化财务数据提取。
- 第一版为单机同步索引，同一时间只处理一份文档。每批 10 个片段，批间检查 8 分钟索引预算；单次外部请求最多等待 60 秒。部署反向代理时须适配长请求，生产环境宜改为持久化后台任务。
- READY 状态才允许检索。失败会保存 FAILED 并尝试删除本次向量；清理失败的代次也不会被检索。重启后遗留的 INDEXING 状态可再次提交重建。仅单进程使用，尚无跨实例任务锁。
- 相同用户与公告 URL 的已完成索引会复用，不每次重下原文；未自动检测同 URL 文件替换。内容哈希用于记录本次解析内容。索引配置变更会生成新代次，旧代次不会再命中，但不会自动清理旧集合。
- 来源列表由后端真实检索结果构造，模型数字引用会校验范围；这不等于验证每句话与引用的语义一致，仍需人工评估。
- 问答结果目前展示于页面，不写入普通聊天记录或财报报告历史。

## 验证

```powershell
mvn test
node --check src/main/resources/static/app.js
node --test src/test/js/report-rag-ui.test.cjs
```

自动化测试覆盖分块、来源引用、用户隔离、失败重试、空检索，以及本机模拟 HTTP 服务下的 Embedding 返回顺序、维度校验和 Qdrant 过滤条件。它们不消耗真实模型额度，也不能替代真实服务联调。

真实服务配置完成后，至少核验：已知指标的正确页面、无答案问题、切换不同公司/年份、删除收藏后拒绝访问、重复提问不重新索引。建议记录 20–30 个问题的预期页码、召回情况、回答依据和耗时后再调节阈值。

参考：[Qdrant 查询协议](https://api.qdrant.tech/api-reference/search/query-points)、[通义 Embedding API](https://www.alibabacloud.com/help/en/model-studio/text-embedding-synchronous-api)。
