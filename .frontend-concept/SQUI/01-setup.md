# Task 01: Project Setup

**Phase**: Foundation (Week 1)
**Estimated Time**: 2-3 hours
**Prerequisites**: None
**Depends On**: None

---

## Objective

Set up the directory structure, package configuration, and development environment for the version-control extension in CHUCC-SQUI.

---

## Tasks

### 1. Create Directory Structure

```bash
cd CHUCC-SQUI/

# Create version-control directory
mkdir -p src/lib/version-control
mkdir -p src/lib/version-control/internal

# Create test directories
mkdir -p tests/version-control
mkdir -p tests/version-control/__fixtures__

# Create docs directory
mkdir -p docs/version-control
```

**File Structure**:
```
CHUCC-SQUI/
├── src/lib/version-control/
│   ├── index.ts                          # Public exports
│   ├── types.ts                          # TypeScript interfaces
│   ├── utils.ts                          # Helper utilities
│   ├── QueryContextSelector.svelte       # Main component
│   ├── QueryContextIndicator.svelte      # Indicator component
│   ├── QueryContextBreadcrumb.svelte     # Breadcrumb component
│   └── internal/
│       ├── CommitSearchInput.svelte      # Internal sub-component
│       └── DateTimePicker.svelte         # Internal sub-component (if needed)
├── tests/version-control/
│   ├── types.test.ts
│   ├── utils.test.ts
│   ├── QueryContextSelector.test.ts
│   ├── QueryContextIndicator.test.ts
│   ├── QueryContextBreadcrumb.test.ts
│   └── __fixtures__/
│       ├── branches.ts                   # Mock branch data
│       └── commits.ts                    # Mock commit data
└── docs/version-control/
    ├── README.md
    ├── api/
    │   ├── QueryContextSelector.md
    │   ├── QueryContextIndicator.md
    │   └── QueryContextBreadcrumb.md
    └── examples/
        ├── basic-usage.md
        ├── with-state-management.md
        └── advanced-callbacks.md
```

---

### 2. Update Package Configuration

#### package.json

Add exports for version-control module:

```json
{
  "name": "chucc-squi",
  "version": "0.9.0",
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "svelte": "./dist/index.js"
    },
    "./version-control": {
      "types": "./dist/version-control/index.d.ts",
      "svelte": "./dist/version-control/index.js"
    }
  },
  "devDependencies": {
    "@playwright/test": "^1.40.0",
    "@storybook/svelte": "^8.0.0",
    "vitest": "^1.0.0"
  }
}
```

**Note**: Adjust based on existing CHUCC-SQUI build configuration.

---

### 3. Configure TypeScript

#### tsconfig.json

Ensure paths are configured for version-control module:

```json
{
  "compilerOptions": {
    "paths": {
      "$lib/*": ["./src/lib/*"],
      "chucc-squi/version-control": ["./src/lib/version-control/index.ts"]
    }
  }
}
```

---

### 4. Configure Testing

#### vitest.config.ts

```typescript
import { defineConfig } from 'vitest/config';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  plugins: [svelte({ hot: !process.env.VITEST })],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      include: ['src/lib/version-control/**/*.{ts,svelte}'],
      exclude: ['src/lib/version-control/**/*.test.ts'],
      thresholds: {
        lines: 80,
        functions: 80,
        branches: 80,
        statements: 80
      }
    }
  }
});
```

#### tests/setup.ts

```typescript
import '@testing-library/jest-dom';
import { expect, afterEach } from 'vitest';
import { cleanup } from '@testing-library/svelte';

// Cleanup after each test
afterEach(() => {
  cleanup();
});
```

---

### 5. Configure Storybook (Optional)

If Storybook is not yet set up in CHUCC-SQUI:

```bash
npx storybook@latest init
```

#### .storybook/main.ts

```typescript
import type { StorybookConfig } from '@storybook/svelte-vite';

const config: StorybookConfig = {
  stories: ['../src/**/*.stories.@(js|ts|svelte)'],
  addons: [
    '@storybook/addon-links',
    '@storybook/addon-essentials',
    '@storybook/addon-a11y'
  ],
  framework: {
    name: '@storybook/svelte-vite',
    options: {}
  }
};

export default config;
```

---

### 6. Create Initial Files

#### src/lib/version-control/index.ts

```typescript
// Public exports for version-control module

// Types
export type {
  QueryContext,
  BranchContext,
  CommitContext,
  AsOfContext,
  Branch,
  Commit,
  ValidationResult,
  ValidationError
} from './types';

// Components
export { default as QueryContextSelector } from './QueryContextSelector.svelte';
export { default as QueryContextIndicator } from './QueryContextIndicator.svelte';
export { default as QueryContextBreadcrumb } from './QueryContextBreadcrumb.svelte';

// Utilities
export {
  buildQueryUrl,
  formatContext,
  validateContext,
  parseContextFromUrl
} from './utils';
```

#### docs/version-control/README.md

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

- `buildQueryUrl()` - Construct SPARQL query URLs
- `formatContext()` - Format context as string
- `validateContext()` - Validate context objects
- `parseContextFromUrl()` - Parse context from URL params

## Examples

See [examples/](./examples/) directory for complete usage examples.
```

---

### 7. Update Main README

Add section to CHUCC-SQUI's main README.md:

```markdown
## Version Control Extension

Optional components for SPARQL 1.2 Protocol Version Control Extension:

```svelte
import { QueryContextSelector } from 'chucc-squi/version-control';
```

See [docs/version-control/README.md](./docs/version-control/README.md) for details.
```

---

## Acceptance Criteria

- ✅ Directory structure created as specified
- ✅ package.json exports configured for `chucc-squi/version-control`
- ✅ TypeScript paths configured correctly
- ✅ Vitest configuration created with 80% coverage threshold
- ✅ Storybook configured (if applicable)
- ✅ Initial index.ts created with placeholder exports
- ✅ Documentation structure created
- ✅ All files compile without errors (even if empty)

---

## Testing

### Verify Setup

```bash
# 1. Install dependencies
npm install

# 2. Verify TypeScript compilation
npm run build

# 3. Verify tests run (even if none exist yet)
npm run test

# 4. Verify Storybook starts (if configured)
npm run storybook
```

---

## Notes

- Keep internal sub-components in `internal/` directory (not exported publicly)
- Use Carbon Design System components for UI (already available in CHUCC-SQUI)
- Follow existing CHUCC-SQUI code style and conventions
- Ensure all new files have proper copyright headers (if applicable)

---

## Next Steps

After completing this task:
→ Proceed to [02-types.md](./02-types.md) to define TypeScript interfaces

---

**Created**: 2025-11-10
**Status**: Not Started
