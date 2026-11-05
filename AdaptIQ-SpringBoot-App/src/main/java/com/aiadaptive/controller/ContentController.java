package com.aiadaptive.controller;

import io.swagger.v3.oas.annotations.Parameter;
import com.aiadaptive.domain.Content;
import com.aiadaptive.domain.Role;
import com.aiadaptive.dto.Dto.*;
import com.aiadaptive.security.AuthContext;
import com.aiadaptive.security.Require;
import com.aiadaptive.services.ContentAdaptationService;
import com.aiadaptive.services.ContentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-04 accessible delivery and FR-10 content management. */
@RestController
@RequestMapping("/api/content")
public class ContentController {

    private final ContentService service;
    private final ContentAdaptationService adaptation;

    public ContentController(ContentService service, ContentAdaptationService adaptation) {
        this.service = service;
        this.adaptation = adaptation;
    }

    /** Students see published content only; staff see everything. */
    @GetMapping
    public List<Content> getAll(@Parameter(hidden = true) AuthContext ctx) {
        return ctx.role() == Role.STUDENT ? service.getPublishedContent() : service.getAllContent();
    }

    @GetMapping("/{id}")
    public Content get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/{id}/adapt")
    public AdaptedContent adapt(@PathVariable String id, @RequestParam(defaultValue = "TEXT") String mode) {
        return adaptation.adapt(service.get(id), mode);
    }

    /** Dry-run of the WCAG gate without saving. */
    @Require({Role.EDUCATOR, Role.ADMIN})
    @PostMapping("/validate")
    public ValidationReport validate(@RequestBody Content content) {
        return service.validate(content);
    }

    /** Upload + validate + publish. Failing content is blocked with HTTP 422 unless an admin overrides. */
    @Require({Role.EDUCATOR, Role.ADMIN})
    @PostMapping
    public UploadResult create(@RequestBody Content content,
                               @RequestParam(defaultValue = "false") boolean override, @Parameter(hidden = true) AuthContext ctx) {
        boolean adminOverride = override && ctx.role() == Role.ADMIN;
        return service.upload(content, ctx.userId(), adminOverride);
    }

    @Require({Role.EDUCATOR, Role.ADMIN})
    @PutMapping("/{id}")
    public Content update(@PathVariable String id, @RequestBody Content content) {
        return service.updateContent(id, content);
    }

    @Require({Role.EDUCATOR, Role.ADMIN})
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.deleteContent(id);
    }
}
