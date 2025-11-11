# Task 02: TypeScript Interfaces

**Phase**: Foundation (Week 1)
**Estimated Time**: 2-3 hours
**Prerequisites**: [01-setup.md](./01-setup.md) completed
**Depends On**: Project structure created

---

## Objective

Define all TypeScript interfaces and types for the version-control module, ensuring type safety and protocol compliance.

---

## Tasks

### 1. Create types.ts

Create `src/lib/version-control/types.ts` with all interface definitions.

#### Core Context Types

```typescript
/**
 * Union type representing all possible query contexts.
 * Maps to SPARQL 1.2 Protocol Version Control Extension parameters.
 */
export type QueryContext = BranchContext | CommitContext | AsOfContext;

/**
 * Query the latest commit on a specific branch.
 * Protocol mapping: ?branch={branchName}
 *
 * @example
 * const context: BranchContext = { type: 'branch', branch: 'main' };
 * // URL: /query?dataset=default&branch=main
 */
export interface BranchContext {
  type: 'branch';
  branch: string;  // e.g., "main", "feature/auth"
}

/**
 * Query a specific immutable commit.
 * Protocol mapping: ?commit={commitId}
 *
 * @example
 * const context: CommitContext = {
 *   type: 'commit',
 *   commit: '019abcde-1234-7890-abcd-ef0123456789'
 * };
 * // URL: /query?dataset=default&commit=019abcde-1234-7890-abcd-ef0123456789
 */
export interface CommitContext {
  type: 'commit';
  commit: string;  // UUIDv7 format
}

/**
 * Query data as it existed at a specific timestamp.
 *
 * **Server-Side Resolution**: The backend finds the commit at or
 * before this timestamp. The resolved commit ID is what actually
 * gets queried.
 *
 * **UI Consideration**: Users select times, but they're querying
 * commits. Show resolved commit in UI: "asOf: 10:30 (commit 019abc...)"
 *
 * Protocol mapping: ?asOf={timestamp}
 *
 * @example
 * const context: AsOfContext = {
 *   type: 'asOf',
 *   asOf: '2025-11-10T10:30:00Z'
 * };
 * // URL: /query?dataset=default&asOf=2025-11-10T10:30:00Z
 * // Server resolves to commit at or before 10:30:00Z
 */
export interface AsOfContext {
  type: 'asOf';
  asOf: string;  // ISO 8601 timestamp
}
```

---

#### Metadata Types

```typescript
/**
 * Branch metadata (for dropdown population).
 */
export interface Branch {
  /** Branch name (e.g., "main", "feature/auth") */
  name: string;

  /** UUIDv7 of latest commit on this branch */
  headCommit: string;

  /** Whether branch is protected (cannot be deleted/force-pushed) */
  protected: boolean;

  /** Timestamp of last commit on this branch */
  lastUpdated: Date;

  /** Optional human-readable description */
  description?: string;
}

/**
 * Commit metadata (for search results and display).
 */
export interface Commit {
  /** Full UUIDv7 commit ID */
  id: string;

  /** Abbreviated commit ID (first 7 characters) */
  shortId: string;

  /** Commit message (first line) */
  message: string;

  /** Author in format "Name <email>" */
  author: string;

  /** Commit creation timestamp */
  timestamp: Date;

  /** Branch name if this commit is HEAD of a branch */
  branch?: string;

  /** Array of parent commit IDs (UUIDv7) */
  parents: string[];

  /** Optional: Tags associated with this commit */
  tags?: string[];
}
```

---

#### Validation Types

```typescript
/**
 * Result of context validation.
 */
export interface ValidationResult {
  /** Whether the context is valid */
  valid: boolean;

  /** Array of validation errors (empty if valid) */
  errors: ValidationError[];
}

/**
 * Individual validation error.
 */
export interface ValidationError {
  /** Field that failed validation */
  field: 'branch' | 'commit' | 'asOf';

  /** Human-readable error message */
  message: string;

  /** Optional: Technical error code */
  code?: string;
}
```

---

#### Component Prop Types

