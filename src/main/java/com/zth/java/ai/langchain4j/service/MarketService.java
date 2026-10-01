package com.zth.java.ai.langchain4j.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zth.java.ai.langchain4j.dto.MarketChatRequest;
import com.zth.java.ai.langchain4j.dto.MarketChatResponse;
import com.zth.java.ai.langchain4j.dto.MarketTodayResponse;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 组织今日市场数据和页面追问；外部行情或模型失败时保留原有规则降级。 */
@Service
public class MarketService {

    private static final String DISCLAIMER = "仅用于学习和研究展示，不构成投资建议。";

    private final ObjectMapper objectMapper;
    private final ChatModel chatModel;
    private final MarketQuoteService quoteService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    public MarketService(ObjectMapper objectMapper, @Qualifier("openAiChatModel") ChatModel chatModel, MarketQuoteService quoteService) {
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.quoteService = quoteService;
    }

    /** 依次加载指数、K 线和新闻，再组装市场简报及风险提示。 */
    public MarketTodayResponse getTodayMarket(String symbol) {
        String normalizedSymbol = MarketQuoteService.normalizeSymbol(symbol);
        List<String> warnings = new ArrayList<>();
        List<MarketTodayResponse.MarketIndexSnapshot> indices = quoteService.indices();
        MarketQuoteService.Bars bars = quoteService.bars(normalizedSymbol);
        List<MarketTodayResponse.KLinePoint> kLines = bars.points();
        if (indices.isEmpty()) warnings.add("指数行情暂时不可用");
        if (kLines.isEmpty()) warnings.add("K线数据暂时不可用");
        List<MarketTodayResponse.MarketNewsItem> news = safeFetchNews(warnings);
        List<MarketTodayResponse.StockIdea> stockIdeas = buildStockIdeas(indices, news);
        String watchedName = bars.name();
        String aiInsight = buildAiInsight(indices, news, stockIdeas, warnings);

        return new MarketTodayResponse(
                LocalDateTime.now(),
                normalizedSymbol,
                watchedName,
                warnings.isEmpty() ? "行情数据已加载" : String.join("；", warnings),
                aiInsight,
                DISCLAIMER,
                indices,
                kLines,
                news,
                stockIdeas,
                buildRiskChecklist(),
                bars.source()
        );
    }

    /** 基于页面快照回答追问；模型失败时返回规则回答。 */
    public MarketChatResponse answerMarketQuestion(MarketChatRequest request) {
        MarketTodayResponse context = request.getContext();
        if (context == null) {
            context = getTodayMarket(request.getSymbol());
        }

        String question = request.getQuestion().trim();
        String contextText = buildMarketContextText(context);
        try {
            String prompt = """
                    你是 FinMate 的谨慎财经研究助手。用户正在“今日市场”页面追问问题。
                    你必须只基于【页面上下文】回答；如果上下文里没有数据，就明确说“当前页面没有展示这项数据”。
                    不要承诺收益，不要给出“买入/卖出/必涨”等交易指令。可以给研究思路、风险点和需要继续验证的数据。

                    【页面上下文】
                    %s

                    【用户问题】
                    %s

                    请用中文回答，结构清楚，控制在 5 条以内。
                    """.formatted(contextText, question);
            return new MarketChatResponse(chatModel.chat(prompt), context.getWatchedSymbol(), context.getGeneratedAt(), "AI");
        } catch (Exception exception) {
            return new MarketChatResponse(
                    buildRuleBasedMarketAnswer(question, context),
                    context.getWatchedSymbol(),
                    context.getGeneratedAt(),
                    "规则版"
            );
        }
    }

    /** 加载新闻，失败或无内容时返回系统研究框架。 */
    private List<MarketTodayResponse.MarketNewsItem> safeFetchNews(List<String> warnings) {
        try {
            List<MarketTodayResponse.MarketNewsItem> news = fetchNews();
            if (!news.isEmpty()) {
                return news;
            }
            warnings.add("新闻列表为空");
        } catch (Exception exception) {
            warnings.add("新闻数据暂时不可用");
        }
        return fallbackNews();
    }

