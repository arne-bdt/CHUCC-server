# Task 07: Comprehensive Testing

**Phase**: Testing & Documentation (Week 3-4)
**Estimated Time**: 6-8 hours
**Prerequisites**: [06-breadcrumb.md](./06-breadcrumb.md) completed
**Depends On**: All components implemented

---

## Objective

Achieve 80%+ test coverage with unit tests (Vitest) and component tests (Playwright).

---

## Test Strategy

### 1. Unit Tests (Already Covered)

- ✅ `tests/version-control/types.test.ts` (Task 02)
- ✅ `tests/version-control/utils.test.ts` (Task 03)

### 2. Component Tests (Playwright)

#### QueryContextSelector Tests

Create `tests/version-control/QueryContextSelector.test.ts`:

```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent, waitFor } from '@testing-library/svelte';
import QueryContextSelector from '$lib/version-control/QueryContextSelector.svelte';
import { mockBranches } from './__fixtures__/contexts';

describe('QueryContextSelector', () => {
  it('renders with branch context', () => {
    const { getByText } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: mockBranches
      }
    });

    expect(getByText(/Branch: main/)).toBeInTheDocument();
  });

  it('opens modal when button clicked', async () => {
    const { getByRole, getByText } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: mockBranches
      }
    });

    const button = getByRole('button');
    await fireEvent.click(button);

    await waitFor(() => {
      expect(getByText('Select Query Context')).toBeInTheDocument();
    });
  });

  it('emits contextChange event when context applied', async () => {
    const handleContextChange = vi.fn();
    const { component, getByRole, getByLabelText } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: mockBranches
      }
    });

    component.$on('contextChange', handleContextChange);

    // Open modal
    await fireEvent.click(getByRole('button'));

    // Select commit context type
    const commitRadio = getByLabelText(/Specific Commit/);
    await fireEvent.click(commitRadio);

    // Enter commit ID
    const commitInput = getByLabelText(/Commit ID/);
    await fireEvent.input(commitInput, {
      target: { value: '019abcde-1234-7890-abcd-ef0123456789' }
    });

    // Apply
    const applyButton = getByText('Apply Context');
    await fireEvent.click(applyButton);

    expect(handleContextChange).toHaveBeenCalledWith(
      expect.objectContaining({
        detail: expect.objectContaining({
          context: { type: 'commit', commit: '019abcde-1234-7890-abcd-ef0123456789' }
        })
      })
    );
  });

  it('shows validation error for invalid commit ID', async () => {
    const { getByRole, getByLabelText, getByText } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: mockBranches
      }
    });

    await fireEvent.click(getByRole('button'));

    const commitRadio = getByLabelText(/Specific Commit/);
    await fireEvent.click(commitRadio);

    const commitInput = getByLabelText(/Commit ID/);
    await fireEvent.input(commitInput, { target: { value: 'invalid' } });

    const applyButton = getByText('Apply Context');
    await fireEvent.click(applyButton);

    await waitFor(() => {
      expect(getByText(/Invalid commit ID format/)).toBeInTheDocument();
    });
  });

  it('disables button when readonly', () => {
    const { getByRole } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: mockBranches,
        readonly: true
      }
    });

    expect(getByRole('button')).toBeDisabled();
  });

  it('shows skeleton when loading', () => {
    const { container } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: mockBranches,
        loading: true
      }
    });

    expect(container.querySelector('.bx--skeleton')).toBeInTheDocument();
  });

  it('emits loadBranches event when modal opens', async () => {
    const handleLoadBranches = vi.fn();
    const { component, getByRole } = render(QueryContextSelector, {
      props: {
        currentContext: { type: 'branch', branch: 'main' },
        branches: []
      }
    });

    component.$on('loadBranches', handleLoadBranches);

    await fireEvent.click(getByRole('button'));

    expect(handleLoadBranches).toHaveBeenCalled();
  });
});
```

#### QueryContextIndicator Tests

Create `tests/version-control/QueryContextIndicator.test.ts`:

