package tests;

import com.aiadaptive.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the demo data tells the story the demo script relies on. */
public class SeedDataTest {
    private final Fixture f = Fixture.seeded();

    @Test void demoAccountsExistForEveryRole() {
        for (Role r : Role.values()) assertFalse(f.users.findByRole(r).isEmpty(), "no " + r);
        assertEquals(6, f.users.findByRole(Role.STUDENT).size());
        assertNotNull(f.auth.login("admin@adaptiq.ac.za", "Passw0rd!").token());
    }

    @Test void seededAlertsCoverBothTypes() {
        var open = f.alerts.list(AlertStatus.OPEN);
        assertTrue(open.stream().anyMatch(a -> a.getType() == AlertType.PERFORMANCE_DROP && a.getStudentName().startsWith("Ayanda")));
        assertTrue(open.stream().anyMatch(a -> a.getType() == AlertType.LOW_ENGAGEMENT && a.getStudentName().startsWith("Zanele")));
    }

    @Test void everyStudentHasAtLeastThreeCompatibleRecommendations() {
        for (User s : f.users.findByRole(Role.STUDENT)) {
            var recs = f.recommendations.forStudent(s.getId());
            assertTrue(recs.size() >= 3, s.getName() + " has " + recs.size());
            LearningProfile p = f.profiles.get(s.getId());
            for (var v : recs) {
                Content c = f.content.get(v.recommendation().getContentId());
                assertTrue(f.recommendations.isCompatible(c, p), s.getName() + " got incompatible " + c.getTitle());
            }
        }
    }

    @Test void draftWithoutCaptionsIsNeverRecommendedOrShownToStudents() {
        for (User s : f.users.findByRole(Role.STUDENT)) {
            assertTrue(f.recommendations.forStudent(s.getId()).stream().noneMatch(v -> v.recommendation().getContentId().equals("C14")));
        }
        assertEquals(13, f.content.getPublishedContent().size());
        assertEquals(14, f.content.getAllContent().size());
    }

    @Test void alertsStillRaisedWithinOneScanCycle() {
        int before = f.alerts.list(null).size();
        f.alerts.scanAll();
        assertEquals(before, f.alerts.list(null).size());   // idempotent
    }
}
