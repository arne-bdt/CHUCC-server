# Frontend Integration Guide

**Purpose**: How CHUCC Frontend consumes CHUCC-SQUI protocol components
**Architecture**: Single-page application (SPA) as thin composition layer

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     CHUCC Frontend (SPA)                    │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ Query        │  │ Graph        │  │ Version      │    │
│  │ Workbench    │  │ Explorer     │  │ Control      │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
│                                                             │
│  Routing + State Management + API Integration              │
└─────────────────────────────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                        CHUCC-SQUI                           │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ SPARQL       │  │ Graph Store  │  │ Version      │    │
│  │ Components   │  │ Components   │  │ Control      │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
│                                                             │
│  Protocol-Level Components (Stateless, Callback-Driven)    │
└─────────────────────────────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    CHUCC Server (Backend)                   │
│                                                             │
│  SPARQL Protocol + GSP + Version Control Extension          │
└─────────────────────────────────────────────────────────────┘
```

---

## Responsibilities

### CHUCC-SQUI (Protocol Components)

**What it does**:
- ✅ Render UI for protocol operations (query editor, graph list, commit graph)
- ✅ Handle user interactions (button clicks, form inputs)
- ✅ Validate inputs (URI formats, SPARQL syntax)
- ✅ Format data for display (triples, commits, branches)
- ✅ Emit events for parent to handle (contextChange, executeQuery, saveGraph)
- ✅ Be themeable (Carbon Design System themes)
- ✅ Be accessible (WCAG 2.1 AA)

**What it does NOT do**:
- ❌ Make API calls to backend
- ❌ Manage application state (current dataset, user session)
- ❌ Handle routing (navigate between views)
- ❌ Store data in localStorage/IndexedDB
- ❌ Implement business logic (backup schedules, health monitoring)

---

### CHUCC Frontend (Application)

**What it does**:
- ✅ Routing (`/query`, `/graphs`, `/version`, etc.)
- ✅ State management (Svelte stores for dataset, context, user)
- ✅ API integration (fetch branches, execute queries, create commits)
- ✅ Authentication (if needed)
- ✅ Error handling (display errors, retry logic)
- ✅ Business logic (backup schedules, health checks, notifications)
- ✅ Layout (navigation, sidebar, responsive design)

**What it does NOT do**:
- ❌ Implement protocol UIs from scratch (use CHUCC-SQUI components)
- ❌ Duplicate component logic (validation, formatting, etc.)

---

## Integration Patterns

### Pattern 1: Stateless Component with Callbacks

**CHUCC-SQUI Component**:
```svelte
<!-- SPARQLEditor.svelte in CHUCC-SQUI -->
<script>
  export let query: string;
  export let onExecute: (query: string) => Promise<void>;
  export let onSave: (query: string, name: string) => Promise<void>;

  // Internal UI state only
  let isExecuting = false;

  async function handleExecute() {
    isExecuting = true;
    try {
      await onExecute(query);
    } finally {
      isExecuting = false;
    }
  }
</script>

<CodeMirrorEditor bind:value={query} />
<Button on:click={handleExecute} disabled={isExecuting}>
  Execute Query
</Button>
```

**CHUCC Frontend Usage**:
```svelte
<!-- QueryWorkbench.svelte in CHUCC Frontend -->
<script>
  import { SPARQLEditor, ResultsTable } from 'chucc-squi/sparql';
  import { queryContext, currentDataset } from '$lib/stores/app';
  import { executeQuery } from '$lib/services/api';

  let query = 'SELECT * WHERE { ?s ?p ?o } LIMIT 100';
  let results = [];
  let error = null;

  async function handleExecute(q: string) {
    try {
      error = null;
      results = await executeQuery(q, $currentDataset, $queryContext);
    } catch (err) {
      error = err.message;
    }
  }
</script>

<SPARQLEditor {query} onExecute={handleExecute} />

