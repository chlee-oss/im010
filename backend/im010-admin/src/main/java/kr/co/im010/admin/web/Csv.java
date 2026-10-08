package kr.co.im010.admin.web;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 엑셀 다운로드: UTF-8 BOM 이 붙은 CSV (엑셀에서 한글이 깨지지 않고 바로 열린다).
 * 수식으로 해석될 수 있는 값(=, +, -, @ 로 시작)은 앞에 ' 를 붙여 CSV 수식 주입을 막는다.
 */
public final class Csv {

    private Csv() {
    }

    public static void write(HttpServletResponse response, String filename, List<String> header, List<List<Object>> rows)
            throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("Cache-Control", "no-store");
        Writer w = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        w.write('﻿');
        line(w, header.stream().map(h -> (Object) h).toList());
        for (List<Object> row : rows) {
            line(w, row);
        }
        w.flush();
    }

    private static void line(Writer w, List<Object> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                w.write(',');
            }
            w.write(cell(cells.get(i)));
        }
        w.write("\r\n");
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !(value instanceof Number)) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
