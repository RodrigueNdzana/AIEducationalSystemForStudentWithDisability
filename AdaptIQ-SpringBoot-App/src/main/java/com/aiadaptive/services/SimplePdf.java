package com.aiadaptive.services;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Minimal dependency-free PDF writer (text only, Helvetica) used for the administrator report export. */
public final class SimplePdf {
    private SimplePdf() {}

    private static final int LINES_PER_PAGE = 52;

    public static byte[] write(String title, List<String> lines) {
        List<List<String>> pages = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        cur.add(title);
        cur.add("");
        for (String l : lines) {
            for (String wrapped : wrap(l, 95)) {
                if (cur.size() >= LINES_PER_PAGE) {
                    pages.add(cur);
                    cur = new ArrayList<>();
                }
                cur.add(wrapped);
            }
        }
        pages.add(cur);

        // object layout: 1 catalog, 2 pages, 3 font, then (page, content) pairs
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) kids.append(4 + i * 2).append(" 0 R ");
        objects.add("<< /Type /Pages /Kids [ " + kids + "] /Count " + pages.size() + " >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        for (int i = 0; i < pages.size(); i++) {
            int contentObj = 5 + i * 2;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents "
                    + contentObj + " 0 R >>");
            StringBuilder s = new StringBuilder("BT /F1 10 Tf 50 800 Td 14 TL\n");
            for (String l : pages.get(i)) s.append("(").append(escape(l)).append(") Tj T*\n");
            s.append("ET");
            byte[] bytes = s.toString().getBytes(StandardCharsets.ISO_8859_1);
            objects.add("<< /Length " + bytes.length + " >>\nstream\n" + s + "\nendstream");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int off : offsets) x.append(String.format("%010d 00000 n \n", off));
        x.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        write(out, x.toString());
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(b, 0, b.length);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') sb.append('\\').append(c);
            else if (c < 32 || c > 126) sb.append('?');
            else sb.append(c);
        }
        return sb.toString();
    }

    private static List<String> wrap(String line, int width) {
        List<String> out = new ArrayList<>();
        String rest = line == null ? "" : line;
        while (rest.length() > width) {
            int cut = rest.lastIndexOf(' ', width);
            if (cut <= 0) cut = width;
            out.add(rest.substring(0, cut));
            rest = rest.substring(cut).trim();
        }
        out.add(rest);
        return out;
    }
}
