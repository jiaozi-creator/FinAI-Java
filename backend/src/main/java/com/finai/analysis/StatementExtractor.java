package com.finai.analysis;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private final List<FieldSpec> specs;

    public StatementExtractor() {
        this.specs = AnalysisConfig.get().fields();
    }

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
            String sha256 = sha256(path);
            if (filePath.toLowerCase().endsWith(".txt")) {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                return withFile(extractPages(List.of(new PageText(1, text)), filePath), filePath, sha256, 1);
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
                return withFile(extractPages(pages, filePath), filePath, sha256, total);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取财报失败: " + filePath, e);
        }
    }

    public StatementExtract extractPages(List<PageText> pages, String sourceFile) {
        List<ExtractedLine> found = new ArrayList<>();
        List<PolicyNote> notes = new ArrayList<>();
        String carriedUnit = "未标注";
        String section = "other";
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
                section = nextSection(rows[i], section);
                String scope = scopeOf(section);
                ExtractedLine line = matchLine(rows[i], page.getPageNumber(), unit, scope, sourceFile);
                if (line == null) {
                    line = matchWrapped(rows, i, page.getPageNumber(), unit, scope, sourceFile);
                }
                if (line == null) {
                    line = matchDeductedFragment(rows[i], page.getPageNumber(), unit, scope, sourceFile);
                }
                if (line != null) {
                    found.add(line);
                }
            }
            parseNonRecurring(text, page.getPageNumber(), unit, scopeOf(section), sourceFile, found, nonRecurringOpen);
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
        FieldSpec best = null;
        String bestAlias = null;
        for (FieldSpec spec : specs) {
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
        if (line.length() < 4 || index + 1 >= rows.length) {
            return null;
        }
        FieldSpec best = null;
        int bestLen = -1;
        for (FieldSpec spec : specs) {
            for (String alias : spec.aliases()) {
                boolean broken = alias.startsWith(line) && alias.length() > line.length() && line.length() >= 8;
                boolean labelOnly = lineStartsAlias(line, alias) && readNumbers(tailAfter(line, alias)).isEmpty();
                if (!broken && !labelOnly) {
                    continue;
                }
                if (alias.length() > bestLen) {
                    best = spec;
                    bestLen = alias.length();
                }
            }
        }
        if (best == null) {
            return null;
        }
        List<BigDecimal> numbers = List.of();
        String numberLine = "";
        for (int offset = 1; offset <= 2 && index + offset < rows.length; offset++) {
            numberLine = normalize(rows[index + offset]);
            numbers = readNumbers(numberLine);
            if (!numbers.isEmpty()) {
                break;
            }
        }
        if (numbers.isEmpty()) {
            return null;
        }
        return toLine(best, numbers, page, unit, scope, sourceFile, line + " " + numberLine);
    }

    /**
     * 主要会计数据里，扣非科目被拆行，2023 年金额会单独落在上一行。
     * 只接受同一行里同时出现的本期和上期。
     */
    private ExtractedLine matchDeductedFragment(String raw, int page, String unit, String scope, String sourceFile) {
        String line = normalize(raw);
        if (!line.contains("扣除非经常")) {
            return null;
        }
        List<BigDecimal> numbers = readNumbers(line);
        if (numbers.size() < 2) {
            return null;
        }
        return toLine(new FieldSpec("net_profit_deducted", "扣非净利润", List.of("扣除非经常性损益的净利润")),
                numbers, page, unit, scope, sourceFile, line);
    }

    private static boolean lineStartsAlias(String line, String alias) {
        return line.equals(alias) || (line.startsWith(alias) && boundary(line, alias.length()));
    }

    private static String tailAfter(String line, String alias) {
        if (line.length() <= alias.length()) {
            return "";
        }
        return line.substring(alias.length());
    }

    private static String nextSection(String raw, String current) {
        String line = raw == null ? "" : raw.replace(" ", "").replace("\u00a0", "");
        line = line.replaceFirst("^[（(][一二三四五六七八九十0-9]+[)）]", "");
        line = line.replaceFirst("^[一二三四五六七八九十0-9]+[、.．]", "");
        if (line.startsWith("购买日公允价值") || line.startsWith("被购买方于购买日")) {
            return "note";
        }
        if (line.startsWith("主要会计数据")) {
            return "summary";
        }
        if (line.startsWith("母公司资产负债表")) {
            return "parent-bs";
        }
        if (line.startsWith("母公司利润表")) {
            return "parent-is";
        }
        if (line.startsWith("母公司现金流量表")) {
            return "parent-cf";
        }
        if (line.startsWith("合并资产负债表")) {
            return "consolidated-bs";
        }
        if (line.startsWith("合并利润表")) {
            return "consolidated-is";
        }
        if (line.startsWith("合并现金流量表")) {
            return "consolidated-cf";
        }
        return current;
    }

    private static String scopeOf(String section) {
        if (section.startsWith("consolidated")) {
            return "consolidated";
        }
        if (section.startsWith("parent")) {
            return "parent";
        }
        if ("summary".equals(section)) {
            return "summary";
        }
        if ("note".equals(section)) {
            return "note";
        }
        return "unknown";
    }

    private ExtractedLine toLine(FieldSpec best, List<BigDecimal> numbers, int page, String unit, String scope, String sourceFile, String snippet) {
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
                found.add(toLine(new FieldSpec("nri_total", "非经常性损益合计", List.of("合计")), numbers, page, unit, scope, sourceFile, line));
                label.setLength(0);
                continue;
            }
            FieldSpec spec = nriSpec(line);
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
                FieldSpec wrapped = nriSpec(window);
                if (wrapped != null) {
                    found.add(toLine(wrapped, numbers, page, unit, scope, sourceFile, around + " " + line));
                }
                label.setLength(0);
                continue;
            }
            label.append(line);
        }
    }

    private static FieldSpec nriSpec(String label) {
        if (label.contains("公允价值")) {
            return new FieldSpec("nri_fair_value", "公允价值变动损益", List.of("公允价值变动损益"));
        }
        if (label.contains("资产处置")) {
            return new FieldSpec("nri_disposal", "资产处置损益", List.of("资产处置损益"));
        }
        if (label.contains("政府补助") && !label.contains("政府补助除外")) {
            return new FieldSpec("nri_subsidy", "政府补助", List.of("政府补助"));
        }
        if (label.contains("其他营业外")) {
            return new FieldSpec("nri_other", "其他营业外收支", List.of("其他营业外收支"));
        }
        if (label.contains("其他符合非经常性")) {
            return new FieldSpec("nri_other_defined", "其他非经常性损益", List.of("其他非经常性损益"));
        }
        if (label.contains("所得税影响")) {
            return new FieldSpec("nri_tax", "所得税影响额", List.of("所得税影响额"));
        }
        if (label.contains("少数股东权益影响")) {
            return new FieldSpec("nri_minority_impact", "少数股东权益影响额", List.of("少数股东权益影响额"));
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
        int scope = switch (line.getScope() == null ? "" : line.getScope()) {
            case "consolidated" -> 100;
            case "summary" -> 40;
            case "unknown" -> 20;
            case "parent" -> 10;
            case "note" -> 0;
            default -> 0;
        };
        String snippet = line.getSnippet() == null ? "" : line.getSnippet();
        if (snippet.contains("归属于") || snippet.contains("扣除非经常")) {
            scope += 30;
        }
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
        line = line.replaceFirst("^[（(][一二三四五六七八九十0-9]+[)）]\\s*", "");
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

    private static StatementExtract withFile(StatementExtract body, String path, String sha256, int pageCount) {
        return StatementExtract.builder()
                .lines(body.getLines())
                .policyNotes(body.getPolicyNotes())
                .sourcePath(path)
                .sha256(sha256)
                .pageCount(pageCount)
                .build();
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static StatementExtract empty() {
        return StatementExtract.builder().lines(List.of()).policyNotes(List.of()).pageCount(0).build();
    }
}
