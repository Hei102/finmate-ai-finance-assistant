package com.zth.java.ai.langchain4j.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** 提供内置财经概念和面试题模板；此类不调用模型或外部接口。 */
@Service
public class FinanceKnowledgeService {

    /** 根据概念名称返回预设解释，未知概念返回通用学习框架。 */
    public String explainFinanceTerm(String term) {
        String key = normalize(term);
        return switch (key) {
            case "etf", "指数etf", "指数基金etf" -> """
                    概念：ETF 是交易型开放式指数基金，可以像股票一样在交易时段买卖。
                    核心特点：跟踪指数、费率通常较低、流动性通常较好。
                    适合场景：做宽基配置、行业主题配置，或者做模拟面试时的产品讲解。
                    风险提示：会受到市场整体波动影响，主题 ETF 还可能有较高集中度。
                    """;
            case "pe", "市盈率" -> """
                    概念：PE（市盈率）= 股价或市值 / 盈利，用来衡量市场愿意给公司多少倍盈利估值。
                    分析重点：要结合行业增速、利润质量、历史估值区间和同业对比一起看。
                    常见误区：PE 高不一定代表贵，也可能意味着市场预期未来增长更快。
                    """;
            case "pb", "市净率" -> """
                    概念：PB（市净率）= 股价或市值 / 净资产。
                    常见场景：银行、保险、地产、资源类公司更常用 PB 做横向对比。
                    分析重点：PB 最好结合 ROE 一起看，单独看高低容易失真。
                    """;
            case "最大回撤", "回撤" -> """
                    概念：回撤是净值或价格从阶段高点回落到低点的幅度，最大回撤表示历史最深跌幅。
                    作用：帮助衡量波动风险和持有体验。
                    面试表达：可以把回撤和收益、波动率、夏普比率一起讲，体现风险收益平衡思维。
                    """;
            case "主动基金", "主动型基金" -> """
                    概念：主动基金由基金经理主动选股、择时或做行业配置，目标通常是争取跑赢基准。
                    观察点：长期业绩、最大回撤、持仓风格、经理稳定性和规模变化。
                    风险提示：经理更换、风格漂移、超额收益不可持续都需要跟踪。
                    """;
            case "指数基金", "被动基金" -> """
                    概念：指数基金以复制指数表现为目标，常见形式包括场外指数基金和场内 ETF。
                    优点：规则透明、费率较低、风格稳定。
                    风险提示：无法主动规避整体市场下跌，指数编制方式也会影响表现。
                    """;
            default -> """
                    当前知识库里没有这个概念的预设答案。
                    你可以继续从四个角度分析：定义、适用场景、关键指标、主要风险。
                    如果是面试场景，建议再补一句“这个指标的局限性是什么”。
                    """;
        };
    }

    /** 按方向选择题库，并把题数限制在三到八题。 */
    public String generateFinanceInterviewQuestions(String direction, Integer count) {
        int size = count == null ? 5 : Math.max(3, Math.min(count, 8));
        List<String> questions = selectQuestions(direction);
        StringBuilder builder = new StringBuilder("下面是一组可直接用于模拟面试的问题：\n");
        for (int i = 0; i < size; i++) {
            builder.append(i + 1).append(". ").append(questions.get(i % questions.size())).append('\n');
        }
        builder.append("回答建议：尽量按“结论先行 -> 逻辑拆解 -> 风险补充”的顺序回答。");
        return builder.toString();
    }

    /** 选择基金、证券或通用方向的面试题。 */
    private List<String> selectQuestions(String direction) {
        String key = normalize(direction);
        if (key.contains("基金") || key.contains("投研")) {
            return List.of(
                    "你如何判断一只主动基金是否值得持续跟踪？",
                    "如果基金近一年收益很好，但最大回撤也很大，你会怎么评价？",
                    "宽基 ETF 和主动基金分别适合什么样的市场环境？",
                    "你会如何向没有金融背景的人解释夏普比率和最大回撤？",
                    "如果让你写一次基金经理路演纪要，你会重点记录哪些信息？"
            );
        }
        if (key.contains("行研") || key.contains("证券")) {
            return List.of(
                    "分析一家公司的基本框架通常包括哪些部分？",
                    "为什么有些行业更适合用 PE，而有些行业更适合用 PB？",
                    "如果一家公司营收增长很快，但利润不增长，你会怎么分析？",
                    "你如何区分行业 beta 机会和公司 alpha 机会？",
                    "阅读一篇券商研报时，你会优先看哪三部分？"
            );
        }
        return List.of(
                "请解释 ETF、指数基金和主动基金的区别。",
                "你怎么看待收益率和最大回撤之间的平衡？",
                "如果让你分析一个行业，你会从哪些维度入手？",
                "为什么面试里经常会问你最近关注的公司或行业？",
                "面对自己不熟悉的金融问题时，你会如何快速建立分析框架？"
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
    }
}
