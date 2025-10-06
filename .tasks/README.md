# Protocol Update Tasks

This directory contains a breakdown of all work needed to update the codebase to match the latest SPARQL 1.2 Protocol Version Control Extension specification.

## Quick Start

1. **Read**: Start with `00-overview.md` for the big picture
2. **Execute**: Work through tasks 01-15 in order (respecting dependencies)
3. **Track**: Update the overview file's checkboxes as you complete tasks

## Task Structure

Each task file contains:
- **Priority**: How critical/urgent the task is
- **Dependencies**: Which tasks must be completed first
- **Protocol Reference**: Relevant sections of the spec
- **Context**: Why this is needed
- **Implementation Tasks**: Step-by-step instructions
- **Acceptance Criteria**: How to know you're done
- **Test Requirements**: What tests to write
- **Files to Create/Modify**: Exact file paths
- **Estimated Complexity**: How difficult the task is

## Recommended Session Plan

### Session 1: Foundation (3-4 hours)
- Task 01: Error handling
- Task 02: Selector validation
- Task 03: Header updates

### Session 2: Core Endpoints (3-4 hours)
- Task 04: GET /version/refs
- Task 05: POST /version/commits
- Task 06: Tag endpoints

### Session 3: Refactoring (2-3 hours)
- Task 07: Reset refactor
- Task 09: Revert endpoint

### Session 4: Advanced Operations (4-5 hours)
- Task 08: Cherry-pick
- Task 10: Rebase
- Task 11: Squash

### Session 5: Enhanced Features (3-4 hours)
- Task 12: ETag concurrency
- Task 13: No-op patch detection
- Task 14: asOf selector support

### Session 6: Cleanup (1-2 hours)
- Task 15: Remove deprecated endpoints
- Final integration testing
- Documentation review

## Progress Tracking

See `00-overview.md` for the current progress checklist.

## TDD Workflow (per CLAUDE.md)

For each task:
1. Write unit tests first
2. Write integration tests
3. Implement to make tests pass
4. Run `mvn clean install` (added/modified tests only)
5. Fix warnings/errors
6. Run `mvn clean install` (all tests)
7. Verify everything passes

## Quality Improvement Tasks

Beyond the protocol implementation, these tasks improve code quality:

- **Task 16**: `16-enhance-superficial-tests.md` - Review and enhance integration tests that verify API contracts but don't test actual business logic

## Notes

- Tasks are designed to be independent where possible
- Some tasks can be done in parallel (see dependencies)
- Each task should complete in a single focused session
- All tasks follow the protocol specification strictly
- Extensions are clearly marked and configurable
