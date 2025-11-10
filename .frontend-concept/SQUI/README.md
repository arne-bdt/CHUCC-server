# CHUCC-SQUI Implementation Plan

**Status**: Planning Complete ✅
**Total Documentation**: 28,600+ words across 12 files
**Ready For**: Implementation in CHUCC-SQUI repository

---

## Quick Navigation

### For Implementers
Start here to build QueryContextSelector component:
1. [00-roadmap.md](./00-roadmap.md) - Overview and timeline
2. [01-setup.md](./01-setup.md) - Project setup (Week 1)
3. [02-types.md](./02-types.md) - TypeScript interfaces (Week 1)
4. [03-utils.md](./03-utils.md) - Helper utilities (Week 1-2)
5. [04-main-component.md](./04-main-component.md) - Main component (Week 2-3)
6. [05-indicator.md](./05-indicator.md) - Indicator component (Week 2-3)
7. [06-breadcrumb.md](./06-breadcrumb.md) - Breadcrumb component (Week 2-3)
8. [07-tests.md](./07-tests.md) - Testing (Week 3-4)
9. [08-docs.md](./08-docs.md) - Documentation (Week 3-4)
10. [09-publish.md](./09-publish.md) - Publishing (Week 4)

### For Architects
Strategic planning for full component library:
- [10-expanded-components.md](./10-expanded-components.md) - Catalog of 20+ components
- [11-component-priority-matrix.md](./11-component-priority-matrix.md) - Implementation priorities
- [12-frontend-integration.md](./12-frontend-integration.md) - Frontend consumption patterns

---

## What's Included

### Phase 1: QueryContextSelector (Tasks 00-09)
**Objective**: First protocol component (context selection for VC extension)

**Components**:
- QueryContextSelector - Modal-based selector
- QueryContextIndicator - Inline read-only display
- QueryContextBreadcrumb - Breadcrumb integration

**Utilities**:
- buildQueryUrl() - URL construction
- formatContext() - Human-readable formatting
- validateContext() - Input validation
- parseContextFromUrl() - URL parsing

**Timeline**: 3-4 weeks
**Deliverable**: Published to npm as `chucc-squi@1.0.0`

---

### Phase 2+: Expanded Toolkit (Tasks 10-12)

**20+ Protocol Components Planned**:

#### SPARQL Protocol (4 components)
- SPARQLEditor, ResultsTable, PrefixManager, ResultsGraph

#### Graph Store Protocol (4 components)
- GraphList, TripleTable, TripleEditor, GraphUploader

#### Version Control Protocol (8 components)
- QueryContextSelector ✅, CommitGraph, BranchList, RDFPatchViewer, ConflictResolver, MergeConfigurator

#### RDF Visualization (4 components)
- RDFTreeView, RDFDiffViewer, NamespaceColorizer, RDFPathNavigator

**Timeline**: 24 weeks total (6 phases)
**Priority**: Critical Path First (unblock frontend early)

---

## Architecture

### CHUCC-SQUI = Protocol-Level Components

```typescript
// Import from protocol-specific modules
import { SPARQLEditor, ResultsTable } from 'chucc-squi/sparql';
import { QueryContextSelector, CommitGraph } from 'chucc-squi/version-control';
import { GraphList, TripleTable } from 'chucc-squi/graph-store';
```

**Characteristics**:
- ✅ Stateless (callback-driven)
- ✅ Protocol-agnostic (works with any backend)
- ✅ Carbon Design System themed
- ✅ Accessibility compliant (WCAG 2.1 AA)
- ✅ Fully tested (80%+ coverage)
- ✅ TypeScript typed

---

### CHUCC Frontend = Thin Composition Layer

```svelte
<script>
  import { SPARQLEditor, ResultsTable } from 'chucc-squi/sparql';

  // Frontend provides: routing, state, API, layout
  // Components provide: UI, validation, events
</script>

<div class="query-workbench">
  <SPARQLEditor bind:query on:execute={handleExecute} />
  <ResultsTable {results} />
</div>
```

**Benefits**:
- 70%+ code reduction vs. building from scratch
- Automatic updates from CHUCC-SQUI improvements
- Consistent UX across all SPARQL applications

---

## Getting Started

### Option A: Implement First Component (Recommended)

**Goal**: Get QueryContextSelector working end-to-end

