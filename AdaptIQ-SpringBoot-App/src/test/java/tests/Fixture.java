package tests;

import com.aiadaptive.config.DataSeeder;
import com.aiadaptive.repositories.inMemory.*;
import com.aiadaptive.services.*;

/** Wires the whole service layer by hand (no Spring context needed) for fast unit tests. */
public class Fixture {
    public final InMemoryUserRepository users = new InMemoryUserRepository();
    public final InMemoryLearningProfileRepository profileRepo = new InMemoryLearningProfileRepository();
    public final InMemoryContentRepository contentRepo = new InMemoryContentRepository();
    public final InMemoryRecommendationRepository recRepo = new InMemoryRecommendationRepository();
    public final InMemoryInteractionRepository interactionRepo = new InMemoryInteractionRepository();
    public final InMemoryAlertRepository alertRepo = new InMemoryAlertRepository();
    public final InMemoryFeedbackRepository feedbackRepo = new InMemoryFeedbackRepository();
    public final InMemoryNotificationRepository notificationRepo = new InMemoryNotificationRepository();

    public final AuthService auth = new AuthService(users);
    public final NotificationService notifications = new NotificationService(notificationRepo, users);
    public final ProfileService profiles = new ProfileService(profileRepo, users);
    public final AccessibilityValidator validator = new AccessibilityValidator();
    public final ContentService content = new ContentService(contentRepo, validator);
    public final ContentAdaptationService adaptation = new ContentAdaptationService();
    public final ModelRegistry registry = new ModelRegistry();
    public final RecommendationService recommendations =
            new RecommendationService(recRepo, contentRepo, profileRepo, interactionRepo, users, registry);
    public final AlertService alerts = new AlertService(alertRepo, interactionRepo, users, notifications);
    public final InteractionService interactions =
            new InteractionService(interactionRepo, contentRepo, recommendations, alerts);
    public final DashboardService dashboards =
            new DashboardService(users, contentRepo, interactionRepo, profileRepo, alerts, 12, 8);
    public final FeedbackService feedback =
            new FeedbackService(feedbackRepo, users, profileRepo, notifications, adaptation);
    public final ReportService reports =
            new ReportService(users, interactionRepo, contentRepo, profileRepo, validator, dashboards);
    public final ModelMonitoringService monitoring =
            new ModelMonitoringService(recRepo, profileRepo, registry);

    public final DataSeeder seeder = new DataSeeder(false, auth, profiles, contentRepo, interactionRepo,
            recommendations, recRepo, alerts);

    public static Fixture seeded() {
        Fixture f = new Fixture();
        f.seeder.seed();
        return f;
    }

    public String idOf(String email) {
        return users.findByEmail(email).orElseThrow().getId();
    }
}
