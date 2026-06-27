# Prompt Shield

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0-green.svg)](https://spring.io/projects/spring-ai)

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

- **Obfuscate sensitive data**: DNI, NIE, email, phone, and custom types
- **Bidirectional**: Obfuscate prompts and restore original values in responses
- **JSON support**: Obfuscate objects with type preservation
- **Spring Boot Starter**: Auto-configuration with sensible defaults
- **Spring AI Advisor**: Seamless integration with Spring AI
- **Configurable**: Custom prefixes, separators, and data types
- **Multiple storage backends**: In-memory, Redis, JPA/H2

## Quick Start

### Maven

```xml
<dependency>
    <groupId>com.ploybot</groupId>
    <artifactId>prompt-shield-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Spring Boot Starter (optional) -->
<dependency>
    <groupId>com.ploybot</groupId>
    <artifactId>prompt-shield-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Spring AI Advisor (optional) -->
<dependency>
    <groupId>com.ploybot</groupId>
    <artifactId>prompt-shield-spring-ai-advisor</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.ploybot:prompt-shield-core:1.0.0'
implementation 'com.ploybot:prompt-shield-spring-boot-starter:1.0.0'
implementation 'com.ploybot:prompt-shield-spring-ai-advisor:1.0.0'
```

## Usage

### Basic Usage

```java
import com.ploybot.promptshield.engine.ObfuscationEngine;

ObfuscationEngine engine = new ObfuscationEngine();

// Obfuscate text
String obfuscated = engine.ofuscar("Mi DNI es 12345678Z y mi email es user@email.com");
// Result: "Mi DNI es ~REDACTED:DNI#1c9f96~ y mi email es ~REDACTED:EMAIL#e5a3b2~"

// Restore original values
String restored = engine.restaurar(obfuscated);
// Result: "Mi DNI es 12345678Z y mi email es user@email.com"
```

### Obfuscate Specific Types

```java
// Obfuscate only DNI
String obfuscated = engine.ofuscar("DNI: 12345678Z, Tel: 612345678", "DNI");
// Result: "DNI: ~REDACTED:DNI#1c9f96~, Tel: 612345678"
```

### JSON Obfuscation

```java
// Obfuscate JSON string
String json = "{\"dni\":\"12345678Z\",\"email\":\"user@email.com\"}";
String obfuscated = engine.ofuscarObjetoJson(json);
// Result: "{\"dni\":\"~REDACTED:DNI#1c9f96~\",\"email\":\"~REDACTED:EMAIL#e5a3b2~\"}"

// Restore JSON
String restored = engine.restaurarObjetoJson(obfuscated);
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
  storage-type: memory
  redacted-prefix: "REDACTED"  # Change to "OBFUSCADO", "OCULTO", etc.
  tag-separator: "#"            # Change to "_", "-", etc.
  service-keys-enabled: true   # false to disable service key detection
  custom-types:
    CODIGO_POSTAL:
      pattern: "\d{5}"
    N_CUENTA:
      pattern: "ES\d{22}"
```

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
}
```

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
    <version>1.0.0</version>
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
    <version>1.0.0</version>
</dependency>
```

```yaml
prompt-shield:
  storage:
    type: jpa
  jpa:
    ttl-hours: 24
    cleanup-enabled: true

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
```

### System Prompt

The advisor automatically injects a system prompt that instructs the AI to preserve the obfuscated placeholders. This ensures the AI doesn't modify, remove, or try to "fix" the `~REDACTED:TYPE#HASH~` markers.

```java
// Available system prompts
PromptShieldAdvisor.SYSTEM_PROMPT_EN  // English
PromptShieldAdvisor.SYSTEM_PROMPT_ES  // Spanish
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
            .call()
            .content();
    }
}
```

### Configuration

```yaml
prompt-shield:
  advisor:
    enabled: true
    order: 0
    restore-on-response: true
    hash-algorithm: SHA-256
    hash-length: 6
    redacted-prefix: "REDACTED"
    tag-separator: "#"
```

## Supported Data Types

### PII (Personally Identifiable Information)

| Type | Pattern | Example |
|------|---------|---------|
| DNI | `\d{8}[A-Za-z]` | 12345678Z |
| NIE | `[XYZxyz]\d{7}[A-Za-z]` | X1234567A |
| EMAIL | `[\w.+-]+@[\\w.-]+\\.\\w{2,}` | user@email.com |
| TELEFONO | `\b\d{9}\b` | 612345678 |
| CODIGO_POSTAL | `\b\d{5}\b` | 28001 |
| N_CUENTA | `ES\d{22}` | ES1234567890123456789012 |

### Service Keys (API Keys)

| Type | Pattern | Example |
|------|---------|---------|
| OPENAI_API_KEY | `sk-proj-[A-Za-z0-9_-]{20,}` | sk-proj-abc123... |
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
config.addCustomType("CODIGO_POSTAL", "\\d{5}");
config.addCustomType("N_CUENTA", "ES\\d{22}");

ObfuscationEngine engine = new ObfuscationEngine(config);
```

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
│   ├── model/                       # ChatCompletionRequest/Response
│   └── autoconfigure/               # Auto-configuration
│
└── prompt-shield-example/           # Example project
```

## API Proxy

An OpenAI-compatible API proxy that automatically obfuscates PII before forwarding requests to any AI provider.

### Features

- **OpenAI-compatible**: Works with any provider that supports OpenAI API format
- **Transparent proxy**: Client sends API key and model, proxy forwards to provider
- **Automatic obfuscation**: PII is obfuscated before sending to the AI provider
- **Automatic restoration**: Original values are restored in the response
- **Tool call support**: Tool call arguments are also restored
- **Streaming support**: Server-Sent Events (SSE) for streaming responses
- **Docker ready**: Dockerfile and docker-compose.yml included

### Quick Start

```bash
# Pull from Docker Hub
docker pull ploybot/prompt-shield-api-proxy:latest

# Run with Docker (transparent mode - no API key needed)
docker run -p 8080:8080 \
  -e PROMPT_SHIELD_PROXY_PROVIDER_BASE_URL=https://api.openai.com \
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

If you want to set a default API key or base URL:

```yaml
# application.yml
prompt-shield:
  proxy:
    enabled: true
    base-path: /v1
    provider:
      base-url: https://api.openai.com  # Default provider
      api-key:                           # Optional default key
      model: gpt-4o-mini                 # Optional default model
    obfuscation:
      inject-system-prompt: true
      restore-on-response: true
```

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
