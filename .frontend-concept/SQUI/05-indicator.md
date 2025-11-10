# Task 05: QueryContextIndicator Component

**Phase**: Components (Week 2-3)
**Estimated Time**: 2-3 hours
**Prerequisites**: [04-main-component.md](./04-main-component.md) completed
**Depends On**: Main component implemented

---

## Objective

Implement a compact, read-only context indicator component for displaying query context inline.

---

## Component

### File: `src/lib/version-control/QueryContextIndicator.svelte`

```svelte
<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import type { QueryContext } from './types';
  import { formatContext } from './utils';

  // Props
  export let context: QueryContext;
  export let showFullDetails = false;
  export let clickable = false;
  export let icon: string | undefined = undefined;
  export let { class: className = '' } = $props();

  const dispatch = createEventDispatcher();

  function handleClick() {
    if (clickable) {
      dispatch('click', { context });
    }
  }

  // Computed formatted text
  $derived formattedText = formatContext(context, {
    short: !showFullDetails,
    includeIcon: icon === undefined ? true : false
  });

  // Custom icon if provided
  $derived displayText = icon !== undefined ? `${icon} ${formattedText}` : formattedText;
</script>

{#if clickable}
  <button
    type="button"
    class="context-indicator clickable {className}"
    on:click={handleClick}
    aria-label="Query context: {formattedText}"
  >
    {displayText}
  </button>
{:else}
  <span class="context-indicator {className}" aria-label="Query context: {formattedText}">
    {displayText}
  </span>
{/if}

<style>
  .context-indicator {
    display: inline-flex;
    align-items: center;
    padding: 0.25rem 0.5rem;
    background: var(--cds-layer-01);
    border: 1px solid var(--cds-border-subtle-01);
    border-radius: 4px;
    font-size: 0.875rem;
    color: var(--cds-text-01);
    white-space: nowrap;
  }

  .context-indicator.clickable {
    cursor: pointer;
    transition: background 0.2s ease;
  }

  .context-indicator.clickable:hover {
    background: var(--cds-layer-hover-01);
  }

  .context-indicator.clickable:focus {
    outline: 2px solid var(--cds-focus);
    outline-offset: 2px;
  }
</style>
```

---

## Usage Examples

### In Query Results Header

```svelte
<script>
  import { QueryContextIndicator } from 'chucc-squi/version-control';
</script>

<div class="results-header">
  ℹ️ Queried at:
  <QueryContextIndicator
    context={{ type: 'branch', branch: 'main' }}
    showFullDetails
  />
</div>
```

### Clickable to Open Context Selector

```svelte
<QueryContextIndicator
  {context}
  clickable
  on:click={() => openContextSelector()}
/>
```

---

## Acceptance Criteria

- ✅ Renders context as compact inline element
- ✅ Supports both clickable and non-clickable modes
- ✅ Shows full or abbreviated details based on prop
- ✅ Custom icon support
- ✅ Accessible (ARIA labels, keyboard focus)
- ✅ Themeable with Carbon CSS variables

---

## Next Steps

→ Proceed to [06-breadcrumb.md](./06-breadcrumb.md) to implement QueryContextBreadcrumb

---

**Created**: 2025-11-10
**Status**: Not Started
