package com.finai.analysis;

import lombok.Value;
import java.util.List;

@Value
public class PageText {
    int pageNumber;
    String text;
    List<PositionedRow> rows;

    public PageText(int pageNumber, String text) {
        this(pageNumber, text, List.of());
    }

    public PageText(int pageNumber, String text, List<PositionedRow> rows) {
        this.pageNumber = pageNumber;
        this.text = text;
        this.rows = rows;
    }

    public record PositionedRow(String text, List<NumberCell> numbers) {}
    public record NumberCell(String text, float x, float endX) {}
}
