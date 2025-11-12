# CHUCC-SQUI Store Migration Impact Assessment

**Date**: 2025-11-12
**CHUCC-SQUI Commit**: [53689c8](https://github.com/arne-bdt/CHUCC-SQUI/commit/53689c8c4b1db43b25032b383cc0037324b6bfae)
**Migration**: Global Singleton Stores → Context-Based Store Instances

---

## Executive Summary

CHUCC-SQUI migrated from global singleton stores to context-based store instances to eliminate state leakage between component instances (particularly in Storybook stories). This migration affects how the CHUCC Frontend integrates with CHUCC-SQUI components.

**Impact**: Low-to-Medium (backward compatible but recommended patterns change)
**Action Required**: Update frontend integration documentation and implementation patterns

---

## Migration Overview

### Old Pattern (Deprecated)

```typescript
// Global singleton - shared across all component instances
import { queryStore } from 'chucc-squi/stores';

// Component uses global store directly
const query = $queryStore;
```

**Problems**:
- State leakage between Storybook stories
- Shared state across multiple component instances
- Difficult to test in isolation
- Multiple browser tabs affect each other

### New Pattern (Recommended)

```svelte
<!-- App.svelte - Root component -->
<script>
  import { StoreProvider } from 'chucc-squi';

  // Provide fresh store instances to component tree
</script>

<StoreProvider initialQuery="SELECT * { ?s ?p ?o }" initialEndpoint="http://...">
  <QueryWorkbench />
</StoreProvider>
```

```typescript
// Component.svelte - Child component
import { getQueryStore } from 'chucc-squi/stores';

// Gets context-provided instance (or falls back to global)
const queryStore = getQueryStore();
const query = $queryStore;
```

**Benefits**:
- Isolated state per component tree
- No cross-contamination in Storybook/tests
- Clean state for each browser tab/instance
- Backward compatible (falls back to globals)

---

## API Changes

### New Components

| Component | Purpose | Import |
|-----------|---------|--------|
| `StoreProvider` | Provides fresh store instances via context | `import { StoreProvider } from 'chucc-squi'` |

### New Utilities

| Function | Purpose | Import |
|----------|---------|--------|
| `getQueryStore()` | Get query store from context or fallback | `import { getQueryStore } from 'chucc-squi/stores'` |
| `getResultsStore()` | Get results store from context or fallback | `import { getResultsStore } from 'chucc-squi/stores'` |
| `getUIStore()` | Get UI store from context or fallback | `import { getUIStore } from 'chucc-squi/stores'` |
| `getEndpointStore()` | Get endpoint store from context or fallback | `import { getEndpointStore } from 'chucc-squi/stores'` |

### Context Keys

| Symbol | Purpose | Import |
|--------|---------|--------|
| `QUERY_STORE_KEY` | Context key for queryStore | `import { QUERY_STORE_KEY } from 'chucc-squi/context'` |
| `RESULTS_STORE_KEY` | Context key for resultsStore | `import { RESULTS_STORE_KEY } from 'chucc-squi/context'` |
| `UI_STORE_KEY` | Context key for uiStore | `import { UI_STORE_KEY } from 'chucc-squi/context'` |
| `ENDPOINT_STORE_KEY` | Context key for endpointStore | `import { ENDPOINT_STORE_KEY } from 'chucc-squi/context'` |

---

## Impact on Frontend Concept Documents

### Files Requiring Updates

| File | Impact | Status |
|------|--------|--------|
| `SQUI/12-frontend-integration.md` | **HIGH** - Contains extensive store usage examples | ⚠️ Needs Update |
| `SQUI/01-setup.md` | **LOW** - May need StoreProvider setup notes | ✅ Review Needed |
| `02-concept/ui-mockups/*.md` | **NONE** - No detailed store implementations | ✅ No Changes |
| `03-chucc-squi-spec/*.md` | **NONE** - Component specs don't reference stores | ✅ No Changes |

### Specific Changes Required

#### 12-frontend-integration.md

**Section: "State Management" (lines 269-299)**

Current approach shows global singleton pattern:
```typescript
// ❌ OLD (still works but not recommended)
import { writable, derived } from 'svelte/store';

export const currentDataset = writable<string>('default');
export const queryContext = writable<QueryContext>({ type: 'branch', branch: 'main' });
```

Should be updated to:
```typescript
// ✅ NEW (context-based with factory functions)
import { writable, derived } from 'svelte/store';

// Factory function creates fresh instances
export function createAppStores() {
  const currentDataset = writable<string>('default');
  const queryContext = writable<QueryContext>({ type: 'branch', branch: 'main' });
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

// Export singletons for backward compatibility
export const appStores = createAppStores();
export const { currentDataset, queryContext, currentAuthor, branchCache, branches } = appStores;
```

**Section: "Pattern 1-3 Examples" (lines 83-228)**

Current examples show direct store imports:
```svelte
// ❌ OLD
import { queryContext, currentDataset } from '$lib/stores/app';
const results = await executeQuery(q, $currentDataset, $queryContext);
```

Should add context-based alternative:
```svelte
// ✅ NEW (with StoreProvider)
<script>
  import { getContext } from 'svelte';
  import { APP_STORES_KEY } from '$lib/stores/context';

  const { currentDataset, queryContext } = getContext(APP_STORES_KEY);
  const results = await executeQuery(q, $currentDataset, $queryContext);
</script>

<!-- Root component wraps with provider -->
<StoreProvider>
  <QueryWorkbench />
</StoreProvider>
```

---

## Recommended Migration Path for Frontend

### Phase 1: Maintain Backward Compatibility (Immediate)

Keep existing global singleton stores in CHUCC Frontend for now:
```typescript
// src/lib/stores/app.ts - No changes required
export const currentDataset = writable<string>('default');
export const queryContext = writable<QueryContext>({ type: 'branch', branch: 'main' });
```

**Rationale**: CHUCC-SQUI components use accessor functions with fallback, so frontend continues working.

### Phase 2: Add Context Support (Optional)

Add factory functions and StoreProvider to frontend when ready:

```svelte
<!-- src/routes/+layout.svelte -->
<script>
  import { AppStoreProvider } from '$lib/stores/provider';
</script>

<AppStoreProvider>
  <slot />
</AppStoreProvider>
```

```typescript
// src/lib/stores/provider.ts
import { setContext } from 'svelte';
import { createAppStores, APP_STORES_KEY } from './app';

export { default as AppStoreProvider } from './AppStoreProvider.svelte';
```

**Benefits**:
- Isolated state for multiple tabs
- Better testing isolation
- Follows CHUCC-SQUI patterns

### Phase 3: Migrate Components (Future)

Update components to use context accessors:
```typescript
// Before
import { queryContext } from '$lib/stores/app';

// After
import { getQueryContext } from '$lib/stores/context';
const queryContext = getQueryContext();
```

**Timeline**: Can be done incrementally as CHUCC-SQUI components mature.

---

## Compatibility Matrix

| Scenario | CHUCC-SQUI Pattern | Frontend Pattern | Works? |
|----------|-------------------|------------------|--------|
| Old SQUI + Old Frontend | Global singletons | Global singletons | ✅ Yes |
| New SQUI + Old Frontend | Context with fallback | Global singletons | ✅ Yes (fallback) |
| New SQUI + New Frontend | Context-based | Context-based | ✅ Yes (isolated) |
| Old SQUI + New Frontend | Global singletons | Context-based | ⚠️ Mixed (works but not ideal) |

**Conclusion**: Migration is backward compatible. Frontend can adopt context pattern at its own pace.

---

## Testing Implications

### Storybook Stories

**Before** (state leakage):
```typescript
// Story 1 affects Story 2
export const Story1 = {
  play: async () => {
    queryStore.set('SELECT * { ?s ?p ?o }'); // Affects all stories!
  }
};
```

**After** (isolated):
```typescript
// Each story gets fresh instances
export const Story1 = {
  render: () => ({
    Component: QueryWorkbench,
    decorators: [
      (story) => ({
        Component: StoreProvider,
        props: { initialQuery: 'SELECT * { ?s ?p ?o }' },
        slot: story
      })
    ]
  })
};
```

### Integration Tests

**Before**:
```typescript
test('query execution', async () => {
  queryStore.set('SELECT * { ?s ?p ?o }'); // Global state
  // Test may fail if another test modified queryStore
});
```

**After**:
```typescript
test('query execution', async () => {
  render(QueryWorkbench, {
    context: new Map([
      [QUERY_STORE_KEY, createQueryStore()]
    ])
  });
  // Fresh store per test
});
```

---

## Action Items

### Immediate (Before UI Implementation)

- [ ] Update `SQUI/12-frontend-integration.md` with context-based patterns
- [ ] Add backward compatibility notes to integration guide
- [ ] Document migration path for frontend developers
- [ ] Add StoreProvider usage examples
- [ ] Update state management section with factory functions

### During UI Implementation

- [ ] Decide: Use global singletons (Phase 1) or context-based (Phase 2)?
- [ ] If Phase 2: Implement AppStoreProvider in frontend
- [ ] If Phase 2: Update all store imports to use accessor functions
- [ ] Add tests validating store isolation

### Future Enhancements

- [ ] Consider context-based routing state (URL params)
- [ ] Evaluate SSR implications (context vs global stores)
- [ ] Performance testing (context overhead vs global access)

---

## Key Takeaways

1. **Migration is backward compatible** - existing code continues working
2. **Recommended pattern changed** - context-based is now preferred
3. **Frontend can migrate incrementally** - no big-bang rewrite needed
4. **Documentation update required** - integration guide needs refresh
5. **Testing improves** - better isolation in Storybook and tests

---

## References

- **CHUCC-SQUI Migration Commit**: https://github.com/arne-bdt/CHUCC-SQUI/commit/53689c8c4b1db43b25032b383cc0037324b6bfae
- **Svelte Context API**: https://svelte.dev/docs/svelte#setcontext
- **Svelte Stores**: https://svelte.dev/docs/svelte-store
- **Related Files**:
  - `SQUI/12-frontend-integration.md` (needs update)
  - `SQUI/01-setup.md` (review recommended)
