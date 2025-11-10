# QueryContextSelector - Implementation Roadmap

**Component Package**: CHUCC-SQUI Version Control Extension
**Target Version**: v1.0.0
**Estimated Timeline**: 3-4 weeks
**Repository**: https://github.com/arne-bdt/CHUCC-SQUI

---

## Overview

Implement the QueryContextSelector component and related utilities as an optional extension to CHUCC-SQUI for systems implementing the SPARQL 1.2 Protocol Version Control Extension.

---

## Goals

### Primary Goals
- ✅ Provide reusable, protocol-compliant context selector UI
- ✅ Support branch, commit, and asOf context types
- ✅ Stateless, callback-driven component design
- ✅ Full TypeScript support with exported types
- ✅ Carbon Design System integration
- ✅ 80%+ test coverage
- ✅ WCAG 2.1 AA accessibility compliance

### Secondary Goals
- 📖 Comprehensive documentation with examples
- 🎨 Storybook stories for visual testing
- 📦 Published to npm as @chucc-squi/version-control (or chucc-squi/version-control)
- 🔄 CI/CD pipeline for automated testing and publishing

---

## Phases

### Phase 1: Foundation (Week 1)
**Goal**: Set up project structure and core types

- [01-setup.md](./01-setup.md) - Project setup and dependencies
- [02-types.md](./02-types.md) - TypeScript interfaces and types

**Deliverables**:
- ✅ Directory structure created
- ✅ TypeScript types exported
- ✅ Package exports configured

---

### Phase 2: Utilities (Week 1-2)
**Goal**: Implement helper functions with tests

- [03-utils.md](./03-utils.md) - Helper utilities (buildQueryUrl, formatContext, etc.)

**Deliverables**:
- ✅ All utility functions implemented
- ✅ Unit tests (80%+ coverage)
- ✅ JSDoc documentation

---

### Phase 3: Components (Week 2-3)
**Goal**: Implement UI components

- [04-main-component.md](./04-main-component.md) - QueryContextSelector (main modal)
- [05-indicator.md](./05-indicator.md) - QueryContextIndicator (read-only display)
- [06-breadcrumb.md](./06-breadcrumb.md) - QueryContextBreadcrumb (navigation)

**Deliverables**:
- ✅ Three components fully implemented
- ✅ Component tests (Playwright)
- ✅ Accessibility compliance verified

---

### Phase 4: Testing & Documentation (Week 3-4)
**Goal**: Complete test coverage and documentation

- [07-tests.md](./07-tests.md) - Comprehensive testing strategy
- [08-docs.md](./08-docs.md) - Documentation and examples

**Deliverables**:
- ✅ 80%+ test coverage achieved
- ✅ Storybook stories created
- ✅ README and API docs complete
- ✅ Usage examples provided

---

### Phase 5: Publishing (Week 4)
**Goal**: Publish to npm registry

- [09-publish.md](./09-publish.md) - Package publishing and versioning

**Deliverables**:
- ✅ Package published to npm
- ✅ GitHub release created
- ✅ Changelog documented
- ✅ Integration guide for frontend projects

---

## Task Progress Tracking

| Phase | Task | Status | Owner | Notes |
|-------|------|--------|-------|-------|
| 1 | Setup | ⏳ Not Started | TBD | See 01-setup.md |
| 1 | Types | ⏳ Not Started | TBD | See 02-types.md |
| 2 | Utils | ⏳ Not Started | TBD | See 03-utils.md |
| 3 | Main Component | ⏳ Not Started | TBD | See 04-main-component.md |
| 3 | Indicator | ⏳ Not Started | TBD | See 05-indicator.md |
| 3 | Breadcrumb | ⏳ Not Started | TBD | See 06-breadcrumb.md |
| 4 | Tests | ⏳ Not Started | TBD | See 07-tests.md |
| 4 | Docs | ⏳ Not Started | TBD | See 08-docs.md |
| 5 | Publish | ⏳ Not Started | TBD | See 09-publish.md |

**Status Legend**:
- ⏳ Not Started
- 🚧 In Progress
- ✅ Complete
- ⚠️ Blocked

---

## Dependencies

### External Dependencies
- `carbon-components-svelte`: ^0.84.0 (already in CHUCC-SQUI)
- `svelte`: ^5.0.0 (already in CHUCC-SQUI)

### Dev Dependencies
- `vitest`: ^1.0.0 (for unit tests)
- `@playwright/test`: ^1.40.0 (for component tests)
- `@storybook/svelte`: ^8.0.0 (for visual documentation)

---

## Success Criteria

### Technical
- ✅ All components render correctly in isolation
- ✅ All utility functions pass unit tests
- ✅ Component tests cover happy paths and edge cases
- ✅ Accessibility audit passes (axe-core)
- ✅ TypeScript compilation succeeds with no errors
- ✅ Bundle size < 50KB (minified + gzipped)

### Functional
- ✅ Context selector works with all three context types (branch, commit, asOf)
- ✅ Validation catches invalid inputs (malformed UUIDs, invalid timestamps)
- ✅ Callbacks work correctly (loadBranches, searchCommits, contextChange)
- ✅ Component is stateless (no internal state leaks)

### Documentation
- ✅ README explains installation and basic usage
- ✅ API documentation covers all props, events, slots
- ✅ Examples demonstrate common use cases
- ✅ Storybook stories show all component states

### Integration
- ✅ CHUCC frontend can import and use component
- ✅ No breaking changes to existing CHUCC-SQUI components
- ✅ Works in both SvelteKit and standalone Svelte apps

---

## Risks and Mitigations

### Risk 1: Carbon Components API Changes
**Impact**: Medium
**Mitigation**: Pin to specific Carbon version, test with multiple versions

### Risk 2: Svelte 5 Breaking Changes
**Impact**: High
**Mitigation**: Use stable Svelte 5 features only, avoid experimental APIs

### Risk 3: Bundle Size Bloat
**Impact**: Low
**Mitigation**: Use tree-shaking, lazy-load sub-components, measure regularly

### Risk 4: Backend API Not Finalized
**Impact**: Medium
**Mitigation**: Use mock data for development, abstract API layer in frontend

---

## Future Enhancements (Post v1.0.0)

### v1.1.0
- CommitSearchInput sub-component with autocomplete
- Recent contexts history (localStorage)
- Keyboard shortcuts (Ctrl+K for quick switch)
- Context presets/bookmarks

### v1.2.0
- Context comparison view (side-by-side diff)
- Context timeline visualization (D3.js)
- Integration with commit graph visualization

### v2.0.0
- Multi-dataset context support
- Advanced filtering (by author, date range)
- Context templates (save/load custom configs)

---

## Communication Plan

### Weekly Updates
- Status report every Friday
- Blockers escalated immediately
- Demo ready components as completed

### Stakeholder Reviews
- Week 1: Types and utilities review
- Week 2: Component design review
- Week 3: Accessibility audit
- Week 4: Pre-publish review

---

## References

- [Component Specification](../03-chucc-squi-spec/query-context-selector-spec.md)
- [Frontend Concept](../02-concept/ui-mockups/01-query-workbench.md#22-query-context-selector)
- [SPARQL 1.2 Protocol VC Extension](https://github.com/arne-bdt/CHUCC-server/blob/main/docs/protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [CHUCC-SQUI Repository](https://github.com/arne-bdt/CHUCC-SQUI)

---

**Roadmap Version**: 1.0
**Created**: 2025-11-10
**Last Updated**: 2025-11-10
**Status**: Draft