    /** 把页面快照转换为模型可阅读的上下文。 */
    private String buildMarketContextText(MarketTodayResponse context) {
        List<MarketTodayResponse.MarketIndexSnapshot> indices = safeList(context.getIndices());
        List<MarketTodayResponse.KLinePoint> kLines = safeList(context.getKLines());
        List<MarketTodayResponse.MarketNewsItem> news = safeList(context.getNews());
        List<MarketTodayResponse.StockIdea> stockIdeas = safeList(context.getStockIdeas());
        List<String> risks = safeList(context.getRiskChecklist());

        StringBuilder builder = new StringBuilder();
        builder.append("生成时间：").append(context.getGeneratedAt()).append('\n');
        builder.append("关注标的：").append(context.getWatchedName()).append(" / ").append(context.getWatchedSymbol()).append('\n');
        builder.append("数据状态：").append(context.getDataStatus()).append('\n');
        builder.append("AI简报：").append(context.getAiInsight()).append('\n');
        builder.append("指数：").append(joinIndexContext(indices)).append('\n');
        builder.append("K线摘要：").append(buildKLineSummary(kLines)).append('\n');
        builder.append("最近K线：").append(joinRecentKLines(kLines)).append('\n');
        builder.append("重大新闻：").append(joinNewsContext(news)).append('\n');
        builder.append("观察清单：").append(joinStockIdeas(stockIdeas)).append('\n');
        builder.append("风险提醒：").append(String.join("；", risks)).append('\n');
        builder.append("免责声明：").append(context.getDisclaimer());
        return builder.toString();
    }

    /** 模型不可用时，按问题关键词组织现有页面数据。 */
    private String buildRuleBasedMarketAnswer(String question, MarketTodayResponse context) {
        String key = normalizeText(question);
        List<MarketTodayResponse.KLinePoint> kLines = safeList(context.getKLines());
        List<MarketTodayResponse.MarketNewsItem> news = safeList(context.getNews());
        List<MarketTodayResponse.StockIdea> ideas = safeList(context.getStockIdeas());
        List<String> risks = safeList(context.getRiskChecklist());

        String prefix = "AI 暂时不可用，我先基于当前页面数据给你一个规则版回答：\n";
        if (key.contains("新闻") || key.contains("消息") || key.contains("影响")) {
            return prefix + "1. 当前页面新闻重点：" + joinNewsContext(news) + "\n2. 观察方式：先判断新闻影响的是宏观流动性、行业景气，还是单一公司情绪。\n3. 这不是交易建议，后续还要看成交额和指数方向是否共振。";
        }
        if (key.contains("k线") || key.contains("走势") || key.contains("技术")) {
            return prefix + "1. K 线摘要：" + buildKLineSummary(kLines) + "\n2. 最近数据：" + joinRecentKLines(kLines) + "\n3. 单看 K 线不够，还要和新闻、成交量以及大盘风格一起验证。";
        }
        if (key.contains("推荐") || key.contains("股票") || key.contains("观察") || key.contains("清单")) {
            return prefix + "1. 当前页面观察清单：" + joinStockIdeas(ideas) + "\n2. 这些只适合继续研究，不等同于买入建议。\n3. 优先确认基本面、估值和风险是否匹配。";
        }
        if (key.contains("风险") || key.contains("注意") || key.contains("避雷")) {
            return prefix + "1. 当前页面风险提醒：" + String.join("；", risks) + "\n2. 如果风险开始影响收入、利润率或现金流，就要降低结论强度。\n3. 不要只根据单日涨跌做判断。";
        }
        return prefix + "1. 关注标的：" + context.getWatchedName() + " / " + context.getWatchedSymbol()
                + "\n2. 指数：" + joinIndexContext(safeList(context.getIndices()))
                + "\n3. K 线：" + buildKLineSummary(kLines)
                + "\n4. 新闻：" + joinNewsContext(news)
                + "\n5. 结论只能用于研究辅助，不构成投资建议。";
    }

    private String joinIndexContext(List<MarketTodayResponse.MarketIndexSnapshot> indices) {
        if (indices.isEmpty()) {
            return "当前页面没有展示指数数据";
        }
        return indices.stream()
                .map(item -> "%s %s，涨跌 %s / %s%%".formatted(item.getName(), item.getLatest(), item.getChange(), item.getChangePercent()))
                .toList()
                .toString();
    }

