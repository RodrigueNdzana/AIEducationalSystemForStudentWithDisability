package tests;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.FeedbackRequest;
import com.aiadaptive.dto.Dto.FeedbackView;
import com.aiadaptive.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FeedbackServiceTest {
    private final Fixture f = Fixture.seeded();
    private final String edna = f.idOf("educator@adaptiq.ac.za");
    private final String thandi = f.idOf("thandi@student.ac.za");   // VISUAL
    private String cognitiveTextOnly() {
        String id = f.auth.register("Cog Student", "cog@student.ac.za", "Secret123").id();
        f.profiles.save(id, new com.aiadaptive.dto.Dto.ProfileRequest(java.util.Set.of(DisabilityCategory.COGNITIVE), null,
                java.util.List.of(ContentFormat.TEXT), null, null, null, null, null, null));
        return id;
    }
    private final String sipho = f.idOf("sipho@student.ac.za");     // HEARING
    private final String neo = f.idOf("neo@student.ac.za");         // none

    @Test void audioFeedbackOverFiveMinutesIsRejected() {
        ApiException e = assertThrows(ApiException.class, () -> f.feedback.submit(edna,
                new FeedbackRequest(neo, FeedbackType.AUDIO, "transcript", 312, null)));
        assertEquals(400, e.getStatus());
        assertTrue(e.getMessage().contains("5 minutes"));
        f.feedback.submit(edna, new FeedbackRequest(neo, FeedbackType.AUDIO, "transcript", 300, null));   // boundary is allowed
    }

    @Test void audioFeedbackNeedsATranscript() {
        assertThrows(ApiException.class, () -> f.feedback.submit(edna,
                new FeedbackRequest(neo, FeedbackType.AUDIO, "", 60, null)));
    }

    @Test void validatesTypeSpecificFields() {
        assertThrows(ApiException.class, () -> f.feedback.submit(edna, new FeedbackRequest(neo, FeedbackType.TEXT, " ", null, null)));
        assertThrows(ApiException.class, () -> f.feedback.submit(edna, new FeedbackRequest(neo, FeedbackType.FILE, null, null, null)));
        assertThrows(ApiException.class, () -> f.feedback.submit(edna, new FeedbackRequest(edna, FeedbackType.TEXT, "hi", null, null)));
        assertThrows(ApiException.class, () -> f.feedback.submit(edna, new FeedbackRequest("ghost", FeedbackType.TEXT, "hi", null, null)));
    }

    @Test void feedbackIsRenderedInTheStudentsAccessibleFormat() {
        String text = "Your answer utilized a loop, but you should demonstrate the base case, and then explain why the loop terminates.";
        String ayanda = cognitiveTextOnly();
        for (String s : new String[]{thandi, ayanda, sipho, neo}) {
            f.feedback.submit(edna, new FeedbackRequest(s, FeedbackType.TEXT, text, null, null));
        }
        FeedbackView v = f.feedback.inbox(thandi).get(0);
        assertEquals("AUDIO_TTS", v.renderedFormat());
        assertNotNull(v.audioScript());
        FeedbackView c = f.feedback.inbox(ayanda).get(0);
        assertEquals("SIMPLIFIED_TEXT", c.renderedFormat());
        assertFalse(c.renderedText().contains("utilized"));
        assertEquals("TEXT_TRANSCRIPT", f.feedback.inbox(sipho).get(0).renderedFormat());
        assertEquals("TEXT", f.feedback.inbox(neo).get(0).renderedFormat());
    }

    @Test void studentIsNotifiedAndCanAcknowledgeAndRespond() {
        Feedback fb = f.feedback.submit(edna, new FeedbackRequest(neo, FeedbackType.TEXT, "Well done.", null, null));
        assertTrue(f.notifications.forUser(neo).stream().anyMatch(n -> n.getMessage().contains("feedback")));
        Feedback acked = f.feedback.acknowledge(fb.getId(), neo, "Thank you!");
        assertTrue(acked.isAcknowledged());
        assertEquals("Thank you!", acked.getStudentResponse());
        assertTrue(f.notifications.forUser(edna).stream().anyMatch(n -> n.getMessage().contains("responded")));
        assertThrows(ApiException.class, () -> f.feedback.acknowledge(fb.getId(), thandi, "x"));
    }
}
