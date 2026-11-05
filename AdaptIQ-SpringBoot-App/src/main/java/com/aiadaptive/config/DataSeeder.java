package com.aiadaptive.config;

import com.aiadaptive.domain.*;
import com.aiadaptive.dto.Dto.ProfileRequest;
import com.aiadaptive.repositories.*;
import com.aiadaptive.services.AlertService;
import com.aiadaptive.services.AuthService;
import com.aiadaptive.services.ProfileService;
import com.aiadaptive.services.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Loads demo users, accessible course content and learner history so every FR can be tried immediately.
 * Disable in production with adaptiq.seed-demo-data=false. All demo accounts use the password below.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Passw0rd!";
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final boolean enabled;
    private final AuthService auth;
    private final ProfileService profiles;
    private final ContentRepository content;
    private final InteractionRepository interactions;
    private final RecommendationService recommendations;
    private final RecommendationRepository recRepo;
    private final AlertService alerts;

    public DataSeeder(@Value("${adaptiq.seed-demo-data:true}") boolean enabled, AuthService auth,
                      ProfileService profiles, ContentRepository content, InteractionRepository interactions,
                      RecommendationService recommendations, RecommendationRepository recRepo, AlertService alerts) {
        this.enabled = enabled;
        this.auth = auth;
        this.profiles = profiles;
        this.content = content;
        this.interactions = interactions;
        this.recommendations = recommendations;
        this.recRepo = recRepo;
        this.alerts = alerts;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) seed();
    }

    public void seed() {
        auth.create("Alex Admin", "admin@adaptiq.ac.za", DEMO_PASSWORD, Role.ADMIN);
        auth.create("Dr Edna Educator", "educator@adaptiq.ac.za", DEMO_PASSWORD, Role.EDUCATOR);
        auth.create("Dina Support", "dso@adaptiq.ac.za", DEMO_PASSWORD, Role.DISABILITY_SUPPORT_OFFICER);
        auth.create("Max Engineer", "engineer@adaptiq.ac.za", DEMO_PASSWORD, Role.ML_ENGINEER);

        String thandi = student("Thandi Mokoena", "thandi@student.ac.za", Set.of(DisabilityCategory.VISUAL),
                List.of("screen-reader"), List.of(ContentFormat.AUDIO, ContentFormat.TEXT), "arrays sorting", "high-contrast", 1.4);
        String sipho = student("Sipho Dlamini", "sipho@student.ac.za", Set.of(DisabilityCategory.HEARING),
                List.of("captioning"), List.of(ContentFormat.TEXT, ContentFormat.VIDEO), "databases", "default", 1.0);
        String lerato = student("Lerato Khumalo", "lerato@student.ac.za", Set.of(DisabilityCategory.MOTOR),
                List.of("voice-control"), List.of(ContentFormat.TEXT, ContentFormat.AUDIO), "algorithms", "default", 1.0);
        String ayanda = student("Ayanda Nkosi", "ayanda@student.ac.za", Set.of(DisabilityCategory.COGNITIVE),
                List.of(), List.of(ContentFormat.TEXT, ContentFormat.AUDIO), "algorithms", "sepia", 1.2);
        String neo = student("Neo Botha", "neo@student.ac.za", Set.of(DisabilityCategory.NONE),
                List.of(), List.of(ContentFormat.VIDEO, ContentFormat.INTERACTIVE), "testing", "dark", 1.0);
        String zanele = student("Zanele Naidoo", "zanele@student.ac.za", Set.of(DisabilityCategory.NONE),
                List.of(), List.of(ContentFormat.VIDEO), "", "default", 1.0);

        seedContent();

        // student, content, score, completed, daysAgo, minutes
        act(thandi, "C01", 88, true, 50, 25); act(thandi, "C03", 80, true, 44, 30); act(thandi, "C04", 90, true, 40, 20);
        act(thandi, "C05", 82, true, 33, 35); act(thandi, "C09", 86, true, 26, 28); act(thandi, "C08", 78, true, 20, 32);
        act(thandi, "C10", 84, true, 12, 30);
        act(sipho, "C01", 70, true, 49, 30); act(sipho, "C02", 76, true, 43, 22); act(sipho, "C03", 72, true, 36, 30);
        act(sipho, "C05", 68, true, 30, 35); act(sipho, "C06", 74, true, 22, 25); act(sipho, "C08", 70, true, 9, 30);
        act(lerato, "C01", 75, true, 48, 28); act(lerato, "C03", 78, true, 41, 30); act(lerato, "C04", 81, true, 35, 20);
        act(lerato, "C05", 77, true, 28, 35); act(lerato, "C08", 80, true, 11, 30);
        act(ayanda, "C01", 82, true, 50, 30); act(ayanda, "C03", 80, true, 45, 30); act(ayanda, "C04", 84, true, 38, 22);
        act(ayanda, "C05", 78, true, 30, 35); act(ayanda, "C08", 55, false, 10, 40); act(ayanda, "C09", 50, false, 6, 25);
        act(ayanda, "C10", 45, false, 3, 45);
        act(neo, "C01", 90, true, 52, 20); act(neo, "C02", 85, true, 46, 20); act(neo, "C03", 88, true, 40, 25);
        act(neo, "C06", 80, true, 30, 30); act(neo, "C07", 83, true, 24, 30); act(neo, "C08", 86, true, 18, 28);
        act(neo, "C10", 90, true, 10, 30); act(neo, "C12", 88, true, 5, 35);
        act(zanele, "C01", 55, true, 40, 20); act(zanele, "C03", 48, false, 30, 15);

        alerts.scanAll();

        // Three recommendation rounds; the first two are rated so the model monitor has history.
        List<String> all = List.of(thandi, sipho, lerato, ayanda, neo, zanele);
        Random rnd = new Random(42);
        for (int round = 0; round < 3; round++) {
            for (String id : all) {
                recommendations.refresh(id);
                if (round < 2) rateSimulated(id, rnd);
            }
        }
        log.info("Demo data loaded. Log in with any seeded account (password: {}).", DEMO_PASSWORD);
    }

    private void rateSimulated(String studentId, Random rnd) {
        LearningProfile p = profiles.get(studentId);
        for (Recommendation r : recRepo.findByStudent(studentId)) {
            if (r.getRating() != null) continue;
            Content c = content.findById(r.getContentId()).orElse(null);
            if (c == null) continue;
            boolean liked = p.getPreferredFormats().contains(c.getFormat()) ? rnd.nextDouble() < 0.85 : rnd.nextDouble() < 0.25;
            recommendations.rate(r.getId(), studentId, liked ? Rating.UP : Rating.DOWN);
        }
    }

    private String student(String name, String email, Set<DisabilityCategory> cats, List<String> tech,
                           List<ContentFormat> formats, String goals, String theme, double font) {
        String id = auth.create(name, email, DEMO_PASSWORD, Role.STUDENT).id();
        profiles.save(id, new ProfileRequest(cats, tech, formats, goals, false, font, theme, false, "none"));
        return id;
    }

    private void act(String studentId, String contentId, int score, boolean completed, int daysAgo, int minutes) {
        Content c = content.findById(contentId).orElseThrow();
        Interaction i = new Interaction();
        i.setStudentId(studentId);
        i.setContentId(contentId);
        i.setModuleId(c.getModuleId());
        i.setScore(score);
        i.setCompleted(completed);
        i.setMinutes(minutes);
        i.setFormatUsed(c.getFormat());
        i.setTimestamp(Instant.now().minus(Duration.ofDays(daysAgo)));
        interactions.save(i);
    }

    private Content c(String id, String title, ContentFormat f, Difficulty d, String module, String topic, String body) {
        Content x = new Content();
        x.setId(id);
        x.setTitle(title);
        x.setFormat(f);
        x.setDifficulty(d);
        x.setModuleId(module);
        x.setTopic(topic);
        x.setBody(body);
        x.setPublished(true);
        return x;
    }

    private void seedContent() {
        Content c1 = c("C01", "What is a Variable?", ContentFormat.TEXT, Difficulty.BEGINNER, "M1", "variables",
                "A variable is a named place in memory that holds a value. You can read the value and you can change it later. "
                + "For example, a variable called age might hold the number 21.");
        Content c2 = c("C02", "Variables Explained (captioned video)", ContentFormat.VIDEO, Difficulty.BEGINNER, "M1", "variables",
                "A short video that shows how variables are created and changed.");
        c2.setHasCaptions(true); c2.setHasAudioNarration(true);
        c2.setTranscript("In this video we create a variable, give it a value and then change that value. Watch how the number on screen updates.");
        Content c3 = c("C03", "Conditions and Loops", ContentFormat.TEXT, Difficulty.BEGINNER, "M2", "loops",
                "A condition lets a program choose between two paths. A loop repeats an action until a condition is no longer true. "
                + "Together they let you build programs that react to data.");
        Content c4 = c("C04", "Podcast: Thinking in Loops", ContentFormat.AUDIO, Difficulty.BEGINNER, "M2", "loops",
                "An audio discussion about how to think about repetition in code.");
        c4.setTranscript("Welcome to the show. Today we talk about loops. A loop is simply a way to say: do this again, and again, until we are done.");
        Content c5 = c("C05", "Arrays and Lists", ContentFormat.TEXT, Difficulty.INTERMEDIATE, "M3", "arrays",
                "An array stores many values in one ordered collection. Each value has an index, and the first index is zero. "
                + "Arrays make it easy to process many items with a loop.");
        c5.setHasImages(true); c5.setHasAltText(true);
        Content c6 = c("C06", "Linked Lists Walkthrough", ContentFormat.VIDEO, Difficulty.INTERMEDIATE, "M3", "linked lists",
                "A captioned video that walks through a linked list step by step.");
        c6.setHasCaptions(true);
        c6.setTranscript("A linked list node stores data and a pointer to the next node. We start at the head and follow the pointers until we reach the end.");
        Content c7 = c("C07", "Drag-and-drop Sorting Lab", ContentFormat.INTERACTIVE, Difficulty.INTERMEDIATE, "M3", "sorting",
                "An interactive lab where you drag cards into the correct order.");
        c7.setRequiresFineMotor(true);
        Content c8 = c("C08", "Sorting Algorithms", ContentFormat.TEXT, Difficulty.INTERMEDIATE, "M4", "sorting",
                "Sorting algorithms arrange items in order. Bubble sort repeatedly compares neighbouring items and swaps them when they are in the wrong order, "
                + "whereas merge sort splits the list in half, sorts each half and then merges the results, which makes it much faster on large lists.");
        Content c9 = c("C09", "Audio lecture: Big-O", ContentFormat.AUDIO, Difficulty.INTERMEDIATE, "M4", "algorithms",
                "An audio lecture about how we measure the speed of algorithms.");
        c9.setTranscript("Big O notation describes how the running time of an algorithm grows as the input grows. A loop over n items is O of n.");
        Content c10 = c("C10", "Relational Databases and SQL", ContentFormat.TEXT, Difficulty.INTERMEDIATE, "M5", "databases",
                "A relational database organises information into tables, and each table consists of rows and columns that represent records and attributes. "
                + "Structured Query Language is utilized to obtain data from these tables, and queries can combine several tables by matching a key that appears in both of them.");
        Content c11 = c("C11", "SQL Sandbox", ContentFormat.INTERACTIVE, Difficulty.INTERMEDIATE, "M5", "databases",
                "Type SQL queries and see the results immediately.");
        c11.setHasAudioNarration(true);
        c11.setTranscript("Type a query in the box and press run. The result table appears below. Keyboard shortcuts are listed at the top.");
        Content c12 = c("C12", "Testing and Continuous Integration", ContentFormat.TEXT, Difficulty.ADVANCED, "M6", "testing",
                "Automated testing verifies that software behaves as specified, and continuous integration runs those tests every time a developer submits a change, "
                + "which allows defects to be detected immediately and consequently reduces the cost of fixing them.");
        Content c13 = c("C13", "Design Patterns in Practice", ContentFormat.VIDEO, Difficulty.ADVANCED, "M6", "design patterns",
                "A captioned, audio-described video about common design patterns.");
        c13.setHasCaptions(true); c13.setHasAudioNarration(true);
        c13.setTranscript("Design patterns are proven solutions to common problems. In this video we look at the factory and the builder patterns.");
        Content c14 = c("C14", "DRAFT: Advanced Recursion (no captions yet)", ContentFormat.VIDEO, Difficulty.ADVANCED, "M6", "recursion",
                "Draft video waiting for captions.");
        c14.setPublished(false);
        for (Content x : List.of(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14)) content.save(x);
    }
}
