# Task 09: Package Publishing

**Phase**: Publishing (Week 4)
**Estimated Time**: 2-4 hours
**Prerequisites**: [08-docs.md](./08-docs.md) completed
**Depends On**: All components, tests, and docs complete

---

## Objective

Publish CHUCC-SQUI version-control extension to npm registry and create GitHub release.

---

## Pre-Publishing Checklist

### 1. Quality Gates

- ✅ All tests pass (`npm run test`)
- ✅ Test coverage ≥80% (`npm run test:coverage`)
- ✅ TypeScript compilation succeeds (`npm run build`)
- ✅ No linting errors (`npm run lint`)
- ✅ Storybook builds (`npm run build-storybook`)
- ✅ Package builds correctly (`npm run package`)

### 2. Documentation

- ✅ README.md complete with installation and usage
- ✅ API documentation complete
- ✅ Examples provided
- ✅ CHANGELOG.md updated with v1.0.0 changes

### 3. Package Configuration

Verify `package.json`:

```json
{
  "name": "chucc-squi",
  "version": "1.0.0",
  "description": "Svelte UI components for SPARQL/RDF applications",
  "keywords": ["svelte", "sparql", "rdf", "ui-components", "version-control"],
  "license": "MIT",
  "repository": {
    "type": "git",
    "url": "https://github.com/arne-bdt/CHUCC-SQUI.git"
  },
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
  "files": [
    "dist",
    "README.md",
    "LICENSE"
  ],
  "peerDependencies": {
    "svelte": "^5.0.0",
    "carbon-components-svelte": "^0.84.0"
  }
}
```

---

## Publishing Steps

### 1. Version Bump

```bash
# Update version in package.json
npm version 1.0.0

# This creates a git tag: v1.0.0
```

### 2. Build Package

```bash
# Clean previous build
rm -rf dist/

# Build package
npm run build

# Verify build output
ls -la dist/
ls -la dist/version-control/
```

### 3. Publish to npm

```bash
# Login to npm (if not already logged in)
npm login

# Publish package
npm publish

# Expected output: + chucc-squi@1.0.0
```

**For scoped package**:
```bash
npm publish --access public
```

### 4. Create GitHub Release

```bash
# Push version tag
git push origin v1.0.0

# Create release on GitHub
gh release create v1.0.0 \
  --title "CHUCC-SQUI v1.0.0" \
  --notes-file RELEASE_NOTES.md
```

#### RELEASE_NOTES.md Template

```markdown
# CHUCC-SQUI v1.0.0

First stable release of CHUCC-SQUI with Version Control Extension support.

## ✨ Features

### Version Control Components

- **QueryContextSelector**: Modal-based context selector (branch/commit/asOf)
- **QueryContextIndicator**: Compact inline context display
- **QueryContextBreadcrumb**: Breadcrumb navigation integration

### Utilities

- `buildQueryUrl()`: Construct SPARQL query URLs with context parameters
- `formatContext()`: Format contexts as human-readable strings
- `validateContext()`: Validate context objects (UUIDv7, ISO 8601)
- `parseContextFromUrl()`: Parse contexts from URL query parameters

## 📦 Installation

```bash
npm install chucc-squi
```

## 🚀 Quick Start

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

## 📖 Documentation

- [API Documentation](https://github.com/arne-bdt/CHUCC-SQUI/tree/main/docs/version-control)
- [Examples](https://github.com/arne-bdt/CHUCC-SQUI/tree/main/docs/version-control/examples)
- [Storybook](https://chucc-squi.netlify.app) (if deployed)

## 🧪 Testing

- 80%+ test coverage
- Unit tests (Vitest)
- Component tests (Playwright)
- Accessibility tests (axe)

## 🙏 Acknowledgments

Built for the [CHUCC project](https://github.com/arne-bdt/CHUCC-server) implementing SPARQL 1.2 Protocol with Version Control Extension.

---

**Full Changelog**: https://github.com/arne-bdt/CHUCC-SQUI/blob/main/CHANGELOG.md
```

---

### 5. Update CHANGELOG.md

```markdown
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-11-XX

### Added

#### Version Control Extension

- QueryContextSelector component for context selection (branch, commit, asOf)
- QueryContextIndicator component for inline context display
- QueryContextBreadcrumb component for navigation integration
- buildQueryUrl() utility for URL construction
- formatContext() utility for human-readable formatting
- validateContext() utility for input validation (UUIDv7, ISO 8601)
- parseContextFromUrl() utility for URL parsing
- Full TypeScript support with exported types
- Comprehensive test suite (80%+ coverage)
- Storybook stories for all components
- API documentation and examples

### Changed

- Package now exports `chucc-squi/version-control` module

### Dependencies

- Requires Svelte ^5.0.0
- Requires carbon-components-svelte ^0.84.0

---

## [0.9.0] - Previous Release

(Previous changelog entries...)
```

---

### 6. Announce Release

Post announcement:

**GitHub Discussions / Twitter / Discord**:
```
🎉 CHUCC-SQUI v1.0.0 Released!

First stable release with Version Control Extension support for SPARQL 1.2 Protocol.

✨ New Components:
- QueryContextSelector (branch/commit/asOf selection)
- QueryContextIndicator (inline display)
- QueryContextBreadcrumb (navigation)

📦 npm install chucc-squi

📖 Docs: https://github.com/arne-bdt/CHUCC-SQUI

#Svelte #SPARQL #RDF #OpenSource
```

---

## Post-Publishing Verification

### 1. Verify npm Package

```bash
# Install from npm in test project
mkdir test-install && cd test-install
npm init -y
npm install chucc-squi

# Verify exports
node -e "console.log(require.resolve('chucc-squi/version-control'))"
```

### 2. Test in Frontend Project

Create test project with SvelteKit:

```bash
npm create svelte@latest test-app
cd test-app
npm install
npm install chucc-squi

# Test import
echo 'import { QueryContextSelector } from "chucc-squi/version-control";' > src/lib/test.js
npm run build
```

### 3. Monitor npm Stats

- Check npm downloads: https://npmjs.com/package/chucc-squi
- Monitor bundle size: https://bundlephobia.com/package/chucc-squi

---

## Acceptance Criteria

- ✅ Package published to npm registry
- ✅ GitHub release created with notes
- ✅ CHANGELOG.md updated
- ✅ Package installable via `npm install chucc-squi`
- ✅ Version-control module importable
- ✅ All exports work correctly in test project
- ✅ Documentation accessible on GitHub

---

## Troubleshooting

### Issue: npm publish fails with 403

**Solution**: Ensure logged in with correct npm account and have publish permissions.

```bash
npm whoami
npm login
```

### Issue: TypeScript types not found

**Solution**: Verify `exports` in package.json includes `types` field.

### Issue: Module not found in consuming app

**Solution**: Check `files` field in package.json includes all necessary build artifacts.

---

## Next Steps

After v1.0.0 release:

1. Monitor GitHub issues for bug reports
2. Collect user feedback
3. Plan v1.1.0 features (commit search autocomplete, context history)
4. Update documentation based on user questions

---

**Created**: 2025-11-10
**Status**: Not Started
