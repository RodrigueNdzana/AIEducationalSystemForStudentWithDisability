package tests;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.*;
import com.aiadaptive.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DashboardAndProfileTest {
    private final Fixture f = Fixture.seeded();

    @Test void expectedProgressComesFromTheSemesterClock() {
        assertEquals(66.7, f.dashboards.expectedPercent(), 0.01);
        assertTrue(f.dashboards.isAtRisk(16.7));
        assertFalse(f.dashboards.isAtRisk(50.0));
        assertFalse(f.dashboards.isAtRisk(33.4));
    }

    @Test void studentDashboardShowsModulesScoresAndInsights() {
        StudentDashboard d = f.dashboards.student(f.idOf("neo@student.ac.za"), null, null);
        assertEquals(6, d.modules().size());
        assertEquals(100.0, d.completionPercent(), 0.01);
        assertFalse(d.timeline().isEmpty());
        assertNotNull(d.averageScore());
        assertFalse(d.insights().isEmpty());
        assertTrue(d.totalMinutes() > 0);
    }

    @Test void historicalTimelineCanBeFilteredByDateRange() {
        String neo = f.idOf("neo@student.ac.za");
        StudentDashboard all = f.dashboards.student(neo, null, null);
        StudentDashboard recent = f.dashboards.student(neo, LocalDate.now().minusDays(20), LocalDate.now());
        assertTrue(recent.timeline().size() < all.timeline().size());
        assertTrue(recent.timeline().size() > 0);
        assertEquals(0, f.dashboards.student(neo, LocalDate.now().plusDays(1), null).timeline().size());
    }

    @Test void educatorDashboardFlagsAtRiskStudentsFirst() {
        EducatorDashboard d = f.dashboards.educator(null, null, null, null);
        assertEquals(6, d.totalStudents());
        assertEquals("Zanele Naidoo", d.students().get(0).name());
        assertTrue(d.students().get(0).atRisk());
        assertEquals(1, d.atRiskCount());
    }

    @Test void educatorFiltersByDisabilityProgressRiskAndCourse() {
        assertEquals(1, f.dashboards.educator(null, "VISUAL", null, null).students().size());
        assertEquals(1, f.dashboards.educator(null, "ANY", null, true).students().size());
        assertTrue(f.dashboards.educator(null, null, "HIGH", null).students().stream().anyMatch(s -> s.name().startsWith("Neo")));
        assertEquals(1, f.dashboards.educator(null, null, "LOW", null).students().size());
        assertTrue(f.dashboards.educator("NO-SUCH-COURSE", null, null, null).students().stream()
                .noneMatch(s -> s.completionPercent() > 0));
        assertThrows(ApiException.class, () -> f.dashboards.educator(null, "TELEPORTATION", null, null));
    }

    @Test void dashboardRejectsNonStudents() {
        assertThrows(ApiException.class, () -> f.dashboards.student(f.idOf("admin@adaptiq.ac.za"), null, null));
        assertThrows(ApiException.class, () -> f.dashboards.student("ghost", null, null));
    }

    @Test void profileUpdatesArePartialAndValidated() {
        String id = f.idOf("neo@student.ac.za");
        LearningProfile p = f.profiles.save(id, new ProfileRequest(Set.of(DisabilityCategory.VISUAL, DisabilityCategory.NONE),
                List.of("screen-reader"), null, null, true, 1.8, "high-contrast-yellow", true, "greyscale"));
        assertEquals(Set.of(DisabilityCategory.VISUAL), p.getDisabilityCategories());   // NONE dropped when others present
        assertEquals(1.8, p.getFontScale(), 0.001);
        assertTrue(p.isExtraTime());
        assertTrue(p.isReduceMotion());
        assertEquals(List.of(ContentFormat.VIDEO, ContentFormat.INTERACTIVE), p.getPreferredFormats());   // untouched
        assertThrows(ApiException.class, () -> f.profiles.save(id, new ProfileRequest(null, null, null, null, null, 5.0, null, null, null)));
        assertThrows(ApiException.class, () -> f.profiles.save(id, new ProfileRequest(null, null, null, null, null, null, "neon", null, null)));
        assertThrows(ApiException.class, () -> f.profiles.save(id, new ProfileRequest(null, null, null, null, null, null, null, null, "sparkle")));
    }

    @Test void atLeastFiveThemesIncludingHighContrast() {
        assertTrue(com.aiadaptive.services.ProfileService.THEMES.size() >= 5);
        assertTrue(com.aiadaptive.services.ProfileService.THEMES.contains("high-contrast"));
    }

    @Test void emptyCategoriesDefaultToNone() {
        String id = f.idOf("neo@student.ac.za");
        LearningProfile p = f.profiles.save(id, new ProfileRequest(Set.of(), null, null, null, null, null, null, null, null));
        assertEquals(Set.of(DisabilityCategory.NONE), p.getDisabilityCategories());
    }
}
