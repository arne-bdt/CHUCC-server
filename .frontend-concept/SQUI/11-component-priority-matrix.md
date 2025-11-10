# Component Priority Matrix

**Purpose**: Determine implementation order for CHUCC-SQUI protocol components
**Method**: Score components by Impact × Frontend Need × Complexity

---

## Scoring Criteria

### Impact (1-5)
- **5**: Critical for core functionality
- **4**: High value, significant UX improvement
- **3**: Medium value, notable feature
- **2**: Nice-to-have, minor improvement
- **1**: Low priority, minimal impact

### Frontend Need (1-5)
- **5**: Immediate need (blocks frontend development)
- **4**: Needed within 1-2 sprints
- **3**: Needed within 1-2 months
- **2**: Needed eventually
- **1**: Low urgency

### Complexity (1-5, inverse scoring)
- **5**: Very simple (1-2 days)
- **4**: Simple (3-5 days)
- **3**: Medium (1-2 weeks)
- **2**: Complex (3-4 weeks)
- **1**: Very complex (5+ weeks)

### Priority Score
`Priority = Impact × Frontend Need × Complexity`

Higher score = Higher priority

---

## Component Scoring

| Component | Impact | Need | Complexity | **Score** | **Priority** |
|-----------|--------|------|------------|-----------|--------------|
| **QueryContextSelector** | 5 | 5 | 3 | **75** | **P0** ✅ (In progress) |
| **SPARQLEditor** | 5 | 5 | 4 | **100** | **P1** 🔴 |
| **ResultsTable** | 5 | 5 | 4 | **100** | **P1** 🔴 |
| **GraphList** | 5 | 5 | 4 | **100** | **P1** 🔴 |
| **TripleTable** | 5 | 5 | 3 | **75** | **P1** 🔴 |
| **CommitGraph** | 4 | 4 | 2 | **32** | **P1** 🔴 |
| **BranchList** | 4 | 4 | 4 | **64** | **P1** 🔴 |
| **RDFPatchViewer** | 4 | 4 | 4 | **64** | **P1** 🔴 |
| **PrefixManager** | 3 | 4 | 4 | **48** | **P2** 🟡 |
| **TripleEditor** | 4 | 3 | 3 | **36** | **P2** 🟡 |
| **GraphUploader** | 4 | 3 | 4 | **48** | **P2** 🟡 |
| **ConflictResolver** | 4 | 3 | 2 | **24** | **P2** 🟡 |
| **MergeConfigurator** | 3 | 3 | 3 | **27** | **P2** 🟡 |
| **RDFTreeView** | 3 | 3 | 3 | **27** | **P2** 🟡 |
| **RDFDiffViewer** | 3 | 3 | 3 | **27** | **P2** 🟡 |
| **QueryContextIndicator** | 3 | 3 | 5 | **45** | **P2** 🟡 (In progress) |
| **QueryContextBreadcrumb** | 2 | 3 | 5 | **30** | **P2** 🟡 (In progress) |
| **ResultsGraph** | 2 | 2 | 2 | **8** | **P3** 🟢 |
| **NamespaceColorizer** | 2 | 2 | 4 | **16** | **P3** 🟢 |
| **RDFPathNavigator** | 2 | 1 | 3 | **6** | **P3** 🟢 |

---

## Priority Tiers

### P0: Foundation (Week 1-4) ✅
**Status**: In progress

- QueryContextSelector (Score: 75) ✅

**Goal**: Complete foundation for version control protocol UI

---

### P1: Essential Components (Week 5-16)
**Status**: Not started

**Score 90-100** (Critical Path):
- SPARQLEditor (Score: 100) 🔴
- ResultsTable (Score: 100) 🔴
- GraphList (Score: 100) 🔴

**Score 60-89** (High Value):
- TripleTable (Score: 75) 🔴
- BranchList (Score: 64) 🔴
- RDFPatchViewer (Score: 64) 🔴

**Score 32-59** (Version Control Core):
- CommitGraph (Score: 32) 🔴

**Goal**: Enable core frontend functionality (query, graph management, version control)

**Frontend Impact**: Without these, frontend cannot implement:
- Query Workbench view
- Graph Explorer view
- Version Control view (basic)

---

### P2: Enhanced Features (Week 17-28)
**Status**: Not started

**Score 30-50**:
- PrefixManager (Score: 48) 🟡
- GraphUploader (Score: 48) 🟡
- QueryContextIndicator (Score: 45) 🟡 (In progress)
- TripleEditor (Score: 36) 🟡
- QueryContextBreadcrumb (Score: 30) 🟡 (In progress)

**Score 20-29**:
- RDFTreeView (Score: 27) 🟡
- RDFDiffViewer (Score: 27) 🟡
- MergeConfigurator (Score: 27) 🟡
- ConflictResolver (Score: 24) 🟡

**Goal**: Improve UX and add advanced features

**Frontend Impact**: Enhances existing views but not blocking

---

### P3: Polish & Extras (Week 29+)
**Status**: Not started

- NamespaceColorizer (Score: 16) 🟢
- ResultsGraph (Score: 8) 🟢
- RDFPathNavigator (Score: 6) 🟢

**Goal**: Nice-to-have features, visual polish

**Frontend Impact**: Optional enhancements

---

## Implementation Sequence

### Option A: Sequential by Priority (Safer)

**Pros**:
- Guaranteed working foundation before moving forward
- Lower risk of integration issues
- Clear milestones

