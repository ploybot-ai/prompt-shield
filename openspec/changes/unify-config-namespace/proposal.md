# Proposal: Unify Configuration Namespace under `promptShield`

## Intent

Currently the library uses **3 different config prefixes** (`obfuscador`, `prompt-shield.advisor`, `prompt-shield.proxy`) plus 2 more for storage (`obfuscador.storage.jpa`, `obfuscador.storage.redis`). This is confusing for users — the example `application.yml` has duplicated `hash-algorithm`, `hash-length`, `custom-types` across sections. The goal is to have **one single root namespace** `promptShield` (YAML: `prompt-shield`) for everything.

## Scope

### In Scope
- Create a single `PromptShieldProperties` class with prefix `prompt-shield` that contains ALL configuration
- Move obfuscation settings (hash, tags, custom-types) to root level
- Move advisor settings under `prompt-shield.advisor`
- Move proxy settings under `prompt-shield.proxy`
- Move storage settings under `prompt-shield.storage`
- Update all `@ConfigurationProperties` classes: `ObfuscationProperties`, `PromptShieldAdvisorProperties`, `ApiProxyProperties`, `JpaStorageProperties`, `RedisStorageProperties`
- Update all auto-configuration classes to use the unified config
- Update example `application.yml`
- Update all tests

### Out of Scope
- Changing the core `ObfuscationConfig` POJO (it stays as-is, it's not a Spring config class)
- Changing runtime behavior
- Changing the `@ConfigurationProperties` prefix of `ApiProxyProperties` (it stays at `prompt-shield.proxy`)

## Approach

### New unified YAML structure

```yaml
prompt-shield:
  # ── Core obfuscation ──
  enabled: true
  hash-algorithm: SHA-256
  hash-length: 6
  redacted-prefix: "REDACTED"
  tag-separator: "#"
  tag-open: "~"
  tag-close: "~"
  storage-type: memory
  custom-types:
    CODIGO_POSTAL:
      pattern: \d{5}
    N_CUENTA:
      pattern: ES\d{22}

  # ── Advisor (Spring AI) ──
  advisor:
    enabled: true
    order: 0
    restore-on-response: true
    inject-system-prompt: true
    system-prompt: ""

  # ── API Proxy ──
  proxy:
    enabled: true
    base-path: /v1
    provider:
      base-url: https://api.openai.com
      api-key: ""
      model: gpt-4o-mini

  # ── Storage ──
  storage:
    type: memory
    jpa:
      ttl-hours: 24
      cleanup-enabled: true
    redis:
      ttl-hours: 24
```

### Implementation Steps

1. **Create `PromptShieldProperties`** — single root class with prefix `prompt-shield`, containing nested `AdvisorProperties`, `ProxyProperties`, `StorageProperties` inner classes + all core obfuscation fields.

2. **Remove old properties classes**: `ObfuscationProperties`, `PromptShieldAdvisorProperties`, `ApiProxyProperties`, `JpaStorageProperties`, `RedisStorageProperties`.

3. **Update auto-configurations**: `ObfuscationAutoConfiguration`, `PromptShieldAdvisorAutoConfiguration`, `ApiProxyAutoConfiguration`, `JpaStorageAutoConfiguration`, `RedisStorageAutoConfiguration` — all read from the unified `PromptShieldProperties`.

4. **Update example `application.yml`**.

5. **Update tests**.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `prompt-shield-core` | None | `ObfuscationConfig` POJO unchanged |
| `prompt-shield-spring-boot-starter` | Modified | Remove `ObfuscationProperties`, update `ObfuscationAutoConfiguration` |
| `prompt-shield-spring-ai-advisor` | Modified | Remove `PromptShieldAdvisorProperties`, update `PromptShieldAdvisorAutoConfiguration` |
| `prompt-shield-api-proxy` | Modified | Remove `ApiProxyProperties`, update `ApiProxyAutoConfiguration` |
| `prompt-shield-storage-jpa` | Modified | Remove `JpaStorageProperties`, update `JpaStorageAutoConfiguration` |
| `prompt-shield-storage-redis` | Modified | Remove `RedisStorageProperties`, update `RedisStorageAutoConfiguration` |
| `prompt-shield-example` | Modified | Update `application.yml` |
| Tests (all modules) | Modified | Update config references |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Breaking change for existing users | High | Document migration; consider keeping old prefixes as deprecated fallback via `@DeprecatedConfigurationProperty` |
| Bean conflicts when multiple modules combined | Medium | Keep `@ConditionalOnMissingBean` on all shared beans; use `@ConditionalOnProperty` with new prefix |
| Storage auto-config `@ConditionalOnProperty` needs updating | Low | Change prefix from `obfuscador.storage` to `prompt-shield.storage` |

## Rollback Plan

Revert all file changes via `git checkout -- .`. No data or state to clean up since this is purely configuration restructuring.

## Dependencies

None.

## Success Criteria

- [ ] All config properties accessible under single `prompt-shield.*` prefix
- [ ] No duplicated properties across sections
- [ ] All existing tests pass
- [ ] Example app works with new config structure
- [ ] `tagOpen`/`tagClose`/`conversationId` now exposed in YAML (currently missing from all Spring configs)
