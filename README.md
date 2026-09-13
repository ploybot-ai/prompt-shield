# Prompt Shield

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.1-green.svg)](https://spring.io/projects/spring-ai)

Library for obfuscating sensitive data in AI prompts. Protect PII (Personally Identifiable Information) before sending to LLMs.

## Why Prompt Shield?

### European Privacy Regulations

The **General Data Protection Regulation (GDPR)**, Regulation (EU) 2016/679, establishes strict requirements for handling personal data. Key principles include:

- **Data minimization**: Only collect and process data that is strictly necessary
- **Purpose limitation**: Data cannot be used for purposes other than those originally consented
- **Storage limitation**: Data must be kept only as long as necessary
- **Integrity and confidentiality**: Appropriate security measures must be implemented

When using AI services (LLMs), sensitive data such as DNI, NIE, emails, or phone numbers may be sent to third-party providers. This creates compliance risks under GDPR.

**Penalties for non-compliance**: Up to €20 million or 4% of annual global turnover, whichever is higher.

### Real-World Example: Xestando Invoicing App

[Xestando](https://app.xestando.com/) is an online invoicing platform for freelancers and SMEs that uses AI to automate document processing. When a user asks the AI to analyze an invoice, the request may contain:

- Customer DNI/NIE for tax identification
- Email addresses for billing
- Phone numbers for contact information
- Bank account numbers (IBAN) for payments

Without obfuscation, this sensitive data would be sent directly to the AI provider, potentially violating GDPR. With Prompt Shield:

```java
@Service
public class InvoiceService {
    
    private final ChatClient chatClient;
    
    public String analyzeInvoice(String invoiceText) {
        // PII is automatically obfuscated before sending to AI
        return chatClient.prompt()
            .user(invoiceText)
            .call()
            .content();
    }
}
```

The AI receives placeholders like `~REDACTED:DNI#1c9f96~` instead of actual DNI values, maintaining compliance while preserving functionality.

## Features

- **Obfuscate sensitive data**: DNI, NIE, email, phone, IBAN, postal code and custom types
- **Service key detection**: OpenAI, Anthropic, AWS, GitHub, Stripe, and 11 more services
- **Bidirectional**: Obfuscate prompts and restore original values in responses
- **JSON support**: Obfuscate objects with type preservation
- **Spring Boot Starter**: Auto-configuration with sensible defaults
- **Spring AI Advisor**: Seamless integration with Spring AI
- **Configurable**: Custom prefixes, separators, data types, and service key toggle
- **Multiple storage backends**: In-memory, Redis, JPA/H2

## Quick Start

### Maven

```xml
<dependency>
    <groupId>com.ploybot</groupId>
    <artifactId>prompt-shield-core</artifactId>
    <version>1.4.0</version>
</dependency>

<!-- Spring Boot Starter (optional) -->
<dependency>
    <groupId>com.ploybot</groupId>
    <artifactId>prompt-shield-spring-boot-starter</artifactId>
    <version>1.4.0</version>
</dependency>

<!-- Spring AI Advisor (optional) -->
<dependency>
    <groupId>com.ploybot</groupId>
    <artifactId>prompt-shield-spring-ai-advisor</artifactId>
    <version>1.4.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.ploybot:prompt-shield-core:1.4.0'
implementation 'com.ploybot:prompt-shield-spring-boot-starter:1.4.0'
implementation 'com.ploybot:prompt-shield-spring-ai-advisor:1.4.0'
```

## Usage

### Basic Usage

```java
import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.engine.ObfuscationScope;

ObfuscationEngine engine = new ObfuscationEngine();
ObfuscationScope scope = engine.forScope("customer-42");

// Obfuscate text
String obfuscated = scope.obfuscate("Mi DNI es 12345678Z y mi email es user@email.com");
// Result: "Mi DNI es ~REDACTED:DNI#1c9f96~ y mi email es ~REDACTED:EMAIL#e5a3b2~"

// Restore original values
String restored = scope.restore(obfuscated);
// Result: "Mi DNI es 12345678Z y mi email es user@email.com"
```

`scopeId` is an opaque isolation boundary chosen by the application. It can be a
conversation id, user id, tenant id, request id, or another stable identifier. A
tag can only be restored using the same scope. Reuse one `ObfuscationEngine` and
create lightweight scopes with `forScope(...)`; this is safe for concurrent use.

### Obfuscate Specific Types

```java
// Obfuscate only DNI
String obfuscated = scope.obfuscate("DNI: 12345678Z, Tel: 612345678", "DNI");
// Result: "DNI: ~REDACTED:DNI#1c9f96~, Tel: 612345678"
```

### JSON Obfuscation

```java
// Obfuscate JSON string
String json = "{\"dni\":\"12345678Z\",\"email\":\"user@email.com\"}";
String obfuscated = scope.obfuscateJson(json);
// Result: "{\"dni\":\"~REDACTED:DNI#1c9f96~\",\"email\":\"~REDACTED:EMAIL#e5a3b2~\"}"

// Restore JSON
String restored = scope.restoreJson(obfuscated);
```

`obfuscateJson(json)` only touches string values, so amounts and ids are never turned
into `CODIGO_POSTAL` or `TELEFONO` tags. Use `obfuscateJson(json, true)` to also cover
numeric values and object keys (a phone stored as a number, a map keyed by email);
numbers that contain sensitive data then become strings.

### Short-lived scopes

Each `ObfuscationScope` remembers the mappings it created. When a scope only lives for
one request, release them once the response has been restored:

```java
ObfuscationScope scope = engine.forScope("request-" + UUID.randomUUID());
try {
    String answer = callModel(scope.obfuscate(prompt));
    return scope.restore(answer);
} finally {
    scope.removeCreatedMappings();
}
```

### Object Obfuscation

```java
Persona persona = new Persona("Juan", "12345678Z", "612345678", "juan@email.com");
String obfuscated = engine.ofuscarObjeto(persona);
// Result: JSON with obfuscated values

Persona restored = engine.restaurarObjeto(obfuscated, Persona.class);
```

## Spring Boot Integration

### Configuration

```yaml
# application.yml
prompt-shield:
  enabled: true
  hash-algorithm: SHA-256
  hash-length: 6
  storage:
    type: memory               # memory | redis | jpa
    memory:
      ttl-hours: 24            # 0 keeps mappings forever
  redacted-prefix: "REDACTED"  # Change to "OBFUSCADO", "OCULTO", etc.
  tag-separator: "#"            # Change to "_", "-", etc.
  service-keys-enabled: true   # false to disable service key detection
  checksum-validation-enabled: true  # validate DNI/NIE/IBAN control digits to avoid false positives
  hmac-secret: ${PROMPT_SHIELD_HMAC_SECRET}  # required for persistent/distributed storage
  custom-types:
    MATRICULA:
      pattern: '\b\d{4}[A-Z]{3}\b'
    "[REFERENCIA_PEDIDO]":     # brackets keep the underscore in the name
      pattern: '\bPED-\d{6}\b'
```

Custom type names must match `[A-Z][A-Z0-9_]*`; any other name is rejected at startup.
Spring Boot drops underscores from map keys unless they are written in bracket notation.

Tag identifiers are derived with a keyed HMAC rather than a plain hash, preventing
offline brute-force recovery of small input spaces. If `hmac-secret` is omitted, a
random key is generated for that engine instance. Always configure the same strong
secret on every instance when using Redis/JPA, multiple application instances, or
restoration after a restart. Rotating this secret invalidates existing mappings.

### Migrating to 1.4

- Detection runs once over the original text: a pattern can no longer match inside a
  tag produced by another type or already present in the text. Custom patterns such
  as `\d{5}` used to corrupt about 12% of the tags.
- New and wider formats: phones with spaces, hyphens or `+34`/`0034`; DNI with dots or
  hyphen (`12.345.678-Z`); NIE with hyphens; Spanish IBANs in groups of four; any
  other country's IBAN (`IBAN` type); OpenAI service-account, admin and legacy keys.
  DNI and NIE now require word boundaries, so a longer reference is left alone.
- The in-memory storage created by the auto-configuration expires mappings after
  `prompt-shield.storage.memory.ttl-hours` (24 by default) since their last use.
- `PromptShieldAdvisor` also obfuscates the application's system prompt and tool
  results (Spring AI 2.0 runs the tool loop through the advisor chain). Disable either
  with `advisor.obfuscate-system-messages` or `advisor.obfuscate-tool-responses`.
  Requests without a scope id get a per-request scope that is released afterwards.
- `@Obfuscate` works: the aspect was never registered. It now also supports class
  level and `restore = true` uses a per-call scope.
- JPA storage: keys are stored as their SHA-256 (long scope ids no longer overflow the
  column); the table is created by `prompt-shield.storage.jpa.initialize-schema`
  instead of a Flyway migration inside the jar (it clashed with the application's
  `V1`); expired rows are ignored and deleted every `cleanup-interval-minutes`; the
  application's own entity and repository scanning is no longer replaced. If you
  applied the old `V1__create_obfuscation_tags_table.sql` with Flyway, keep a copy in
  your migrations.
- Redis storage uses `SET NX` and renews the TTL when a mapping is reused.
- `ObfuscationTag#getTag()` is deprecated: its `<TYPE_hash>` form never matched the
  placeholders. Use `ObfuscationEngine#formatTag(tag)`.

### Migrating to 1.3

- Use `engine.forScope(scopeId)` instead of mutating `ObfuscationConfig` per
  conversation. `getConversationId()` and `setConversationId(...)` remain as
  deprecated compatibility aliases for the default scope.
- Configure `hmac-secret` before deployment. New identifiers differ from those
  generated by older plain-hash versions. Existing tags remain restorable only
  while their original storage mappings are retained.
- Checksum validation is enabled by default for DNI, NIE, and IBAN. Set
  `checksum-validation-enabled: false` only if invalid or synthetic identifiers
  must also be detected.

### Using @Obfuscate Annotation

```java
@Service
public class MyService {

    @Obfuscate
    public String processPrompt(String prompt) {
        // prompt is automatically obfuscated
        return aiClient.call(prompt);
    }

    @Obfuscate(types = {"DNI", "EMAIL"})
    public String processSpecific(String prompt) {
        // Only DNI and EMAIL are obfuscated
        return aiClient.call(prompt);
    }

    @Obfuscate(restore = true)
    public String ask(String prompt) {
        // Tags in the returned String are restored and the mappings released
        return aiClient.call(prompt);
    }

    public String mixed(@Obfuscate String prompt, String untouched) {
        return aiClient.call(prompt + untouched);
    }
}
```

The annotation can also be placed on a class. Only `String` arguments are obfuscated.
Without `restore`, tags are created in the engine's default scope, shared by every
caller. Disable the aspect with `prompt-shield.annotation.enabled=false`.

## Storage Backends

Prompt Shield supports multiple storage backends for obfuscation tags:

### In-Memory (Default)

```yaml
prompt-shield:
  storage:
    type: memory
```

### Redis

```xml
<dependency>
    <groupId>com.ploybot</groupId>
    <artifactId>prompt-shield-storage-redis</artifactId>
    <version>1.4.0</version>
</dependency>
```

```yaml
prompt-shield:
  storage:
    type: redis
    redis:
      ttl-hours: 24

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### JPA/H2

```xml
<dependency>
    <groupId>com.ploybot</groupId>
    <artifactId>prompt-shield-storage-jpa</artifactId>
    <version>1.4.0</version>
</dependency>
```

```yaml
prompt-shield:
  storage:
    type: jpa
    jpa:
      ttl-hours: 24                 # since the mapping was last used
      cleanup-enabled: true
      cleanup-interval-minutes: 60
      initialize-schema: embedded   # embedded | always | never

spring:
  datasource:
    url: jdbc:h2:mem:promptshield
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: validate
  h2:
    console:
      enabled: true
```

## Spring AI Advisor Integration

### Configuration

```yaml
prompt-shield:
  advisor:
    enabled: true
    order: 0
    restore-on-response: true
    inject-system-prompt: true  # Auto-inject system prompt to preserve placeholders
    # system-prompt: "Your custom system prompt"  # Optional custom prompt
    obfuscate-system-messages: true   # the application's system prompt text
    obfuscate-tool-responses: true    # tool results before they reach the model
```

The advisor obfuscates system, user and assistant messages, tool call arguments and
tool results, and restores the response. Keep its order above Spring AI's
`ToolCallingAdvisor.DEFAULT_ORDER` (the default 0 is): every iteration of the tool loop
then goes through it, so tools receive the real values and the model never sees their
results in clear.

### System Prompt

The advisor automatically injects a system prompt that instructs the AI to preserve the obfuscated placeholders. This ensures the AI doesn't modify, remove, or try to "fix" the `~REDACTED:TYPE#HASH~` markers.

```java
String english = PromptShieldAdvisor.generateSystemPrompt(config, "en");
String spanish = PromptShieldAdvisor.generateSystemPrompt(config, "es");
```

### Java Configuration

```java
@Configuration
public class AIConfig {
    
    @Bean
    public ChatClient chatClient(ChatModel chatModel, PromptShieldAdvisor advisor) {
        return ChatClient.builder(chatModel)
            .defaultAdvisors(advisor)
            .build();
    }
}
```

### Usage

```java
@Service
public class AIService {
    
    private final ChatClient chatClient;
    
    public String askAI(String userMessage) {
        // Data is automatically obfuscated before sending to AI
        // and restored in the response
        return chatClient.prompt()
            .user(userMessage)
            // Use the same value as ChatMemory when tags must survive multiple turns.
            .advisors(a -> a.param(PromptShieldAdvisor.SCOPE_ID_KEY, "user-42"))
            .call()
            .content();
    }
}
```

## Supported Data Types

### PII (Personally Identifiable Information)

| Type | Pattern | Example |
|------|---------|---------|
| DNI | 8 digits (optionally `12.345.678`) + optional `-` + letter, checksum | 12345678Z, 12.345.678-Z |
| NIE | `X`/`Y`/`Z` + 7 digits + letter, optional hyphens, checksum | X1234567L, X-1234567-L |
| EMAIL | `[\w.+-]+@[\w.-]+\.\w{2,}` | user@email.com |
| N_CUENTA | Spanish IBAN, compact or in groups of four, checksum | ES91 2100 0418 4502 0005 1332 |
| IBAN | Any other IBAN, compact or grouped, checksum | DE89 3704 0044 0532 0130 00 |
| TELEFONO | Spanish phone with spaces, hyphens or `+34`/`0034`, or any 9 digits | +34 612 34 56 78 |
| CODIGO_POSTAL | `\b\d{5}\b` | 28001 |

Types are applied in this order; when two matches overlap, the earlier type wins.

### Service Keys (API Keys)

| Type | Pattern | Example |
|------|---------|---------|
| OPENAI_API_KEY | `sk-` (optionally `proj-`, `svcacct-`, `admin-`) + 20 or more characters including a digit | sk-proj-abc123... |
| ANTHROPIC_API_KEY | `sk-ant-[A-Za-z0-9_-]{20,}` | sk-ant-api03-... |
| GOOGLE_AI_KEY | `AIza[A-Za-z0-9_-]{35}` | AIzaSyA1b2C3d4... |
| HUGGINGFACE_TOKEN | `hf_[A-Za-z0-9]{34}` | hf_abc123... |
| AWS_ACCESS_KEY | `AKIA[0-9A-Z]{16}` | AKIAIOSFODNN7... |
| AZURE_STORAGE_KEY | `[A-Za-z0-9+/]{88}==` | AbCdEf...== |
| DIGITALOCEAN_TOKEN | `dop_v1_[a-f0-9]{64}` | dop_v1_abc... |
| GITHUB_TOKEN | `gh[psoa]_[A-Za-z0-9]{36}` | ghp_abc123... |
| GITLAB_TOKEN | `glpat-[A-Za-z0-9_-]{20,}` | glpat-abc123... |
| NPM_TOKEN | `npm_[A-Za-z0-9]{36}` | npm_abc123... |
| PYPI_TOKEN | `pypi-[A-Za-z0-9_-]{60,}` | pypi-AgEIcHl... |
| SLACK_TOKEN | `xox[bpsa]-[0-9]{10,13}-[a-zA-Z0-9-]{20,}` | xoxb-123456789012-... |
| TWILIO_API_KEY | `SK[0-9a-fA-F]{32}` | SKabcdef0123... |
| SENDGRID_KEY | `SG\.[A-Za-z0-9_-]{22}\.[A-Za-z0-9_-]{43}` | SG.abc123...XYZ |
| MAILGUN_API_KEY | `key-[0-9a-zA-Z]{32}` | key-abcdef0123... |
| STRIPE_KEY | `(?:sk\|pk\|rk)_(?:live\|test)_[0-9a-zA-Z]{24,}` | sk_live_abc123... |

> Los service keys se pueden desactivar con `service-keys-enabled: false` en la configuración.

## Custom Types

```java
ObfuscationConfig config = new ObfuscationConfig();
config.addCustomType("MATRICULA", "\\b\\d{4}[A-Z]{3}\\b");
config.addCustomType("REFERENCIA_PEDIDO", "\\bPED-\\d{6}\\b");

ObfuscationEngine engine = new ObfuscationEngine(config);
```

Names must match `[A-Z][A-Z0-9_]*`. Custom types run after the built-in ones, in the
order they were added.

## Tag Format

Default format: `~REDACTED:TYPE#HASH~`

Examples:
- `~REDACTED:DNI#1c9f96~`
- `~REDACTED:EMAIL#e5a3b2~`
- `~REDACTED:TELEFONO#d500e1~`

### Custom Format

```java
ObfuscationConfig config = new ObfuscationConfig();
config.setRedactedPrefix("OBFUSCADO");
config.setTagSeparator("_");
config.setTagOpen("[[");
config.setTagClose("]]");

// Result: [[OBFUSCADO:DNI_1c9f96]]
```

## Architecture

```
prompt-shield/
├── prompt-shield-core/              # Core library
│   ├── engine/                      # ObfuscationEngine
│   ├── model/                       # ObfuscationTag, ObfuscationConfig
│   ├── registry/                    # DataTypeRegistry, SensitiveDataType
│   ├── hash/                        # HashGenerator
│   ├── storage/                     # StorageService, InMemoryStorageService
│   └── exception/                   # Custom exceptions
│
├── prompt-shield-spring-boot-starter/  # Spring Boot auto-configuration
│   ├── autoconfigure/               # Auto-configuration classes
│   ├── annotation/                  # @Obfuscate annotation
│   └── interceptor/                 # AOP interceptor
│
├── prompt-shield-spring-ai-advisor/    # Spring AI Advisor
│   ├── advisor/                     # PromptShieldAdvisor
│   └── autoconfigure/               # Auto-configuration
│
├── prompt-shield-api-proxy/            # OpenAI-compatible API Proxy
│   ├── controller/                  # ProxyController
│   ├── client/                      # AiProviderClient
│   ├── service/                     # ProxyService
│   └── autoconfigure/               # Auto-configuration
│
└── prompt-shield-example/           # Example project
```

## API Proxy

An OpenAI-compatible API proxy that automatically obfuscates PII before forwarding requests to any AI provider.

### Features

- **OpenAI-compatible**: Works with any provider that supports OpenAI API format
- **Transparent proxy**: Forwards the raw request, so every OpenAI field is kept
- **Access control**: Proxy tokens, or the client's own provider key
- **Automatic obfuscation**: PII is obfuscated before sending to the AI provider
- **Automatic restoration**: Original values are restored in the response
- **Tool call support**: Tool call arguments are also restored
- **Streaming support**: Server-Sent Events (SSE) for streaming responses
- **Docker ready**: Dockerfile and docker-compose.yml included

### Quick Start

```bash
# Pull from Docker Hub
docker pull ploybot/prompt-shield-api-proxy:latest

# Run with Docker (clients send their own provider key)
docker run -p 8080:8080 \
  -e PROMPT_SHIELD_PROXY_PROVIDER_BASE_URL=https://api.openai.com \
  ploybot/prompt-shield-api-proxy:latest

# Or keep the provider key on the server and give clients proxy tokens
docker run -p 8080:8080 \
  -e PROMPT_SHIELD_PROXY_PROVIDER_API_KEY=sk-server-key \
  -e PROMPT_SHIELD_PROXY_CLIENT_TOKENS=token-a,token-b \
  ploybot/prompt-shield-api-proxy:latest

# Or use docker-compose
cd prompt-shield-api-proxy
docker-compose up
```

### How It Works

The proxy is **transparent** - it doesn't store any API keys or models. The client sends everything:

1. **API Key**: Client sends `Authorization: Bearer sk-xxx` header
2. **Model**: Client sends `model` field in request body
3. **Proxy**: Forwards request to provider with client's API key
4. **Obfuscation**: PII is obfuscated before sending to provider
5. **Restoration**: Original values are restored in the response

### Usage

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-your-api-key" \
  -d '{
    "model": "gpt-4o-mini",
    "messages": [
      {"role": "user", "content": "Este es mi DNI 12345678Z y este es mi email juan@test.com, ponlo en un JSON"}
    ]
  }'
```

**Response:**
```json
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": "{\"dni\": \"12345678Z\", \"email\": \"juan@test.com\"}"
    }
  }]
}
```

> **Nota**: Durante el procesamiento, el DNI y email se ofuscan automáticamente antes de enviar al proveedor de IA. La IA nunca ve los datos reales. Los valores se restauran en la respuesta final.

### With Ollama (Local)

```bash
# Start Ollama
ollama serve

