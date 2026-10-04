package com.finai.analysis;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional real-sample smoke: runs only when official PDFs exist under data/raw.
 * Does not assert financial accuracy; C/E review is required before freezing baselines.
 */
class RealSampleExtractSmokeTest {

    private final StatementExtractor extractor = new StatementExtractor();

    @Test
    void extractsStableLinesFromMaotai2023AnnualWhenPdfPresent() throws Exception {
        Path pdf = resolveRawPdf("600519_2023_annual.pdf");
        Assumptions.assumeTrue(Files.isRegularFile(pdf), "Official Maotai PDF not present; skip real-sample smoke");

        StatementExtract first = extractor.extract(pdf.toString());
        StatementExtract second = extractor.extract(pdf.toString());

        assertThat(first.getLines()).isNotEmpty();
        assertThat(signature(first)).isEqualTo(signature(second));

        Map<String, ExtractedLine> byId = first.getLines().stream()
                .collect(Collectors.toMap(ExtractedLine::getFieldId, line -> line, (a, b) -> a));
        assertThat(byId).containsKeys("revenue", "total_assets");
        assertThat(byId.get("revenue").getCurrent()).isNotNull();
        assertThat(byId.get("revenue").getPage()).isPositive();
        assertThat(byId.get("revenue").getSnippet()).isNotBlank();
    }

    @Test
    void extractsStableLinesFromWuliangye2023AnnualWhenPdfPresent() throws Exception {
        Path pdf = resolveRawPdf("000858_2023_annual.pdf");
        Assumptions.assumeTrue(Files.isRegularFile(pdf), "Official Wuliangye PDF not present; skip real-sample smoke");

        StatementExtract first = extractor.extract(pdf.toString());
        StatementExtract second = extractor.extract(pdf.toString());

        assertThat(first.getLines()).isNotEmpty();
        assertThat(signature(first)).isEqualTo(signature(second));
        assertThat(first.getLines().stream().anyMatch(line -> "revenue".equals(line.getFieldId()))).isTrue();
    }

    private static Path resolveRawPdf(String fileName) {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path direct = cwd.resolve("data").resolve("raw").resolve(fileName);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path fromBackend = cwd.resolve("..").resolve("data").resolve("raw").resolve(fileName).normalize();
        return fromBackend;
    }

    private static List<String> signature(StatementExtract extract) {
        return extract.getLines().stream()
                .sorted(Comparator.comparing(ExtractedLine::getFieldId))
                .map(line -> line.getFieldId() + "|"
                        + stringify(line.getCurrent()) + "|"
                        + stringify(line.getPrior()) + "|"
                        + line.getPage())
                .collect(Collectors.toList());
    }

    private static String stringify(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }
}