1. **Week 1**: Setup + Types + Utils
   - Complete tasks 01-03
   - ~10 hours work
   - Result: Foundation ready, utilities tested

2. **Week 2-3**: Components
   - Complete tasks 04-06
   - ~12 hours work
   - Result: All 3 components working

3. **Week 3-4**: Testing + Docs + Publish
   - Complete tasks 07-09
   - ~12 hours work
   - Result: Published to npm, ready for frontend

**Total**: ~34 hours over 4 weeks

**Immediate Value**: Frontend can use context selector right away

---

### Option B: Plan Next Components

**Goal**: Prioritize Phase 2 components based on frontend needs

1. Review component priority matrix (Task 11)
2. Identify blocking components for frontend development
3. Create task files for SPARQLEditor, ResultsTable, GraphList
4. Begin parallel development

**Decision Criteria**:
- Which frontend view to build first? (Query Workbench, Graph Explorer, Version Control)
- What components block that view?
- What's the critical path?

---

### Option C: Frontend Prototyping

**Goal**: Validate architecture with mocked components

1. Create frontend routes with placeholder components
2. Define API contracts between frontend and components
3. Build layouts and state management
4. Replace mocks with real CHUCC-SQUI components as they're built

**Benefits**:
- Frontend development can start immediately
- Early feedback on component APIs
- Validates integration patterns

---

## Success Metrics

### Per Component
- ✅ 80%+ test coverage
- ✅ Storybook stories created
- ✅ API documentation complete
- ✅ Used in at least one frontend view
- ✅ Accessibility audit passed

### Per Phase
- ✅ All components published to npm
- ✅ Frontend view(s) implemented using components
- ✅ End-to-end user flow working
- ✅ Performance targets met

### Overall Project
- ✅ 70%+ frontend code reduction achieved
- ✅ All protocol operations covered by components
- ✅ Reusable by other SPARQL projects
- ✅ Community adoption (GitHub stars, npm downloads)

---

## Technical Stack

### Dependencies

| Category | Library | Version | Purpose |
|----------|---------|---------|---------|
| Framework | Svelte | 5.x | Component framework |
| UI Components | Carbon Components | 0.84.x | Base UI system |
| Code Editor | CodeMirror | 6.x | SPARQL/RDF editing |
| Graph Viz | Cytoscape.js | 3.x | Commit DAG visualization |
| Testing | Vitest | 1.x | Unit tests |
| Testing | Playwright | 1.x | Component tests |
| Build | Vite | 5.x | Build tool |

### Dev Tools
- TypeScript (strict mode)
- Prettier (code formatting)
- ESLint (linting)
- Storybook (component showcase)
- Chromatic (visual regression)

---

## Quality Gates

Every component must pass:

1. **Unit Tests**: 80%+ coverage (Vitest)
2. **Component Tests**: Happy path + edge cases (Playwright)
3. **Accessibility**: No axe violations (WCAG 2.1 AA)
4. **TypeScript**: No compilation errors (strict mode)
5. **Documentation**: API docs + usage examples
6. **Storybook**: At least 3 stories per component
7. **Bundle Size**: Component < 20KB gzipped
8. **Performance**: Renders in < 100ms (virtual scrolling for large data)

---

## File Organization

### In This Directory (.frontend-concept/SQUI/)

```
SQUI/
├── README.md                           # This file
├── 00-roadmap.md                       # Overview and timeline
├── 01-setup.md                         # Project setup
├── 02-types.md                         # TypeScript interfaces
├── 03-utils.md                         # Helper utilities
├── 04-main-component.md                # QueryContextSelector
├── 05-indicator.md                     # QueryContextIndicator
├── 06-breadcrumb.md                    # QueryContextBreadcrumb
├── 07-tests.md                         # Testing strategy
├── 08-docs.md                          # Documentation
├── 09-publish.md                       # Publishing
├── 10-expanded-components.md           # Component catalog
├── 11-component-priority-matrix.md     # Priorities
└── 12-frontend-integration.md          # Integration guide
```

### In CHUCC-SQUI Repository (Future)

