# Query Context Selector - CHUCC-SQUI Component Specification

**Component Name**: `QueryContextSelector`
**Package**: `chucc-squi` (optional extension for SPARQL 1.2 Protocol Version Control Extension)
**Version**: 1.0.0
**Status**: Specification Draft

---

## Overview

The Query Context Selector is a reusable Svelte 5 component that provides a unified interface for selecting query execution contexts in systems implementing the [SPARQL 1.2 Protocol Version Control Extension](https://github.com/arne-bdt/CHUCC-server/blob/main/docs/protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md).

### Use Cases

- **Query Workbench**: Select context before executing SPARQL queries
- **Graph Explorer**: Browse graphs at different points in history
- **Time Travel**: Quick context switching for historical queries
- **Batch Operations**: Specify target context for operations

### Design Principles

1. **Stateless**: Component manages UI state only; parent manages application state
2. **Protocol-Agnostic**: Works with any backend implementing the VC extension
3. **Callback-Driven**: Parent provides data via callbacks (async loading)
4. **Carbon-Based**: Uses IBM Carbon Design System components
5. **Accessible**: WCAG 2.1 AA compliant (keyboard navigation, screen readers)

---

## TypeScript Interfaces

### Core Types

```typescript
/**
 * Union type representing all possible query contexts.
 */
export type QueryContext = BranchContext | CommitContext | AsOfContext;

/**
 * Query the latest commit on a specific branch.
 * Maps to: ?branch={branchName}
 */
export interface BranchContext {
  type: 'branch';
  branch: string;  // e.g., "main", "feature/auth"
}

/**
 * Query a specific immutable commit.
 * Maps to: ?commit={commitId}
 */
export interface CommitContext {
  type: 'commit';
  commit: string;  // UUIDv7 format: "019abc..."
}

/**
 * Query data as it existed at a specific timestamp (time-travel).
 * Maps to: ?asOf={timestamp}
 */
export interface AsOfContext {
  type: 'asOf';
  asOf: string;    // ISO 8601 timestamp: "2025-11-10T10:30:00Z"
}

/**
 * Branch metadata (for dropdown population).
 */
export interface Branch {
  name: string;           // "main", "feature/auth"
  headCommit: string;     // UUIDv7 of latest commit
  protected: boolean;     // Whether branch is protected
  lastUpdated: Date;      // Timestamp of last commit
  description?: string;   // Optional branch description
}

/**
 * Commit metadata (for search results).
 */
export interface Commit {
  id: string;             // UUIDv7: "019abc..."
  shortId: string;        // Abbreviated: "019abc"
  message: string;        // "Add user authentication"
  author: string;         // "Alice <alice@example.org>"
  timestamp: Date;        // Commit creation time
  branch?: string;        // Branch name (if HEAD of branch)
  parents: string[];      // Parent commit IDs
}

/**
 * Validation result for context inputs.
 */
export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
}

export interface ValidationError {
  field: 'branch' | 'commit' | 'asOf';
  message: string;
}
```

---

## Component API

### QueryContextSelector

The main component for selecting query contexts.

#### Props

```typescript
export interface QueryContextSelectorProps {
  /**
   * Current query context (controlled component).
   * Parent must provide and update this value.
   */
  currentContext: QueryContext;

  /**
   * List of available branches (for branch selector dropdown).
   * Can be empty array if not yet loaded.
   * @default []
   */
  branches?: Branch[];

  /**
   * Whether the component is in read-only mode.
   * If true, shows indicator but disables changing context.
   * @default false
   */
  readonly?: boolean;

  /**
   * Whether to show full context details inline (vs. compact mode).
   * Full: "Branch: main (latest commit 019abc...)"
   * Compact: "Branch: main"
   * @default false
   */
  showFullDetails?: boolean;

  /**
   * Custom CSS class for styling.
   */
  class?: string;

  /**
   * Size variant (from Carbon Design System).
   * @default 'md'
   */
  size?: 'sm' | 'md' | 'lg';

  /**
   * Whether to disable the component.
   * @default false
   */
  disabled?: boolean;

  /**
   * Loading state (shows skeleton while fetching data).
   * @default false
   */
  loading?: boolean;
}
```

#### Events

```typescript
/**
 * Emitted when user selects a new context.
 * Parent should update currentContext prop.
 */
export interface ContextChangeEvent {
  detail: {
    context: QueryContext;
    previous: QueryContext;
  };
}

/**
 * Emitted when user requests to load branches (for lazy loading).
 * Parent should fetch branches and update branches prop.
 */
export interface LoadBranchesEvent {
  detail: {
    dataset?: string;  // Optional dataset context
  };
}

/**
 * Emitted when user searches for commits (in commit selector).
 * Parent should fetch matching commits and return via callback.
 */
export interface SearchCommitsEvent {
  detail: {
    query: string;     // User's search query
    dataset?: string;  // Optional dataset context
  };
}
```

#### Slots

```typescript
/**
 * Optional slot for custom help text above context selector.
 */
slot name="help-text";

/**
 * Optional slot for additional actions (e.g., "View in Version Control").
 */
slot name="actions";
```

#### Usage Example

```svelte
<script lang="ts">
  import { QueryContextSelector } from 'chucc-squi/version-control';
  import type { QueryContext, Branch, Commit } from 'chucc-squi/version-control';

  let context: QueryContext = { type: 'branch', branch: 'main' };
  let branches: Branch[] = [];
  let loading = false;

  async function handleContextChange(event: CustomEvent<ContextChangeEvent>) {
    const { context: newContext } = event.detail;
    context = newContext;

    // Optionally: Execute query with new context
    await executeQuery(context);
  }

  async function handleLoadBranches() {
    loading = true;
    branches = await fetchBranches('my-dataset');
    loading = false;
  }

  async function handleSearchCommits(event: CustomEvent<SearchCommitsEvent>) {
    const { query } = event.detail;
    return await searchCommits('my-dataset', query);
  }
</script>

<QueryContextSelector
  {context}
  {branches}
  {loading}
  on:contextChange={handleContextChange}
  on:loadBranches={handleLoadBranches}
  on:searchCommits={handleSearchCommits}
>
  <div slot="help-text">
    Select a context to query historical data or specific commits.
  </div>
</QueryContextSelector>
```

---

## Sub-Components

### QueryContextIndicator

A compact, inline display of the current context (read-only).

#### Props

```typescript
export interface QueryContextIndicatorProps {
  context: QueryContext;

  /**
   * Whether to show full details or compact version.
   * @default false
   */
  showFullDetails?: boolean;

  /**
   * Whether to show as a clickable link (emits click event).
   * @default false
   */
  clickable?: boolean;

  /**
   * Icon to show before text.
   * @default varies by context type (🌿 for branch, 📌 for commit, 🕐 for asOf)
   */
  icon?: string;

  class?: string;
}
```

#### Usage Example

```svelte
<script>
  import { QueryContextIndicator } from 'chucc-squi/version-control';
</script>

<!-- In query results header -->
<div class="results-header">
  ℹ️ Queried at:
  <QueryContextIndicator
    context={{ type: 'branch', branch: 'main' }}
    showFullDetails
  />
</div>
<!-- Renders: "ℹ️ Queried at: 🌿 Branch: main (latest)" -->
```

---

### QueryContextBreadcrumb

Shows context as part of a page breadcrumb trail.

#### Props

```typescript
export interface QueryContextBreadcrumbProps {
  context: QueryContext;

  /**
   * Whether the breadcrumb item is clickable.
   * @default false
   */
  clickable?: boolean;

  class?: string;
}
```

#### Usage Example

```svelte
<script>
  import { Breadcrumb, BreadcrumbItem } from 'carbon-components-svelte';
  import { QueryContextBreadcrumb } from 'chucc-squi/version-control';
</script>

<Breadcrumb>
  <BreadcrumbItem href="/query">Query Workbench</BreadcrumbItem>
  <QueryContextBreadcrumb
    context={{ type: 'commit', commit: '019abc...' }}
  />
</Breadcrumb>
<!-- Renders: "Query Workbench > Commit: 019abc..." -->
```

---

## Helper Utilities

### buildQueryUrl

Constructs a query URL with context parameters.

```typescript
/**
 * Builds a SPARQL query URL with context parameters.
 *
 * @param basePath - Base path (e.g., "/query")
 * @param options - Query options
 * @returns Full URL with query parameters
 *
 * @example
 * buildQueryUrl('/query', {
 *   dataset: 'default',
 *   context: { type: 'branch', branch: 'main' }
 * });
 * // => "/query?dataset=default&branch=main"
 */
export function buildQueryUrl(
  basePath: string,
  options: {
    dataset: string;
    context: QueryContext;
    additionalParams?: Record<string, string>;
  }
): string;
```

#### Implementation

```typescript
export function buildQueryUrl(
  basePath: string,
  options: {
    dataset: string;
    context: QueryContext;
    additionalParams?: Record<string, string>;
  }
): string {
  const params = new URLSearchParams();
  params.set('dataset', options.dataset);

  // Add context parameter based on type
  switch (options.context.type) {
    case 'branch':
      params.set('branch', options.context.branch);
      break;
    case 'commit':
      params.set('commit', options.context.commit);
      break;
    case 'asOf':
      params.set('asOf', options.context.asOf);
      break;
  }

  // Add additional parameters
  if (options.additionalParams) {
    for (const [key, value] of Object.entries(options.additionalParams)) {
      params.set(key, value);
    }
  }

  return `${basePath}?${params.toString()}`;
}
```

---

### formatContext

Formats a context object as a human-readable string.

```typescript
/**
 * Formats a query context as a human-readable string.
 *
 * @param context - Query context to format
 * @param options - Formatting options
 * @returns Formatted string
 *
 * @example
 * formatContext({ type: 'branch', branch: 'main' });
 * // => "Branch: main (latest)"
 *
 * formatContext({ type: 'commit', commit: '019abc...' }, { short: true });
 * // => "Commit: 019abc"
 */
export function formatContext(
  context: QueryContext,
  options?: {
    short?: boolean;      // Use abbreviated format
    includeIcon?: boolean; // Include emoji icon
  }
): string;
```

#### Implementation

```typescript
export function formatContext(
  context: QueryContext,
  options: {
    short?: boolean;
    includeIcon?: boolean;
  } = {}
): string {
  const { short = false, includeIcon = false } = options;
  const icon = includeIcon ? getContextIcon(context) : '';

  switch (context.type) {
    case 'branch':
      const suffix = short ? '' : ' (latest)';
      return `${icon}Branch: ${context.branch}${suffix}`.trim();

    case 'commit':
      const commitId = short ? context.commit.slice(0, 7) : context.commit;
      return `${icon}Commit: ${commitId}`.trim();

    case 'asOf':
      const timestamp = short
        ? new Date(context.asOf).toLocaleDateString()
        : context.asOf;
      return `${icon}asOf: ${timestamp}`.trim();

    default:
      return 'Unknown context';
  }
}

function getContextIcon(context: QueryContext): string {
  switch (context.type) {
    case 'branch': return '🌿 ';
    case 'commit': return '📌 ';
    case 'asOf': return '🕐 ';
    default: return '';
  }
}
```

---

### validateContext

Validates a query context object.

```typescript
/**
 * Validates a query context for correctness.
 *
 * @param context - Query context to validate
 * @returns Validation result with errors (if any)
 *
 * @example
 * validateContext({ type: 'commit', commit: 'invalid' });
 * // => { valid: false, errors: [{ field: 'commit', message: 'Invalid commit ID format' }] }
 */
export function validateContext(context: QueryContext): ValidationResult;
```

#### Implementation

```typescript
export function validateContext(context: QueryContext): ValidationResult {
  const errors: ValidationError[] = [];

  switch (context.type) {
    case 'branch':
      if (!context.branch || context.branch.trim() === '') {
        errors.push({
          field: 'branch',
          message: 'Branch name is required'
        });
      }
      if (context.branch && !/^[a-zA-Z0-9/_-]+$/.test(context.branch)) {
        errors.push({
          field: 'branch',
          message: 'Branch name contains invalid characters'
        });
      }
      break;

    case 'commit':
      if (!context.commit || context.commit.trim() === '') {
        errors.push({
          field: 'commit',
          message: 'Commit ID is required'
        });
      }
      // UUIDv7 format: 8-4-4-4-12 hex characters
      if (context.commit && !/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(context.commit)) {
        errors.push({
          field: 'commit',
          message: 'Invalid commit ID format (expected UUIDv7)'
        });
      }
      break;

    case 'asOf':
      if (!context.asOf || context.asOf.trim() === '') {
        errors.push({
          field: 'asOf',
          message: 'Timestamp is required'
        });
      }
      // ISO 8601 format check
      if (context.asOf && isNaN(Date.parse(context.asOf))) {
        errors.push({
          field: 'asOf',
          message: 'Invalid timestamp format (expected ISO 8601)'
        });
      }
      break;
  }

  return {
    valid: errors.length === 0,
    errors
  };
}
```

---

### parseContextFromUrl

Parses a query context from URL parameters.

```typescript
/**
 * Parses a query context from URL search parameters.
 *
 * @param searchParams - URLSearchParams or query string
 * @returns Parsed query context, or null if no context found
 *
 * @example
 * parseContextFromUrl('?dataset=default&branch=main');
 * // => { type: 'branch', branch: 'main' }
 *
 * parseContextFromUrl('?dataset=default&commit=019abc...');
 * // => { type: 'commit', commit: '019abc...' }
 */
export function parseContextFromUrl(
  searchParams: URLSearchParams | string
): QueryContext | null;
```

#### Implementation

```typescript
export function parseContextFromUrl(
  searchParams: URLSearchParams | string
): QueryContext | null {
  const params = typeof searchParams === 'string'
    ? new URLSearchParams(searchParams)
    : searchParams;

  // Priority: commit > asOf > branch
  const commit = params.get('commit');
  if (commit) {
    return { type: 'commit', commit };
  }

  const asOf = params.get('asOf');
  if (asOf) {
    return { type: 'asOf', asOf };
  }

  const branch = params.get('branch');
  if (branch) {
    return { type: 'branch', branch };
  }

  return null;
}
```

---

## Component Implementation Guide

### File Structure

```
chucc-squi/
├── src/
│   └── lib/
│       └── version-control/
│           ├── QueryContextSelector.svelte
│           ├── QueryContextIndicator.svelte
│           ├── QueryContextBreadcrumb.svelte
│           ├── index.ts
│           ├── types.ts
│           └── utils.ts
├── tests/
│   └── version-control/
│       ├── QueryContextSelector.test.ts
│       ├── utils.test.ts
│       └── __fixtures__/
└── docs/
    └── version-control/
        ├── QueryContextSelector.md
        └── examples.md
```

### QueryContextSelector.svelte (Skeleton)

```svelte
<script lang="ts">
  import { Modal, Button, RadioButton, Dropdown } from 'carbon-components-svelte';
  import type { QueryContext, Branch } from './types';
  import { formatContext, validateContext } from './utils';

  // Props
  export let currentContext: QueryContext;
  export let branches: Branch[] = [];
  export let readonly = false;
  export let showFullDetails = false;
  export let loading = false;
  export let disabled = false;
  export let size: 'sm' | 'md' | 'lg' = 'md';

  // Local state
  let isModalOpen = false;
  let pendingContext: QueryContext = { ...currentContext };
  let validationErrors: ValidationError[] = [];

  // Event dispatcher
  import { createEventDispatcher } from 'svelte';
  const dispatch = createEventDispatcher();

  function openModal() {
    if (readonly || disabled) return;
    pendingContext = { ...currentContext };
    isModalOpen = true;
    dispatch('loadBranches', { dataset: undefined });
  }

  function applyContext() {
    const validation = validateContext(pendingContext);
    if (!validation.valid) {
      validationErrors = validation.errors;
      return;
    }

    dispatch('contextChange', {
      context: pendingContext,
      previous: currentContext
    });
    isModalOpen = false;
  }

  async function handleCommitSearch(query: string) {
    const commits = await dispatch('searchCommits', { query });
    return commits;
  }
</script>

<!-- Button to open selector -->
<Button
  kind="ghost"
  {size}
  {disabled}
  skeleton={loading}
  on:click={openModal}
>
  {formatContext(currentContext, { short: !showFullDetails, includeIcon: true })} ▾
</Button>

<!-- Modal for context selection -->
<Modal
  bind:open={isModalOpen}
  modalHeading="Select Query Context"
  primaryButtonText="Apply Context"
  secondaryButtonText="Cancel"
  on:click:button--primary={applyContext}
  on:click:button--secondary={() => isModalOpen = false}
>
  <slot name="help-text" />

  <!-- Radio buttons for context type -->
  <RadioButton
    value="branch"
    bind:group={pendingContext.type}
    labelText="Branch (latest)"
  >
    <p slot="labelText">
      Branch (latest)
      <br />
      <small>Queries the latest commit on the selected branch.</small>
    </p>
  </RadioButton>

  {#if pendingContext.type === 'branch'}
    <Dropdown
      titleText="Select Branch"
      items={branches.map(b => ({ id: b.name, text: b.name }))}
      bind:selectedId={pendingContext.branch}
      disabled={branches.length === 0}
      placeholder={branches.length === 0 ? 'Loading branches...' : 'Select a branch'}
    />
  {/if}

  <RadioButton
    value="commit"
    bind:group={pendingContext.type}
    labelText="Specific Commit"
  >
    <p slot="labelText">
      Specific Commit
      <br />
      <small>Queries a specific immutable commit.</small>
    </p>
  </RadioButton>

  {#if pendingContext.type === 'commit'}
    <!-- Commit search input component (to be implemented) -->
    <CommitSearchInput
      bind:value={pendingContext.commit}
      on:search={handleCommitSearch}
    />
  {/if}

  <RadioButton
    value="asOf"
    bind:group={pendingContext.type}
    labelText="Time Travel (asOf)"
  >
    <p slot="labelText">
      Time Travel (asOf)
      <br />
      <small>Queries data as it existed at a specific time.</small>
    </p>
  </RadioButton>

  {#if pendingContext.type === 'asOf'}
    <DateTimePicker
      bind:value={pendingContext.asOf}
      datePickerType="single"
      dateFormat="Y-m-d"
      timeFormat="H:i:S"
    />
  {/if}

  <!-- Validation errors -->
  {#if validationErrors.length > 0}
    <InlineNotification
      kind="error"
      title="Validation Error"
      subtitle={validationErrors.map(e => e.message).join(', ')}
      lowContrast
    />
  {/if}

  <slot name="actions" />
</Modal>

<style>
  /* Component-specific styles */
</style>
```

---

## Testing Strategy

### Unit Tests

```typescript
// tests/version-control/utils.test.ts
import { describe, it, expect } from 'vitest';
import {
  formatContext,
  validateContext,
  buildQueryUrl,
  parseContextFromUrl
} from '$lib/version-control/utils';

describe('formatContext', () => {
  it('formats branch context with full details', () => {
    const context = { type: 'branch', branch: 'main' };
    expect(formatContext(context)).toBe('Branch: main (latest)');
  });

  it('formats branch context with short format', () => {
    const context = { type: 'branch', branch: 'main' };
    expect(formatContext(context, { short: true })).toBe('Branch: main');
  });

  it('formats commit context with abbreviated ID', () => {
    const context = { type: 'commit', commit: '019abcdef-1234-7890-abcd-ef0123456789' };
    expect(formatContext(context, { short: true })).toBe('Commit: 019abcd');
  });

  it('includes icon when requested', () => {
    const context = { type: 'branch', branch: 'main' };
    expect(formatContext(context, { includeIcon: true })).toBe('🌿 Branch: main (latest)');
  });
});

describe('validateContext', () => {
  it('validates correct branch context', () => {
    const context = { type: 'branch', branch: 'main' };
    const result = validateContext(context);
    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
  });

  it('rejects empty branch name', () => {
    const context = { type: 'branch', branch: '' };
    const result = validateContext(context);
    expect(result.valid).toBe(false);
    expect(result.errors[0].message).toContain('required');
  });

  it('rejects invalid commit ID format', () => {
    const context = { type: 'commit', commit: 'invalid' };
    const result = validateContext(context);
    expect(result.valid).toBe(false);
    expect(result.errors[0].message).toContain('Invalid commit ID format');
  });

  it('validates correct UUIDv7 commit ID', () => {
    const context = { type: 'commit', commit: '019abcde-1234-7890-abcd-ef0123456789' };
    const result = validateContext(context);
    expect(result.valid).toBe(true);
  });

  it('rejects invalid ISO 8601 timestamp', () => {
    const context = { type: 'asOf', asOf: 'not-a-date' };
    const result = validateContext(context);
    expect(result.valid).toBe(false);
    expect(result.errors[0].message).toContain('Invalid timestamp format');
  });
});

describe('buildQueryUrl', () => {
  it('builds URL with branch context', () => {
    const url = buildQueryUrl('/query', {
      dataset: 'default',
      context: { type: 'branch', branch: 'main' }
    });
    expect(url).toBe('/query?dataset=default&branch=main');
  });

  it('builds URL with commit context', () => {
    const url = buildQueryUrl('/query', {
      dataset: 'default',
      context: { type: 'commit', commit: '019abc...' }
    });
    expect(url).toBe('/query?dataset=default&commit=019abc...');
  });

  it('builds URL with asOf context', () => {
    const url = buildQueryUrl('/query', {
      dataset: 'default',
      context: { type: 'asOf', asOf: '2025-11-10T10:30:00Z' }
    });
    expect(url).toBe('/query?dataset=default&asOf=2025-11-10T10%3A30%3A00Z');
  });

  it('includes additional parameters', () => {
    const url = buildQueryUrl('/query', {
      dataset: 'default',
      context: { type: 'branch', branch: 'main' },
      additionalParams: { limit: '100', offset: '0' }
    });
    expect(url).toContain('limit=100');
    expect(url).toContain('offset=0');
  });
});

describe('parseContextFromUrl', () => {
  it('parses branch context from URL', () => {
    const context = parseContextFromUrl('?dataset=default&branch=main');
    expect(context).toEqual({ type: 'branch', branch: 'main' });
  });

  it('parses commit context from URL', () => {
    const context = parseContextFromUrl('?dataset=default&commit=019abc...');
    expect(context).toEqual({ type: 'commit', commit: '019abc...' });
  });

  it('prioritizes commit over branch', () => {
    const context = parseContextFromUrl('?branch=main&commit=019abc...');
    expect(context).toEqual({ type: 'commit', commit: '019abc...' });
  });

  it('returns null when no context parameters', () => {
    const context = parseContextFromUrl('?dataset=default');
    expect(context).toBeNull();
  });
});
```

### Component Tests

```typescript
// tests/version-control/QueryContextSelector.test.ts
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/svelte';
import QueryContextSelector from '$lib/version-control/QueryContextSelector.svelte';

describe('QueryContextSelector', () => {
  it('renders with branch context', () => {
    const { getByText } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: []
      }
    });

    expect(getByText(/Branch: main/)).toBeInTheDocument();
  });

  it('opens modal when button clicked', async () => {
    const { getByRole, getByText } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: []
      }
    });

    await fireEvent.click(getByRole('button'));
    expect(getByText('Select Query Context')).toBeInTheDocument();
  });

  it('emits contextChange event when context applied', async () => {
    const handleContextChange = vi.fn();
    const { component, getByRole } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: [{ name: 'main' }, { name: 'develop' }]
      }
    });

    component.$on('contextChange', handleContextChange);

    // Open modal, change context, apply
    await fireEvent.click(getByRole('button'));
    // ... interact with modal ...
    // await fireEvent.click(getByText('Apply Context'));

    // expect(handleContextChange).toHaveBeenCalledWith(
    //   expect.objectContaining({
    //     detail: { context: { type: 'branch', branch: 'develop' } }
    //   })
    // );
  });

  it('disables button when readonly prop is true', () => {
    const { getByRole } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: [],
        readonly: true
      }
    });

    expect(getByRole('button')).toBeDisabled();
  });

  it('shows loading skeleton when loading prop is true', () => {
    const { container } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: [],
        loading: true
      }
    });

    expect(container.querySelector('.bx--skeleton')).toBeInTheDocument();
  });
});
```

---

## Documentation

### README.md (for version-control package)

```markdown
# Version Control Components

Optional extension for CHUCC-SQUI providing components for systems implementing the SPARQL 1.2 Protocol Version Control Extension.

## Installation

```bash
npm install chucc-squi
```

## Usage

```svelte
<script>
  import { QueryContextSelector } from 'chucc-squi/version-control';

  let context = { type: 'branch', branch: 'main' };
</script>

<QueryContextSelector
  {context}
  on:contextChange={(e) => context = e.detail.context}
/>
```

## Components

- **QueryContextSelector**: Full modal-based context selector
- **QueryContextIndicator**: Compact read-only context display
- **QueryContextBreadcrumb**: Context as breadcrumb item

## Utilities

- `buildQueryUrl()`: Construct SPARQL query URLs
- `formatContext()`: Format context as string
- `validateContext()`: Validate context objects
- `parseContextFromUrl()`: Parse context from URL params

## TypeScript Support

Full TypeScript support with exported types:

```typescript
import type { QueryContext, BranchContext, CommitContext, AsOfContext } from 'chucc-squi/version-control';
```

## Examples

See [examples.md](./examples.md) for complete usage examples.
```

---

## Accessibility Checklist

- [ ] **Keyboard Navigation**: Tab, Enter, Escape work correctly
- [ ] **ARIA Labels**: All interactive elements labeled
- [ ] **Focus Management**: Focus moves logically through modal
- [ ] **Screen Reader**: Announcements for state changes
- [ ] **Color Contrast**: WCAG 2.1 AA compliant (4.5:1 ratio)
- [ ] **Focus Indicators**: Visible focus outlines
- [ ] **Error Messages**: Associated with form fields via `aria-describedby`

---

## Roadmap

### v1.0.0 (Initial Release)
- [x] TypeScript interfaces
- [ ] QueryContextSelector component
- [ ] QueryContextIndicator component
- [ ] QueryContextBreadcrumb component
- [ ] Utility functions
- [ ] Unit tests (80%+ coverage)
- [ ] Component tests (Playwright)
- [ ] Documentation
- [ ] Storybook stories

### v1.1.0 (Enhancements)
- [ ] CommitSearchInput sub-component (with autocomplete)
- [ ] Recent contexts history (localStorage)
- [ ] Keyboard shortcuts (Ctrl+K for quick context switch)
- [ ] Context presets (bookmarks)

### v1.2.0 (Advanced Features)
- [ ] Context comparison view (side-by-side)
- [ ] Context timeline visualization
- [ ] Integration with graph visualization (show commit on timeline)

---

**Document Version**: 1.0.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)
**Status**: Ready for Implementation
