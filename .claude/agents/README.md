# Specialized Agents for CHUCC Server

**Quick reference guide for specialized agents**

## Available Agents

### 1. 🏗️ cqrs-compliance-checker

**Purpose:** Verify CQRS + Event Sourcing architecture compliance

**When to use:**
- After implementing command handlers
- After modifying projectors
- After adding new controllers
- When changing event flow

**Checks:**
- Command handlers create events only (no repository updates)
- Projectors update repositories only (no business logic)
- Controllers return immediately (no blocking)
- Events are immutable (records)

**Invoke with:**
```
Me: Please run the cqrs-compliance-checker to verify architecture compliance.
```

---

### 2. 🧪 test-isolation-validator

**Purpose:** Verify correct test patterns for async architecture

**When to use:**
- After writing integration tests
- After modifying test setup
- When tests are flaky
- When unsure about projector on/off

**Checks:**
- API tests don't enable projector
- API tests don't query repositories
- Projector tests enable projector via @TestPropertySource
- Projector tests use await() for async verification

**Invoke with:**
```
Me: Please run the test-isolation-validator to check my tests.
```

---

### 3. 📊 event-schema-evolution-checker

**Purpose:** Ensure event schema changes are backward compatible

**When to use:**
- Before modifying event classes
- After adding new event types
- When adding/removing event fields
- Before merging event-related PRs

**Checks:**
- No fields removed from existing events
- New fields are nullable or have defaults
- No field type changes
- Events remain immutable
- Projector handlers exist for new events

**Invoke with:**
```
Me: Please run the event-schema-evolution-checker to verify compatibility.
```

---

### 4. 📚 documentation-sync-agent

**Purpose:** Keep architecture documentation synchronized with code

**When to use:**
- Before completing large tasks
- After adding new components
- After architectural changes
- Periodically (monthly maintenance)

**Checks:**
- Component counts match actual counts
- Test counts are current
- Event type lists are complete
- Code examples are accurate
- New components are documented

**Invoke with:**
```
Me: Please run the documentation-sync-agent to check for doc drift.
```

---

## General-Purpose Agents (Pre-existing)

### 5. 🔍 code-reviewer

**Purpose:** Comprehensive code quality review

**When to use:**
- After completing feature implementation
- Before creating pull request
- After bug fixes or refactoring

**Created by:** User (pre-existing)

---

### 6. 🔒 security-officer

**Purpose:** Security vulnerability review

**When to use:**
- When implementing authentication/authorization
- When handling sensitive data
- After security-sensitive changes

**Created by:** User (pre-existing)

---

## Usage Patterns

### Typical Development Workflow

```
1. Implement feature
   ↓
2. Run @cqrs-compliance-checker
   - Verify architecture compliance
   ↓
3. Write tests
   ↓
4. Run @test-isolation-validator
   - Verify test patterns
   ↓
5. Before completion: Run @documentation-sync-agent
   - Update docs if needed
   ↓
6. Before PR: Run @code-reviewer
   - Comprehensive quality check
```

### When Modifying Events

```
1. Plan event schema change
   ↓
2. Run @event-schema-evolution-checker (proactive)
   - Check if change is safe
   ↓
3. Implement change
   ↓
4. Run @event-schema-evolution-checker (verify)
   - Confirm backward compatibility
   ↓
5. Run @test-isolation-validator
   - Verify projector tests updated
```

### Periodic Maintenance

```
Monthly or before major releases:

1. Run @documentation-sync-agent
   - Check for doc drift
   ↓
2. Update component counts
   ↓
3. Update test counts
   ↓
4. Verify examples are current
```

---

## Agent Priority by Issue Type

### Architecture Violations
1. **Primary:** @cqrs-compliance-checker
2. **Secondary:** @code-reviewer

### Test Issues
1. **Primary:** @test-isolation-validator
2. **Secondary:** @code-reviewer

### Event Schema Changes
1. **Primary:** @event-schema-evolution-checker
2. **Secondary:** @cqrs-compliance-checker (verify projectors updated)

### Documentation Drift
1. **Primary:** @documentation-sync-agent

### Security Concerns
1. **Primary:** @security-officer
2. **Secondary:** @code-reviewer

### General Quality
1. **Primary:** @code-reviewer

---

## How to Invoke Agents

### Option 1: Explicit Request (Recommended)

```
Me: I've just implemented the CherryPickCommandHandler.
Please run the cqrs-compliance-checker to verify architecture compliance.
```

### Option 2: After Task Completion

```
Me: I've completed the batch operations feature.
Please run all relevant agents to verify quality.

(Agent will determine: cqrs-compliance-checker, test-isolation-validator,
documentation-sync-agent)
```

### Option 3: Proactive (Agent Self-Invokes)

Agents should proactively invoke specialized agents when appropriate:

```
Assistant: I've implemented the new command handler. Let me verify
CQRS compliance before proceeding.
<Invokes cqrs-compliance-checker>
```

---

## Agent Output Format

All specialized agents provide structured feedback:

```
## [Agent Name] Check

### ✅ Compliant/Current
[What's correct]

### ❌ Critical Issues
[Must fix - violations, errors]

### ⚠️ Warnings
[Should fix - potential issues]

### 💡 Suggestions
[Optional improvements]

### 📋 Checklist
[Action items]

### 📚 References
[Relevant documentation]
```

---

## Agent Configuration

All agents use:
- **Model:** `sonnet` (Claude Sonnet)
- **Invocation:** Via Task tool
- **Context:** Full access to codebase and documentation

---

## Troubleshooting

### "Agent not found"

Ensure agent file exists: `.claude/agents/[agent-name].md`

### "Agent didn't catch issue"

1. Review agent prompt in `.claude/agents/[agent-name].md`
2. Check if issue type is in agent's checklist
3. Consider updating agent prompt

### "Agent is too strict"

Agents are intentionally strict to prevent architectural violations.
If false positive, discuss with team before suppressing.

---

## Extending Agents

### Adding New Checks

Edit agent file: `.claude/agents/[agent-name].md`

Add to checklist section:
```markdown
## New Check: [Name]

**What to verify:**
- ✅ Check item 1
- ✅ Check item 2

**Violations to catch:**
```java
// Example violation
```

**Fix:**
```java
// Example fix
```
```

### Creating New Agents

Follow template in `recommended-agents.md`, then create:
`.claude/agents/new-agent-name.md`

---

## Best Practices

1. **Invoke agents early** - Catch issues before they compound
2. **Fix critical issues immediately** - Don't accumulate technical debt
3. **Update agents** - Keep agent prompts current with patterns
4. **Run before PRs** - Ensure quality before review
5. **Trust agents** - Agents catch issues humans miss

---

## Agent Metrics

Track agent effectiveness:
- Issues caught by agent type
- Time saved by early detection
- False positive rate
- Agent invocation frequency

---

## References

- [Recommended Agents Analysis](recommended-agents.md)
- [CQRS Compliance Guide](../../docs/architecture/cqrs-event-sourcing.md)
- [Testing Patterns](../CLAUDE.md#testing-guidelines)
- [Architecture Overview](../../docs/architecture/README.md)

---

## Summary

**Four specialized agents** created for CHUCC Server:
1. ✅ **cqrs-compliance-checker** - Architecture integrity
2. ✅ **test-isolation-validator** - Test correctness
3. ✅ **event-schema-evolution-checker** - Production safety
4. ✅ **documentation-sync-agent** - Documentation accuracy

These agents complement the existing general-purpose agents (code-reviewer, security-officer) to provide comprehensive quality assurance for this CQRS + Event Sourcing project.

**Invoke agents proactively** to catch issues early and maintain high code quality!
