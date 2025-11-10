# Task 06: QueryContextBreadcrumb Component

**Phase**: Components (Week 2-3)
**Estimated Time**: 2 hours
**Prerequisites**: [05-indicator.md](./05-indicator.md) completed
**Depends On**: Indicator component implemented

---

## Objective

Implement a breadcrumb item component for displaying query context in navigation breadcrumbs.

---

## Component

### File: `src/lib/version-control/QueryContextBreadcrumb.svelte`

```svelte
<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { BreadcrumbItem } from 'carbon-components-svelte';
  import type { QueryContext } from './types';
  import { formatContext } from './utils';

  // Props
  export let context: QueryContext;
  export let clickable = false;
  export let { class: className = '' } = $props();

  const dispatch = createEventDispatcher();

  function handleClick(event: MouseEvent) {
    if (clickable) {
      event.preventDefault();
      dispatch('click', { context });
    }
  }

  $derived formattedText = formatContext(context, { short: true, includeIcon: true });
</script>

<BreadcrumbItem
  href={clickable ? '#' : undefined}
  isCurrentPage={!clickable}
  class={className}
  on:click={handleClick}
>
  {formattedText}
</BreadcrumbItem>
```

---

## Usage Example

```svelte
<script>
  import { Breadcrumb, BreadcrumbItem } from 'carbon-components-svelte';
  import { QueryContextBreadcrumb } from 'chucc-squi/version-control';

  let context = { type: 'branch', branch: 'main' };
</script>

<Breadcrumb>
  <BreadcrumbItem href="/">Home</BreadcrumbItem>
  <BreadcrumbItem href="/query">Query Workbench</BreadcrumbItem>
  <QueryContextBreadcrumb {context} />
</Breadcrumb>
<!-- Renders: Home > Query Workbench > 🌿 Branch: main -->
```

---

## Acceptance Criteria

- ✅ Integrates with Carbon Breadcrumb component
- ✅ Shows short format with icon
- ✅ Supports clickable navigation
- ✅ Marked as current page when not clickable
- ✅ Emits click event with context

---

## Next Steps

→ Proceed to [07-tests.md](./07-tests.md) for comprehensive testing

---

**Created**: 2025-11-10
**Status**: Not Started
