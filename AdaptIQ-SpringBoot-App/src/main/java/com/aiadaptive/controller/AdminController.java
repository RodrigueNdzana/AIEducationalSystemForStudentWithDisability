package com.aiadaptive.controller;

import com.aiadaptive.domain.Role;
import com.aiadaptive.dto.Dto.*;
import com.aiadaptive.repositories.UserRepository;
import com.aiadaptive.security.Require;
import com.aiadaptive.services.AuthService;
import com.aiadaptive.services.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/** FR-01 account administration and FR-11 reporting. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService auth;
    private final UserRepository users;
    private final ReportService reports;

    public AdminController(AuthService auth, UserRepository users, ReportService reports) {
        this.auth = auth;
        this.users = users;
        this.reports = reports;
    }

    @Require({Role.ADMIN})
    @PostMapping("/users")
    public UserView createUser(@RequestBody CreateUserRequest r) {
        return auth.create(r.name(), r.email(), r.password(), r.role());
    }

    @Require({Role.ADMIN})
    @GetMapping("/users")
    public List<UserView> listUsers() {
        return users.findAll().stream().map(UserView::of).collect(Collectors.toList());
    }

    @Require({Role.ADMIN})
    @PostMapping("/users/{id}/unlock")
    public UserView unlock(@PathVariable String id) {
        return auth.unlock(id);
    }

    @Require({Role.ADMIN, Role.DISABILITY_SUPPORT_OFFICER})
    @GetMapping("/reports")
    public PlatformReport report(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return reports.build(start(from), end(to));
    }

    @Require({Role.ADMIN, Role.DISABILITY_SUPPORT_OFFICER})
    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        PlatformReport r = reports.build(start(from), end(to));
        if (format.equalsIgnoreCase("pdf")) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=adaptiq-report.pdf")
                    .contentType(MediaType.APPLICATION_PDF).body(reports.toPdf(r));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=adaptiq-report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reports.toCsv(r).getBytes(StandardCharsets.UTF_8));
    }

    private static Instant start(String d) {
        return d == null || d.isBlank() ? null : LocalDate.parse(d).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Instant end(String d) {
        return d == null || d.isBlank() ? null : LocalDate.parse(d).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1);
    }
}
