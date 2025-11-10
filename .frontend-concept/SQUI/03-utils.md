# Task 03: Helper Utilities

**Phase**: Utilities (Week 1-2)
**Estimated Time**: 4-6 hours
**Prerequisites**: [02-types.md](./02-types.md) completed
**Depends On**: TypeScript interfaces defined

---

## Objective

Implement all helper utility functions for context manipulation, validation, formatting, and URL construction. Achieve 80%+ test coverage.

---

## Tasks

### 1. Implement buildQueryUrl()

Add to `src/lib/version-control/utils.ts`:

```typescript
import type { QueryContext } from './types';

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
 *
 * @example
 * buildQueryUrl('/query', {
 *   dataset: 'default',
 *   context: { type: 'commit', commit: '019abc...' },
 *   additionalParams: { limit: '100' }
 * });
 * // => "/query?dataset=default&commit=019abc...&limit=100"
 */
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

### 2. Implement formatContext()

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
 * @example
 * formatContext({ type: 'commit', commit: '019abcdef-1234-7890-abcd-ef0123456789' }, { short: true });
 * // => "Commit: 019abcd"
 *
 * @example
 * formatContext({ type: 'asOf', asOf: '2025-11-10T10:30:00Z' }, { includeIcon: true });
 * // => "🕐 asOf: 2025-11-10T10:30:00Z"
 */
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
      const commitId = short
        ? context.commit.slice(0, 7)
        : context.commit;
      return `${icon}Commit: ${commitId}`.trim();

    case 'asOf':
      const timestamp = short
        ? new Date(context.asOf).toLocaleDateString()
        : context.asOf;
      return `${icon}asOf: ${timestamp}`.trim();

    default:
      // Exhaustive type checking
      const _exhaustive: never = context;
      return 'Unknown context';
  }
}

/**
 * Returns an emoji icon for the given context type.
 * @internal
 */
function getContextIcon(context: QueryContext): string {
  switch (context.type) {
    case 'branch':
      return '🌿 ';
    case 'commit':
      return '📌 ';
    case 'asOf':
      return '🕐 ';
    default:
      return '';
  }
}
```

---

### 3. Implement validateContext()

```typescript
import type { ValidationResult, ValidationError } from './types';

/**
 * Validates a query context for correctness.
 *
 * @param context - Query context to validate
 * @returns Validation result with errors (if any)
 *
 * @example
 * validateContext({ type: 'commit', commit: 'invalid' });
 * // => { valid: false, errors: [{ field: 'commit', message: 'Invalid commit ID format' }] }
 *
 * @example
 * validateContext({ type: 'branch', branch: 'main' });
 * // => { valid: true, errors: [] }
 */
export function validateContext(context: QueryContext): ValidationResult {
  const errors: ValidationError[] = [];

  switch (context.type) {
    case 'branch':
      validateBranch(context.branch, errors);
      break;
    case 'commit':
      validateCommit(context.commit, errors);
      break;
    case 'asOf':
      validateAsOf(context.asOf, errors);
      break;
  }

  return {
    valid: errors.length === 0,
    errors
  };
}

/**
 * Validates a branch name.
 * @internal
 */
function validateBranch(branch: string, errors: ValidationError[]): void {
  if (!branch || branch.trim() === '') {
    errors.push({
      field: 'branch',
      message: 'Branch name is required',
      code: 'BRANCH_REQUIRED'
    });
    return;
  }

  // Branch name validation: alphanumeric, slash, underscore, hyphen
  if (!/^[a-zA-Z0-9/_-]+$/.test(branch)) {
    errors.push({
      field: 'branch',
      message: 'Branch name contains invalid characters (allowed: a-z, A-Z, 0-9, /, _, -)',
      code: 'BRANCH_INVALID_CHARS'
    });
  }

  // Check for invalid patterns
  if (branch.startsWith('/') || branch.endsWith('/')) {
    errors.push({
      field: 'branch',
      message: 'Branch name cannot start or end with /',
      code: 'BRANCH_INVALID_SLASHES'
    });
  }

  if (branch.includes('//')) {
    errors.push({
      field: 'branch',
      message: 'Branch name cannot contain consecutive slashes',
      code: 'BRANCH_CONSECUTIVE_SLASHES'
    });
  }
}

/**
 * Validates a commit ID (UUIDv7 format).
 * @internal
 */
function validateCommit(commit: string, errors: ValidationError[]): void {
  if (!commit || commit.trim() === '') {
    errors.push({
      field: 'commit',
      message: 'Commit ID is required',
      code: 'COMMIT_REQUIRED'
    });
    return;
  }

  // UUIDv7 format: xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx
  // where x is hex digit, y is one of [89ab]
  const uuidv7Pattern = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

  if (!uuidv7Pattern.test(commit)) {
    errors.push({
      field: 'commit',
      message: 'Invalid commit ID format (expected UUIDv7: xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx)',
      code: 'COMMIT_INVALID_FORMAT'
    });
  }
}

/**
 * Validates an asOf timestamp (ISO 8601 format).
 * @internal
 */
function validateAsOf(asOf: string, errors: ValidationError[]): void {
  if (!asOf || asOf.trim() === '') {
    errors.push({
      field: 'asOf',
      message: 'Timestamp is required',
      code: 'ASOF_REQUIRED'
    });
    return;
  }

  // Try to parse as Date
  const timestamp = Date.parse(asOf);
  if (isNaN(timestamp)) {
    errors.push({
      field: 'asOf',
      message: 'Invalid timestamp format (expected ISO 8601: YYYY-MM-DDTHH:mm:ss.sssZ)',
      code: 'ASOF_INVALID_FORMAT'
    });
    return;
  }

  // Check if timestamp is in the future (optional validation)
  const now = Date.now();
  if (timestamp > now) {
    errors.push({
      field: 'asOf',
      message: 'Timestamp cannot be in the future',
      code: 'ASOF_FUTURE'
    });
  }
}
```

