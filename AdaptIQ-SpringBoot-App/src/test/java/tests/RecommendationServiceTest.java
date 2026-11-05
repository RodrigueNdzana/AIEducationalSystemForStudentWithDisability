package tests;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.ProfileRequest;
import com.aiadaptive.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class RecommendationServiceTest {

    private Content content(String id, ContentFormat f, boolean captions, boolean images, boolean alt, boolean narration) {
        Content c = new Content();
        c.setId(id); c.setTitle(id); c.setFormat(f); c.setDifficulty(Difficulty.BEGINNER);
        c.setModuleId("M-" + id); c.setPublished(true);
        c.setHasCaptions(captions); c.setHasImages(images); c.setHasAltText(alt); c.setHasAudioNarration(narration);
        return c;
    }

    private Fixture withStudent(Set<DisabilityCategory> cats, List<ContentFormat> prefs) {
        Fixture f = new Fixture();
        String id = f.auth.register("S", "s@x.ac.za", "Secret123").id();
        f.profiles.save(id, new ProfileRequest(cats, null, prefs, null, null, null, null, null, null));
        return f;
    }

    @Test void hardFilterNeverRecommendsUncaptionedVideoToHearingStudent() {
        Fixture f = withStudent(Set.of(DisabilityCategory.HEARING), List.of(ContentFormat.VIDEO));
        f.contentRepo.save(content("v-nocc", ContentFormat.VIDEO, false, false, false, false));
        f.contentRepo.save(content("v-cc", ContentFormat.VIDEO, true, false, false, false));
        f.contentRepo.save(content("t1", ContentFormat.TEXT, false, false, false, false));
        String sid = f.idOf("s@x.ac.za");
        var recs = f.recommendations.refresh(sid);
        assertTrue(recs.stream().noneMatch(r -> r.getContentId().equals("v-nocc")));
        assertTrue(recs.stream().anyMatch(r -> r.getContentId().equals("v-cc")));
    }

    @Test void visualStudentGetsNoImagesWithoutAltText() {
        Fixture f = withStudent(Set.of(DisabilityCategory.VISUAL), List.of(ContentFormat.AUDIO));
        f.contentRepo.save(content("img-noalt", ContentFormat.TEXT, false, true, false, false));
        f.contentRepo.save(content("img-alt", ContentFormat.TEXT, false, true, true, false));
        f.contentRepo.save(content("vid-nonarr", ContentFormat.VIDEO, true, false, false, false));
        var ids = f.recommendations.refresh(f.idOf("s@x.ac.za")).stream().map(Recommendation::getContentId).toList();
        assertEquals(List.of("img-alt"), ids);
    }

    @Test void motorStudentAvoidsFineMotorContent() {
        Fixture f = withStudent(Set.of(DisabilityCategory.MOTOR), List.of());
        Content lab = content("lab", ContentFormat.INTERACTIVE, false, false, false, true);
        lab.setRequiresFineMotor(true);
        f.contentRepo.save(lab);
        f.contentRepo.save(content("txt", ContentFormat.TEXT, false, false, false, false));
        var ids = f.recommendations.refresh(f.idOf("s@x.ac.za")).stream().map(Recommendation::getContentId).toList();
        assertEquals(List.of("txt"), ids);
    }

    @Test void mostRestrictiveRuleWinsForMultipleCategories() {
        Fixture f = withStudent(Set.of(DisabilityCategory.HEARING, DisabilityCategory.MOTOR), List.of());
        Content c = content("both", ContentFormat.VIDEO, true, false, false, false);
        c.setRequiresFineMotor(true);
        f.contentRepo.save(c);
        assertTrue(f.recommendations.refresh(f.idOf("s@x.ac.za")).isEmpty());
    }

    @Test void scoresAreWithinZeroToHundredAndSortedDescending() {
        Fixture f = Fixture.seeded();
        for (String email : List.of("thandi@student.ac.za", "sipho@student.ac.za", "neo@student.ac.za")) {
            var recs = f.recommendations.refresh(f.idOf(email));
            assertTrue(recs.size() >= 3, email + " got " + recs.size());
            int prev = 101;
            for (Recommendation r : recs) {
                assertTrue(r.getMatchScore() >= 0 && r.getMatchScore() <= 100);
                assertTrue(r.getMatchScore() <= prev);
                prev = r.getMatchScore();
                assertNotNull(r.getReason());
            }
        }
    }

    @Test void preferredFormatRanksHigherOnColdStart() {
        Fixture f = withStudent(Set.of(DisabilityCategory.NONE), List.of(ContentFormat.AUDIO));
        Content a = content("aud", ContentFormat.AUDIO, false, false, false, false);
        a.setTranscript("text");
        f.contentRepo.save(a);
        f.contentRepo.save(content("txt", ContentFormat.TEXT, false, false, false, false));
        var recs = f.recommendations.refresh(f.idOf("s@x.ac.za"));
        assertEquals("aud", recs.get(0).getContentId());
        assertEquals(0.5, recs.get(0).getSimilarityScore(), 0.001);   // neutral cold-start similarity
    }

    @Test void ratedRecommendationsAreArchivedNotLostOnRefresh() {
        Fixture f = withStudent(Set.of(DisabilityCategory.NONE), List.of());
        f.contentRepo.save(content("a", ContentFormat.TEXT, false, false, false, false));
        f.contentRepo.save(content("b", ContentFormat.TEXT, false, false, false, false));
        String sid = f.idOf("s@x.ac.za");
        var first = f.recommendations.refresh(sid);
        f.recommendations.rate(first.get(0).getId(), sid, Rating.UP);
        f.recommendations.refresh(sid);
        int rated = (int) f.recRepo.findAll().stream().filter(r -> r.getRating() != null).count();
        assertEquals(1, rated);
        assertEquals(2, f.recommendations.forStudent(sid).size());   // archived one is hidden from the active list
    }

    @Test void cannotRateSomeoneElsesRecommendation() {
        Fixture f = withStudent(Set.of(DisabilityCategory.NONE), List.of());
        f.contentRepo.save(content("a", ContentFormat.TEXT, false, false, false, false));
        String sid = f.idOf("s@x.ac.za");
        var rec = f.recommendations.refresh(sid).get(0);
        assertEquals(403, assertThrows(ApiException.class, () -> f.recommendations.rate(rec.getId(), "someone-else", Rating.UP)).getStatus());
        assertThrows(ApiException.class, () -> f.recommendations.rate(rec.getId(), sid, null));
    }

    @Test void manualCreateClampsScore() {
        Fixture f = new Fixture();
        Recommendation r = f.recommendations.createRecommendation(new Recommendation("r1", "s", "c", 104));
        assertEquals(100, r.getMatchScore());   // DEF-002: match score must never leave 0-100
        assertEquals(0, f.recommendations.createRecommendation(new Recommendation("r2", "s", "c", -5)).getMatchScore());
    }

    @Test void completingAnActivityRefreshesRecommendations() {
        Fixture f = Fixture.seeded();
        String neo = f.idOf("neo@student.ac.za");
        java.time.Instant t0 = java.time.Instant.now();
        f.interactions.log(neo, new com.aiadaptive.dto.Dto.InteractionRequest("C11", 85, true, 20, null));
        var active = f.recommendations.forStudent(neo);
        assertTrue(active.size() >= 3);
        assertTrue(active.stream().allMatch(v -> !v.recommendation().getCreatedAt().isBefore(t0)),
                "recommendations were not regenerated after completing an activity");
    }
}