```
CHUCC-SQUI/
├── src/lib/
│   ├── sparql/
│   │   ├── SPARQLEditor.svelte
│   │   ├── ResultsTable.svelte
│   │   └── index.ts
│   ├── graph-store/
│   │   ├── GraphList.svelte
│   │   ├── TripleTable.svelte
│   │   └── index.ts
│   ├── version-control/
│   │   ├── QueryContextSelector.svelte  ✅ First component
│   │   ├── QueryContextIndicator.svelte
│   │   ├── QueryContextBreadcrumb.svelte
│   │   ├── types.ts
│   │   ├── utils.ts
│   │   └── index.ts
│   └── rdf/
│       ├── RDFTreeView.svelte
│       └── index.ts
├── tests/
│   └── version-control/
│       ├── utils.test.ts
│       └── QueryContextSelector.test.ts
└── docs/
    └── version-control/
        ├── README.md
        └── api/
```

---

## Related Documentation

### Frontend Concept
- [../02-concept/ui-mockups/01-query-workbench.md](../02-concept/ui-mockups/01-query-workbench.md) - Query Workbench mockup with context selector
- [../02-concept/ui-mockups/03-version-control.md](../02-concept/ui-mockups/03-version-control.md) - Version Control mockup with commit graph

### Component Spec
- [../03-chucc-squi-spec/query-context-selector-spec.md](../03-chucc-squi-spec/query-context-selector-spec.md) - Original detailed specification

### Architecture
- [../../docs/architecture/README.md](../../docs/architecture/README.md) - CHUCC-server architecture
- [../../docs/protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md](../../docs/protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md) - Protocol specification

---

## FAQ

### Q: Why separate CHUCC-SQUI and CHUCC Frontend?

**A**: Separation of concerns. CHUCC-SQUI provides reusable protocol components that any SPARQL system can use. CHUCC Frontend is the specific application for CHUCC-server with routing, state, business logic.

**Analogy**: CHUCC-SQUI is like Bootstrap/Material-UI (component library). CHUCC Frontend is like your specific website built with Bootstrap.

---

### Q: Can I use CHUCC-SQUI with a non-CHUCC backend?

**A**: Yes! Components are protocol-level. If your backend implements:
- SPARQL 1.2 Protocol → Use `chucc-squi/sparql` components
- Graph Store Protocol → Use `chucc-squi/graph-store` components
- Version Control Extension → Use `chucc-squi/version-control` components

Components are backend-agnostic (callback-driven).

---

### Q: What if I only need one component?

**A**: Tree-shaking! Modern bundlers only include what you import:

```typescript
// Only imports QueryContextSelector code
import { QueryContextSelector } from 'chucc-squi/version-control';
```

Result: ~15KB gzipped for just the context selector.

---

### Q: How do I contribute?

**A**:
1. Pick a component from priority matrix (Task 11)
2. Follow the task files (Tasks 01-09 for QueryContextSelector)
3. Submit PR to CHUCC-SQUI repository
4. All PRs must pass quality gates

See [01-setup.md](./01-setup.md) for development setup.

---

### Q: When will CHUCC-SQUI be stable?

**A**:
- **v1.0.0**: After QueryContextSelector published (4 weeks)
- **v1.5.0**: After P1 components complete (16 weeks)
- **v2.0.0**: After all planned components complete (24 weeks)

Semantic versioning used for compatibility.

---

## Next Steps

**Immediate** (This Week):
1. Review this roadmap
2. Choose implementation approach (Option A/B/C above)
3. Create GitHub issues in CHUCC-SQUI for tracked tasks

**Short Term** (Next 4 Weeks):
1. Implement QueryContextSelector (Tasks 01-09)
2. Publish to npm as `chucc-squi@1.0.0`
3. Integrate in CHUCC Frontend Query Workbench

**Medium Term** (Weeks 5-16):
1. Implement P1 components (SPARQLEditor, ResultsTable, GraphList, etc.)
2. Build frontend views using components
3. Gather user feedback

**Long Term** (Weeks 17-24):
1. Implement P2 components
2. Polish and optimization
3. Expand to P3 components based on demand

---

## Questions or Feedback?

This planning documentation lives in the CHUCC-server repository:
`/home/user/CHUCC-server/.frontend-concept/SQUI/`

For implementation, components will live in:
`https://github.com/arne-bdt/CHUCC-SQUI`

---

**Planning Complete**: 2025-11-10
**Ready For Implementation**: Yes ✅
**Estimated First Release**: 4 weeks from start