    /** 计算区间涨跌、最高最低价和最近成交量。 */
    private String buildKLineSummary(List<MarketTodayResponse.KLinePoint> kLines) {
        if (kLines.isEmpty()) {
            return "当前页面没有展示K线数据";
        }
        MarketTodayResponse.KLinePoint first = kLines.get(0);
        MarketTodayResponse.KLinePoint last = kLines.get(kLines.size() - 1);
        double highest = kLines.stream().mapToDouble(MarketTodayResponse.KLinePoint::getHigh).max().orElse(0);
        double lowest = kLines.stream().mapToDouble(MarketTodayResponse.KLinePoint::getLow).min().orElse(0);
        double change = first.getClose() == 0 ? 0 : (last.getClose() - first.getClose()) / first.getClose() * 100;
        return "%s 至 %s 共 %d 根日线，收盘从 %.2f 到 %.2f，区间涨跌 %.2f%%，最高 %.2f，最低 %.2f，最近成交量 %.0f"
                .formatted(first.getDate(), last.getDate(), kLines.size(), first.getClose(), last.getClose(), change, highest, lowest, last.getVolume());
    }

    private String joinRecentKLines(List<MarketTodayResponse.KLinePoint> kLines) {
        if (kLines.isEmpty()) {
            return "当前页面没有展示K线数据";
        }
        return kLines.stream()
                .skip(Math.max(0, kLines.size() - 8))
                .map(item -> "%s 开%.2f 收%.2f 高%.2f 低%.2f".formatted(item.getDate(), item.getOpen(), item.getClose(), item.getHigh(), item.getLow()))
                .toList()
                .toString();
    }

    private String joinNewsContext(List<MarketTodayResponse.MarketNewsItem> news) {
        if (news.isEmpty()) {
            return "当前页面没有展示新闻数据";
        }
        return news.stream()
                .limit(8)
                .map(item -> "%s【%s】：%s".formatted(item.getImpactTag(), item.getSource(), item.getTitle()))
                .toList()
                .toString();
    }

