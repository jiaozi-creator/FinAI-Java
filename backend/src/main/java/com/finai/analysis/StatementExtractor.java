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

    private static final List<Spec> SPECS = List.of(
            new Spec("revenue", "营业收入", List.of("营业总收入", "营业收入")),
            new Spec("operating_cost", "营业成本", List.of("营业成本")),
            new Spec("net_profit", "归母净利润", List.of("归属于上市公司股东的净利润", "归属于母公司所有者的净利润", "归属于母公司股东的净利润", "净利润")),
            new Spec("net_profit_deducted", "扣非净利润", List.of("归属于上市公司股东的扣除非经常性损益的净利润", "扣除非经常性损益后的净利润", "扣除非经常性损益的净利润")),
            new Spec("operating_cash_flow", "经营活动现金流", List.of("经营活动产生的现金流量净额")),
            new Spec("capex", "资本开支", List.of("购建固定资产、无形资产和其他长期资产支付的现金")),
            new Spec("total_assets", "资产总计", List.of("资产总计", "资产合计", "总资产")),
            new Spec("total_liabilities", "负债合计", List.of("负债合计", "负债总计")),
            new Spec("net_assets", "归母权益", List.of("归属于上市公司股东的净资产", "归属于母公司所有者权益合计", "归属于母公司所有者权益（或股东权益）合计", "归属于母公司股东权益合计", "所有者权益合计", "股东权益合计")),
            new Spec("cash", "货币资金", List.of("货币资金")),
            new Spec("accounts_receivable", "应收账款", List.of("应收账款")),
            new Spec("inventory", "存货", List.of("存货")),
            new Spec("goodwill", "商誉", List.of("商誉")),
            new Spec("current_assets", "流动资产合计", List.of("流动资产合计")),
            new Spec("current_liabilities", "流动负债合计", List.of("流动负债合计")),
            new Spec("short_term_debt", "短期借款", List.of("短期借款")),
            new Spec("long_term_debt", "长期借款", List.of("长期借款")),
            new Spec("minority_interest", "少数股东权益", List.of("少数股东权益")),
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
        boolean[] nonRecurringOpen = {false};
        for (PageText page : pages) {
            String text = page.getText() == null ? "" : page.getText();
            String detectedUnit = detectUnit(text);
            if (!"未标注".equals(detectedUnit)) {
                carriedUnit = detectedUnit;
            }
            String unit = carriedUnit;
            String scope = detectScope(text);
            String[] rows = text.split("\\R");
            for (int i = 0; i < rows.length; i++) {
                ExtractedLine line = matchLine(rows[i], page.getPageNumber(), unit, scope, sourceFile);
                if (line == null) {
                    line = matchWrapped(rows, i, page.getPageNumber(), unit, scope, sourceFile);
                }
                if (line != null) {
                    found.add(line);
                }
            }
            parseNonRecurring(text, page.getPageNumber(), unit, scope, sourceFile, found, nonRecurringOpen);
            PolicyNote note = matchPolicy(text, page.getPageNumber());
            if (note != null) {
                notes.add(note);
            }
        }
        return StatementExtract.builder()
                .lines(dedupe(found))
                .policyNotes(notes)
                .build();
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
        List<BigDecimal> numbers = readNumbers(tail);
        if (numbers.isEmpty()) {
            return null;
        }
        return toLine(best, numbers, page, unit, scope, sourceFile, line);
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
                if (alias.startsWith(line) && alias.length() > line.length()) {
                    if (best == null || alias.length() > best.aliases().stream().mapToInt(String::length).max().orElse(0)) {
                        best = spec;
                    }
                }
            }
        }
        if (best == null) {
            return null;
        }
        List<BigDecimal> numbers = readNumbers(normalize(rows[index + 1]));
        if (numbers.isEmpty()) {
            return null;
        }
        return toLine(best, numbers, page, unit, scope, sourceFile, line + " " + normalize(rows[index + 1]));
    }

    private ExtractedLine toLine(Spec best, List<BigDecimal> numbers, int page, String unit, String scope, String sourceFile, String snippet) {
        return ExtractedLine.builder()
                .fieldId(best.fieldId())
                .fieldName(best.name())
                .current(numbers.get(0))
                .prior(numbers.size() > 1 ? numbers.get(1) : null)
                .unit(unit)
                .scope(scope)
                .page(page)
                .snippet(truncate(snippet, 400))
                .confidence(numbers.size() > 1 ? 0.8 : 0.55)
                .sourceFile(sourceFile)
                .build();
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
                continue;
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

    private List<ExtractedLine> dedupe(List<ExtractedLine> found) {
        Map<String, ExtractedLine> best = new LinkedHashMap<>();
        for (ExtractedLine line : found) {
            ExtractedLine old = best.get(line.getFieldId());
            if (old == null || rank(line) > rank(old)) {
                best.put(line.getFieldId(), line);
            }
        }
        return new ArrayList<>(best.values());
    }

    private int rank(ExtractedLine line) {
        int scope = switch (line.getScope()) {
            case "consolidated" -> 20;
            case "unknown" -> 10;
            default -> 0;
        };
        int score = scope + (line.getPrior() != null ? 1 : 0);
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

    private static String normalize(String raw) {
        String line = raw == null ? "" : raw.trim();
        line = line.replaceFirst("^[一二三四五六七八九十0-9]+[、.．]\\s*", "");
        line = line.replaceFirst("^(其中|减|加|：|:)+\\s*", "");
        return line.trim();
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
        return StatementExtract.builder().lines(List.of()).policyNotes(List.of()).build();
    }

    private record Spec(String fieldId, String name, List<String> aliases) {
    }
}
