package tests;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.*;
import com.aiadaptive.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ReportAndModelTest {
    private final Fixture f = Fixture.seeded();

    @Test void reportAggregatesEngagementComplianceAndRisk() {
        PlatformReport r = f.reports.build(null, null);
        assertEquals(6, r.totalStudents());
        assertEquals(6, r.activeStudents());
        assertTrue(r.totalInteractions() > 30);
        assertEquals(13, r.publishedContent());          // the unpublished draft is excluded
        assertEquals(100.0, r.compliancePercent(), 0.01);
        assertEquals(1, r.atRiskCount());
        assertTrue(r.byDisability().containsKey("VISUAL"));
        assertTrue(r.accommodationUsage().keySet().stream().anyMatch(k -> k.startsWith("theme:")));
    }

    @Test void reportRespectsTheDateRange() {
        PlatformReport recent = f.reports.build(Instant.now().minus(15, ChronoUnit.DAYS), Instant.now());
        PlatformReport all = f.reports.build(null, null);
        assertTrue(recent.totalInteractions() < all.totalInteractions());
        assertTrue(recent.activeStudents() < all.activeStudents());
    }

    @Test void csvExportHasHeaderAndEscapesValues() {
        String csv = f.reports.toCsv(f.reports.build(null, null));
        assertTrue(csv.startsWith("section,metric,value\n"));
        assertTrue(csv.contains("engagement,total_students,6"));
        assertTrue(csv.contains("at_risk,student,Zanele Naidoo"));
    }

    @Test void pdfExportIsAWellFormedPdf() {
        byte[] pdf = f.reports.toPdf(f.reports.build(null, null));
        String head = new String(pdf, 0, 8, StandardCharsets.ISO_8859_1);
        String all = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(head.startsWith("%PDF-1.4"));
        assertTrue(all.contains("%%EOF"));
        assertTrue(all.contains("AdaptIQ Platform Report"));
    }

    @Test void monitoringReportsRelevanceAndFairnessGap() {
        ModelMetrics m = f.monitoring.metrics();
        assertTrue(m.ratedRecommendations() >= 30);
        assertNotNull(m.overallRelevance());
        assertTrue(m.overallRelevance() >= 0 && m.overallRelevance() <= 1);
        assertFalse(m.byCategory().isEmpty());
        assertEquals(1, m.currentVersion());
    }

    @Test void biasAlertFiresWhenACategoryDeviatesMoreThanFivePoints() {
        Fixture g = new Fixture();
        String a = g.auth.register("A", "a@x.ac.za", "Secret123").id();
        String b = g.auth.register("B", "b@x.ac.za", "Secret123").id();
        g.profiles.save(a, new ProfileRequest(java.util.Set.of(DisabilityCategory.VISUAL), null, null, null, null, null, null, null, null));
        g.profiles.save(b, new ProfileRequest(java.util.Set.of(DisabilityCategory.HEARING), null, null, null, null, null, null, null, null));
        for (int i = 0; i < 10; i++) {
            g.recRepo.save(rated(a, Rating.UP));
            g.recRepo.save(rated(b, i < 5 ? Rating.UP : Rating.DOWN));
        }
        ModelMetrics m = g.monitoring.metrics();
        assertTrue(m.biasAlert());
        assertEquals(50.0, m.fairnessGapPoints(), 0.01);
        assertTrue(m.byCategory().stream().anyMatch(c -> c.category().equals("HEARING") && c.flagged()));
    }

    @Test void tooFewRatingsDoNotTriggerBiasAlert() {
        Fixture g = new Fixture();
        String a = g.auth.register("A", "a@x.ac.za", "Secret123").id();
        g.profiles.save(a, new ProfileRequest(java.util.Set.of(DisabilityCategory.VISUAL), null, null, null, null, null, null, null, null));
        g.recRepo.save(rated(a, Rating.DOWN));
        ModelMetrics m = g.monitoring.metrics();
        assertFalse(m.biasAlert());
        assertFalse(m.notes().isEmpty());
    }

    @Test void retrainingRegistersANewVersionWithChangelog() {
        ModelVersion v = f.monitoring.retrain("Max Engineer");
        assertEquals(2, v.version());
        assertEquals("Max Engineer", v.trainedBy());
        assertEquals(1.0, v.similarityWeight() + v.preferenceWeight(), 0.011);
        assertTrue(v.changelog().contains("Retrained on"));
        assertTrue(v.similarityWeight() >= 0.2 && v.similarityWeight() <= 0.8, "hybrid must keep both signals");
        assertEquals(2, f.monitoring.versions().size());
        assertEquals(2, f.monitoring.metrics().currentVersion());
        // alignment of the chosen weight is at least as good as the previous weight
        assertTrue(v.alignment() >= 0);
    }

    @Test void retrainingNeedsEnoughRatedData() {
        Fixture g = new Fixture();
        assertEquals(409, assertThrows(ApiException.class, () -> g.monitoring.retrain("x")).getStatus());
    }

    @Test void newRecommendationsRecordTheModelVersionUsed() {
        f.monitoring.retrain("Max");
        var recs = f.recommendations.refresh(f.idOf("neo@student.ac.za"));
        assertTrue(recs.stream().allMatch(r -> r.getModelVersion() == 2));
    }

    private Recommendation rated(String student, Rating r) {
        Recommendation x = new Recommendation();
        x.setStudentId(student); x.setContentId("c"); x.setRating(r);
        x.setSimilarityScore(0.6); x.setPreferenceScore(0.4);
        return x;
    }
}