{#if error}
  <ErrorBanner message={error} />
{/if}

{#if results.length > 0}
  <ResultsTable {results} />
{/if}
```

**Key Principle**: Component is stateless (doesn't know about dataset, context). Frontend provides data via callbacks.

---

### Pattern 2: Controlled Components

**CHUCC-SQUI Component**:
```svelte
<!-- QueryContextSelector.svelte in CHUCC-SQUI -->
<script>
  export let currentContext: QueryContext;
  export let branches: Branch[];

  // Component doesn't fetch branches itself
  // Parent provides branches via prop
</script>
```

**CHUCC Frontend Usage**:
```svelte
<script>
  import { QueryContextSelector } from 'chucc-squi/version-control';
  import { queryContext } from '$lib/stores/app';
  import { fetchBranches } from '$lib/services/api';

  let branches = [];

  onMount(async () => {
    branches = await fetchBranches($currentDataset);
  });
</script>

<QueryContextSelector
  currentContext={$queryContext}
  {branches}
  on:contextChange={(e) => queryContext.set(e.detail.context)}
/>
```

**Key Principle**: Frontend controls data flow. Component displays and emits events.

---

### Pattern 3: Composition of Multiple Components

**CHUCC Frontend View**:
```svelte
<!-- QueryWorkbench.svelte -->
<script>
  import { SPARQLEditor, ResultsTable, PrefixManager } from 'chucc-squi/sparql';
  import { QueryContextSelector } from 'chucc-squi/version-control';

  // Frontend composes multiple components
  // Handles coordination between them
</script>

<div class="query-workbench">
  <header>
    <QueryContextSelector {...contextProps} />
    <PrefixManager {...prefixProps} />
  </header>

  <main>
    <SPARQLEditor {...editorProps} />
  </main>

  <footer>
    <ResultsTable {...resultsProps} />
  </footer>
</div>

<style>
  /* Frontend defines layout */
  .query-workbench {
    display: grid;
    grid-template-rows: auto 1fr auto;
    height: 100vh;
  }
</style>
```

**Key Principle**: Frontend owns layout and coordination. Components are building blocks.

---

### Pattern 4: AsOf Resolution

**Challenge**: asOf context resolves to commit server-side. Should UI show resolved commit?

**Option A - Show both** (recommended):
```svelte
<script>
  let context = { type: 'asOf', asOf: '2025-11-10T10:30:00Z' };
  let resolvedCommit = null;

  async function handleExecute() {
    const response = await executeQuery(query, dataset, context);
    // Server includes resolved commit in response headers
    resolvedCommit = response.headers.get('X-Resolved-Commit');
  }
</script>

<QueryContextIndicator {context} />
{#if resolvedCommit}
  <small>(resolved to commit {resolvedCommit.slice(0, 7)})</small>
{/if}
```

**Option B - Convert to CommitContext after resolution**:
```svelte
async function handleExecute() {
  if (context.type === 'asOf') {
    // Convert to commit context after resolution
    const commit = await resolveAsOf(context.asOf);
    context = { type: 'commit', commit };
  }
  await executeQuery(query, dataset, context);
}
```

**Recommendation**: Use Option A. Keep asOf as user input but show resolved commit for transparency.

---

## State Management

**IMPORTANT**: As of 2025-11-12, CHUCC-SQUI migrated to context-based store instances to eliminate state leakage. See [14-store-migration-impact.md](./14-store-migration-impact.md) for full details.

### Approach 1: Global Stores (Legacy, Still Supported)

**Use when**: Simple apps, backward compatibility, or Phase 1 implementation

```typescript
// src/lib/stores/app.ts

import { writable, derived } from 'svelte/store';
import type { QueryContext } from 'chucc-squi/version-control';

// Current dataset (selected by user in Dataset Manager)
export const currentDataset = writable<string>('default');

// Current query context (branch/commit/asOf)
export const queryContext = writable<QueryContext>({
  type: 'branch',
  branch: 'main'
});

// Current author (for commit attribution)
export const currentAuthor = writable<string>('');

// Branches (cached per dataset)
export const branchCache = writable<Map<string, Branch[]>>(new Map());

// Derived: branches for current dataset
export const branches = derived(
  [currentDataset, branchCache],
  ([$dataset, $cache]) => $cache.get($dataset) || []
);
```

**Pros**:
- Simple to implement
- No context boilerplate
- Works with CHUCC-SQUI fallback mechanism

**Cons**:
- Shared state across tabs/instances
- Testing isolation challenges
- Storybook story contamination

---

### Approach 2: Context-Based Stores (Recommended)

**Use when**: Multiple instances, better isolation, or Phase 2+ implementation

```typescript
// src/lib/stores/app.ts

import { writable, derived } from 'svelte/store';
import type { QueryContext } from 'chucc-squi/version-control';

/**
 * Factory function creates fresh store instances.
 * Used by StoreProvider for context-based isolation.
 */
export function createAppStores() {
  const currentDataset = writable<string>('default');
  const queryContext = writable<QueryContext>({
    type: 'branch',
    branch: 'main'
  });
  const currentAuthor = writable<string>('');
  const branchCache = writable<Map<string, Branch[]>>(new Map());

  const branches = derived(
    [currentDataset, branchCache],
    ([$dataset, $cache]) => $cache.get($dataset) || []
  );

  return {
    currentDataset,
    queryContext,
    currentAuthor,
    branchCache,
    branches
  };
}

// Export singleton for backward compatibility
export const appStores = createAppStores();
export const { currentDataset, queryContext, currentAuthor, branchCache, branches } = appStores;
```

```typescript
// src/lib/stores/context.ts

import { getContext, setContext } from 'svelte';
import { createAppStores } from './app';

// Context key (Symbol for type safety)
export const APP_STORES_KEY = Symbol('app-stores');

/**
 * Set app stores in context (call from root component).
 */
export function setAppStores(stores = createAppStores()) {
  setContext(APP_STORES_KEY, stores);
  return stores;
}

/**
 * Get app stores from context (with fallback to global).
 */
export function getAppStores() {
  // Try context first
  const contextStores = getContext(APP_STORES_KEY);
  if (contextStores) return contextStores;

  // Fall back to global singleton
  return appStores;
}
```

```svelte
<!-- src/lib/components/AppStoreProvider.svelte -->
<script>
  import { setAppStores } from '$lib/stores/context';

  // Create fresh stores for this component tree
  const stores = setAppStores();
</script>

<slot />
```

```svelte
<!-- src/routes/+layout.svelte - Root component -->
<script>
  import AppStoreProvider from '$lib/components/AppStoreProvider.svelte';
</script>

<AppStoreProvider>
  <slot />
</AppStoreProvider>
```

**Pros**:
- Isolated state per component tree
- Better testing (no cross-contamination)
- Multiple tabs work independently
- Follows CHUCC-SQUI patterns

**Cons**:
- More boilerplate (provider + context)
- SSR considerations (context vs global)
- Requires Svelte context understanding

---

### Component-Local State (Components)

```typescript
// Inside SPARQLEditor component
let isExecuting = false;
let validationErrors = [];
let cursorPosition = { line: 0, ch: 0 };
```

**Rule**: Only UI state lives in components. Application state lives in stores.

---

### Using Stores in Components

#### Approach 1: Direct Import (Legacy)

```svelte
<!-- src/routes/query/+page.svelte -->
<script>
  import { queryContext, currentDataset } from '$lib/stores/app';
  import { executeQuery } from '$lib/services/api';

  async function handleExecute(query: string) {
    const results = await executeQuery(query, $currentDataset, $queryContext);
    return results;
  }
</script>
```

**Pros**: Simple, minimal code
**Cons**: Global state, testing challenges

#### Approach 2: Context Accessor (Recommended)

```svelte
<!-- src/routes/query/+page.svelte -->
<script>
  import { getAppStores } from '$lib/stores/context';
  import { executeQuery } from '$lib/services/api';

  // Get stores from context (or fallback to global)
  const { queryContext, currentDataset } = getAppStores();

  async function handleExecute(query: string) {
    const results = await executeQuery(query, $currentDataset, $queryContext);
    return results;
  }
</script>
```

**Pros**: Isolated state, better testing, forward-compatible
**Cons**: Slightly more verbose

---

## API Integration

### Service Layer (Frontend)

```typescript
// src/lib/services/api.ts

import type { QueryContext } from 'chucc-squi/version-control';
import { buildQueryUrl } from 'chucc-squi/version-control';

export async function executeQuery(
  query: string,
  dataset: string,
  context: QueryContext
): Promise<QueryResults> {
  const url = buildQueryUrl('/query', { dataset, context });

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/sparql-query' },
    body: query
  });

  if (!response.ok) {
    throw new Error(`Query failed: ${response.statusText}`);
  }

  return response.json();
}

export async function fetchBranches(dataset: string): Promise<Branch[]> {
  const response = await fetch(`/version/branches?dataset=${dataset}`);
  return response.json();
}

export async function createCommit(
  dataset: string,
  message: string,
  patch: string
): Promise<{ commitId: string }> {
  const response = await fetch(`/version/commits?dataset=${dataset}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/rdf-patch',
      'SPARQL-VC-Message': message,
      'SPARQL-VC-Author': getCurrentAuthor()
    },
    body: patch
  });

  return response.json();
}
```

**Key Principle**: Frontend owns all API calls. Components never call APIs directly.

---

## Error Handling

### In Components (CHUCC-SQUI)

```svelte
<!-- Component validates and shows inline errors -->
<script>
  export let value: string;

  $: validation = validateInput(value);
</script>

{#if validation.errors.length > 0}
  <InlineNotification kind="error" title="Validation Error">
    {validation.errors[0].message}
  </InlineNotification>
{/if}
```

### In Frontend (Application)

```svelte
<!-- Frontend handles API errors and shows global errors -->
<script>
  import { errorStore } from '$lib/stores/errors';

  async function handleExecute(query) {
    try {
      const results = await executeQuery(query, $currentDataset, $queryContext);
      // Success handling...
    } catch (error) {
      errorStore.add({
        title: 'Query Execution Failed',
        message: error.message,
        timestamp: new Date()
      });
    }
  }
</script>

<!-- Global error banner at top of app -->
{#if $errorStore.length > 0}
  <ErrorBanner errors={$errorStore} />
{/if}
```

---

## Example: Complete Query Workbench Integration

### CHUCC-SQUI Provides

```typescript
// SPARQLEditor component
// ResultsTable component
// PrefixManager component
```

### CHUCC Frontend Implements

```svelte
<!-- src/routes/query/+page.svelte -->
<script lang="ts">
  import { onMount } from 'svelte';
  import { SPARQLEditor, ResultsTable, PrefixManager } from 'chucc-squi/sparql';
  import { QueryContextSelector } from 'chucc-squi/version-control';
  import { queryContext, currentDataset, currentAuthor } from '$lib/stores/app';
  import { executeQuery, fetchBranches, fetchPrefixes } from '$lib/services/api';

  // Local state
  let query = 'SELECT * WHERE { ?s ?p ?o } LIMIT 100';
  let results = [];
  let branches = [];
  let prefixes = [];
  let error = null;
  let isExecuting = false;

  // Load initial data
  onMount(async () => {
    try {
      [branches, prefixes] = await Promise.all([
        fetchBranches($currentDataset),
        fetchPrefixes($currentDataset)
      ]);
    } catch (err) {
      error = err.message;
    }
  });

  // Handle query execution
  async function handleExecuteQuery(q: string) {
    isExecuting = true;
    error = null;
    try {
      results = await executeQuery(q, $currentDataset, $queryContext);
    } catch (err) {
      error = err.message;
    } finally {
      isExecuting = false;
    }
  }

  // Handle context change
  function handleContextChange(event: CustomEvent) {
    queryContext.set(event.detail.context);
    // Optionally: re-execute query with new context
  }

  // Handle prefix updates
  function handlePrefixUpdate(event: CustomEvent) {
    prefixes = event.detail.prefixes;
    // Optionally: save to backend
  }
</script>

<!-- Layout -->
<div class="query-workbench">
  <header class="toolbar">
    <QueryContextSelector
      currentContext={$queryContext}
      {branches}
      on:contextChange={handleContextChange}
      on:loadBranches={async () => {
        branches = await fetchBranches($currentDataset);
      }}
    />

    <PrefixManager
      {prefixes}
      on:update={handlePrefixUpdate}
    />
  </header>

  <main class="editor-pane">
    <SPARQLEditor
      bind:query
      {prefixes}
      onExecute={handleExecuteQuery}
      {isExecuting}
    />
  </main>

  <footer class="results-pane">
    {#if error}
      <ErrorBanner message={error} />
    {/if}

    {#if results.length > 0}
      <ResultsTable {results} />
    {/if}
  </footer>
</div>

<style>
  .query-workbench {
    display: grid;
    grid-template-rows: 60px 1fr 1fr;
    height: 100vh;
  }

  .toolbar {
    display: flex;
    gap: 1rem;
    padding: 1rem;
    border-bottom: 1px solid var(--cds-border-subtle-01);
  }

  .editor-pane, .results-pane {
    padding: 1rem;
    overflow: auto;
  }
</style>
```

**Lines of Code**:
- Frontend: ~100 lines (routing, state, API, layout)
- CHUCC-SQUI components: 0 lines (imported from package)

**Without CHUCC-SQUI**:
- Frontend would need: ~800+ lines (editor, table, context selector, prefix manager)

**Savings**: 87% reduction in frontend code

---

## Testing Strategy

### Component Tests (CHUCC-SQUI)

```typescript
// SPARQLEditor.test.ts
import { render, fireEvent } from '@testing-library/svelte';
import SPARQLEditor from '$lib/sparql/SPARQLEditor.svelte';

test('emits execute event when button clicked', async () => {
  const handleExecute = vi.fn();
  const { getByRole } = render(SPARQLEditor, {
    props: {
      query: 'SELECT * WHERE { ?s ?p ?o }',
      onExecute: handleExecute
    }
  });

  await fireEvent.click(getByRole('button', { name: /execute/i }));

  expect(handleExecute).toHaveBeenCalledWith('SELECT * WHERE { ?s ?p ?o }');
});
```

### Integration Tests (Frontend)

```typescript
// QueryWorkbench.test.ts
import { render, fireEvent, waitFor } from '@testing-library/svelte';
import { rest } from 'msw';
import { setupServer } from 'msw/node';
import QueryWorkbench from '$routes/query/+page.svelte';

const server = setupServer(
  rest.post('/query', (req, res, ctx) => {
    return res(ctx.json({ results: [...] }));
  })
);

beforeAll(() => server.listen());
afterAll(() => server.close());

test('executes query and displays results', async () => {
  const { getByRole, getByText } = render(QueryWorkbench);

  const executeButton = getByRole('button', { name: /execute/i });
  await fireEvent.click(executeButton);

  await waitFor(() => {
    expect(getByText(/results/i)).toBeInTheDocument();
  });
});
```

### E2E Tests (Playwright)

```typescript
// query-workbench.spec.ts
import { test, expect } from '@playwright/test';

test('query workbench flow', async ({ page }) => {
  await page.goto('/query');

  // Wait for components to load
  await page.waitForSelector('[data-testid="sparql-editor"]');

  // Enter query
  await page.fill('[data-testid="sparql-editor"]', 'SELECT * WHERE { ?s ?p ?o } LIMIT 10');

  // Execute query
  await page.click('button:has-text("Execute")');

  // Verify results displayed
  await expect(page.locator('[data-testid="results-table"]')).toBeVisible();
});
```

---

## Performance Considerations

### Lazy Loading Components

```typescript
// src/routes/query/+page.ts (SvelteKit load function)
export async function load() {
  // Lazy load heavy components
  const { SPARQLEditor } = await import('chucc-squi/sparql');
  const { CommitGraph } = await import('chucc-squi/version-control');

  return {
    SPARQLEditor,
    CommitGraph
  };
}
```

### Code Splitting

```javascript
// vite.config.js
export default {
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'chucc-squi-sparql': ['chucc-squi/sparql'],
          'chucc-squi-vc': ['chucc-squi/version-control'],
          'chucc-squi-gsp': ['chucc-squi/graph-store']
        }
      }
    }
  }
};
```

---

## Deployment

### CHUCC-SQUI (NPM Package)

```bash
# Published to npm registry
npm publish

# Semantic versioning
v1.0.0 - Initial release
v1.1.0 - Add commit search feature
v1.2.0 - Add RDF tree view
v2.0.0 - Breaking change (Svelte 6)
```

### CHUCC Frontend (Web App)

```bash
# Build frontend
npm run build

# Deploy to hosting (Netlify, Vercel, etc.)
# Frontend imports CHUCC-SQUI from npm
```

**Benefits**:
- CHUCC-SQUI updates independently
- Frontend can pin to specific version
- Multiple frontends can use same CHUCC-SQUI version

---

## Store Migration (CHUCC-SQUI v2.x)

**Context**: CHUCC-SQUI migrated to context-based stores as of 2025-11-12 (commit [53689c8](https://github.com/arne-bdt/CHUCC-SQUI/commit/53689c8c4b1db43b25032b383cc0037324b6bfae)).

See [14-store-migration-impact.md](./14-store-migration-impact.md) for comprehensive analysis.

### What Changed?

| Before (v1.x) | After (v2.x) |
|---------------|--------------|
| Global singleton stores | Context-based store instances |
| `import { queryStore } from 'chucc-squi'` | `import { getQueryStore } from 'chucc-squi'` |
| Single shared state | Isolated state per component tree |
| Storybook story leakage | Clean isolation per story |

### Migration Strategy

**Option A: No Changes Required** (Recommended for initial implementation)
- Keep using global stores in frontend
- CHUCC-SQUI components use fallback mechanism
- Works immediately, no code changes

**Option B: Adopt Context Pattern** (For better isolation)
1. Convert stores to factory functions
2. Add `AppStoreProvider` at root
3. Use context accessors in components
4. Benefits: isolated state, better testing

**Timeline**:
- Option A: 0 days (already works)
- Option B: 1-2 days (when isolation needed)

**Recommendation**: Start with Option A. Migrate to Option B when you need:
- Multiple browser tabs with independent state
- Better Storybook story isolation
- Improved integration test isolation

---

## Component Adoption Path

### Phase 1: Adopt QueryContextSelector

```diff
- <!-- Frontend implements own context selector -->
- <select bind:value={contextType}>
-   <option value="branch">Branch</option>
-   <option value="commit">Commit</option>
- </select>

+ <!-- Use CHUCC-SQUI component -->
+ <QueryContextSelector
+   currentContext={$queryContext}
+   {branches}
+   on:contextChange={(e) => queryContext.set(e.detail.context)}
+ />
```

**Result**: 50 lines removed from frontend

### Phase 2: Adopt SPARQLEditor + ResultsTable

```diff
- <!-- Frontend implements own editor and table -->
- <textarea bind:value={query} />
- <table>...</table>

+ <SPARQLEditor bind:query onExecute={handleExecute} />
+ <ResultsTable {results} />
```

**Result**: 300 lines removed from frontend

### Phase 3: Adopt All Components

**Result**: 70%+ frontend code reduction

---

**Document Version**: 1.1
**Created**: 2025-11-10
**Updated**: 2025-11-12 (Store migration to context-based pattern)
**Status**: Planning