# Use proxy with Ollama
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer not-needed" \
  -d '{
    "model": "llama3",
    "messages": [
      {"role": "user", "content": "Crea un JSON con mi DNI 12345678Z y mi email juan@test.com"}
    ]
  }'
```

### Configuration (Optional)

```yaml
# application.yml
prompt-shield:
  proxy:
    enabled: true                        # off by default when used as a library
    base-path: /v1
    inject-system-prompt: true
    restore-on-response: true
    client-tokens: []                    # required bearer tokens; server key used upstream
    allow-unauthenticated: false         # let anonymous requests use the server key
    provider:
      base-url: https://api.openai.com
      api-key:                           # server-side provider key
```

Without `client-tokens`, a client's own `Authorization` header is forwarded. A request
without one is rejected when a provider key is configured, unless
`allow-unauthenticated` is set.

### Supported Providers

Any provider that implements the OpenAI API format:

- OpenAI (GPT-4, GPT-3.5, etc.)
- Azure OpenAI
- Ollama (with OpenAI compatibility)
- LM Studio
- vLLM
- And many more...

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/ploybot-ai/prompt-shield/issues)
- **Documentation**: [GitHub Wiki](https://github.com/ploybot-ai/prompt-shield/wiki)
- **Tutorial**: [Prompt Shield Tutorial (ES/EN)](https://learning-ai.eu/prompt-shield-tutorial.html)

## Acknowledgments

- Built with [Spring AI](https://spring.io/projects/spring-ai)
- Part of the [Spring AI Community](https://github.com/spring-ai-community)

## Company

Developed by [Ploybot Intelligence S.L.](https://ploybot.com)