    private String joinStockIdeas(List<MarketTodayResponse.StockIdea> stockIdeas) {
        if (stockIdeas.isEmpty()) {
            return "当前页面没有展示观察清单";
        }
        return stockIdeas.stream()
                .map(item -> "%s/%s：%s，风险：%s".formatted(item.getName(), item.getSymbol(), item.getReason(), item.getRisk()))
                .toList()
                .toString();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    /** 优先使用快讯接口，无结果时尝试财经新闻网页。 */
    private List<MarketTodayResponse.MarketNewsItem> fetchNews() throws Exception {
        // 东方财富快讯接口偶尔会调整字段；快讯失败时再退到财经新闻 HTML 列表，保证页面有可用内容。
        List<MarketTodayResponse.MarketNewsItem> news = tryFetchEastmoneyFastNews();
        if (!news.isEmpty()) {
            return news;
        }

        return tryFetchEastmoneyFinanceNews();
    }

    private List<MarketTodayResponse.MarketNewsItem> tryFetchEastmoneyFastNews() {
        try {
            return fetchEastmoneyFastNews();
        } catch (Exception exception) {
            return List.of();
        }
    }

    /** 兼容快讯接口的多种字段名称，最多返回八条新闻。 */
    private List<MarketTodayResponse.MarketNewsItem> fetchEastmoneyFastNews() throws Exception {
        URI uri = UriComponentsBuilder.fromUriString("https://np-listapi.eastmoney.com/comm/web/getFastNewsList")
                .queryParam("client", "web")
                .queryParam("biz", "web_news_col")
                .queryParam("fastColumn", "102")
                .queryParam("sortEnd", "0")
                .queryParam("pageSize", "12")
                .queryParam("pageIndex", "1")
                .queryParam("req_trace", UUID.randomUUID().toString().replace("-", ""))
                .build(true)
                .toUri();
        JsonNode root = getJson(uri, "https://finance.eastmoney.com/");
        JsonNode list = firstArray(
                root.path("data").path("fastNewsList"),
                root.path("data").path("list"),
                root.path("data").path("items")
        );
        List<MarketTodayResponse.MarketNewsItem> items = new ArrayList<>();
        if (list.isArray()) {
            for (JsonNode node : list) {
                String title = firstText(node, "title", "newsTitle", "showTitle");
                if (title == null || title.isBlank()) {
                    continue;
                }
                String summary = firstText(node, "summary", "digest", "content", "simtitle");
                items.add(new MarketTodayResponse.MarketNewsItem(
                        title,
                        trim(summary == null ? title : summary, 120),
                        firstText(node, "url", "newsUrl", "shareUrl"),
                        firstText(node, "source", "mediaName", "sourceName"),
                        firstText(node, "showTime", "publishTime", "dateTime"),
                        classifyImpact(title + " " + Objects.toString(summary, ""))
                ));
            }
        }
        return items.stream().limit(8).toList();
    }

    private List<MarketTodayResponse.MarketNewsItem> tryFetchEastmoneyFinanceNews() {
        try {
            return fetchEastmoneyFinanceNews();
        } catch (Exception exception) {
            return List.of();
        }
    }

    /** 从新闻网页提取标题和链接，并过滤无关链接、去重。 */
    private List<MarketTodayResponse.MarketNewsItem> fetchEastmoneyFinanceNews() throws Exception {
        URI uri = URI.create("https://finance.eastmoney.com/a/czqyw.html");
        String html = getText(uri, "https://finance.eastmoney.com/");
        Pattern pattern = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        Set<String> seenTitles = new LinkedHashSet<>();
        List<MarketTodayResponse.MarketNewsItem> items = new ArrayList<>();
        while (matcher.find() && items.size() < 8) {
            String title = cleanText(matcher.group(2));
            if (!isUsefulNewsTitle(title) || !seenTitles.add(title)) {
                continue;
            }
            String url = absolutizeUrl(matcher.group(1), "https://finance.eastmoney.com");
            items.add(new MarketTodayResponse.MarketNewsItem(
                    title,
                    trim(title, 120),
                    url,
                    "东方财富",
                    LocalDateTime.now().toString(),
                    classifyImpact(title)
            ));
        }
        return items;
    }

    /** 用模型生成市场简报，失败时保留规则版内容。 */
    private String buildAiInsight(List<MarketTodayResponse.MarketIndexSnapshot> indices,
                                  List<MarketTodayResponse.MarketNewsItem> news,
                                  List<MarketTodayResponse.StockIdea> stockIdeas,
                                  List<String> warnings) {
        try {
            String prompt = """
                    你是一个谨慎的股票研究助理。请基于下面的指数表现、新闻和观察清单，输出中文市场简报。
                    要求：
                    1. 不要承诺收益，不要说“必买”。
                    2. 用 4 条以内短句。
                    3. 说明适合继续跟踪的方向和主要风险。
                    指数：%s
                    新闻：%s
                    观察清单：%s
                    """.formatted(indices, news, stockIdeas);
            return chatModel.chat(prompt);
        } catch (Exception exception) {
            warnings.add("AI简报暂时不可用，已使用规则版观察清单");
            return "今日重点看三件事：指数方向、成交额变化、政策和产业新闻是否共振。观察清单只用于继续研究，先看基本面和风险，再考虑交易。";
        }
    }

    /** 按页面信息生成研究观察清单，不是实时选股策略。 */
    private List<MarketTodayResponse.StockIdea> buildStockIdeas(List<MarketTodayResponse.MarketIndexSnapshot> indices,
                                                                List<MarketTodayResponse.MarketNewsItem> news) {
        // 这里先做规则版观察清单：它只是研究入口，不是自动荐股或交易信号。
        String text = (news == null ? List.<MarketTodayResponse.MarketNewsItem>of() : news).stream()
                .map(item -> item.getTitle() + " " + item.getSummary())
                .reduce("", (a, b) -> a + " " + b)
                .toLowerCase(Locale.ROOT);
        List<MarketTodayResponse.StockIdea> ideas = new ArrayList<>();

        if (containsAny(text, "ai", "人工智能", "算力", "芯片", "半导体")) {
            ideas.add(new MarketTodayResponse.StockIdea("002230", "科大讯飞", "AI应用", "新闻中出现 AI、算力或半导体线索，适合继续跟踪订单和利润兑现。", "估值波动大，商业化进度不及预期会压制表现。"));
            ideas.add(new MarketTodayResponse.StockIdea("688981", "中芯国际", "半导体", "半导体链条对政策和国产替代敏感，适合观察景气修复。", "周期性强，资本开支和毛利率压力需要跟踪。"));
        }
        if (containsAny(text, "新能源", "电池", "储能", "光伏", "锂")) {
            ideas.add(new MarketTodayResponse.StockIdea("300750", "宁德时代", "新能源电池", "新能源和储能仍是高关注产业线，龙头公司适合作为行业温度计。", "价格竞争、原材料波动和海外政策扰动较大。"));
        }
        if (containsAny(text, "银行", "券商", "金融", "利率", "降准", "地产")) {
            ideas.add(new MarketTodayResponse.StockIdea("600030", "中信证券", "券商金融", "市场成交和风险偏好变化会影响券商弹性。", "行情依赖度高，市场缩量时业绩和估值都可能承压。"));
            ideas.add(new MarketTodayResponse.StockIdea("600036", "招商银行", "银行", "银行适合观察利率、地产和资产质量预期变化。", "净息差和信用风险仍是核心变量。"));
        }

        ideas.add(new MarketTodayResponse.StockIdea("510300", "沪深300ETF", "宽基配置", "如果个股判断不清晰，宽基 ETF 更适合做市场整体观察。", "无法规避系统性下跌。"));
        ideas.add(new MarketTodayResponse.StockIdea("515180", "中证红利ETF", "红利风格", "弱波动或防御场景下，红利资产常被市场关注。", "高股息不等于低风险，风格切换会影响表现。"));

        return ideas.stream()
                .collect(ArrayList::new, (list, idea) -> {
                    boolean exists = list.stream().anyMatch(existing -> existing.getSymbol().equals(idea.getSymbol()));
                    if (!exists && list.size() < 5) {
                        list.add(idea);
                    }
                }, ArrayList::addAll);
    }

    /** 返回页面固定展示的研究风险提示。 */
    private List<String> buildRiskChecklist() {
        return List.of(
                "先确认新闻是否来自权威来源，避免只看标题交易。",
                "关注成交额是否放大，单日涨跌不代表趋势已经确认。",
                "推荐列表只是观察清单，不等同于买入建议。",
                "如果 API Key 未配置，AI 简报会自动降级为规则版。"
        );
    }

    private JsonNode getJson(URI uri) throws Exception {
        return getJson(uri, null);
    }

    private JsonNode getJson(URI uri, String referer) throws Exception {
        return objectMapper.readTree(getText(uri, referer));
    }

    /** 统一发送市场数据 GET 请求，设置超时并检查响应状态。 */
    private String getText(URI uri, String referer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json,text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET();
        if (referer != null && !referer.isBlank()) {
            builder.header("Referer", referer);
        }
        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** 外部新闻不可用时，返回通用研究提示。 */
    private List<MarketTodayResponse.MarketNewsItem> fallbackNews() {
        return List.of(
                new MarketTodayResponse.MarketNewsItem("关注宏观政策、流动性和成交额变化", "实时新闻接口暂时不可用，先用宏观、产业、资金三条线做复盘。", "", "系统框架", LocalDateTime.now().toString(), "宏观"),
                new MarketTodayResponse.MarketNewsItem("关注新能源、AI、金融地产等高敏感方向", "这些方向对政策、利率和产业订单变化更敏感，适合做观察列表。", "", "系统框架", LocalDateTime.now().toString(), "行业")
        );
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
    }

    /** 从兼容字段中选择第一个数组节点。 */
    private JsonNode firstArray(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return objectMapper.createArrayNode();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "-" : value.asText("-");
    }

    /** 从候选字段中选出首个有效文本。 */
    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!"-".equals(value) && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /** 去掉新闻 HTML 标签并还原常用转义字符。 */
    private String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isUsefulNewsTitle(String title) {
        if (title == null || title.length() < 8 || title.length() > 90) {
            return false;
        }
        return !containsAny(title, "首页", "股吧", "专题", "视频", "博客", "图片", "直播", "点击查看更多")
                && containsAny(title, "A股", "市场", "股", "证监会", "央行", "财政", "经济", "政策", "人民币",
                "沪指", "深成指", "创业板", "成交", "利率", "AI", "半导体", "新能源", "银行", "券商", "业绩");
    }

    private String absolutizeUrl(String url, String base) {
        if (url == null || url.isBlank()) {
            return "";
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("/")) {
            return base + url;
        }
        return url;
    }

    /** 按关键词给新闻附加影响分类。 */
    private String classifyImpact(String text) {
        if (containsAny(text, "央行", "利率", "降准", "财政", "政策")) {
            return "宏观政策";
        }
        if (containsAny(text, "AI", "人工智能", "芯片", "半导体", "算力")) {
            return "科技成长";
        }
        if (containsAny(text, "新能源", "电池", "储能", "光伏")) {
            return "新能源";
        }
        if (containsAny(text, "银行", "券商", "地产")) {
            return "金融地产";
        }
        return "市场要闻";
    }

    private boolean containsAny(String text, String... keys) {
        if (text == null) {
            return false;
        }
        for (String key : keys) {
            if (text.contains(key.toLowerCase(Locale.ROOT)) || text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String trim(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...";
    }
}
