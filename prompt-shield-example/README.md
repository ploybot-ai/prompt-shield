# Prompt Shield - Spring AI Example

Example project demonstrating how to use Prompt Shield with Spring AI to automatically protect sensitive data in AI conversations.

## How it works

1. User sends a message with sensitive data (DNI, email, phone, etc.)
2. `PromptShieldAdvisor` automatically:
   - Obfuscates sensitive data before sending to AI: `Mi DNI es 12345678A` → `Mi DNI es [[REDACTED:DNI#a1b2c3]]`
   - Injects a system prompt telling the AI to preserve placeholders
   - Restores original values in the AI response
3. User gets a clean response with original sensitive data restored

## Configuration

### Environment Variables

```bash
export OPENAI_API_KEY=your-api-key-here
```

### application.yml

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini

prompt-shield:
  advisor:
    enabled: true
    inject-system-prompt: true
    restore-on-response: true
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/ai/chat` | Send a message to AI with automatic obfuscation |
| POST | `/api/ai/chat-with-context` | Send a message with conversation history |
| GET | `/api/ai/health` | Health check |

## Run the example

```bash
# From the root project
mvn clean install -DskipTests
cd prompt-shield-example

# Set your OpenAI API key
export OPENAI_API_KEY=your-key

# Run
mvn spring-boot:run
```

## Test it

```bash
# Send a message with sensitive data
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Mi nombre es Juan y mi DNI es 12345678A. Mi email es juan@example.com, escribe un json con esos datos"}'

# The AI will receive the obfuscated version and return a response
# with the original data restored
```

## What happens behind the scenes

1. **Input**: `"Mi nombre es Juan y mi DNI es 12345678A. Mi email es juan@example.com"`
2. **After obfuscation** (sent to AI): `"Mi nombre es Juan y mi DNI es ~REDACTED:DNI#a1b2c3~. Mi email es ~REDACTED:EMAIL#d4e5f6~"`
3. **System prompt injected**: Tells AI to preserve `~REDACTED:TYPE#HASH~` placeholders
4. **AI response**: `"El DNI ~REDACTED:DNI#a1b2c3~ pertenece a Juan. El email ~REDACTED:EMAIL#d4e5f6~ es válido."`
5. **After restoration** (returned to user): `"El DNI 12345678A pertenece a Juan. El email juan@example.com es válido."`
