package com.aiadaptive.services;

import com.aiadaptive.domain.Content;
import com.aiadaptive.dto.Dto.AdaptedContent;
import com.aiadaptive.dto.Dto.CaptionSegment;
import com.aiadaptive.exception.ApiException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FR-04: delivers content in several accessible formats. The NLP here is deliberately rule-based
 * (plain-language word substitution + sentence splitting) so it is deterministic and explainable;
 * the AI model service described in the design can replace {@link #simplify(String)} behind the same API.
 */
@Service
public class ContentAdaptationService {

    public static final double TARGET_GRADE = 8.0;
    private static final double WORDS_PER_SECOND = 2.5;   // 150 wpm narration / caption pacing
    private static final int CAPTION_WORDS = 10;

    private static final Map<Pattern, String> PLAIN = new LinkedHashMap<>();
    static {
        String[][] pairs = {
            // inflected forms first so "utilized" becomes "used", not "utilized"
            {"utilized", "used"}, {"utilizes", "uses"}, {"utilizing", "using"}, {"required", "needed"},
            {"requires", "needs"}, {"demonstrated", "showed"}, {"demonstrates", "shows"}, {"obtained", "got"},
            {"facilitates", "helps"}, {"implemented", "carried out"}, {"determined", "found out"},
            {"constructed", "built"}, {"allocated", "given"}, {"attempted", "tried"}, {"commenced", "started"},
            {"in order to", "to"}, {"prior to", "before"}, {"utilize", "use"}, {"approximately", "about"},
            {"demonstrate", "show"}, {"subsequently", "then"}, {"commence", "start"}, {"terminate", "end"},
            {"additional", "more"}, {"numerous", "many"}, {"facilitate", "help"}, {"require", "need"},
            {"obtain", "get"}, {"sufficient", "enough"}, {"methodology", "method"}, {"implement", "carry out"},
            {"consequently", "so"}, {"therefore", "so"}, {"furthermore", "also"}, {"fundamental", "basic"},
            {"modification", "change"}, {"individuals", "people"}, {"assistance", "help"},
            {"purchase", "buy"}, {"attempt", "try"}, {"regarding", "about"}, {"commonly", "often"},
            {"construct", "build"}, {"determine", "find out"}, {"characteristics", "features"},
            {"represents", "is"}, {"optimal", "best"}, {"efficient", "fast"}, {"allocate", "give"}
        };
        for (String[] p : pairs) {
            PLAIN.put(Pattern.compile("\\b" + Pattern.quote(p[0]) + "\\b", Pattern.CASE_INSENSITIVE), p[1]);
        }
    }

    public record Simplified(String original, String simplified, double gradeBefore, double gradeAfter) {}

    public Simplified simplify(String text) {
        String src = text == null ? "" : text.trim();
        double before = Readability.gradeLevel(src);
        String t = src;
        for (Map.Entry<Pattern, String> e : PLAIN.entrySet()) {
            Matcher m = e.getKey().matcher(t);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String rep = e.getValue();
                if (Character.isUpperCase(m.group().charAt(0))) {
                    rep = Character.toUpperCase(rep.charAt(0)) + rep.substring(1);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            t = sb.toString();
        }
        List<String> out = new ArrayList<>();
        for (String s : Readability.sentences(t)) splitLong(s, out, 0);
        String result = String.join(" ", out);
        return new Simplified(src, result, before, Readability.gradeLevel(result));
    }

    private void splitLong(String sentence, List<String> out, int depth) {
        List<String> words = Readability.words(sentence);
        if (words.size() <= 16 || depth >= 3) {
            out.add(sentence);
            return;
        }
        String[] delimiters = {", and ", ", but ", "; ", ", which ", ", because ", ", so "};
        int mid = sentence.length() / 2;
        int bestIdx = -1;
        String bestDelim = null;
        for (String d : delimiters) {
            int from = 0;
            int idx;
            while ((idx = sentence.indexOf(d, from)) >= 0) {
                if (idx > 15 && idx < sentence.length() - 15
                        && (bestIdx < 0 || Math.abs(idx - mid) < Math.abs(bestIdx - mid))) {
                    bestIdx = idx;
                    bestDelim = d;
                }
                from = idx + d.length();
            }
        }
        if (bestIdx < 0) {
            out.add(sentence);
            return;
        }
        String first = sentence.substring(0, bestIdx).trim();
        String rest = sentence.substring(bestIdx + bestDelim.length()).trim();
        if (!first.endsWith(".") && !first.endsWith("!") && !first.endsWith("?")) first += ".";
        if (bestDelim.equals(", which ")) rest = "This " + rest;
        else if (bestDelim.equals(", but ")) rest = "But " + rest;
        else if (bestDelim.equals(", because ")) rest = "This is because " + rest;
        else if (bestDelim.equals(", so ")) rest = "So " + rest;
        if (!rest.isEmpty()) rest = Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
        splitLong(first, out, depth + 1);
        splitLong(rest, out, depth + 1);
    }

    /** Text prepared for text-to-speech: symbols and abbreviations expanded so they are read aloud correctly. */
    public String audioScript(String text) {
        String t = text == null ? "" : text;
        t = t.replace("e.g.", "for example").replace("i.e.", "that is").replace("etc.", "and so on")
                .replace("&", " and ").replace("%", " percent").replace("+", " plus ")
                .replaceAll("\\s+", " ").trim();
        return t;
    }

    public int estimateSeconds(String text) {
        return (int) Math.ceil(Readability.words(text).size() / WORDS_PER_SECOND);
    }

    public List<CaptionSegment> captions(String transcript) {
        List<CaptionSegment> segs = new ArrayList<>();
        List<String> words = new ArrayList<>();
        if (transcript != null) for (String w : transcript.trim().split("\\s+")) if (!w.isEmpty()) words.add(w);
        double t = 0;
        for (int i = 0; i < words.size(); i += CAPTION_WORDS) {
            List<String> chunk = words.subList(i, Math.min(words.size(), i + CAPTION_WORDS));
            double dur = chunk.size() / WORDS_PER_SECOND;
            segs.add(new CaptionSegment(round1(t), round1(t + dur), String.join(" ", chunk)));
            t += dur;
        }
        return segs;
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    /** mode: TEXT | SIMPLIFIED_TEXT | AUDIO | CAPTIONS | HIGH_CONTRAST */
    public AdaptedContent adapt(Content c, String mode) {
        String m = mode == null ? "TEXT" : mode.toUpperCase();
        String source = (c.getBody() == null || c.getBody().isBlank()) ? c.getTranscript() : c.getBody();
        if (source == null) source = "";
        switch (m) {
            case "TEXT":
            case "HIGH_CONTRAST":
                return new AdaptedContent(c.getId(), c.getTitle(), m, source, null, null, null,
                        Readability.gradeLevel(source), null,
                        m.equals("HIGH_CONTRAST") ? "Apply a high-contrast theme in Accessibility Settings." : null);
            case "SIMPLIFIED_TEXT": {
                Simplified s = simplify(source);
                String note = s.gradeAfter() <= TARGET_GRADE ? null
                        : "Reached grade " + s.gradeAfter() + " (target " + TARGET_GRADE
                          + "); dense technical content may stay above the target.";
                return new AdaptedContent(c.getId(), c.getTitle(), m, s.simplified(), null, null, null,
                        s.gradeBefore(), s.gradeAfter(), note);
            }
            case "AUDIO": {
                String script = audioScript(source);
                return new AdaptedContent(c.getId(), c.getTitle(), m, source, script, estimateSeconds(script),
                        null, null, null, "Play with the browser's text-to-speech (speechSynthesis).");
            }
            case "CAPTIONS": {
                String tr = (c.getTranscript() == null || c.getTranscript().isBlank()) ? source : c.getTranscript();
                if (tr.isBlank()) throw ApiException.badRequest("This content has no transcript to caption");
                return new AdaptedContent(c.getId(), c.getTitle(), m, tr, null, null, captions(tr), null, null, null);
            }
            default:
                throw ApiException.badRequest("Unknown mode '" + mode
                        + "'. Use TEXT, SIMPLIFIED_TEXT, AUDIO, CAPTIONS or HIGH_CONTRAST");
        }
    }
}
