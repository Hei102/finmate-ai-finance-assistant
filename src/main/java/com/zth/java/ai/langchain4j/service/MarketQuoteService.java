package com.zth.java.ai.langchain4j.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zth.java.ai.langchain4j.dto.MarketTodayResponse.KLinePoint;
import com.zth.java.ai.langchain4j.dto.MarketTodayResponse.MarketIndexSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 真实行情双源读取；任何源失败都记录原因，不制造行情数据。 */
@Service
public class MarketQuoteService {
    private static final Logger log = LoggerFactory.getLogger(MarketQuoteService.class);
    private final ObjectMapper mapper;
    private final HttpClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public MarketQuoteService(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(4)).build());
    }

    MarketQuoteService(ObjectMapper mapper, HttpClient client) {
        this.mapper = mapper;
        this.client = client;
    }

    public record Bars(List<KLinePoint> points, String source, String name) {}

    public List<MarketIndexSnapshot> indices() {
        try {
            return tencentIndices();
        } catch (Exception e) {
            failed("腾讯指数", e);
        }
        try {
            return eastmoneyIndices();
        } catch (Exception e) {
            failed("东方财富指数", e);
            return List.of();
        }
    }

    public Bars bars(String symbol) {
        String code = normalizeSymbol(symbol);
        try {
            return tencentBars(code);
        } catch (Exception e) {
            failed("腾讯K线 " + code, e);
        }
        try {
            return eastmoneyBars(code);
        } catch (Exception e) {
            failed("东方财富K线 " + code, e);
            return new Bars(List.of(), "不可用", code);
        }
    }

    // 裸 000001 保持页面原有的上证指数语义；sz000001 明确表示深圳股票。
    static String normalizeSymbol(String value) {
        String code = value == null || value.isBlank() ? "sh000001" : value.trim().toLowerCase(Locale.ROOT);
        if (code.matches("(sh|sz|bj)\\d{6}")) return code;
        if (!code.matches("\\d{6}")) throw new IllegalArgumentException("请输入六位代码或带 sh/sz/bj 前缀的代码");
        if (code.equals("000001") || code.startsWith("6") || code.startsWith("5")) return "sh" + code;
        if (code.startsWith("4") || code.startsWith("8") || code.startsWith("92")) return "bj" + code;
        return "sz" + code;
    }

    private List<MarketIndexSnapshot> tencentIndices() throws Exception {
        String body = get("https://qt.gtimg.cn/q=sh000001,sz399001,sz399006", "GB18030");
        var matcher = Pattern.compile("v_(sh000001|sz399001|sz399006)=\"([^\"]+)\"").matcher(body);
        List<MarketIndexSnapshot> result = new ArrayList<>();
        while (matcher.find()) {
            String[] f = matcher.group(2).split("~", -1);
            if (f.length < 38) throw new IllegalStateException("指数字段不完整");
            positive(f[3]);
            number(f[31]); number(f[32]);
            String time = LocalDateTime.parse(f[30], DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toString();
            result.add(new MarketIndexSnapshot(f[2], f[1], f[3], f[31], f[32], f[6],
                    Double.toString(number(f[37]) * 10000), "腾讯财经", time));
        }
        requireIndices(result);
        return result;
    }

    private List<MarketIndexSnapshot> eastmoneyIndices() throws Exception {
        JsonNode root = json("https://push2.eastmoney.com/api/qt/ulist.np/get?fltt=2&secids=1.000001,0.399001,0.399006&fields=f12,f14,f2,f3,f4,f5,f6,f124");
        List<MarketIndexSnapshot> result = new ArrayList<>();
        for (JsonNode f : root.path("data").path("diff")) {
            positive(f.path("f2").asText());
            number(f.path("f3").asText()); number(f.path("f4").asText());
            long timestamp = f.path("f124").asLong(0);
            String time = timestamp > 0 ? Instant.ofEpochSecond(timestamp).atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime().toString() : null;
            result.add(new MarketIndexSnapshot(f.path("f12").asText(), f.path("f14").asText(),
                    f.path("f2").asText(), f.path("f4").asText(), f.path("f3").asText(),
                    f.path("f5").asText(), f.path("f6").asText(), "东方财富", time));
        }
        requireIndices(result);
        return result;
    }

    private Bars tencentBars(String code) throws Exception {
        JsonNode root = json("https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=" + code + ",day,,,90,qfq");
        JsonNode data = root.path("data").path(code);
        JsonNode rows = data.path("qfqday");
        if (!rows.isArray() || rows.isEmpty()) rows = data.path("day");
        List<KLinePoint> points = new ArrayList<>();
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 6) throw new IllegalStateException("K线字段不完整");
            points.add(point(row.get(0).asText(), row.get(1).asText(), row.get(2).asText(),
                    row.get(3).asText(), row.get(4).asText(), row.get(5).asText(), null));
        }
        String name = data.path("qt").path(code).path(1).asText(code);
        return new Bars(validateBars(points), "腾讯财经", name);
    }

    private Bars eastmoneyBars(String code) throws Exception {
        String secid = (code.startsWith("sh") ? "1." : "0.") + code.substring(2);
        JsonNode data = json("https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=" + secid
                + "&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57&klt=101&fqt=1&lmt=90&end=20500101").path("data");
        List<KLinePoint> points = new ArrayList<>();
        for (JsonNode row : data.path("klines")) {
            String[] f = row.asText().split(",", -1);
            if (f.length < 7) throw new IllegalStateException("K线字段不完整");
            points.add(point(f[0], f[1], f[2], f[3], f[4], f[5], f[6]));
        }
        return new Bars(validateBars(points), "东方财富", data.path("name").asText(code));
    }

    private KLinePoint point(String date, String open, String close, String high, String low, String volume, String amount) {
        LocalDate.parse(date);
        double o = positive(open), c = positive(close), h = positive(high), l = positive(low);
        if (h < Math.max(o, c) || l > Math.min(o, c) || l > h) throw new IllegalStateException("K线价格范围异常");
        double v = number(volume);
        Double a = amount == null ? null : number(amount);
        if (v < 0 || (a != null && a < 0)) throw new IllegalStateException("成交数据异常");
        return new KLinePoint(date, o, c, h, l, v, a);
    }

    private List<KLinePoint> validateBars(List<KLinePoint> points) {
        if (points.isEmpty()) throw new IllegalStateException("K线为空");
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).getDate().compareTo(points.get(i - 1).getDate()) <= 0)
                throw new IllegalStateException("K线日期重复或乱序");
        }
        return List.copyOf(points.subList(Math.max(0, points.size() - 90), points.size()));
    }

    private void requireIndices(List<MarketIndexSnapshot> points) {
        if (points.size() != 3 || points.stream().map(MarketIndexSnapshot::getSymbol).distinct().count() != 3
                || !points.stream().map(MarketIndexSnapshot::getSymbol).toList().containsAll(List.of("000001", "399001", "399006")))
            throw new IllegalStateException("指数数据缺失");
    }

    private double positive(String value) {
        double result = number(value);
        if (result <= 0) throw new IllegalStateException("行情价格无效");
        return result;
    }

    private double number(String value) {
        double result = Double.parseDouble(value);
        if (!Double.isFinite(result)) throw new IllegalStateException("行情数值无效");
        return result;
    }

    private JsonNode json(String url) throws Exception { return mapper.readTree(get(url, "UTF-8")); }

    private String get(String url, String charset) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6))
                .header("User-Agent", "Mozilla/5.0").header("Referer", url.contains("eastmoney")
                        ? "https://quote.eastmoney.com/" : "https://gu.qq.com/").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(Charset.forName(charset)));
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        return response.body();
    }

    private void failed(String source, Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        log.warn("行情源 {} 请求失败: {}", source, e.toString());
    }
}
