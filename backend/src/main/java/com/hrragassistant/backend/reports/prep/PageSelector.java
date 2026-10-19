package com.hrragassistant.backend.reports.prep;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the pages of a long annual report that matter for the analysis when
 * the whole file is too big for the AI provider: the primary financial
 * statements (and the pages right after them, since statements span pages),
 * key figures, the auditor's report, important notes and the outlook.
 * Works on English, Dutch and German reports. Pages are 0-based.
 */
public final class PageSelector {

    private record Rule(Pattern pattern, int weight) {
    }

    private static Rule rule(int weight, String... phrases) {
        return new Rule(Pattern.compile(String.join("|", phrases), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), weight);
    }

    /** The statements themselves: their pages and the next two are always taken first. */
    private static final Rule PRIMARY = rule(10,
            "balance sheet", "statement of financial position", "income statement", "statement of profit or loss",
            "profit and loss account", "statement of comprehensive income", "statement of cash flows", "cash flow statement",
            "statement of changes in equity",
            "geconsolideerde balans", "\\bbalans\\b", "winst-en-verliesrekening", "kasstroomoverzicht", "mutatieoverzicht",
            "konzernbilanz", "\\bbilanz\\b", "gewinn- und verlustrechnung", "kapitalflussrechnung", "eigenkapitalveränderungsrechnung");

    private static final List<Rule> SECONDARY = List.of(
            rule(5, "key figures", "financial highlights", "at a glance", "five-year summary", "kerncijfers", "kennzahlen", "auf einen blick"),
            rule(4, "independent auditor", "auditor's report", "auditor’s report", "controleverklaring", "bestätigungsvermerk"),
            rule(3, "notes to the consolidated", "segment information", "borrowings", "financial risk", "going concern",
                    "contingent liabilit", "toelichting", "anhang"),
            rule(2, "outlook", "principal risks", "risk factors", "letter from", "chief executive", "strategy", "vooruitzichten", "ausblick"));

    private static final Pattern CONTENTS = Pattern.compile("table of contents|\\bcontents\\b|inhoudsopgave|\\binhoud\\b|inhaltsverzeichnis",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NUMBER = Pattern.compile("\\(?-?\\d{1,3}(?:[.,\\s]\\d{3})+(?:[.,]\\d+)?\\)?|\\b\\d+[.,]\\d+\\b");

    public record Selection(SortedSet<Integer> pages, boolean foundStatements, boolean hasText) {
    }

    private PageSelector() {
    }

    /** Chooses at most {@code budget} pages out of {@code pageTexts.size()}. */
    public static Selection select(List<String> pageTexts, int budget) {
        int n = pageTexts.size();
        SortedSet<Integer> chosen = new TreeSet<>();
        if (n == 0 || budget <= 0) {
            return new Selection(chosen, false, false);
        }
        if (n <= budget) {
            for (int i = 0; i < n; i++) chosen.add(i);
            return new Selection(chosen, false, totalChars(pageTexts) > 0);
        }
        boolean hasText = totalChars(pageTexts) >= 200 * Math.min(n, 20);
        if (!hasText) {
            // A scan without a text layer: take the opening pages (summary, key figures) and spread the rest.
            for (int i = 0; i < Math.min(3, budget); i++) chosen.add(i);
            double step = (double) n / Math.max(1, budget - chosen.size());
            for (double p = 3; chosen.size() < budget && p < n; p += step) chosen.add((int) p);
            return new Selection(chosen, false, false);
        }

        int[] score = new int[n];
        boolean[] primary = new boolean[n];
        for (int i = 0; i < n; i++) {
            String text = pageTexts.get(i) == null ? "" : pageTexts.get(i).toLowerCase(Locale.ROOT);
            int primaryHits = count(PRIMARY.pattern(), text);
            int s = Math.min(primaryHits, 3) * PRIMARY.weight();
            for (Rule r : SECONDARY) {
                s += Math.min(count(r.pattern(), text), 2) * r.weight();
            }
            int numbers = count(NUMBER, text);
            if (numbers >= 40) s += 4;
            else if (numbers >= 15) s += 2;
            // A table of contents names every statement but holds none of them.
            if (CONTENTS.matcher(text).find() && primaryHits >= 2 && numbers < 40) {
                s /= 4;
                primaryHits = 0;
            }
            primary[i] = primaryHits > 0 && numbers >= 15;
            score[i] = s;
        }

        boolean found = false;
        // 1. The opening pages: company name, period, headline figures.
        for (int i = 0; i < Math.min(3, n) && chosen.size() < budget; i++) chosen.add(i);
        // 2. Statement pages plus the two after (and one before) them, strongest first.
        List<Integer> statementPages = new ArrayList<>();
        for (int i = 0; i < n; i++) if (primary[i]) statementPages.add(i);
        statementPages.sort(Comparator.comparingInt((Integer i) -> -score[i]));
        for (int p : statementPages) {
            found = true;
            for (int q = p - 1; q <= p + 2 && chosen.size() < budget; q++) {
                if (q >= 0 && q < n) chosen.add(q);
            }
        }
        // 3. Fill the rest of the budget by score.
        List<Integer> rest = new ArrayList<>();
        for (int i = 0; i < n; i++) if (!chosen.contains(i) && score[i] > 0) rest.add(i);
        rest.sort(Comparator.comparingInt((Integer i) -> -score[i]).thenComparingInt(i -> i));
        for (int p : rest) {
            if (chosen.size() >= budget) break;
            chosen.add(p);
        }
        return new Selection(chosen, found, true);
    }

    /**
     * Adds further pages in document order until {@code budget} is reached, so
     * a provider that accepts many pages also gets the surrounding narrative.
     */
    public static SortedSet<Integer> fill(SortedSet<Integer> core, int pageCount, int budget) {
        SortedSet<Integer> out = new TreeSet<>(core);
        for (int i = 0; i < pageCount && out.size() < budget; i++) {
            out.add(i);
        }
        return out;
    }

    /** 1-based ranges for people: {0,1,2,141,142,143} -> "1–3, 142–144". */
    public static String ranges(SortedSet<Integer> pages) {
        StringBuilder sb = new StringBuilder();
        Integer start = null, prev = null;
        for (int p : pages) {
            if (start == null) {
                start = prev = p;
            } else if (p == prev + 1) {
                prev = p;
            } else {
                append(sb, start, prev);
                start = prev = p;
            }
        }
        if (start != null) append(sb, start, prev);
        return sb.toString();
    }

    private static void append(StringBuilder sb, int start, int end) {
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(start + 1);
        if (end > start) sb.append('–').append(end + 1);
    }

    private static int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int c = 0;
        while (m.find()) c++;
        return c;
    }

    private static long totalChars(List<String> texts) {
        long t = 0;
        for (String s : texts) t += s == null ? 0 : s.strip().length();
        return t;
    }
}
