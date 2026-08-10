package com.automatedinterview.document;

import java.text.Normalizer;

public final class DocumentNormalizer {
    private DocumentNormalizer() { }

    public static String normalize(String value) {
        if (value == null) return "";
        String lf = repairCommonMojibake(value.replace("\r\n", "\n").replace('\r', '\n'));
        StringBuilder result = new StringBuilder(lf.length());
        String[] lines = lf.split("\\n", -1);
        int first = 0;
        int last = lines.length;
        while (first < last && lines[first].isBlank()) first++;
        while (last > first && lines[last - 1].isBlank()) last--;
        for (int index = first; index < last; index++) {
            String line = lines[index];
            if (line.codePoints().anyMatch(character -> Character.isISOControl(character) && character != '\t')) throw new IllegalArgumentException("INVALID_DOCUMENT");
            result.append(Normalizer.normalize(line.replaceAll("\\s+", " ").strip(), Normalizer.Form.NFC));
            if (index + 1 < last) result.append('\n');
        }
        String normalized = result.toString().replaceAll("\\n{3,}", "\n\n");
        if (normalized.codePointCount(0, normalized.length()) > 30_000) throw new IllegalArgumentException("DOCUMENT_LIMIT_EXCEEDED");
        return normalized;
    }

    private static String repairCommonMojibake(String value) {
        return value.replace("â€“", "-").replace("â€”", "-")
            .replace("â€˜", "'").replace("â€™", "'")
            .replace("â€œ", "\"").replace("â€", "\"")
            .replace("â€¦", "...").replace("ï¿½", "")
            .replace('\uFFFD', ' ');
    }
}
