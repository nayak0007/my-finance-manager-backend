package com.myfinancemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinancemanager.common.dto.PageResponse;
import com.myfinancemanager.common.exception.ExternalServiceException;
import com.myfinancemanager.common.exception.ResourceNotFoundException;
import com.myfinancemanager.common.exception.ServiceUnavailableException;
import com.myfinancemanager.domain.AIInsight;
import com.myfinancemanager.domain.InsightCategory;
import com.myfinancemanager.domain.InsightStatus;
import com.myfinancemanager.dto.dashboard.CategoryBreakdownResponse;
import com.myfinancemanager.dto.dashboard.DashboardSummaryResponse;
import com.myfinancemanager.dto.dashboard.MonthlyTrendPoint;
import com.myfinancemanager.dto.insights.AIInsightResponse;
import com.myfinancemanager.dto.insights.GenerateInsightsRequest;
import com.myfinancemanager.dto.investment.PortfolioSummaryResponse;
import com.myfinancemanager.integration.openrouter.OpenRouterClient;
import com.myfinancemanager.repository.AIInsightRepository;
import com.myfinancemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private static final int MAX_INSIGHTS = 10;

    private final AIInsightRepository insightRepository;
    private final UserRepository userRepository;
    private final DashboardService dashboardService;
    private final InvestmentService investmentService;
    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<AIInsightResponse> list(UUID userId, InsightStatus status, Pageable pageable) {
        if (status != null) {
            return PageResponse.from(
                    insightRepository.findByUserIdAndStatusOrderByGeneratedAtDesc(userId, status, pageable),
                    AIInsightResponse::from);
        }
        return PageResponse.from(
                insightRepository.findByUserIdOrderByGeneratedAtDesc(userId, pageable),
                AIInsightResponse::from);
    }

    @Transactional(readOnly = true)
    public AIInsightResponse get(UUID userId, UUID id) {
        return AIInsightResponse.from(load(userId, id));
    }

    @Transactional
    public List<AIInsightResponse> generate(UUID userId, GenerateInsightsRequest request) {
        if (!openRouterClient.isConfigured()) {
            throw new ServiceUnavailableException(
                    "AI insights are not configured. Core tracking features remain available.");
        }
        int months = request != null && request.months() != null
                ? Math.max(1, Math.min(request.months(), 24))
                : 3;
        LocalDate end = LocalDate.now();
        LocalDate from = end.minusMonths(months - 1L).withDayOfMonth(1);

        DashboardSummaryResponse summary =
                dashboardService.summary(userId, "custom", from, end);
        List<CategoryBreakdownResponse> categories =
                dashboardService.expensesByCategory(userId, "custom", from, end);
        List<MonthlyTrendPoint> trend = dashboardService.monthlyTrend(userId, months);
        PortfolioSummaryResponse portfolio = investmentService.portfolioSummary(userId);

        String financialContext = buildContext(months, summary, categories, trend, portfolio);
        String focus = request != null && request.focus() != null ? request.focus().trim() : "";

        String systemPrompt = """
                You are a personal finance assistant for a tracking app. Analyse the user's aggregated
                financial data and produce concise, practical observations. You are NOT a certified
                financial advisor; never give regulated investment advice and never guarantee returns.
                Respond ONLY with a JSON object of the form:
                {"insights":[{"title":"short title","category":"SPENDING|SAVINGS|INVESTMENT|BUDGET|GENERAL","text":"2-4 sentence explanation"}]}
                Return between 1 and 5 insights. Base every statement strictly on the supplied data.
                """;
        String userPrompt = "Financial summary:\n" + financialContext
                + (focus.isBlank() ? "" : "\n\nExtra focus requested by the user: " + focus);

        String content = openRouterClient.completeJson(systemPrompt, userPrompt);
        List<AIInsight> insights = parseInsights(userId, content);

        List<AIInsightResponse> responses = new ArrayList<>();
        for (AIInsight insight : insights) {
            responses.add(AIInsightResponse.from(insightRepository.save(insight)));
        }
        return responses;
    }

    @Transactional
    public AIInsightResponse updateStatus(UUID userId, UUID id, InsightStatus status) {
        AIInsight insight = load(userId, id);
        insight.setStatus(status);
        return AIInsightResponse.from(insightRepository.save(insight));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        insightRepository.delete(load(userId, id));
    }

    private List<AIInsight> parseInsights(UUID userId, String content) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFences(content));
            JsonNode array = root.path("insights");
            if (!array.isArray()) {
                throw new ExternalServiceException("AI insights response did not contain an insights array");
            }
            List<AIInsight> insights = new ArrayList<>();
            for (JsonNode node : array) {
                if (insights.size() >= MAX_INSIGHTS) {
                    break;
                }
                String text = node.path("text").asText("");
                if (text.isBlank()) {
                    continue;
                }
                AIInsight insight = new AIInsight();
                insight.setUser(userRepository.getReferenceById(userId));
                insight.setTitle(node.path("title").asText(null));
                insight.setInsightText(text);
                insight.setCategory(parseCategory(node.path("category").asText(null)));
                insight.setModelUsed(openRouterClient.model());
                insight.setGeneratedAt(Instant.now());
                insight.setStatus(InsightStatus.NEW);
                insights.add(insight);
            }
            if (insights.isEmpty()) {
                throw new ExternalServiceException("AI did not return any usable insights");
            }
            return insights;
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceException("Unable to parse AI insights response", ex);
        }
    }

    private InsightCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return InsightCategory.GENERAL;
        }
        try {
            return InsightCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return InsightCategory.GENERAL;
        }
    }

    private AIInsight load(UUID userId, UUID id) {
        return insightRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Insight not found"));
    }

    private String buildContext(int months, DashboardSummaryResponse summary,
                                List<CategoryBreakdownResponse> categories,
                                List<MonthlyTrendPoint> trend,
                                PortfolioSummaryResponse portfolio) {
        StringBuilder builder = new StringBuilder();
        builder.append("Period: last ").append(months).append(" month(s), ending ")
                .append(summary.periodEnd()).append('\n');
        builder.append("Total income: ").append(summary.totalIncome()).append('\n');
        builder.append("Total expenses: ").append(summary.totalExpenses()).append('\n');
        builder.append("Net savings: ").append(summary.netSavings()).append('\n');
        builder.append("Investments made in period: ").append(summary.totalInvestments()).append('\n');
        builder.append("Expense categories: ");
        builder.append(categories.isEmpty() ? "none" : categories.stream()
                .map(category -> category.category() + "=" + category.amount()
                        + " (" + category.percentage() + "%)")
                .reduce((a, b) -> a + ", " + b).orElse("none"));
        builder.append('\n').append("Monthly trend (month income/expense): ");
        builder.append(trend.isEmpty() ? "none" : trend.stream()
                .map(point -> point.label() + " " + point.income() + "/" + point.expense())
                .reduce((a, b) -> a + ", " + b).orElse("none"));
        builder.append('\n').append("Portfolio: invested=").append(portfolio.totalInvested())
                .append(", currentValue=").append(portfolio.currentValue())
                .append(", gainLoss=").append(portfolio.gainLoss())
                .append(", gainLossPercent=").append(portfolio.gainLossPercent());
        return builder.toString();
    }

    private String stripCodeFences(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
