package com.zth.java.ai.langchain4j.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** 下载巨潮 PDF，逐页提取文字并控制模型输入长度；不包含向量检索或 OCR。 */
@Service
public class FinancialReportPdfService {
    private static final int MAX_BYTES = 25 * 1024 * 1024;
    private static final int MAX_PAGES = 600;
    private static final int MAX_CONTEXT_CHARS = 48_000;
    private static final List<String> KEYWORDS = List.of(
            "主要会计数据", "主要财务指标", "营业收入", "归属于上市公司股东", "经营活动产生的现金流",
            "管理层讨论", "主营业务", "风险因素", "可能面对的风险", "合并资产负债表", "合并利润表", "合并现金流量表");

    /** 校验来源链接，下载并提取 PDF；失败时转换为可读提示。 */
    public PdfEvidence read(String url) {
        URI uri = validateUrl(url);
        try {
            return extract(download(uri));
        } catch (IOException exception) {
            throw new IllegalArgumentException("财报 PDF 下载或解析失败，请稍后重试或打开原始 PDF 核验", exception);
        }
    }

    // 链接来自前端，必须限定来源，避免服务器被用来访问任意地址。
    /** 仅允许巨潮官方财报路径，阻止任意地址下载。 */
    public URI validateUrl(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"static.cninfo.com.cn".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !uri.getPath().matches("(?i)/finalpage/\\d{4}-\\d{2}-\\d{2}/[a-z0-9_-]+\\.pdf")) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("请选择有效的巨潮资讯财报 PDF 链接", exception);
        }
    }

    /** 限制文件大小和下载时间，并禁止跳转到其他地址。 */
    private byte[] download(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Accept", "application/pdf");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("Unexpected PDF response: " + connection.getResponseCode());
            }
            if (connection.getContentLengthLong() > MAX_BYTES) {
                throw new IllegalArgumentException("财报 PDF 超过 25 MB，请选择较小的报告");
            }
            try (var input = connection.getInputStream(); var output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_BYTES) {
                        throw new IllegalArgumentException("财报 PDF 超过 25 MB，请选择较小的报告");
                    }
                    if (System.nanoTime() > deadline) {
                        throw new IOException("PDF download timed out");
                    }
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    /** 逐页提取文字，保留物理页码，拒绝扫描件和超限文档。 */
    PdfEvidence extract(byte[] bytes) throws IOException {
        ParsedPdf parsed = extractPages(bytes);
        return selectEvidence(parsed.pages(), parsed.totalPages(), MAX_CONTEXT_CHARS);
    }

    /** RAG 使用选段前的全文，各片段仍保留物理页码。 */
    public ParsedPdf readPages(String url) {
        try {
            return extractPages(download(validateUrl(url)));
        } catch (IOException exception) {
            throw new IllegalArgumentException("财报 PDF 下载或解析失败，请稍后重试", exception);
        }
    }

    ParsedPdf extractPages(byte[] bytes) throws IOException {
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F' || bytes[4] != '-') {
            throw new IllegalArgumentException("下载内容不是有效的 PDF 文件");
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent()) {
                throw new IllegalArgumentException("该 PDF 已加密或不允许提取文字，请打开原文核验");
            }
            int totalPages = document.getNumberOfPages();
            if (totalPages > MAX_PAGES) {
                throw new IllegalArgumentException("财报超过 600 页，暂不支持自动分析，请打开原文核验");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<PageText> pages = new ArrayList<>();
            int extractedChars = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
            for (int page = 1; page <= totalPages; page++) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalArgumentException("财报解析耗时过长，请选择较小的报告");
                }
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).replace("\u0000", "").strip();
                extractedChars += text.length();
                if (extractedChars > 2_000_000) {
                    throw new IllegalArgumentException("财报文字量过大，暂不支持自动分析，请打开原文核验");
                }
                if (!text.isBlank()) {
                    pages.add(new PageText(page, text));
                }
            }
            if (pages.stream().mapToInt(page -> page.text().length()).sum() < 80) {
                throw new IllegalArgumentException("PDF 中没有足够的可提取文字，可能为扫描件；当前暂不支持 OCR，请打开原文核验");
            }
            return new ParsedPdf(totalPages, List.copyOf(pages));
        }
    }

    /** 按关键词优先选择页面，再按原始页序拼接，控制输入长度。 */
    PdfEvidence selectEvidence(List<PageText> pages, int totalPages, int budget) {
        List<PageText> ranked = pages.stream()
                .sorted(Comparator.comparingInt(this::score).reversed().thenComparingInt(PageText::number))
                .toList();
        List<PageText> selected = new ArrayList<>();
        int remaining = budget;
        boolean shortened = false;
        for (PageText page : ranked) {
            int overhead = ("【PDF第" + page.number() + "页】\n\n").length();
            if (remaining <= overhead) {
                break;
            }
            int length = Math.min(page.text().length(), remaining - overhead);
            selected.add(new PageText(page.number(), page.text().substring(0, length)));
            shortened |= length < page.text().length();
            remaining -= length + overhead;
        }
        selected.sort(Comparator.comparingInt(PageText::number));
        String context = selected.stream()
                .map(page -> "【PDF第" + page.number() + "页】\n" + page.text() + "\n")
                .collect(Collectors.joining("\n"));
        return new PdfEvidence(totalPages, pages.size(),
                selected.stream().map(PageText::number).toList(),
                shortened || selected.size() < pages.size(), context);
    }

    /** 计算页面优先级；财务和风险内容加分，目录页降权。 */
    private int score(PageText page) {
        int score = page.number() <= 3 ? 1 : 0;
        for (String keyword : KEYWORDS) {
            if (page.text().contains(keyword)) {
                score += 3;
            }
        }
        // 目录只罗列标题，即使命中很多关键词，也不应优先作为分析证据。
        if (page.text().contains("目录") || page.text().contains("目 录")) {
            score -= 20;
        }
        return score;
    }

    /** 单页原文；number 是从 1 开始的 PDF 物理页码。 */
    public record PageText(int number, String text) { }

    public record ParsedPdf(int totalPages, List<PageText> pages) { }

    /** 提供给模型的正文及覆盖范围；truncated 表示没有纳入所有可提取文字。 */
    public record PdfEvidence(int totalPages, int readablePages, List<Integer> includedPages,
                              boolean truncated, String context) {
        /** 说明总页数、选取页码及遗漏范围，避免把选段当作全文。 */
        public String coverage() {
            return "PDF 共 " + totalPages + " 页，提取到文字的页面 " + readablePages + " 页。\n"
                    + "本次提供给模型的 PDF 页码：" + includedPages.stream().map(String::valueOf).collect(Collectors.joining("、"))
                    + "。\n" + (truncated ? "长文档已按财务指标、业务和风险关键词选取片段，部分内容未纳入，不代表全文审阅。"
                    : "已纳入全部可提取文字。")
                    + (readablePages < totalPages ? "部分页面未提取到文字，图片和扫描内容未被分析。" : "")
                    + "页码为 PDF 文件物理页码；表格列、单位和数值仍需结合原文核验。";
        }
    }
}
