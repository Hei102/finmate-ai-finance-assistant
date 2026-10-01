package com.zth.java.ai.langchain4j.rag;

import com.zth.java.ai.langchain4j.service.FinancialReportPdfService.PageText;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ReportChunker {
    public record Chunk(int number, int page, String text) { }

    /** 按页切分，优先在换行处结束；保留重叠，不丢弃长文档尾部。 */
    public List<Chunk> split(List<PageText> pages) {
        List<Chunk> chunks = new ArrayList<>();
        for (PageText page : pages) {
            String text = page.text();
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + 800, text.length());
                if (end < text.length()) {
                    int newline = text.lastIndexOf('\n', end - 1);
                    if (newline > start + 400) {
                        end = newline + 1;
                    }
                    if (Character.isHighSurrogate(text.charAt(end - 1))) {
                        end--;
                    }
                }
                String part = text.substring(start, end).strip();
                if (!part.isBlank()) {
                    chunks.add(new Chunk(chunks.size(), page.number(), part));
                }
                if (end == text.length()) {
                    break;
                }
                start = end - 100;
                if (Character.isLowSurrogate(text.charAt(start))) {
                    start++;
                }
            }
        }
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("财报没有可索引的正文");
        }
        return chunks;
    }
}