**Cons**:
- Frontend development must wait for each component
- Longer time to usable frontend

**Sequence**:
1. QueryContextSelector ✅
2. SPARQLEditor, ResultsTable, GraphList (parallel if possible)
3. TripleTable
4. BranchList, RDFPatchViewer, CommitGraph (parallel)
5. P2 components
6. P3 components

---

### Option B: Parallel by Module (Faster)

**Pros**:
- Faster time to usable frontend
- Can develop frontend views in parallel
- More efficient use of developer time

**Cons**:
- Higher coordination overhead
- Risk of integration issues
- Requires clear API contracts upfront

**Sequence**:

**Sprint 1-2 (Weeks 1-4)**:
- QueryContextSelector ✅

**Sprint 3-4 (Weeks 5-8)**:
- SPARQLEditor + ResultsTable (SPARQL module complete)
- GraphList (GSP module started)

**Sprint 5-6 (Weeks 9-12)**:
- TripleTable (GSP module continued)
- BranchList + RDFPatchViewer (VC module continued)

**Sprint 7-8 (Weeks 13-16)**:
- CommitGraph (VC module complete)
- PrefixManager (SPARQL module enhancement)

**Sprint 9-10 (Weeks 17-20)**:
- P2 components based on frontend feedback

---

### Option C: Critical Path First (Recommended)

**Strategy**: Focus on components that unblock frontend development

**Pros**:
- Frontend can start building views ASAP
- Iterative feedback loop
- Real-world testing drives component improvements

**Cons**:
- Some rework may be needed based on integration learnings

**Phase 1 (Weeks 1-4)**: Foundation
- QueryContextSelector ✅
- **Goal**: Version control context selection working

**Phase 2 (Weeks 5-8)**: Query Workbench
- SPARQLEditor (P1, Score: 100)
- ResultsTable (P1, Score: 100)
- **Goal**: Frontend can build Query Workbench view

**Phase 3 (Weeks 9-12)**: Graph Explorer
- GraphList (P1, Score: 100)
- TripleTable (P1, Score: 75)
- **Goal**: Frontend can build Graph Explorer view

**Phase 4 (Weeks 13-16)**: Version Control
- CommitGraph (P1, Score: 32)
- BranchList (P1, Score: 64)
- RDFPatchViewer (P1, Score: 64)
- **Goal**: Frontend can build Version Control view

**Phase 5 (Weeks 17+)**: Enhancements
- P2 components based on user feedback and frontend needs

---

## Recommended Approach

**Use Option C: Critical Path First**

### Rationale

1. **Unblocks Frontend Development Early**
   - After Phase 2 (Week 8), frontend can build Query Workbench
   - After Phase 3 (Week 12), frontend can build Graph Explorer
   - After Phase 4 (Week 16), frontend can build Version Control

2. **Real-World Feedback Loop**
   - Frontend developers test components immediately
   - API improvements driven by actual usage
   - Early detection of integration issues

3. **Incremental Value Delivery**
   - Usable views delivered every 4 weeks
   - Stakeholders see progress
   - Can pivot based on feedback

4. **Parallel Development Possible**
   - Once API contracts defined, frontend and CHUCC-SQUI teams can work in parallel
   - Frontend uses mocks until components ready
   - Integration happens incrementally

---

## Success Metrics

### Per Component
- ✅ 80%+ test coverage
- ✅ API documentation complete
- ✅ Storybook stories created
- ✅ Integrated in frontend (smoke test)
- ✅ Accessibility audit passed

### Per Phase
- ✅ Frontend view built using new components
- ✅ End-to-end user flow working
- ✅ Performance benchmarks met
- ✅ User feedback collected

---

## Dependencies

### External Libraries

| Component | Library | Version | Purpose |
|-----------|---------|---------|---------|
| SPARQLEditor, RDFPatchViewer | CodeMirror | 6.x | Code editing with syntax highlighting |
| ResultsTable, TripleTable | Svelte Virtual List | 4.x | Virtual scrolling for 10k+ rows |
| CommitGraph | Cytoscape.js OR D3.js | 3.x / 7.x | DAG visualization |
| GraphUploader | Carbon Components | 0.84.x | File upload UI |
| All | Carbon Design System | 0.84.x | Base UI components |

### Internal Dependencies

```
QueryContextSelector (P0)
  └─> Used by: SPARQLEditor, GraphList, TripleTable, BranchList

SPARQLEditor (P1)
  └─> Uses: PrefixManager (P2) - optional enhancement

TripleTable (P1)
  └─> Uses: TripleEditor (P2) - inline editing feature
  └─> Uses: NamespaceColorizer (P3) - optional enhancement

CommitGraph (P1)
  └─> Uses: QueryContextSelector (P0) - click commit to switch context

ConflictResolver (P2)
  └─> Uses: RDFDiffViewer (P2) - shows diff for conflicts
```

---

## Re-Evaluation Triggers

**When to re-prioritize:**
- Frontend development blocked by missing component
- User feedback indicates critical missing feature
- New protocol feature requires new component
- Performance issues require optimization component
- Breaking change in Carbon Design System

**How to re-prioritize:**
- Update Impact/Need/Complexity scores
- Recalculate priority scores
- Adjust implementation sequence
- Communicate changes to stakeholders

---

**Document Version**: 1.0
**Created**: 2025-11-10
**Last Updated**: 2025-11-10
**Next Review**: After Phase 1 completion
