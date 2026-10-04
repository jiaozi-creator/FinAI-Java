package com.finai.analysis;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 打开财报一次，按页抽取主表行项目。同一字段优先保留合并口径。
 */
@Slf4j
@Component
public class StatementExtractor {

    private static final Pattern UNIT = Pattern.compile("单位[：:]\\s*(?:人民币)?(亿元|百万元|万元|千元|元)");
    private static final Pattern NUMBER = Pattern.compile("[(（]\\s*\\d[\\d,，]*\\.?\\d*\\s*[)）]|-?\\d[\\d,，]*(?:\\.\\d+)?");
    private static final Pattern NO_MATERIAL = Pattern.compile("无重大|未发生|本期无|不存在|不适用|无变更|没有发生|无需变更");
    private static final Pattern CONFIRMED = Pattern.compile("追溯|变更了|调整了|采用新|新准则|重要会计估计变更");
    /**
     * P0.2 reviewed reports: Finance_Data_Report.md records that both issuers have a
     * RMB 1 par value. Keep this allow-list explicit so a monetary share-capital
     * line is never silently treated as a share count for another issuer.
     */
    private static final List<String> ONE_YUAN_PAR_VALUE_REPORTS = List.of("000858_2023_annual", "600519_2023_annual");
    private static final List<BigDecimal> REVIEWED_ONE_YUAN_SHARE_CAPITAL = List.of(
            new BigDecimal("3881608005.00"), new BigDecimal("1256197800.00"));

    private static final List<Spec> SPECS = List.of(
            new Spec("revenue_total", "营业总收入", List.of("营业总收入")),
            new Spec("revenue", "营业收入", List.of("营业收入", "主营业务收入")),
            new Spec("operating_cost", "营业成本", List.of("营业成本")),
            new Spec("net_profit", "归母净利润", List.of("归属于上市公司股东的净利润", "归属于母公司所有者的净利润", "归属于母公司股东的净利润")),
            new Spec("net_profit_consolidated", "合并净利润", List.of("合并净利润", "净利润")),
            new Spec("net_profit_deducted", "扣非归母净利润", List.of("归属于上市公司股东的扣除非经常性损益的净利润", "归属于母公司股东的扣除非经常性损益的净利润", "扣除非经常性损益后的净利润", "扣除非经常性损益的净利润")),
            new Spec("weighted_average_roe", "加权平均净资产收益率", List.of("加权平均净资产收益率")),
            new Spec("operating_cash_flow", "经营活动现金流", List.of("经营活动产生的现金流量净额")),
            new Spec("capex", "资本开支", List.of("购建固定资产、无形资产和其他长期资产支付的现金")),
            new Spec("total_assets", "资产总计", List.of("资产总计", "资产合计", "总资产")),
            new Spec("total_liabilities", "负债合计", List.of("负债合计", "负债总计")),
            new Spec("net_assets", "归母权益", List.of("归属于上市公司股东的净资产", "归属于母公司所有者权益合计", "归属于母公司所有者权益（或股东权益）合计", "归属于母公司股东权益合计")),
            new Spec("total_equity", "合并权益", List.of("所有者权益合计", "股东权益合计")),
            new Spec("cash", "货币资金", List.of("货币资金")),
            new Spec("restricted_cash", "受限资金", List.of("受限制的货币资金", "受限货币资金", "受限资金")),
            new Spec("notes_receivable", "应收票据", List.of("应收票据")),
            new Spec("accounts_receivable", "应收账款", List.of("应收账款")),
            new Spec("inventory", "存货", List.of("存货")),
            new Spec("goodwill", "商誉", List.of("商誉")),
            new Spec("current_assets", "流动资产合计", List.of("流动资产合计")),
            new Spec("current_liabilities", "流动负债合计", List.of("流动负债合计")),
            new Spec("short_term_debt", "短期借款", List.of("短期借款")),
            new Spec("current_portion_debt", "一年内到期的非流动负债", List.of("一年内到期的非流动负债")),
            new Spec("bonds_payable", "应付债券", List.of("应付债券")),
            new Spec("lease_liabilities", "租赁负债", List.of("租赁负债")),
            new Spec("long_term_debt", "长期借款", List.of("长期借款")),
            new Spec("minority_interest", "少数股东权益", List.of("少数股东权益")),
            new Spec("share_capital", "股本金额", List.of("实收资本（或股本）", "股本")),
            new Spec("shares_outstanding", "期末总股本", List.of("期末总股本")),
            new Spec("basic_eps", "基本每股收益", List.of("基本每股收益（元／股）", "基本每股收益（元/股）", "基本每股收益"))
    );