```typescript
/**
 * Props for QueryContextSelector component.
 */
export interface QueryContextSelectorProps {
  /** Current query context (controlled component) */
  currentContext: QueryContext;

  /** List of available branches (can be empty if not yet loaded) */
  branches?: Branch[];

  /** Whether component is in read-only mode */
  readonly?: boolean;

  /** Whether to show full context details inline */
  showFullDetails?: boolean;

  /** Custom CSS class for styling */
  class?: string;

  /** Size variant (from Carbon Design System) */
  size?: 'sm' | 'md' | 'lg';

  /** Whether to disable the component */
  disabled?: boolean;

  /** Loading state (shows skeleton) */
  loading?: boolean;
}

/**
 * Props for QueryContextIndicator component.
 */
export interface QueryContextIndicatorProps {
  /** Context to display */
  context: QueryContext;

  /** Whether to show full details or compact version */
  showFullDetails?: boolean;

  /** Whether to show as clickable link */
  clickable?: boolean;

  /** Icon to show before text (default varies by context type) */
  icon?: string;

  /** Custom CSS class */
  class?: string;
}

/**
 * Props for QueryContextBreadcrumb component.
 */
export interface QueryContextBreadcrumbProps {
  /** Context to display in breadcrumb */
  context: QueryContext;

  /** Whether breadcrumb item is clickable */
  clickable?: boolean;

  /** Custom CSS class */
  class?: string;
}
```

---

#### Event Detail Types

```typescript
/**
 * Event detail for contextChange event.
 */
export interface ContextChangeEventDetail {
  /** New context selected by user */
  context: QueryContext;

  /** Previous context (before change) */
  previous: QueryContext;
}

/**
 * Event detail for loadBranches event.
 */
export interface LoadBranchesEventDetail {
  /** Optional dataset context for filtering branches */
  dataset?: string;
}

/**
 * Event detail for searchCommits event.
 */
export interface SearchCommitsEventDetail {
  /** User's search query */
  query: string;

  /** Optional dataset context for filtering commits */
  dataset?: string;
}
```

---

### 2. Create Type Guards

Add type guard utilities to types.ts:

```typescript
/**
 * Type guard to check if context is BranchContext.
 */
export function isBranchContext(context: QueryContext): context is BranchContext {
  return context.type === 'branch';
}

/**
 * Type guard to check if context is CommitContext.
 */
export function isCommitContext(context: QueryContext): context is CommitContext {
  return context.type === 'commit';
}

/**
 * Type guard to check if context is AsOfContext.
 */
export function isAsOfContext(context: QueryContext): context is AsOfContext {
  return context.type === 'asOf';
}
```

---

### 3. Update index.ts

Update `src/lib/version-control/index.ts` to export types:

```typescript
// Types
export type {
  QueryContext,
  BranchContext,
  CommitContext,
  AsOfContext,
  Branch,
  Commit,
  ValidationResult,
  ValidationError,
  QueryContextSelectorProps,
  QueryContextIndicatorProps,
  QueryContextBreadcrumbProps,
  ContextChangeEventDetail,
  LoadBranchesEventDetail,
  SearchCommitsEventDetail
} from './types';

// Type guards
export {
  isBranchContext,
  isCommitContext,
  isAsOfContext
} from './types';

// Components (placeholder exports for now)
// export { default as QueryContextSelector } from './QueryContextSelector.svelte';
// export { default as QueryContextIndicator } from './QueryContextIndicator.svelte';
// export { default as QueryContextBreadcrumb } from './QueryContextBreadcrumb.svelte';

// Utilities (placeholder exports for now)
// export {
//   buildQueryUrl,
//   formatContext,
//   validateContext,
//   parseContextFromUrl
// } from './utils';
```

---

### 4. Create Test Fixtures

Create `tests/version-control/__fixtures__/contexts.ts`:

