package tests;

import com.aiadaptive.domain.Content;
import com.aiadaptive.dto.Dto.AdaptedContent;
import com.aiadaptive.exception.ApiException;
import com.aiadaptive.services.ContentAdaptationService;
import com.aiadaptive.services.ContentAdaptationService.Simplified;
import com.aiadaptive.services.Readability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContentAdaptationServiceTest {
    private final ContentAdaptationService svc = new ContentAdaptationService();

    private static final String DENSE =
            "A relational database organises information into tables, and each table consists of rows and columns that "
            + "represent records and attributes. Structured Query Language is utilized to obtain data from these tables, "
            + "and queries can combine several tables by matching a key that appears in both of them.";

    @Test void simplificationLowersTheReadingLevel() {
        Simplified s = svc.simplify(DENSE);
        assertTrue(s.gradeAfter() < s.gradeBefore(), "after " + s.gradeAfter() + " before " + s.gradeBefore());
        assertFalse(s.simplified().toLowerCase().contains("utilized"));
        assertTrue(s.simplified().contains("used") || s.simplified().contains("use"));
    }

    @Test void simplificationOfEmptyTextIsSafe() {
        assertEquals("", svc.simplify("").simplified());
        assertEquals("", svc.simplify(null).simplified());
    }

    @Test void readabilityGradeIsLowerForShortWords() {
        double easy = Readability.gradeLevel("The cat sat on the mat. It is a big cat.");
        double hard = Readability.gradeLevel("Notwithstanding considerable methodological limitations, researchers demonstrated "
                + "multidimensional interdependencies throughout organisational infrastructures.");
        assertTrue(easy < hard);
    }

    @Test void captionsAreTimedAndCoverAllWords() {
        String t = "one two three four five six seven eight nine ten eleven twelve thirteen";
        var segs = svc.captions(t);
        assertEquals(2, segs.size());
        assertEquals(0.0, segs.get(0).startSeconds(), 0.001);
        assertEquals(segs.get(0).endSeconds(), segs.get(1).startSeconds(), 0.11);
        assertTrue(segs.get(1).text().endsWith("thirteen"));
    }

    @Test void audioScriptExpandsSymbols() {
        String s = svc.audioScript("Use arrays, e.g. lists & maps. Pass mark is 50%.");
        assertTrue(s.contains("for example"));
        assertTrue(s.contains("and"));
        assertTrue(s.contains("percent"));
        assertTrue(svc.estimateSeconds(s) > 0);
    }

    @Test void adaptModes() {
        Content c = new Content();
        c.setId("X"); c.setTitle("T"); c.setBody(DENSE);
        assertNotNull(svc.adapt(c, "TEXT").text());
        AdaptedContent simple = svc.adapt(c, "simplified_text");
        assertNotNull(simple.gradeAfter());
        assertNotNull(svc.adapt(c, "AUDIO").audioScript());
        assertFalse(svc.adapt(c, "CAPTIONS").captions().isEmpty());
        assertThrows(ApiException.class, () -> svc.adapt(c, "HOLOGRAM"));
    }
}