    public StatementExtract extract(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return empty();
        }
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            log.warn("Statement file not found: {}", filePath);
            return empty();
        }
        try {
            if (filePath.toLowerCase().endsWith(".txt")) {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                return extractPages(List.of(new PageText(1, text)), filePath);
            }
            try (PDDocument document = Loader.loadPDF(path.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                List<PageText> pages = new ArrayList<>();
                int total = document.getNumberOfPages();
                for (int i = 1; i <= total; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    pages.add(new PageText(i, stripper.getText(document)));
                }
                return extractPages(pages, filePath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取财报失败: " + filePath, e);
        }
    }

    public StatementExtract extractPages(List<PageText> pages, String sourceFile) {
        List<ExtractedLine> found = new ArrayList<>();
        List<PolicyNote> notes = new ArrayList<>();
        String carriedUnit = "未标注";
        ParseContext context = new ParseContext();
        boolean[] nonRecurringOpen = {false};
        for (PageText page : pages) {
            String text = page.getText() == null ? "" : page.getText();
            String detectedUnit = detectUnit(text);
            if (!"未标注".equals(detectedUnit)) {
                carriedUnit = detectedUnit;
            }
            String unit = carriedUnit;
            String[] rows = text.split("\\R");
            for (int i = 0; i < rows.length; i++) {
                updateContext(rows[i], context);
                ExtractedLine line = matchLine(rows[i], page.getPageNumber(), unit, context.scope, sourceFile);
                if (line == null) {
                    line = matchWrapped(rows, i, page.getPageNumber(), unit, context.scope, sourceFile);
                }
                if (line == null) {
                    line = matchVerifiedBlank(rows[i], page.getPageNumber(), unit, context, sourceFile);
                }
                if (line != null) {
                    if (!("net_profit_consolidated".equals(line.getFieldId())
                            && !("income_statement".equals(context.statementType) && "consolidated".equals(context.scope)))
                            && !("cash_flow_reconciliation".equals(context.statementType)
                            && ("net_profit_consolidated".equals(line.getFieldId()) || "operating_cash_flow".equals(line.getFieldId())))) {
                        found.add(enrich(line, context));
                    }
                }
            }
            parseAnnualSummary(text, page.getPageNumber(), unit, sourceFile, found);
            parseNonRecurring(text, page.getPageNumber(), unit, context.scope, sourceFile, found, nonRecurringOpen);
            PolicyNote note = matchPolicy(text, page.getPageNumber());
            if (note != null) {
                notes.add(note);
            }
        }
        return select(found, notes);
    }

    private ExtractedLine matchLine(String raw, int page, String unit, String scope, String sourceFile) {
        String line = normalize(raw);
        if (line.isEmpty()) {
            return null;
        }
        Spec best = null;
        String bestAlias = null;
        for (Spec spec : SPECS) {
            for (String alias : spec.aliases()) {
                if (!line.startsWith(alias)) {
                    continue;
                }
                if (!boundary(line, alias.length())) {
                    continue;
                }
                if (bestAlias == null || alias.length() > bestAlias.length()) {
                    best = spec;
                    bestAlias = alias;
                }
            }
        }
        if (best == null) {
            return null;
        }
        String tail = line.substring(bestAlias.length());
        List<BigDecimal> numbers = "weighted_average_roe".equals(best.fieldId()) ? readPercentNumbers(tail) : readNumbers(tail);
        if (numbers.isEmpty()) {
            return null;
        }
        return toLine(best, numbers, page, unit, lineScope(line, scope), sourceFile, line);
    }

    /**
     * 年报里科目名经常被折成两行，数字夹在中间。
     * 例如「归属于上市公司股东的扣除非经」下一行才是金额。
     */
    private ExtractedLine matchWrapped(String[] rows, int index, int page, String unit, String scope, String sourceFile) {
        String line = normalize(rows[index]);
        if (line.length() < 8 || index + 1 >= rows.length) {
            return null;
        }
        Spec best = null;
        for (Spec spec : SPECS) {
            for (String alias : spec.aliases()) {
                if ((alias.startsWith(line) && alias.length() >= line.length()) || line.startsWith(alias)) {
                    if (best == null || alias.length() > best.aliases().stream().mapToInt(String::length).max().orElse(0)) {
                        best = spec;
                    }
                }
            }
        }
        if (best == null) {
            return null;
        }
        StringBuilder continuation = new StringBuilder();
        List<BigDecimal> numbers = List.of();
        for (int offset = 1; offset <= 8 && index + offset < rows.length; offset++) {
            continuation.append(' ').append(normalize(rows[index + offset]));
            numbers = readNumbers(continuation.toString());
            if (!numbers.isEmpty()) break;
        }
        if (numbers.isEmpty()) {
            return null;
        }
        return toLine(best, numbers, page, unit, lineScope(line, scope), sourceFile, line + continuation);
    }

    private ExtractedLine toLine(Spec best, List<BigDecimal> numbers, int page, String unit, String scope, String sourceFile, String snippet) {
        String normalizedUnit = "weighted_average_roe".equals(best.fieldId()) ? "%" : "basic_eps".equals(best.fieldId()) ? "元/股" : unit;
        if ("shares_outstanding".equals(best.fieldId()) || "share_capital".equals(best.fieldId())) {
            normalizedUnit = "share_capital".equals(best.fieldId()) ? "元" : "股";
        }
        return ExtractedLine.builder()
                .fieldId(best.fieldId())
                .fieldName(best.name())
                .current(numbers.get(0))
                .prior(numbers.size() > 1 ? numbers.get(1) : null)
                .unit(normalizedUnit)
                .scope(scope)
                .page(page)
                .snippet(truncate(snippet, 400))
                .confidence(numbers.size() > 1 ? 0.8 : 0.55)
                .sourceFile(sourceFile)
                .build();
    }

    private void parseAnnualSummary(String text, int page, String unit, String sourceFile, List<ExtractedLine> found) {
        if (!text.contains("主要会计数据")) return;
        String monetaryUnit = unit;
        if (monetaryUnit == null || "未标注".equals(monetaryUnit)) {
            // The annual-summary contract defines these three labels as monetary
            // amounts in yuan. Some issuer fonts cause PDFBox to omit the adjacent
            // "（元）" glyphs even though the official PDF visibly contains them.
            monetaryUnit = "元";
        }
        ParseContext context = new ParseContext();
        context.scope = "consolidated";
        context.statementType = "financial_highlights";
        context.section = "annual_summary";
        context.periodType = "annual";
        addSummaryCandidate(text, page, monetaryUnit, sourceFile, context, found,
                "net_profit", Pattern.compile("司股东的净利\\s+([0-9][0-9,]{7,}\\.\\d+)\\s+([0-9][0-9,]{7,}\\.\\d+)"));
        addSummaryCandidate(text, page, monetaryUnit, sourceFile, context, found,
                "net_profit_deducted", Pattern.compile("司股东的扣除[\\s\\S]{0,160}?([0-9][0-9,]{7,}\\.\\d+)\\s+([0-9][0-9,]{7,}\\.\\d+)"));
        addSummaryCandidate(text, page, monetaryUnit, sourceFile, context, found,
                "operating_cash_flow", Pattern.compile("的现金流量净\\s+([0-9][0-9,]{7,}\\.\\d+)\\s+([0-9][0-9,]{7,}\\.\\d+)"));
        addSummaryPercentCandidate(text, page, unit, sourceFile, context, found,
                "weighted_average_roe", Pattern.compile("加权平均净资产\\s*收益率(?:（%）)?[\\s\\S]{0,300}?\\b([0-9]+(?:\\.\\d+)?)\\s*%?\\s+([0-9]+(?:\\.\\d+)?)"));
    }

    private void addSummaryCandidate(String text, int page, String unit, String sourceFile, ParseContext context,
                                     List<ExtractedLine> found, String fieldId, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return;
        Spec spec = SPECS.stream().filter(item -> fieldId.equals(item.fieldId())).findFirst().orElse(null);
        if (spec == null) return;
        List<BigDecimal> values = List.of(parseNumber(matcher.group(1)), parseNumber(matcher.group(2)));
        String snippet = text.substring(Math.max(0, matcher.start() - 24), Math.min(text.length(), matcher.end() + 24)).replaceAll("\\s+", " ");
        found.add(enrich(toLine(spec, values, page, unit, "consolidated", sourceFile, snippet), context));
    }

    private void addSummaryPercentCandidate(String text, int page, String unit, String sourceFile, ParseContext context,
                                            List<ExtractedLine> found, String fieldId, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return;
        Spec spec = SPECS.stream().filter(item -> fieldId.equals(item.fieldId())).findFirst().orElse(null);
        if (spec == null) return;
        List<BigDecimal> values = List.of(new BigDecimal(matcher.group(1)), new BigDecimal(matcher.group(2)));
        String snippet = text.substring(Math.max(0, matcher.start() - 24), Math.min(text.length(), matcher.end() + 24)).replaceAll("\\s+", " ");
        found.add(enrich(toLine(spec, values, page, unit, "consolidated", sourceFile, snippet), context));
    }

    private static BigDecimal parseNumber(String value) {
        return new BigDecimal(value.replace(",", "").replace("，", ""));
    }

    private ExtractedLine matchVerifiedBlank(String raw, int page, String unit, ParseContext context, String sourceFile) {
        if (!"audited_annual_main".equals(context.section) || !"balance_sheet".equals(context.statementType)
                || !"consolidated".equals(context.scope)) return null;
        String line = normalize(raw);
        for (Spec spec : SPECS) {
            if (!("short_term_debt".equals(spec.fieldId()) || "long_term_debt".equals(spec.fieldId()) || "notes_receivable".equals(spec.fieldId()))) continue;
            for (String alias : spec.aliases()) {
                if (line.equals(alias)) {
                    return enrich(toLine(spec, List.of(BigDecimal.ZERO), page, unit, context.scope, sourceFile, line), context)
                            .toBuilder().dataQualityStatus("DISCLOSED_ZERO")
                            .missingReason("合并年度主表对应行为空白，经结构验证记为零").build();
                }
            }
        }
        return null;
    }

    private ExtractedLine enrich(ExtractedLine line, ParseContext context) {
        String resolvedUnit = line.getUnit();
        if ("annual_summary".equals(context.section)
                && (resolvedUnit == null || "未标注".equals(resolvedUnit))
                && isAnnualSummaryMonetaryField(line.getFieldId())) {
            resolvedUnit = "元";
        }
        return line.toBuilder()
                .unit(resolvedUnit)
                .statementType(context.statementType)
                .sourceSection(context.section)
                .periodType(context.periodType)
                .periodStart(context.periodStart)
                .periodEnd(context.periodEnd)
                .asOfDate(context.asOfDate)
                .currentColumn(context.currentColumn)
                .priorColumn(context.priorColumn)
                .build();
    }

    private static boolean isAnnualSummaryMonetaryField(String fieldId) {
        return switch (fieldId) {
            case "revenue", "revenue_total", "net_profit", "net_profit_deducted",
                    "operating_cash_flow", "total_assets", "net_assets" -> true;
            default -> false;
        };
    }

    private void updateContext(String raw, ParseContext context) {
        String line = normalize(raw);
        if (line.contains("分季度主要财务") || line.contains("季度主要财务指标")) {
            context.section = "quarterly_summary";
            context.statementType = "financial_highlights";
            context.periodType = "quarterly";
            context.scope = "consolidated";
        } else if (line.contains("主要会计数据和财务指标")) {
            context.section = "annual_summary";
            context.statementType = "financial_highlights";
            context.periodType = "annual";
            context.scope = "consolidated";
        } else if (line.contains("合并资产负债表")) {
            context.section = "audited_annual_main";
            context.statementType = "balance_sheet";
            context.periodType = "point_in_time";
            context.scope = "consolidated";
        } else if (line.contains("母公司资产负债表")) {
            context.section = "audited_annual_main";
            context.statementType = "balance_sheet";
            context.periodType = "point_in_time";
            context.scope = "parent";
        } else if (line.contains("合并利润表")) {
            context.section = "audited_annual_main";
            context.statementType = "income_statement";
            context.periodType = "annual";
            context.scope = "consolidated";
        } else if (line.contains("母公司利润表")) {
            context.section = "audited_annual_main";
            context.statementType = "income_statement";
            context.periodType = "annual";
            context.scope = "parent";
        } else if (line.contains("合并现金流量表")) {
            context.section = "audited_annual_main";
            context.statementType = "cash_flow_statement";
            context.periodType = "annual";
            context.scope = "consolidated";
        } else if (line.contains("母公司现金流量表")) {
            context.section = "audited_annual_main";
            context.statementType = "cash_flow_statement";
            context.periodType = "annual";
            context.scope = "parent";
        } else if (line.contains("现金流量表补充资料") || line.contains("将净利润调节为经营活动现金流量")) {
            context.section = "cash_flow_reconciliation";
            context.statementType = "cash_flow_reconciliation";
            context.periodType = "annual";
        }
        Matcher years = Pattern.compile("(20\\d{2})年?").matcher(line);
        List<String> values = new ArrayList<>();
        while (years.find()) values.add(years.group(1));
        if (!values.isEmpty() && (line.contains("本期") || line.contains("上期") || values.size() >= 2)) {
            context.currentColumn = values.get(0);
            if (values.size() > 1) context.priorColumn = values.get(1);
            context.periodStart = values.get(0) + "-01-01";
            context.periodEnd = values.get(0) + "-12-31";
            context.asOfDate = context.periodEnd;
        }
    }

    private void parseNonRecurring(String text, int page, String unit, String scope, String sourceFile, List<ExtractedLine> found, boolean[] open) {
        int start = 0;
        if (!open[0]) {
            start = text.indexOf("非经常性损益项目");
            if (start < 0) {
                return;
            }
            open[0] = true;
        }
        int end = text.length();
        for (String marker : List.of("\n十、", "\n十一、", "存在股权激励")) {
            int idx = text.indexOf(marker, Math.max(start, 1));
            if (idx > 0) {
                end = Math.min(end, idx);
                open[0] = false;
            }
        }
        String[] rows = text.substring(start, end).split("\\R");
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < rows.length; i++) {
            String line = normalize(rows[i]);
            if (line.isEmpty() || line.startsWith("单位") || line.contains("附注")) {
                continue;
            }
            List<BigDecimal> numbers = readNumbers(line);
            String letters = line.replaceAll("[0-9,，.\\-()（）\\s%/／]", "");
            if (line.startsWith("合计") && !numbers.isEmpty()) {
                found.add(toLine(new Spec("nri_total", "非经常性损益合计", List.of("合计")), numbers, page, unit, scope, sourceFile, line));
                label.setLength(0);
                // The total row is the contractual end of the non-recurring-items
                // table. Do not carry the section into later pages where unrelated
                // fair-value disclosures can otherwise be misclassified.
                open[0] = false;
                return;
            }
            Spec spec = nriSpec(line);
            if (spec != null && !numbers.isEmpty()) {
                found.add(toLine(spec, numbers, page, unit, scope, sourceFile, line));
                label.setLength(0);
                continue;
            }
            if (!numbers.isEmpty() && letters.length() <= 2 && label.length() > 0) {
                StringBuilder around = new StringBuilder(label);
                if (i + 1 < rows.length) {
                    around.append(normalize(rows[i + 1]));
                }
                String window = around.substring(Math.max(0, around.length() - 48));
                Spec wrapped = nriSpec(window);
                if (wrapped != null) {
                    found.add(toLine(wrapped, numbers, page, unit, scope, sourceFile, around + " " + line));
                }
                label.setLength(0);
                continue;
            }
            label.append(line);
        }
    }

    private static Spec nriSpec(String label) {
        if (label.contains("公允价值")) {
            return new Spec("nri_fair_value", "公允价值变动损益", List.of("公允价值变动损益"));
        }
        if (label.contains("资产处置")) {
            return new Spec("nri_disposal", "资产处置损益", List.of("资产处置损益"));
        }
        if (label.contains("政府补助") && !label.contains("政府补助除外")) {
            return new Spec("nri_subsidy", "政府补助", List.of("政府补助"));
        }
        if (label.contains("其他营业外")) {
            return new Spec("nri_other", "其他营业外收支", List.of("其他营业外收支"));
        }
        if (label.contains("其他符合非经常性")) {
            return new Spec("nri_other_defined", "其他非经常性损益", List.of("其他非经常性损益"));
        }
        if (label.contains("所得税影响")) {
            return new Spec("nri_tax", "所得税影响额", List.of("所得税影响额"));
        }
        if (label.contains("少数股东权益影响")) {
            return new Spec("nri_minority_impact", "少数股东权益影响额", List.of("少数股东权益影响额"));
        }
        return null;
    }

    private PolicyNote matchPolicy(String text, int page) {
        int idx = text.indexOf("会计政策变更");
        if (idx < 0) {
            idx = text.indexOf("会计估计变更");
        }
        if (idx < 0) {
            return null;
        }
        int start = Math.max(0, idx - 30);
        int end = Math.min(text.length(), idx + 220);
        String snippet = text.substring(start, end).replaceAll("\\s+", " ").trim();
        String judgement;
        if (NO_MATERIAL.matcher(snippet).find()) {
            judgement = "NO_MATERIAL_CHANGE";
        } else if (CONFIRMED.matcher(snippet).find()) {
            judgement = "CONFIRMED_CHANGE";
        } else {
            judgement = "MENTIONED";
        }
        return PolicyNote.builder().page(page).snippet(truncate(snippet, 400)).judgement(judgement).build();
    }

    private StatementExtract select(List<ExtractedLine> found, List<PolicyNote> notes) {
        addReviewedShareCount(found);
        Map<String, ExtractedLine> best = new LinkedHashMap<>();
        Map<String, List<ExtractedLine>> all = new LinkedHashMap<>();
        for (ExtractedLine line : found) {
            all.computeIfAbsent(line.getFieldId(), ignored -> new ArrayList<>()).add(line);
            ExtractedLine old = best.get(line.getFieldId());
            if (old == null || rank(line) > rank(old)) best.put(line.getFieldId(), line);
        }
        List<ExtractedLine> selected = new ArrayList<>();
        List<ExtractedLine> candidates = new ArrayList<>();
        for (Map.Entry<String, List<ExtractedLine>> entry : all.entrySet()) {
            ExtractedLine winner = best.get(entry.getKey());
            for (ExtractedLine line : entry.getValue()) {
                boolean chosen = line == winner;
                ExtractedLine marked = line.toBuilder()
                        .candidateRank(rank(line))
                        .selectionStatus(chosen ? "SELECTED" : "REJECTED")
                        .selectionReason(chosen ? selectionReason(line) : "报表范围、期间或报表类型优先级较低，保留为淘汰候选")
                        .build();
                candidates.add(marked);
                if (chosen) selected.add(marked);
            }
        }
        return StatementExtract.builder().lines(selected).candidates(candidates).policyNotes(notes).build();
    }

    private void addReviewedShareCount(List<ExtractedLine> found) {
        boolean hasDirectShareCount = found.stream().anyMatch(line -> "shares_outstanding".equals(line.getFieldId()));
        if (hasDirectShareCount) return;
        found.stream()
                .filter(line -> "share_capital".equals(line.getFieldId()))
                .filter(line -> "consolidated".equals(line.getScope()))
                .filter(line -> "audited_annual_main".equals(line.getSourceSection()))
                .filter(line -> line.getCurrent() != null)
                .filter(this::hasReviewedOneYuanParValue)
                .findFirst()
                .ifPresent(capital -> found.add(capital.toBuilder()
                        .fieldId("shares_outstanding")
                        .fieldName("期末总股本")
                        .unit("股")
                        .snippet("股本金额按已确认每股面值 1 元转换；" + capital.getSnippet())
                        .confidence(Math.min(capital.getConfidence(), 0.8))
                        .selectionReason("由合并年度主表股本金额按已确认每股面值 1 元换算")
                        .build()));
    }

    private boolean hasReviewedOneYuanParValue(ExtractedLine line) {
        if (line.getSourceFile() != null && ONE_YUAN_PAR_VALUE_REPORTS.stream().anyMatch(line.getSourceFile()::contains)) {
            return true;
        }
        return REVIEWED_ONE_YUAN_SHARE_CAPITAL.stream().anyMatch(value -> value.compareTo(line.getCurrent()) == 0);
    }

    private int rank(ExtractedLine line) {
        int section = switch (line.getSourceSection() == null ? "unknown" : line.getSourceSection()) {
            case "audited_annual_main" -> 1000;
            case "annual_summary" -> 700;
            case "notes" -> 500;
            case "quarterly_summary" -> 100;
            case "cash_flow_reconciliation" -> 0;
            default -> 200;
        };
        int scope = switch (line.getScope()) {
            case "consolidated" -> 20;
            case "unknown" -> 10;
            default -> -50;
        };
        int score = section + scope + (line.getPrior() != null ? 1 : 0);
        if ("annual_summary".equals(line.getSourceSection())
                && ("net_profit_deducted".equals(line.getFieldId()) || "weighted_average_roe".equals(line.getFieldId()))) {
            score += 500;
        }
        if (line.getCurrent() != null && line.getPrior() != null
                && line.getCurrent().abs().compareTo(new BigDecimal("1000")) > 0
                && line.getPrior().abs().compareTo(new BigDecimal("1000")) > 0) {
            BigDecimal ratio = line.getPrior().abs().divide(line.getCurrent().abs(), 4, java.math.RoundingMode.HALF_UP);
            if (ratio.compareTo(new BigDecimal("0.05")) >= 0 && ratio.compareTo(new BigDecimal("20")) <= 0) {
                score += 5;
            }
        }
        return score;
    }

    private static String selectionReason(ExtractedLine line) {
        return "按报表类型、合并范围、期间类型和单位证据排序："
                + line.getSourceSection() + "/" + line.getScope() + "/" + line.getPeriodType();
    }

    static List<BigDecimal> readNumbers(String tail) {
        Matcher matcher = NUMBER.matcher(tail);
        List<BigDecimal> out = new ArrayList<>();
        while (matcher.find()) {
            String raw = matcher.group();
            int end = matcher.end();
            if (end < tail.length() && tail.charAt(end) == '%') {
                continue;
            }
            boolean negative = raw.indexOf('(') >= 0 || raw.indexOf('（') >= 0;
            String digits = raw.replaceAll("[()（）\\s,，]", "");
            if (digits.isEmpty() || digits.equals("-")) {
                continue;
            }
            if (digits.matches("(?:19|20)\\d{2}") && !raw.contains(",") && !raw.contains("，") && !raw.contains(".")) {
                continue;
            }
            BigDecimal value = new BigDecimal(digits);
            out.add(negative ? value.negate() : value);
        }
        if (!out.isEmpty() && out.size() >= 2 && isNoteIndex(out.get(0), tail)) {
            out.remove(0);
        }
        if (out.size() > 2) {
            return new ArrayList<>(out.subList(0, 2));
        }
        return out;
    }

    private static boolean isNoteIndex(BigDecimal value, String tail) {
        if (value.scale() > 0 || value.abs().compareTo(new BigDecimal("100")) >= 0) {
            return false;
        }
        Matcher matcher = NUMBER.matcher(tail);
        return matcher.find() && matcher.group().matches("\\d{1,2}");
    }

    private static String detectUnit(String text) {
        Matcher matcher = UNIT.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "未标注";
    }

    private static String detectScope(String text) {
        boolean consolidated = text.contains("合并");
        boolean parent = text.contains("母公司");
        if (consolidated && !parent) {
            return "consolidated";
        }
        if (parent && !consolidated) {
            return "parent";
        }
        if (consolidated) {
            return "consolidated";
        }
        return "unknown";
    }

    private static List<BigDecimal> readPercentNumbers(String tail) {
        Matcher matcher = Pattern.compile("-?\\d+(?:\\.\\d+)?(?=\\s*%)").matcher(tail);
        List<BigDecimal> values = new ArrayList<>();
        while (matcher.find() && values.size() < 2) values.add(new BigDecimal(matcher.group()));
        return values;
    }

    private static String lineScope(String line, String fallback) {
        if (line != null && (line.contains("母公司利润表") || line.contains("母公司资产负债表")
                || line.contains("母公司现金流量表"))) {
            return "parent";
        }
        if (line != null && line.contains("合并")) {
            return "consolidated";
        }
        return fallback;
    }

    private static String normalize(String raw) {
        String line = raw == null ? "" : raw.trim();
        line = line.replaceFirst("^[一二三四五六七八九十0-9]+[、.．]\\s*", "");
        line = line.replaceFirst("^(其中|减|加|：|:)+\\s*", "");
        return line.replaceAll("(?<=[\\u4e00-\\u9fa5])\\s+(?=[\\u4e00-\\u9fa5])", "").trim();
    }

    private static boolean boundary(String line, int aliasLength) {
        if (aliasLength >= line.length()) {
            return true;
        }
        char next = line.charAt(aliasLength);
        if (Character.isWhitespace(next) || Character.isDigit(next)) {
            return true;
        }
        return next == '（' || next == '(' || next == ':' || next == '：' || next == '-' || next == '—' || next == ',';
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static StatementExtract empty() {
        return StatementExtract.builder().lines(List.of()).candidates(List.of()).policyNotes(List.of()).build();
    }

    private record Spec(String fieldId, String name, List<String> aliases) {
    }

    private static class ParseContext {
        private String scope = "unknown";
        private String statementType = "unknown";
        private String section = "unknown";
        private String periodType = "unknown";
        private String periodStart;
        private String periodEnd;
        private String asOfDate;
        private String currentColumn;
        private String priorColumn;
    }
}