```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/svelte';
import QueryContextIndicator from '$lib/version-control/QueryContextIndicator.svelte';
import { mockBranchContext } from './__fixtures__/contexts';

describe('QueryContextIndicator', () => {
  it('renders context as non-clickable span', () => {
    const { getByText } = render(QueryContextIndicator, {
      props: { context: mockBranchContext }
    });

    expect(getByText(/Branch: main/)).toBeInTheDocument();
  });

  it('renders context with full details', () => {
    const { getByText } = render(QueryContextIndicator, {
      props: {
        context: mockBranchContext,
        showFullDetails: true
      }
    });

    expect(getByText(/Branch: main \(latest\)/)).toBeInTheDocument();
  });

  it('renders as clickable button when clickable=true', () => {
    const { getByRole } = render(QueryContextIndicator, {
      props: {
        context: mockBranchContext,
        clickable: true
      }
    });

    expect(getByRole('button')).toBeInTheDocument();
  });

  it('emits click event when clicked', async () => {
    const handleClick = vi.fn();
    const { component, getByRole } = render(QueryContextIndicator, {
      props: {
        context: mockBranchContext,
        clickable: true
      }
    });

    component.$on('click', handleClick);

    await fireEvent.click(getByRole('button'));

    expect(handleClick).toHaveBeenCalledWith(
      expect.objectContaining({
        detail: { context: mockBranchContext }
      })
    );
  });

  it('shows custom icon when provided', () => {
    const { getByText } = render(QueryContextIndicator, {
      props: {
        context: mockBranchContext,
        icon: '🔖'
      }
    });

    expect(getByText(/🔖/)).toBeInTheDocument();
  });
});
```

#### QueryContextBreadcrumb Tests

Create `tests/version-control/QueryContextBreadcrumb.test.ts`:

```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/svelte';
import QueryContextBreadcrumb from '$lib/version-control/QueryContextBreadcrumb.svelte';
import { mockBranchContext } from './__fixtures__/contexts';

describe('QueryContextBreadcrumb', () => {
  it('renders context in breadcrumb item', () => {
    const { getByText } = render(QueryContextBreadcrumb, {
      props: { context: mockBranchContext }
    });

    expect(getByText(/Branch: main/)).toBeInTheDocument();
  });

  it('renders as current page when not clickable', () => {
    const { container } = render(QueryContextBreadcrumb, {
      props: {
        context: mockBranchContext,
        clickable: false
      }
    });

    const item = container.querySelector('.bx--breadcrumb-item--current');
    expect(item).toBeInTheDocument();
  });

  it('emits click event when clickable', async () => {
    const handleClick = vi.fn();
    const { component, getByRole } = render(QueryContextBreadcrumb, {
      props: {
        context: mockBranchContext,
        clickable: true
      }
    });

    component.$on('click', handleClick);

    const link = getByRole('link');
    await fireEvent.click(link);

    expect(handleClick).toHaveBeenCalledWith(
      expect.objectContaining({
        detail: { context: mockBranchContext }
      })
    );
  });
});
```

---

### 3. Accessibility Tests

Add to component tests:

```typescript
import { axe, toHaveNoViolations } from 'jest-axe';
expect.extend(toHaveNoViolations);

it('has no accessibility violations', async () => {
  const { container } = render(QueryContextSelector, {
    props: {
      currentContext: mockBranchContext,
      branches: mockBranches
    }
  });

  const results = await axe(container);
  expect(results).toHaveNoViolations();
});
```

---

### 4. Coverage Report

Run and verify coverage:

```bash
npm run test:coverage

# Check coverage thresholds
# Expected: ≥80% lines, functions, branches, statements
```

---

## Acceptance Criteria

- ✅ All component tests pass
- ✅ Unit test coverage ≥80%
- ✅ Component test coverage ≥70%
- ✅ Accessibility tests pass (no axe violations)
- ✅ Tests run in CI/CD pipeline
- ✅ No flaky tests

---

## Next Steps

→ Proceed to [08-docs.md](./08-docs.md) for documentation

---

**Created**: 2025-11-10
**Status**: Not Started
