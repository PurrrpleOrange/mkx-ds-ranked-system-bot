package com.mkx.ranked.discord.formatter;

import java.util.ArrayList;
import java.util.List;

final class DiscordTableFormatter {

    enum Alignment {
        LEFT,
        RIGHT
    }

    record Column(String title, Alignment alignment) {

        static Column left(String title) {
            return new Column(title, Alignment.LEFT);
        }

        static Column right(String title) {
            return new Column(title, Alignment.RIGHT);
        }
    }

    record Group(String label, List<List<String>> rows) {
    }

    private DiscordTableFormatter() {
    }

    static List<String> render(
            String heading,
            String intro,
            List<Column> columns,
            List<List<String>> sourceRows,
            int maxLength
    ) {
        return renderGrouped(
                heading,
                intro,
                columns,
                List.of(new Group(null, sourceRows)),
                maxLength
        );
    }

    static List<String> renderGrouped(
            String heading,
            String intro,
            List<Column> columns,
            List<Group> sourceGroups,
            int maxLength
    ) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Table must contain at least one column.");
        }

        List<Group> groups = sourceGroups.stream()
                .map(group -> new Group(
                        sanitizeLabel(group.label()),
                        group.rows().stream()
                                .map(row -> normalizeRow(row, columns.size()))
                                .toList()
                ))
                .filter(group -> !group.rows().isEmpty())
                .toList();
        List<List<String>> rows = groups.stream().flatMap(group -> group.rows().stream()).toList();
        int[] widths = calculateWidths(columns, rows);
        String header = formatRow(
                columns.stream().map(Column::title).toList(),
                columns,
                widths
        );
        String separator = formatSeparator(widths);

        List<String> chunks = new ArrayList<>();
        StringBuilder current = startChunk(heading, intro, false, header, separator);
        int rowsInCurrentChunk = 0;
        for (Group group : groups) {
            boolean groupLabelWritten = false;
            for (List<String> row : group.rows()) {
                String label = groupLabelWritten ? "" : formatGroupLabel(group.label(), rowsInCurrentChunk > 0);
                String line = formatRow(row, columns, widths) + "\n";
                if (rowsInCurrentChunk > 0
                        && current.length() + label.length() + line.length() + 3 > maxLength) {
                    current.append("```");
                    chunks.add(current.toString());
                    current = startChunk(heading, null, true, header, separator);
                    rowsInCurrentChunk = 0;
                    label = formatGroupLabel(group.label(), false);
                }
                current.append(label).append(line);
                rowsInCurrentChunk++;
                groupLabelWritten = true;
            }
        }
        current.append("```");
        chunks.add(current.toString());
        return chunks;
    }

    private static String formatGroupLabel(String label, boolean prependBlankLine) {
        if (label == null || label.isBlank()) {
            return "";
        }
        return (prependBlankLine ? "\n" : "") + label + "\n";
    }

    private static String sanitizeLabel(String label) {
        return label == null ? null : sanitizeCell(label);
    }

    private static List<String> normalizeRow(List<String> row, int expectedSize) {
        if (row.size() != expectedSize) {
            throw new IllegalArgumentException("Table row size must match column count.");
        }
        return row.stream().map(DiscordTableFormatter::sanitizeCell).toList();
    }

    private static StringBuilder startChunk(
            String heading,
            String intro,
            boolean continuation,
            String header,
            String separator
    ) {
        StringBuilder result = new StringBuilder();
        if (heading != null && !heading.isBlank()) {
            result.append("**").append(heading);
            if (continuation) {
                result.append(" — ПРОДОЛЖЕНИЕ");
            }
            result.append("**\n\n");
        }
        if (intro != null && !intro.isBlank()) {
            result.append(intro.strip()).append("\n\n");
        }
        return result.append("```text\n")
                .append(header).append('\n')
                .append(separator).append('\n');
    }

    private static int[] calculateWidths(List<Column> columns, List<List<String>> rows) {
        int[] widths = new int[columns.size()];
        for (int column = 0; column < columns.size(); column++) {
            widths[column] = displayLength(columns.get(column).title());
            for (List<String> row : rows) {
                widths[column] = Math.max(widths[column], displayLength(row.get(column)));
            }
        }
        return widths;
    }

    private static String formatRow(List<String> cells, List<Column> columns, int[] widths) {
        List<String> formatted = new ArrayList<>();
        for (int column = 0; column < columns.size(); column++) {
            String value = cells.get(column);
            formatted.add(columns.get(column).alignment() == Alignment.RIGHT
                    ? padLeft(value, widths[column])
                    : padRight(value, widths[column]));
        }
        return String.join("  ", formatted);
    }

    private static String formatSeparator(int[] widths) {
        List<String> columns = new ArrayList<>();
        for (int width : widths) {
            columns.add("-".repeat(width));
        }
        return String.join("  ", columns);
    }

    private static String padLeft(String value, int width) {
        return " ".repeat(width - displayLength(value)) + value;
    }

    private static String padRight(String value, int width) {
        return value + " ".repeat(width - displayLength(value));
    }

    private static int displayLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static String sanitizeCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ")
                .replace("```", "'''")
                .trim();
    }
}
