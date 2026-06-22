# Prompt Shield

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3+-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0+-green.svg)](https://spring.io/projects/spring-ai)

Library for obfuscating sensitive data in AI prompts. Protect PII (Personally Identifiable Information) before sending to LLMs.

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
    <version>1.0.0-beta.1</version>
</dependency>

<!-- Spring Boot Starter (optional) -->
<dependency>
    <groupId>com.ploybot</groupId>
    <artifactId>prompt-shield-spring-boot-starter</artifactId>
    <version>1.0.0-beta.1</version>
</dependency>

<!-- Spring AI Advisor (optional) -->
<dependency>
    <groupId>com.ploybot</groupId>
    <artifactId>prompt-shield-spring-ai-advisor</artifactId>
    <version>1.0.0-beta.1</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.ploybot:prompt-shield-core:1.0.0-beta.1'
implementation 'com.ploybot:prompt-shield-spring-boot-starter:1.0.0-beta.1'
implementation 'com.ploybot:prompt-shield-spring-ai-advisor:1.0.0-beta.1'
```

> **Note**: For the latest development version, use `1.0.1-SNAPSHOT`

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
obfuscador:
  enabled: true
  hash-algorithm: SHA-256
  hash-length: 6
  storage-type: memory
  redacted-prefix: "REDACTED"  # Change to "OBFUSCADO", "OCULTO", etc.
  tag-separator: "#"            # Change to "_", "-", etc.
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
obfuscador:
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
obfuscador:
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
obfuscador:
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

| Type | Pattern | Example |
|------|---------|---------|
| DNI | `\d{8}[A-Za-z]` | 12345678Z |
| NIE | `[XYZxyz]\d{7}[A-Za-z]` | X1234567A |
| EMAIL | `[\w.+-]+@[\w.-]+\.\w{2,}` | user@email.com |
| TELEFONO | `\d{9}` | 612345678 |
| CODIGO_POSTAL | `\d{5}` | 28001 |
| N_CUENTA | `ES\d{22}` | ES1234567890123456789012 |

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
- **Automatic obfuscation**: PII is obfuscated before sending to the AI provider
- **Automatic restoration**: Original values are restored in the response
- **Tool call support**: Tool call arguments are also restored
- **Streaming support**: Server-Sent Events (SSE) for streaming responses
- **Docker ready**: Dockerfile and docker-compose.yml included

### Quick Start

```bash
# Run with Docker
docker run -p 8080:8080 \
  -e PROMPT_SHIELD_PROXY_PROVIDER_BASE_URL=https://api.openai.com \
  -e PROMPT_SHIELD_PROXY_PROVIDER_API_KEY=sk-xxx \
  ploybot/prompt-shield-api-proxy:latest

# Or use docker-compose
cd prompt-shield-api-proxy
docker-compose up
```

### Configuration

```yaml
prompt-shield:
  proxy:
    enabled: true
    base-path: /v1
    provider:
      base-url: https://api.openai.com
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
    obfuscation:
      enabled: true
      inject-system-prompt: true
      restore-on-response: true
```

### Usage

```bash
# Chat completion
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o-mini",
    "messages": [
      {"role": "user", "content": "Mi DNI es 12345678Z y mi email es juan@test.com"}
    ]
  }'

# Response (restored)
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": "El DNI 12345678Z es válido. El email juan@test.com está correcto."
    }
  }]
}
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
