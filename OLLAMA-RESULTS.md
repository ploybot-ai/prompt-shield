# Ollama Integration Test Results (EXACT MATCH)

## Environment
- **Date**: 2026-06-22
- **Model**: qwen3.5:0.8b-mlx (local Ollama at localhost:11434)
- **Test**: DelimiterOllamaIntegrationTest
- **Method**: Response must contain EXACT placeholder literal (no partial match)
- **API**: Direct HTTP (handles thinking models)
- **Status**: PASSED

## Scoring
+25 per context (Plain, HTML, XML, JSON) = 100 max

## Results

| Format | Example | Plain | HTML | XML | JSON | Score | Verdict |
|--------|---------|-------|------|-----|------|-------|---------|
| `{{}}` | `{{REDACTED:EMAIL#81b562}}` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `[]` | `[REDACTED:EMAIL#81b562]` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `«»` | `«REDACTED:EMAIL#81b562»` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `⟦⟧` | `⟦REDACTED:EMAIL#81b562⟧` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `####` | `##REDACTED:EMAIL#81b562##` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `\|\|` | `\|REDACTED:EMAIL#81b562\|` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `\|\|\|\|` | `\|\|REDACTED:EMAIL#81b562\|\|` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| **`~~`** | `~REDACTED:EMAIL#81b562~` | **✓** | **✓** | **✓** | **✓** | **100** | **★★★ PERFECT** |
| `~~~~` | `~~REDACTED:EMAIL#81b562~~` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `^^` | `^REDACTED:EMAIL#81b562^` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `****` | `**REDACTED:EMAIL#81b562**` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `$$` | `$REDACTED:EMAIL#81b562$` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `%%` | `%REDACTED:EMAIL#81b562%` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `%%%%` | `%%REDACTED:EMAIL#81b562%%` | ✓ | ✓ | ✓ | ✓ | 100 | ★★★ PERFECT |
| `[[]]` | `[[REDACTED:EMAIL#81b562]]` | ✓ | ✗ | ✓ | ✓ | 75 | ★★ GOOD |
| `____` | `__REDACTED:EMAIL#81b562__` | ✗ | ✓ | ✓ | ✓ | 75 | ★★ GOOD |
| ````` `` | ```` ``REDACTED:EMAIL#81b562`` ```` | ✗ | ✓ | ✓ | ✓ | 75 | ★★ GOOD |
| `$$$$` | `$$REDACTED:EMAIL#81b562$$` | ✓ | ✗ | ✓ | ✓ | 75 | ★★ GOOD |
| `@@@@` | `@@REDACTED:EMAIL#81b562@@` | ✗ | ✓ | ✓ | ✗ | 50 | ★ MIXED |
| `^^^^` | `^^REDACTED:EMAIL#81b562^^` | ✗ | ✗ | ✓ | ✓ | 50 | ★ MIXED |
| `` ` ` ` | `` `REDACTED:EMAIL#81b562` `` | ✓ | ✓ | ✗ | ✗ | 50 | ★ MIXED |
| `**` | `*REDACTED:EMAIL#81b562*` | ✓ | ✗ | ✓ | ✗ | 50 | ★ MIXED |
| `<<>>` | `<<REDACTED:EMAIL#81b562>>` | ✗ | ✗ | ✗ | ✗ | 0 | FAIL |

## Final Ranking

```
████████████████████  100/100  {{}}      {{REDACTED:EMAIL#81b562}}
████████████████████  100/100  []        [REDACTED:EMAIL#81b562]
████████████████████  100/100  «»        «REDACTED:EMAIL#81b562»
████████████████████  100/100  ⟦⟧        ⟦REDACTED:EMAIL#81b562⟧
████████████████████  100/100  ####      ##REDACTED:EMAIL#81b562##
████████████████████  100/100  ||        |REDACTED:EMAIL#81b562|
████████████████████  100/100  ||||      ||REDACTED:EMAIL#81b562||
████████████████████  100/100  ~~        ~REDACTED:EMAIL#81b562~
████████████████████  100/100  ~~~~      ~~REDACTED:EMAIL#81b562~~
████████████████████  100/100  ^^        ^REDACTED:EMAIL#81b562^
████████████████████  100/100  ****      **REDACTED:EMAIL#81b562**
████████████████████  100/100  $$        $REDACTED:EMAIL#81b562$
████████████████████  100/100  %%        %REDACTED:EMAIL#81b562%
████████████████████  100/100  %%%%      %%REDACTED:EMAIL#81b562%%
██████████████░░░░░░   75/100  [[]]      [[REDACTED:EMAIL#81b562]]
██████████████░░░░░░   75/100  ____      __REDACTED:EMAIL#81b562__
██████████████░░░░░░   75/100  ````      ``REDACTED:EMAIL#81b562``
██████████████░░░░░░   75/100  $$$$      $$REDACTED:EMAIL#81b562$$
█████████░░░░░░░░░░░   50/100  @@@@      @@REDACTED:EMAIL#81b562@@
█████████░░░░░░░░░░░   50/100  ^^^^      ^^REDACTED:EMAIL#81b562^^
█████████░░░░░░░░░░░   50/100  `         `REDACTED:EMAIL#81b562`
█████████░░░░░░░░░░░   50/100  **        *REDACTED:EMAIL#81b562*
                              0/100  <<>>      <<REDACTED:EMAIL#81b562>>