---

### 4. Implement parseContextFromUrl()

```typescript
/**
 * Parses a query context from URL search parameters.
 *
 * Priority: commit > asOf > branch
 * (If multiple context parameters are present, commit takes precedence)
 *
 * @param searchParams - URLSearchParams or query string
 * @returns Parsed query context, or null if no context found
 *
 * @example
 * parseContextFromUrl('?dataset=default&branch=main');
 * // => { type: 'branch', branch: 'main' }
 *
 * @example
 * parseContextFromUrl('?dataset=default&commit=019abc...');
 * // => { type: 'commit', commit: '019abc...' }
 *
 * @example
 * parseContextFromUrl('?dataset=default&branch=main&commit=019abc...');
 * // => { type: 'commit', commit: '019abc...' } (commit takes priority)
 *
 * @example
 * parseContextFromUrl('?dataset=default');
 * // => null (no context parameters)
 */
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

### 5. Export from index.ts

Update `src/lib/version-control/index.ts`:

```typescript
// ... existing exports ...

// Utilities
export {
  buildQueryUrl,
  formatContext,
  validateContext,
  parseContextFromUrl
} from './utils';
```

---

### 6. Write Comprehensive Tests

Create `tests/version-control/utils.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import {
  formatContext,
  validateContext,
  buildQueryUrl,
  parseContextFromUrl
} from '$lib/version-control/utils';
import {
  mockBranchContext,
  mockCommitContext,
  mockAsOfContext
} from './__fixtures__/contexts';

describe('formatContext', () => {
  describe('Branch Context', () => {
    it('formats with full details by default', () => {
      expect(formatContext(mockBranchContext)).toBe('Branch: main (latest)');
    });

    it('formats with short format', () => {
      expect(formatContext(mockBranchContext, { short: true })).toBe('Branch: main');
    });

    it('includes icon when requested', () => {
      expect(formatContext(mockBranchContext, { includeIcon: true }))
        .toBe('🌿 Branch: main (latest)');
    });

    it('includes icon with short format', () => {
      expect(formatContext(mockBranchContext, { short: true, includeIcon: true }))
        .toBe('🌿 Branch: main');
    });
  });

  describe('Commit Context', () => {
    it('formats with full commit ID', () => {
      expect(formatContext(mockCommitContext))
        .toBe('Commit: 019abcde-1234-7890-abcd-ef0123456789');
    });

    it('formats with abbreviated commit ID', () => {
      expect(formatContext(mockCommitContext, { short: true }))
        .toBe('Commit: 019abcd');
    });

    it('includes icon when requested', () => {
      expect(formatContext(mockCommitContext, { includeIcon: true }))
        .toBe('📌 Commit: 019abcde-1234-7890-abcd-ef0123456789');
    });
  });

  describe('AsOf Context', () => {
    it('formats with full timestamp', () => {
      expect(formatContext(mockAsOfContext))
        .toBe('asOf: 2025-11-10T10:30:00Z');
    });

    it('formats with date only (short format)', () => {
      const result = formatContext(mockAsOfContext, { short: true });
      expect(result).toContain('asOf:');
      expect(result).toContain('11/10/2025'); // Locale-specific
    });

    it('includes icon when requested', () => {
      expect(formatContext(mockAsOfContext, { includeIcon: true }))
        .toBe('🕐 asOf: 2025-11-10T10:30:00Z');
    });
  });
});

