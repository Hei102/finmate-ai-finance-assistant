package com.zth.java.ai.langchain4j.tools;

import com.zth.java.ai.langchain4j.service.FinanceKnowledgeService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 提供大模型可调用的财经工具；具体知识和模板由 FinanceKnowledgeService 处理。 */
@Component
@RequiredArgsConstructor
public class FinanceTools {

    private final FinanceKnowledgeService financeKnowledgeService;

    /** 向模型提供财经概念解释工具。 */
    @Tool(
            name = "explain_finance_term",
            value = "Use this tool when the user asks about finance concepts, valuation metrics, fund types, or the difference between common finance terms."
    )
    public String explainFinanceTerm(@P("Finance term or metric name") String term) {
        return financeKnowledgeService.explainFinanceTerm(term);
    }

    /** 向模型提供模拟面试题工具。 */
    @Tool(
            name = "generate_finance_interview_questions",
            value = "Use this tool when the user wants mock interview questions for finance internships, equity research, fund research, or general finance learning."
    )
    public String generateFinanceInterviewQuestions(
            @P("Internship direction such as fund research, equity research, securities, or general finance") String direction,
            @P(value = "Number of questions", required = false) Integer count
    ) {
        return financeKnowledgeService.generateFinanceInterviewQuestions(direction, count);
    }
}