```

## Key Findings

1. **14 formats scored PERFECT (100)** with qwen3.5:0.8b-mlx
2. **`<<>>` FAILS completely** — model escapes `<` and `>` as `\u003c\u003e`
3. **`~~` remains PERFECT** — consistent across both models tested
4. **`[[]]` (current default) scored 75** — fails only in HTML context
5. **`{{}}` also PERFECT** — most conventional-looking option

## Model Comparison

| Model | `~~` | `{{}}` | `[[]]` | Notes |
|-------|------|--------|--------|-------|
| qwen2.5:0.5b | 100 | 75 | 25 | Small model, stricter |
| qwen3.5:0.8b-mlx | 100 | 100 | 75 | Larger model, more flexible |

**`~~` is the only format that scored 100 in BOTH models.**

## Analysis of PERFECT Formats (Score 100)

| Format | Example | Pros | Cons |
|---------|---------|------|------|
| `{{}}` | `{{REDACTED:EMAIL#81b562}}` | Estándar en templates | Conflicto con Handlebars/Mustache |
| `[]` | `[REDACTED:EMAIL#81b562]` | Simple y limpio | Conflicto con markdown links, JSON arrays |
| `«»` | `«REDACTED:EMAIL#81b562»` | Unicode, casi nunca usado | Caracteres no-ASCII |
| `⟦⟧` | `⟦REDACTED:EMAIL#81b562⟧` | Unicode, muy raro | Caracteres no-ASCII |
| `####` | `##REDACTED:EMAIL#81b562##` | Visible | Conflicto con markdown headers |
| `\|\|` | `\|REDACTED:EMAIL#81b562\|` | Pipe estándar | Conflicto con OR operator |
| `\|\|\|\|` | `\|\|REDACTED:EMAIL#81b562\|\|` | Menos común que `\|\|` | Visualmente pesado |
| **`~~`** | `~REDACTED:EMAIL#81b562~` | **Limpio, raro en prompts** | Strikethrough markdown |
| `~~~~` | `~~REDACTED:EMAIL#81b562~~` | Más raro que `~~` | Visualmente pesado |
| `^^` | `^REDACTED:EMAIL#81b562^` | XOR, raro en prompts | Puede confundir con exponente |
| `****` | `**REDACTED:EMAIL#81b562**` | Bold markdown | Conflicto directo con bold |
| `$$` | `$REDACTED:EMAIL#81b562$` | Dollar sign | Conflicto con LaTeX/math |
| `%%` | `%REDACTED:EMAIL#81b562%` | Percent | Conflicto con comentarios/modulo |
| `%%%%` | `%%REDACTED:EMAIL#81b562%%` | Menos común | Visualmente pesado |

**Recommendation: `~~`**
- Limpio y corto
- Strikethrough (`~~texto~~`) casi nunca aparece en prompts con datos sensibles
- ASCII-safe
- Funcionó perfecto en ambos modelos
- Más "neutral" que `{{}}` o `[]`

## Recommendation

**Change default from `[[ ]]` to `~~ ~~`**

- Only format with 100/100 in both models tested
- Works across all 4 contexts (Plain, HTML, XML, JSON)
- ASCII-safe, no encoding issues
- Strikethrough markdown (`~~text~~`) is rare in prompts with sensitive data

## Detailed Responses (qwen3.5:0.8b-mlx)

### ~~ (PERFECT - 100)
```
Plain:  ~REDACTED:EMAIL#81b562~
HTML:   ~REDACTED:EMAIL#81b562~
XML:    ~REDACTED:EMAIL#81b562~
JSON:   Email: ~REDACTED:EMAIL#81b562~
```

### {{}} (PERFECT - 100)
```
Plain:  {{REDACTED:EMAIL#81b562}}
HTML:   {{REDACTED:EMAIL#81b562}}
XML:    Email: {{REDACTED:EMAIL#81b562}}
JSON:   Email: {{REDACTED:EMAIL#81b562}}
```

### [[]] (GOOD - 75)
```
Plain:  [[REDACTED:EMAIL#81b562]]
HTML:   [[REDACTED:EMAIL#81     ← FAILED (truncated)
XML:    [[REDACTED:EMAIL#81b562]]
JSON:   [[REDACTED:EMAIL#81b562]]
```

### <<>> (FAIL - 0)
```
Plain:  \u003c\u003cREDACTED:EMAIL#81b562\u003e\u003e
HTML:   \u003c\u003cREDACTED:EMAIL#81b562\u003e\u003e
XML:    \u003c\u003cREDACTED:EMAIL#8
JSON:   \u003c\u003cREDACTED:EMAIL#81b562\u003e\u003e
```
