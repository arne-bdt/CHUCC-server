# Task 04: QueryContextSelector Component

**Phase**: Components (Week 2-3)
**Estimated Time**: 8-10 hours
**Prerequisites**: [03-utils.md](./03-utils.md) completed
**Depends On**: Types and utilities implemented

---

## Objective

Implement the main QueryContextSelector component with modal UI, validation, and event handling.

---

## Component Structure

### File: `src/lib/version-control/QueryContextSelector.svelte`

```svelte
<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { Modal, Button, RadioButton, Dropdown, InlineNotification, TextInput } from 'carbon-components-svelte';
  import type { QueryContext, Branch, ValidationError } from './types';
  import { formatContext, validateContext } from './utils';

  // Props
  export let currentContext: QueryContext;
  export let branches: Branch[] = [];
  export let readonly = false;
  export let showFullDetails = false;
  export let loading = false;
  export let disabled = false;
  export let size: 'sm' | 'md' | 'lg' = 'md';
  export let { class: className = '' } = $props();

  // Local state
  let isModalOpen = $state(false);
  let pendingContext = $state<QueryContext>({ ...currentContext });
  let validationErrors = $state<ValidationError[]>([]);

  // Event dispatcher
  const dispatch = createEventDispatcher();

  function openModal() {
    if (readonly || disabled) return;
    pendingContext = { ...currentContext };
    validationErrors = [];
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

  function handleTypeChange() {
    // Reset context-specific fields when type changes
    validationErrors = [];
  }

  // Reactive computed values
  $effect(() => {
    // Sync pending context when prop changes externally
    if (!isModalOpen) {
      pendingContext = { ...currentContext };
    }
  });

  // Branch dropdown items
  $derived branchItems = branches.map(b => ({
    id: b.name,
    text: b.name
  }));
</script>

<!-- Trigger Button -->
<Button
  kind="ghost"
  {size}
  {disabled}
  skeleton={loading}
  class={className}
  on:click={openModal}
>
  {formatContext(currentContext, { short: !showFullDetails, includeIcon: true })} ▾
</Button>

<!-- Context Selection Modal -->
<Modal
  bind:open={isModalOpen}
  modalHeading="Select Query Context"
  primaryButtonText="Apply Context"
  secondaryButtonText="Cancel"
  on:click:button--primary={applyContext}
  on:click:button--secondary={() => isModalOpen = false}
  on:close={() => isModalOpen = false}
>
  <slot name="help-text">
    <p class="help-text">
      Select a context to query data at a specific point in time.
    </p>
  </slot>

  <!-- Branch Context -->
  <RadioButton
    value="branch"
    bind:group={pendingContext.type}
    on:change={handleTypeChange}
  >
    <div slot="labelText">
      <strong>Branch (latest)</strong><br />
      <small>Queries the latest commit on the selected branch.</small>
    </div>
  </RadioButton>

  {#if pendingContext.type === 'branch'}
    <div class="context-input">
      <Dropdown
        titleText="Select Branch"
        items={branchItems}
        bind:selectedId={pendingContext.branch}
        disabled={branches.length === 0}
        placeholder={branches.length === 0 ? 'Loading branches...' : 'Select a branch'}
      />
    </div>
  {/if}

  <!-- Commit Context -->
  <RadioButton
    value="commit"
    bind:group={pendingContext.type}
    on:change={handleTypeChange}
  >
    <div slot="labelText">
      <strong>Specific Commit</strong><br />
      <small>Queries a specific immutable commit.</small>
    </div>
  </RadioButton>

  {#if pendingContext.type === 'commit'}
    <div class="context-input">
      <TextInput
        labelText="Commit ID (UUIDv7)"
        placeholder="019abcde-1234-7890-abcd-ef0123456789"
        bind:value={pendingContext.commit}
        helperText="Enter full UUIDv7 commit identifier"
      />
    </div>
  {/if}

  <!-- AsOf Context -->
  <RadioButton
    value="asOf"
    bind:group={pendingContext.type}
    on:change={handleTypeChange}
  >
    <div slot="labelText">
      <strong>Time Travel (asOf)</strong><br />
      <small>Queries data as it existed at a specific time.</small>
    </div>
  </RadioButton>

  {#if pendingContext.type === 'asOf'}
    <div class="context-input">
      <TextInput
        labelText="Timestamp (ISO 8601)"
        placeholder="2025-11-10T10:30:00Z"
        bind:value={pendingContext.asOf}
        helperText="Enter ISO 8601 timestamp (YYYY-MM-DDTHH:mm:ss.sssZ)"
      />
    </div>
  {/if}

  <!-- Validation Errors -->
  {#if validationErrors.length > 0}
    <InlineNotification
      kind="error"
      title="Validation Error"
      subtitle={validationErrors.map(e => e.message).join(', ')}
      lowContrast
      hideCloseButton
    />
  {/if}

  <slot name="actions" />
</Modal>

<style>
  .help-text {
    margin-bottom: 1rem;
    color: var(--cds-text-02);
  }

  .context-input {
    margin-top: 0.5rem;
    margin-bottom: 1.5rem;
    margin-left: 2rem;
  }
</style>
```

---

## Sub-Components (Optional Enhancement)

### CommitSearchInput.svelte (Future)

For autocomplete commit search functionality. Can be added in v1.1.0.

---

## Acceptance Criteria

- ✅ Component renders with all three context types (branch, commit, asOf)
- ✅ Modal opens/closes correctly
- ✅ Validation errors displayed inline
- ✅ contextChange event emitted with correct payload
- ✅ loadBranches event emitted when modal opens
- ✅ Readonly and disabled states work correctly
- ✅ Loading skeleton displays when loading=true
- ✅ Component works with Svelte 5 runes ($state, $effect, $derived)

---

## Testing

See [07-tests.md](./07-tests.md) for component tests using Playwright.

---

## Next Steps

→ Proceed to [05-indicator.md](./05-indicator.md) to implement QueryContextIndicator

---

**Created**: 2025-11-10
**Status**: Not Started
