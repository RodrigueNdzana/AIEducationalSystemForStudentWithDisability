package tests;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.UploadResult;
import com.aiadaptive.dto.Dto.ValidationReport;
import com.aiadaptive.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContentServiceTest {
    private final Fixture f = new Fixture();

    private Content video(boolean captions) {
        Content c = new Content();
        c.setTitle("Lecture 1");
        c.setFormat(ContentFormat.VIDEO);
        c.setDifficulty(Difficulty.BEGINNER);
        c.setModuleId("M1");
        c.setFileName("lecture1.mp4");
        c.setHasCaptions(captions);
        return c;
    }

    @Test void videoWithoutCaptionsIsBlockedWith422() {
        ApiException e = assertThrows(ApiException.class, () -> f.content.upload(video(false), "u1", false));
        assertEquals(422, e.getStatus());
        ValidationReport r = (ValidationReport) e.getDetails();
        assertFalse(r.passed());
        assertTrue(r.errors().get(0).contains("captions"));
        assertEquals(0, f.contentRepo.findAll().size());
    }

    @Test void adminCanOverrideTheGate() {
        UploadResult r = f.content.upload(video(false), "admin1", true);
        assertTrue(r.published());
        assertFalse(r.report().passed());
        assertEquals(1, f.contentRepo.findPublished().size());
    }

    @Test void compliantContentIsPublished() {
        UploadResult r = f.content.upload(video(true), "edu1", false);
        assertTrue(r.report().passed());
        assertNotNull(r.content().getId());
        assertEquals("edu1", r.content().getUploadedBy());
    }

    @Test void imagesNeedAltTextAndFileTypesAreChecked() {
        Content c = video(true);
        c.setFormat(ContentFormat.TEXT);
        c.setFileName("notes.exe");
        c.setHasImages(true);
        ValidationReport r = f.content.validate(c);
        assertFalse(r.passed());
        assertEquals(2, r.errors().size());
    }

    @Test void audioNeedsTranscript() {
        Content c = new Content();
        c.setTitle("Podcast"); c.setModuleId("M2"); c.setFormat(ContentFormat.AUDIO); c.setFileName("p.mp3");
        assertFalse(f.content.validate(c).passed());
        c.setTranscript("Hello and welcome.");
        assertTrue(f.content.validate(c).passed());
    }

    @Test void updateCannotMakePublishedContentInaccessible() {
        Content saved = f.content.upload(video(true), "edu1", false).content();
        Content change = video(false);
        assertThrows(ApiException.class, () -> f.content.updateContent(saved.getId(), change));
    }

    @Test void missingContentIs404() {
        assertEquals(404, assertThrows(ApiException.class, () -> f.content.get("nope")).getStatus());
    }
}
