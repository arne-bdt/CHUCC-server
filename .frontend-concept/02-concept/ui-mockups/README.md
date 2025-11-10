# CHUCC Frontend - UI Mockups

**Date**: 2025-11-10
**Version**: 1.0

---

## Overview

This directory contains detailed UI mockups for the 7 primary views of the CHUCC frontend:

1. **Query Workbench** (`01-query-workbench.md`) - SPARQL query interface (Fuseki-inspired)
2. **Graph Explorer** (`02-graph-explorer.md`) - Graph Store Protocol operations
3. **Version Control** (`03-version-control.md`) - Commit history and branch management (Fork-inspired)
4. **Merge & Conflicts** (`04-merge-conflicts.md`) - Conflict resolution interface
5. **Time Travel** (`05-time-travel.md`) - Historical queries with asOf selector
6. **Dataset Manager** (`06-dataset-manager.md`) - Multi-tenant dataset management
7. **Batch Operations** (`07-batch-operations.md`) - Atomic batch operation builder

---

## Mockup Format

Each mockup document includes:

1. **ASCII Wireframe** - Visual layout representation
2. **Component Breakdown** - Detailed specifications for each UI component
3. **Interaction Patterns** - How users interact with the view
4. **State Management** - What data is tracked and how
5. **Responsive Behavior** - Mobile, tablet, desktop layouts
6. **Accessibility Notes** - WCAG 2.1 AA considerations

---

## Design Principles

### From Fuseki (Query-Focused)
- ✅ Tab-based navigation for feature areas
- ✅ Split-pane layout (editor + results)
- ✅ Simple, functional design
- ✅ Dataset selector dropdown

### From Fork (Version Control)
- ✅ Three-pane layout (sidebar | graph | details)
- ✅ Colored branch graph with collapsible commits
- ✅ Drag-and-drop for operations
- ✅ Right-click context menus
- ✅ Quick Launch command palette

### CHUCC-Specific
- ✅ RDF-aware diff visualization (RDF Patch viewer)
- ✅ Graph-level conflict resolution
- ✅ Time-travel controls (asOf selector)
- ✅ Multi-dataset management
- ✅ Batch operation builder

---

## Carbon Design System

All mockups use Carbon Design System components:
- **Theme**: G10 (light gray) by default, switchable to White, G90, G100
- **Typography**: IBM Plex Sans (body), IBM Plex Mono (code)
- **Color Palette**: Carbon tokens (blue, green, red, yellow for semantics)
- **Spacing**: 8px grid system
- **Icons**: carbon-icons-svelte

---

## Next Steps

1. Review mockups for completeness and accuracy
2. Create interactive prototypes (Figma/Sketch) based on these specifications
3. User testing with stakeholders
4. Iterate based on feedback
5. Begin implementation (following component gap analysis priorities)

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)
