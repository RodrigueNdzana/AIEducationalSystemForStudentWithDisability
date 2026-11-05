package tests;

import com.aiadaptive.domain.*;
import com.aiadaptive.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AlertServiceTest {

    private String studentWithScores(Fixture f, List<Integer> scores, int lastDaysAgo) {
        String id = f.auth.register("Stu", "stu@x.ac.za", "Secret123").id();
        f.auth.create("Edna", "edna@x.ac.za", "Secret123", Role.EDUCATOR);
        int n = scores.size();
        for (int i = 0; i < n; i++) {
            Interaction in = new Interaction();
            in.setStudentId(id); in.setContentId("c"); in.setScore(scores.get(i));
            in.setTimestamp(Instant.now().minus(Duration.ofDays(lastDaysAgo + (n - 1 - i))));
            f.interactionRepo.save(in);
        }
        return id;
    }

    @Test void raisesAlertWhenRecentScoresDropMoreThan15PercentBelowBaseline() {
        Fixture f = new Fixture();
        String id = studentWithScores(f, List.of(80, 82, 78, 80, 60, 58, 62), 0);   // baseline 80, recent 60 => -25%
        var raised = f.alerts.evaluate(id);
        assertEquals(1, raised.size());
        assertEquals(AlertType.PERFORMANCE_DROP, raised.get(0).getType());
        assertTrue(raised.get(0).getMessage().contains("below"));
    }

    @Test void noAlertWhenDropIsWithinThreshold() {
        Fixture f = new Fixture();
        String id = studentWithScores(f, List.of(80, 82, 78, 80, 72, 70, 74), 0);   // -10%
        assertTrue(f.alerts.evaluate(id).isEmpty());
    }

    @Test void noAlertWithTooFewScores() {
        Fixture f = new Fixture();
        String id = studentWithScores(f, List.of(80, 40, 30), 0);
        assertTrue(f.alerts.evaluate(id).isEmpty());
    }

    @Test void doesNotDuplicateAnOpenAlert() {
        Fixture f = new Fixture();
        String id = studentWithScores(f, List.of(80, 82, 78, 80, 60, 58, 62), 0);
        f.alerts.evaluate(id);
        assertTrue(f.alerts.evaluate(id).isEmpty());
        assertEquals(1, f.alerts.list(null).size());
    }

    @Test void inactivityRaisesEngagementAlert() {
        Fixture f = new Fixture();
        String id = studentWithScores(f, List.of(70, 70), 20);
        var raised = f.alerts.evaluate(id);
        assertEquals(AlertType.LOW_ENGAGEMENT, raised.get(0).getType());
    }

    @Test void educatorsAreNotifiedInPlatformAndByEmail() {
        Fixture f = new Fixture();
        studentWithScores(f, List.of(80, 82, 78, 80, 60, 58, 62), 0);
        f.alerts.evaluate(f.idOf("stu@x.ac.za"));
        var edu = f.notifications.forUser(f.idOf("edna@x.ac.za"));
        assertTrue(edu.stream().anyMatch(n -> n.getChannel().equals("IN_APP")));
        assertTrue(edu.stream().anyMatch(n -> n.getChannel().equals("EMAIL")));
    }

    @Test void acknowledgeDismissAndEscalateInOneAction() {
        Fixture f = new Fixture();
        f.auth.create("Dina", "dina@x.ac.za", "Secret123", Role.DISABILITY_SUPPORT_OFFICER);
        String id = studentWithScores(f, List.of(80, 82, 78, 80, 60, 58, 62), 0);
        Alert a = f.alerts.evaluate(id).get(0);
        assertEquals(AlertStatus.ACKNOWLEDGED, f.alerts.act(a.getId(), "acknowledge", "e1").getStatus());
        assertEquals(AlertStatus.DISMISSED, f.alerts.act(a.getId(), "DISMISS", "e1").getStatus());
        assertEquals(AlertStatus.ESCALATED, f.alerts.act(a.getId(), "ESCALATE", "e1").getStatus());
        assertFalse(f.notifications.forUser(f.idOf("dina@x.ac.za")).isEmpty());
        assertThrows(ApiException.class, () -> f.alerts.act(a.getId(), "EXPLODE", "e1"));
    }

    @Test void loggingAnInteractionTriggersImmediateDetection() {
        Fixture f = Fixture.seeded();
        String thandi = f.idOf("thandi@student.ac.za");
        long open = f.alerts.openCountFor(thandi);
        for (int i = 0; i < 3; i++) {
            f.interactions.log(thandi, new com.aiadaptive.dto.Dto.InteractionRequest("C12", 30, false, 10, null));
        }
        assertTrue(f.alerts.openCountFor(thandi) > open);
    }
}
