package com.hrragassistant.backend.reports.prep;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageSelectorTest {

    static final String FILLER = "Our people and culture make the difference in everything we do. ".repeat(20);
    static final String NUMBERS = " 1,234 5,678 9,012 3,456 7,890 ".repeat(12);

    static List<String> annualReport(int pages) {
        List<String> p = new ArrayList<>();
        for (int i = 0; i < pages; i++) p.add(FILLER);
        p.set(2, "Table of contents  Consolidated balance sheet 150  Consolidated income statement 151  Statement of cash flows 153");
        p.set(5, "Key figures 2025" + NUMBERS);
        p.set(140, "Independent auditor's report" + FILLER);
        p.set(150, "Consolidated balance sheet as at 31 December" + NUMBERS);
        p.set(151, "Consolidated income statement" + NUMBERS);
        p.set(153, "Consolidated statement of cash flows" + NUMBERS);
        return p;
    }

    @Test
    void picksStatementsKeyFiguresAndAuditorWithinBudget() {
        PageSelector.Selection s = PageSelector.select(annualReport(312), 40);
        assertThat(s.foundStatements()).isTrue();
        assertThat(s.pages()).hasSizeLessThanOrEqualTo(40).contains(0, 1, 2, 5, 140, 149, 150, 151, 152, 153, 154, 155);
    }

    @Test
    void smallBudgetStillKeepsTheStatements() {
        assertThat(PageSelector.select(annualReport(312), 8).pages()).hasSizeLessThanOrEqualTo(8).contains(150, 151);
    }

    @Test
    void readsDutchStatementTitles() {
        List<String> p = new ArrayList<>();
        for (int i = 0; i < 200; i++) p.add(FILLER);
        p.set(120, "Geconsolideerde balans per 31 december" + NUMBERS);
        p.set(122, "Geconsolideerde winst-en-verliesrekening" + NUMBERS);
        assertThat(PageSelector.select(p, 20).pages()).contains(120, 122);
    }

    @Test
    void spreadsAcrossScansWithoutText() {
        List<String> scan = new ArrayList<>();
        for (int i = 0; i < 300; i++) scan.add("");
        PageSelector.Selection s = PageSelector.select(scan, 50);
        assertThat(s.hasText()).isFalse();
        assertThat(s.pages()).hasSize(50).contains(0);
        assertThat(s.pages().last()).isGreaterThan(250);
    }

    @Test
    void fillsAndFormatsRanges() {
        assertThat(PageSelector.fill(new TreeSet<>(List.of(10, 11)), 100, 5)).containsExactly(0, 1, 2, 10, 11);
        assertThat(PageSelector.ranges(new TreeSet<>(List.of(0, 1, 2, 141, 142, 143, 200)))).isEqualTo("1–3, 142–144, 201");
    }
}