describe('validateContext', () => {
  describe('Branch Context', () => {
    it('validates correct branch name', () => {
      const result = validateContext({ type: 'branch', branch: 'main' });
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('validates branch with slashes', () => {
      const result = validateContext({ type: 'branch', branch: 'feature/auth' });
      expect(result.valid).toBe(true);
    });

    it('rejects empty branch name', () => {
      const result = validateContext({ type: 'branch', branch: '' });
      expect(result.valid).toBe(false);
      expect(result.errors[0].message).toContain('required');
      expect(result.errors[0].code).toBe('BRANCH_REQUIRED');
    });

    it('rejects branch name with invalid characters', () => {
      const result = validateContext({ type: 'branch', branch: 'invalid@branch' });
      expect(result.valid).toBe(false);
      expect(result.errors[0].message).toContain('invalid characters');
    });

    it('rejects branch name starting with slash', () => {
      const result = validateContext({ type: 'branch', branch: '/feature' });
      expect(result.valid).toBe(false);
      expect(result.errors[0].message).toContain('cannot start or end');
    });

    it('rejects branch name with consecutive slashes', () => {
      const result = validateContext({ type: 'branch', branch: 'feature//auth' });
      expect(result.valid).toBe(false);
      expect(result.errors[0].message).toContain('consecutive slashes');
    });
  });

  describe('Commit Context', () => {
    it('validates correct UUIDv7', () => {
      const result = validateContext(mockCommitContext);
      expect(result.valid).toBe(true);
    });

    it('rejects empty commit ID', () => {
      const result = validateContext({ type: 'commit', commit: '' });
      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe('COMMIT_REQUIRED');
    });

    it('rejects invalid UUID format', () => {
      const result = validateContext({ type: 'commit', commit: 'invalid-uuid' });
      expect(result.valid).toBe(false);
      expect(result.errors[0].message).toContain('Invalid commit ID format');
    });

    it('rejects UUIDv4 (not v7)', () => {
      const result = validateContext({
        type: 'commit',
        commit: '550e8400-e29b-41d4-a716-446655440000' // UUIDv4
      });
      expect(result.valid).toBe(false);
      expect(result.errors[0].message).toContain('UUIDv7');
    });

    it('accepts lowercase and uppercase UUIDs', () => {
      const lowercase = validateContext({
        type: 'commit',
        commit: '019abcde-1234-7890-abcd-ef0123456789'
      });
      const uppercase = validateContext({
        type: 'commit',
        commit: '019ABCDE-1234-7890-ABCD-EF0123456789'
      });
      expect(lowercase.valid).toBe(true);
      expect(uppercase.valid).toBe(true);
    });
  });

  describe('AsOf Context', () => {
    it('validates correct ISO 8601 timestamp', () => {
      const result = validateContext(mockAsOfContext);
      expect(result.valid).toBe(true);
    });

    it('rejects empty timestamp', () => {
      const result = validateContext({ type: 'asOf', asOf: '' });
      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe('ASOF_REQUIRED');
    });

    it('rejects invalid timestamp format', () => {
      const result = validateContext({ type: 'asOf', asOf: 'not-a-date' });
      expect(result.valid).toBe(false);
      expect(result.errors[0].message).toContain('Invalid timestamp format');
    });

    it('rejects future timestamp', () => {
      const futureDate = new Date(Date.now() + 86400000).toISOString(); // +1 day
      const result = validateContext({ type: 'asOf', asOf: futureDate });
      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe('ASOF_FUTURE');
    });
  });
});

describe('buildQueryUrl', () => {
  it('builds URL with branch context', () => {
    const url = buildQueryUrl('/query', {
      dataset: 'default',
      context: mockBranchContext
    });
    expect(url).toBe('/query?dataset=default&branch=main');
  });

  it('builds URL with commit context', () => {
    const url = buildQueryUrl('/query', {
      dataset: 'default',
      context: mockCommitContext
    });
    expect(url).toBe('/query?dataset=default&commit=019abcde-1234-7890-abcd-ef0123456789');
  });

  it('builds URL with asOf context', () => {
    const url = buildQueryUrl('/query', {
      dataset: 'default',
      context: mockAsOfContext
    });
    expect(url).toContain('/query?dataset=default&asOf=2025-11-10T10%3A30%3A00Z');
  });

  it('includes additional parameters', () => {
    const url = buildQueryUrl('/query', {
      dataset: 'default',
      context: mockBranchContext,
      additionalParams: { limit: '100', offset: '0' }
    });
    expect(url).toContain('dataset=default');
    expect(url).toContain('branch=main');
    expect(url).toContain('limit=100');
    expect(url).toContain('offset=0');
  });

  it('encodes special characters in parameters', () => {
    const url = buildQueryUrl('/query', {
      dataset: 'my dataset',
      context: { type: 'branch', branch: 'feature/auth' }
    });
    expect(url).toContain('my+dataset');
    expect(url).toContain('feature%2Fauth');
  });
});

describe('parseContextFromUrl', () => {
  it('parses branch context from URL string', () => {
    const context = parseContextFromUrl('?dataset=default&branch=main');
    expect(context).toEqual({ type: 'branch', branch: 'main' });
  });

  it('parses branch context from URLSearchParams', () => {
    const params = new URLSearchParams('?dataset=default&branch=main');
    const context = parseContextFromUrl(params);
    expect(context).toEqual({ type: 'branch', branch: 'main' });
  });

  it('parses commit context', () => {
    const context = parseContextFromUrl('?dataset=default&commit=019abc...');
    expect(context).toEqual({ type: 'commit', commit: '019abc...' });
  });

  it('parses asOf context', () => {
    const context = parseContextFromUrl('?dataset=default&asOf=2025-11-10T10:30:00Z');
    expect(context).toEqual({ type: 'asOf', asOf: '2025-11-10T10:30:00Z' });
  });

  it('prioritizes commit over branch', () => {
    const context = parseContextFromUrl('?branch=main&commit=019abc...');
    expect(context).toEqual({ type: 'commit', commit: '019abc...' });
  });

  it('prioritizes commit over asOf', () => {
    const context = parseContextFromUrl('?asOf=2025-11-10T10:30:00Z&commit=019abc...');
    expect(context).toEqual({ type: 'commit', commit: '019abc...' });
  });

  it('prioritizes asOf over branch', () => {
    const context = parseContextFromUrl('?branch=main&asOf=2025-11-10T10:30:00Z');
    expect(context).toEqual({ type: 'asOf', asOf: '2025-11-10T10:30:00Z' });
  });

  it('returns null when no context parameters', () => {
    const context = parseContextFromUrl('?dataset=default');
    expect(context).toBeNull();
  });

  it('returns null for empty string', () => {
    const context = parseContextFromUrl('');
    expect(context).toBeNull();
  });
});
```

---

## Acceptance Criteria

- ✅ All utility functions implemented in utils.ts
- ✅ All functions have JSDoc comments with examples
- ✅ Unit tests achieve 80%+ coverage
- ✅ All tests pass
- ✅ TypeScript compilation succeeds
- ✅ Functions exported from index.ts
- ✅ Edge cases handled (empty strings, invalid formats, special characters)

---

## Testing

```bash
# Run utility tests
npm run test tests/version-control/utils.test.ts

# Run with coverage
npm run test:coverage tests/version-control/utils.test.ts

# Verify coverage threshold (should be ≥80%)
```

---

## Notes

- Use `URLSearchParams` for URL construction (handles encoding automatically)
- UUIDv7 regex must check version bit (7) and variant bits ([89ab])
- Future timestamp validation is optional but recommended for better UX
- All functions should be pure (no side effects)

---

## Next Steps

After completing this task:
→ Proceed to [04-main-component.md](./04-main-component.md) to implement QueryContextSelector

---

**Created**: 2025-11-10
**Status**: Not Started