```typescript
import type { QueryContext, Branch, Commit } from '$lib/version-control/types';

/**
 * Mock branch contexts for testing.
 */
export const mockBranchContext: QueryContext = {
  type: 'branch',
  branch: 'main'
};

export const mockFeatureBranchContext: QueryContext = {
  type: 'branch',
  branch: 'feature/authentication'
};

/**
 * Mock commit contexts for testing.
 */
export const mockCommitContext: QueryContext = {
  type: 'commit',
  commit: '019abcde-1234-7890-abcd-ef0123456789'
};

/**
 * Mock asOf contexts for testing.
 */
export const mockAsOfContext: QueryContext = {
  type: 'asOf',
  asOf: '2025-11-10T10:30:00Z'
};

/**
 * Mock branches for testing.
 */
export const mockBranches: Branch[] = [
  {
    name: 'main',
    headCommit: '019abcde-1234-7890-abcd-ef0123456789',
    protected: true,
    lastUpdated: new Date('2025-11-10T14:00:00Z'),
    description: 'Main production branch'
  },
  {
    name: 'develop',
    headCommit: '019xyz12-5678-7890-abcd-ef0123456789',
    protected: false,
    lastUpdated: new Date('2025-11-10T12:00:00Z'),
    description: 'Development branch'
  },
  {
    name: 'feature/authentication',
    headCommit: '019fgh34-9012-7890-abcd-ef0123456789',
    protected: false,
    lastUpdated: new Date('2025-11-09T18:00:00Z')
  }
];

/**
 * Mock commits for testing.
 */
export const mockCommits: Commit[] = [
  {
    id: '019abcde-1234-7890-abcd-ef0123456789',
    shortId: '019abcd',
    message: 'Add user authentication',
    author: 'Alice <alice@example.org>',
    timestamp: new Date('2025-11-10T14:00:00Z'),
    branch: 'main',
    parents: ['019xyz12-5678-7890-abcd-ef0123456789'],
    tags: ['v1.0.0']
  },
  {
    id: '019xyz12-5678-7890-abcd-ef0123456789',
    shortId: '019xyz1',
    message: 'Update schema',
    author: 'Bob <bob@example.org>',
    timestamp: new Date('2025-11-10T12:00:00Z'),
    parents: ['019fgh34-9012-7890-abcd-ef0123456789']
  }
];
```

---

### 5. Write Type Tests

Create `tests/version-control/types.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import {
  isBranchContext,
  isCommitContext,
  isAsOfContext
} from '$lib/version-control/types';
import {
  mockBranchContext,
  mockCommitContext,
  mockAsOfContext
} from './__fixtures__/contexts';

describe('Type Guards', () => {
  describe('isBranchContext', () => {
    it('returns true for branch context', () => {
      expect(isBranchContext(mockBranchContext)).toBe(true);
    });

    it('returns false for commit context', () => {
      expect(isBranchContext(mockCommitContext)).toBe(false);
    });

    it('returns false for asOf context', () => {
      expect(isBranchContext(mockAsOfContext)).toBe(false);
    });
  });

  describe('isCommitContext', () => {
    it('returns true for commit context', () => {
      expect(isCommitContext(mockCommitContext)).toBe(true);
    });

    it('returns false for branch context', () => {
      expect(isCommitContext(mockBranchContext)).toBe(false);
    });

    it('returns false for asOf context', () => {
      expect(isCommitContext(mockAsOfContext)).toBe(false);
    });
  });

  describe('isAsOfContext', () => {
    it('returns true for asOf context', () => {
      expect(isAsOfContext(mockAsOfContext)).toBe(true);
    });

    it('returns false for branch context', () => {
      expect(isAsOfContext(mockBranchContext)).toBe(false);
    });

    it('returns false for commit context', () => {
      expect(isAsOfContext(mockCommitContext)).toBe(false);
    });
  });
});

describe('Type Narrowing', () => {
  it('allows accessing branch property after type guard', () => {
    const context = mockBranchContext;

    if (isBranchContext(context)) {
      // TypeScript should allow this without error
      expect(context.branch).toBe('main');
    }
  });

  it('allows accessing commit property after type guard', () => {
    const context = mockCommitContext;

    if (isCommitContext(context)) {
      expect(context.commit).toBe('019abcde-1234-7890-abcd-ef0123456789');
    }
  });

  it('allows accessing asOf property after type guard', () => {
    const context = mockAsOfContext;

    if (isAsOfContext(context)) {
      expect(context.asOf).toBe('2025-11-10T10:30:00Z');
    }
  });
});
```

---

## Acceptance Criteria

- ✅ types.ts created with all interface definitions
- ✅ All types have JSDoc comments explaining purpose
- ✅ Type guards implemented and exported
- ✅ index.ts exports all types
- ✅ Test fixtures created for all context types
- ✅ Type guard tests pass
- ✅ TypeScript compilation succeeds with no errors
- ✅ No `any` types used (strict type safety)

---

## Testing

```bash
# Run type tests
npm run test tests/version-control/types.test.ts

# Check TypeScript compilation
npm run build

# Verify type exports
npm run build && node -e "const { QueryContext } = require('./dist/version-control')"
```

---

## Notes

- UUIDv7 format: `xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx` (hex digits, version 7)
- ISO 8601 timestamp format: `YYYY-MM-DDTHH:mm:ss.sssZ`
- Type guards enable exhaustive type checking in switch statements
- All types should be immutable (readonly properties where applicable)

---

## Next Steps

After completing this task:
→ Proceed to [03-utils.md](./03-utils.md) to implement helper utilities

---

**Created**: 2025-11-10
**Status**: Not Started
