# Task 08: Documentation & Examples

**Phase**: Testing & Documentation (Week 3-4)
**Estimated Time**: 4-6 hours
**Prerequisites**: [07-tests.md](./07-tests.md) completed
**Depends On**: All components tested

---

## Objective

Create comprehensive documentation with API references, usage examples, and Storybook stories.

---

## Documentation Files

### 1. API Documentation

#### docs/version-control/api/QueryContextSelector.md

```markdown
# QueryContextSelector

Full-featured modal component for selecting query contexts (branch, commit, or asOf).

## Props

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `currentContext` | `QueryContext` | Required | Current context (controlled) |
| `branches` | `Branch[]` | `[]` | Available branches |
| `readonly` | `boolean` | `false` | Read-only mode |
| `showFullDetails` | `boolean` | `false` | Show full context details |
| `loading` | `boolean` | `false` | Show loading skeleton |
| `disabled` | `boolean` | `false` | Disable component |
| `size` | `'sm' \| 'md' \| 'lg'` | `'md'` | Button size |
| `class` | `string` | `''` | Custom CSS class |

## Events

| Event | Detail | Description |
|-------|--------|-------------|
| `contextChange` | `{ context: QueryContext, previous: QueryContext }` | Emitted when user selects new context |
| `loadBranches` | `{ dataset?: string }` | Emitted when modal opens (load branches) |
| `searchCommits` | `{ query: string, dataset?: string }` | Emitted when searching commits |

## Slots

| Slot | Description |
|------|-------------|
| `help-text` | Optional help text above selector |
| `actions` | Optional additional actions |

## Example

```svelte
<script>
  import { QueryContextSelector } from 'chucc-squi/version-control';

  let context = { type: 'branch', branch: 'main' };
  let branches = [];

  async function loadBranches() {
    branches = await fetchBranches();
  }
</script>

<QueryContextSelector
  {context}
  {branches}
  on:contextChange={(e) => context = e.detail.context}
  on:loadBranches={loadBranches}
/>
```
```

Create similar docs for **QueryContextIndicator** and **QueryContextBreadcrumb**.

---

### 2. Usage Examples

#### docs/version-control/examples/basic-usage.md

```markdown
# Basic Usage

## Simple Context Selector

```svelte
<script>
  import { QueryContextSelector } from 'chucc-squi/version-control';

  let context = { type: 'branch', branch: 'main' };
</script>

<QueryContextSelector
  {context}
  on:contextChange={(e) => {
    context = e.detail.context;
    console.log('New context:', context);
  }}
/>
```

## With Branch Loading

```svelte
<script>
  import { QueryContextSelector } from 'chucc-squi/version-control';

  let context = { type: 'branch', branch: 'main' };
  let branches = [];
  let loading = false;

  async function handleLoadBranches() {
    loading = true;
    try {
      const response = await fetch('/api/branches');
      branches = await response.json();
    } finally {
      loading = false;
    }
  }
</script>

<QueryContextSelector
  {context}
  {branches}
  {loading}
  on:contextChange={(e) => context = e.detail.context}
  on:loadBranches={handleLoadBranches}
/>
```

## Using Context Indicator

```svelte
<script>
  import { QueryContextIndicator } from 'chucc-squi/version-control';

  let context = { type: 'branch', branch: 'main' };
</script>

<div class="results-header">
  ℹ️ Queried at: <QueryContextIndicator {context} showFullDetails />
</div>
```
```

#### docs/version-control/examples/with-state-management.md

Example with Svelte stores for state management.

#### docs/version-control/examples/advanced-callbacks.md

Example with async commit search and validation.

---

### 3. Storybook Stories

#### src/lib/version-control/QueryContextSelector.stories.ts

```typescript
import type { Meta, StoryObj } from '@storybook/svelte';
import QueryContextSelector from './QueryContextSelector.svelte';
import { mockBranches } from '../../tests/version-control/__fixtures__/contexts';

const meta = {
  title: 'Version Control/QueryContextSelector',
  component: QueryContextSelector,
  tags: ['autodocs'],
  argTypes: {
    currentContext: { control: 'object' },
    branches: { control: 'object' },
    readonly: { control: 'boolean' },
    showFullDetails: { control: 'boolean' },
    loading: { control: 'boolean' },
    disabled: { control: 'boolean' },
    size: { control: 'select', options: ['sm', 'md', 'lg'] }
  }
} satisfies Meta<QueryContextSelector>;

export default meta;
type Story = StoryObj<typeof meta>;

export const BranchContext: Story = {
  args: {
    currentContext: { type: 'branch', branch: 'main' },
    branches: mockBranches
  }
};

export const CommitContext: Story = {
  args: {
    currentContext: {
      type: 'commit',
      commit: '019abcde-1234-7890-abcd-ef0123456789'
    },
    branches: mockBranches
  }
};

export const AsOfContext: Story = {
  args: {
    currentContext: {
      type: 'asOf',
      asOf: '2025-11-10T10:30:00Z'
    },
    branches: mockBranches
  }
};

export const Loading: Story = {
  args: {
    currentContext: { type: 'branch', branch: 'main' },
    branches: [],
    loading: true
  }
};

export const Readonly: Story = {
  args: {
    currentContext: { type: 'branch', branch: 'main' },
    branches: mockBranches,
    readonly: true
  }
};

export const Disabled: Story = {
  args: {
    currentContext: { type: 'branch', branch: 'main' },
    branches: mockBranches,
    disabled: true
  }
};

export const SmallSize: Story = {
  args: {
    currentContext: { type: 'branch', branch: 'main' },
    branches: mockBranches,
    size: 'sm'
  }
};

export const FullDetails: Story = {
  args: {
    currentContext: { type: 'branch', branch: 'main' },
    branches: mockBranches,
    showFullDetails: true
  }
};
```

Create similar stories for **QueryContextIndicator** and **QueryContextBreadcrumb**.

---

### 4. Update Main README

Add section to `docs/version-control/README.md`:

```markdown
# Version Control Components

Optional extension for CHUCC-SQUI providing components for systems implementing the SPARQL 1.2 Protocol Version Control Extension.

## Installation

```bash
npm install chucc-squi
```

## Quick Start

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

- [QueryContextSelector](./api/QueryContextSelector.md) - Full modal-based selector
- [QueryContextIndicator](./api/QueryContextIndicator.md) - Compact read-only display
- [QueryContextBreadcrumb](./api/QueryContextBreadcrumb.md) - Breadcrumb integration

## Utilities

- [buildQueryUrl()](./api/utilities.md#buildqueryurl) - Construct SPARQL query URLs
- [formatContext()](./api/utilities.md#formatcontext) - Format context as string
- [validateContext()](./api/utilities.md#validatecontext) - Validate context objects
- [parseContextFromUrl()](./api/utilities.md#parsecontextfromurl) - Parse context from URL

## Examples

See [examples/](./examples/) directory for complete usage examples.

## TypeScript Support

Full TypeScript support with exported types:

```typescript
import type { QueryContext, BranchContext, CommitContext, AsOfContext } from 'chucc-squi/version-control';
```

## License

MIT
```

---

## Acceptance Criteria

- ✅ API documentation complete for all components
- ✅ At least 3 usage examples documented
- ✅ Storybook stories created for all components
- ✅ Main README updated
- ✅ Utilities documented with JSDoc
- ✅ All examples tested and working

---

## Next Steps

→ Proceed to [09-publish.md](./09-publish.md) for package publishing

---

**Created**: 2025-11-10
**Status**: Not Started
