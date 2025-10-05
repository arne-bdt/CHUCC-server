package org.chucc.vcserver.controller;

import org.chucc.vcserver.dto.ConflictItem;
import org.chucc.vcserver.exception.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * Test controller for triggering VcExceptions in integration tests.
 * Only active in test profiles.
 */
@RestController
@RequestMapping("/test/errors")
@Profile({"test", "it"})
public class TestErrorController {

    @GetMapping(value = "/selector-conflict", produces = MediaType.APPLICATION_JSON_VALUE)
    public void throwSelectorConflict() {
        throw new SelectorConflictException("Multiple mutually exclusive selectors provided");
    }

    @GetMapping(value = "/merge-conflict", produces = MediaType.APPLICATION_JSON_VALUE)
    public void throwMergeConflict() {
        List<ConflictItem> conflicts = Arrays.asList(
                new ConflictItem(
                        "http://example.org/graph1",
                        "http://example.org/subject1",
                        "http://example.org/predicate1",
                        "http://example.org/object1",
                        "Conflicting values"
                )
        );
        throw new MergeConflictException("Merge cannot proceed due to conflicts", conflicts);
    }

    @GetMapping(value = "/concurrent-write", produces = MediaType.APPLICATION_JSON_VALUE)
    public void throwConcurrentWriteConflict() {
        throw new ConcurrentWriteConflictException("Concurrent write detected, operation rejected");
    }

    @GetMapping(value = "/graph-not-found", produces = MediaType.APPLICATION_JSON_VALUE)
    public void throwGraphNotFound() {
        throw new GraphNotFoundException("Specified graph does not exist");
    }

    @GetMapping(value = "/tag-retarget-forbidden", produces = MediaType.APPLICATION_JSON_VALUE)
    public void throwTagRetargetForbidden() {
        throw new TagRetargetForbiddenException("Attempt to change immutable tag target");
    }

    @GetMapping(value = "/rebase-conflict", produces = MediaType.APPLICATION_JSON_VALUE)
    public void throwRebaseConflict() {
        List<ConflictItem> conflicts = Arrays.asList(
                new ConflictItem(
                        "http://example.org/graph2",
                        "http://example.org/subject2",
                        "http://example.org/predicate2",
                        "http://example.org/object2"
                )
        );
        throw new RebaseConflictException("Rebase operation encountered conflicts", conflicts);
    }

    @GetMapping(value = "/cherry-pick-conflict", produces = MediaType.APPLICATION_JSON_VALUE)
    public void throwCherryPickConflict() {
        List<ConflictItem> conflicts = Arrays.asList(
                new ConflictItem(
                        "http://example.org/graph3",
                        "http://example.org/subject3",
                        "http://example.org/predicate3",
                        "http://example.org/object3"
                )
        );
        throw new CherryPickConflictException("Cherry-pick operation encountered conflicts", conflicts);
    }
}
