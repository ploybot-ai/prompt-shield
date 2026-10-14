# Prompt Shield - API Proxy

OpenAI-compatible API proxy with automatic PII obfuscation. Protect sensitive data before sending to any AI provider.

## Features

- **Transparent proxy**: Forwards the raw request, so every OpenAI field (`top_p`, `response_format`, images...) is kept
- **Access control**: Proxy tokens, or the client's own provider key
- **Automatic obfuscation**: DNI, NIE, email, phone and custom types are obfuscated
- **Automatic restoration**: Original values are restored in the AI response
- **Tool call support**: Tool call arguments and tool results are obfuscated; arguments are restored
- **Streaming support**: Server-Sent Events, including tags and tool arguments split across chunks
- **Any provider**: Works with OpenAI, Ollama, Azure, LM Studio, vLLM, etc.

## Quick Start

### Docker

```bash
docker pull ploybot/prompt-shield-api-proxy:latest

docker run -p 8080:8080 \
  -e PROMPT_SHIELD_PROXY_PROVIDER_BASE_URL=https://api.openai.com \
  ploybot/prompt-shield-api-proxy:latest
```

### Docker Compose

```bash
cp .env.example .env
# Edit .env with your provider URL

docker-compose up
```

### Local

```bash
# Build
mvn clean package -DskipTests -pl prompt-shield-api-proxy -am

# Run
java -jar prompt-shield-api-proxy/target/prompt-shield-api-proxy-*.jar --server.port=8081
```

## How It Works

The proxy forwards the client's request as is, except for the obfuscated text. Each
request gets its own scope, released once the response has been restored, so a tag can
never be restored into another client's response.

```
Client → Proxy → Provider
  │        │        │
  │   Obfuscate PII
  │        │        │
  │        └──→─────┘
  │              │
  │   Restore PII
  │        │
  ←────────┘
```

1. Client sends the request with `Authorization: Bearer <key or proxy token>`
2. Proxy checks the token (see [Access control](#access-control))
3. Proxy obfuscates PII in messages (DNI, email, phone, etc.)
4. Proxy forwards the request to the provider
5. Provider responds with obfuscated placeholders
6. Proxy restores original values in response
7. Client receives response with real data

## Usage

### Chat Completion

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

> **Nota**: El DNI y email se ofuscan automáticamente antes de enviar al proveedor. La IA nunca ve los datos reales. Los valores se restauran en la respuesta.

### Streaming

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-your-api-key" \
  -d '{
    "model": "gpt-4o-mini",
    "stream": true,
    "messages": [
      {"role": "user", "content": "Valida mi DNI 12345678Z"}
    ]
  }'
```

### List Models

```bash
curl http://localhost:8080/v1/models \
  -H "Authorization: Bearer sk-your-api-key"
```

### Health Check

```bash
curl http://localhost:8080/v1/health
```

## Supported Data Types

### PII (Personally Identifiable Information)

| Type | Pattern | Example |
|------|---------|---------|
| DNI | 8 digits (optionally `12.345.678`) + optional `-` + letter, checksum | 12345678Z |
| NIE | `X`/`Y`/`Z` + 7 digits + letter, optional hyphens, checksum | X1234567L |
| EMAIL | `[\w.+-]+@[\w.-]+\.\w{2,}` | user@email.com |
| N_CUENTA | Spanish IBAN, compact or grouped, checksum | ES91 2100 0418 4502 0005 1332 |
| IBAN | Any other IBAN, checksum | DE89 3704 0044 0532 0130 00 |
| TELEFONO | Spanish phone with spaces, hyphens or `+34`, or any 9 digits | +34 612 34 56 78 |
| CODIGO_POSTAL | `\b\d{5}\b` | 28001 |

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

## Supported Providers

Any provider implementing the OpenAI API format:

| Provider | Base URL |
|----------|----------|
| OpenAI | `https://api.openai.com` |
| Ollama (local) | `http://localhost:11434` |
| LM Studio | `http://localhost:1234` |
| vLLM | `http://localhost:8000` |
| Azure OpenAI | `https://your-resource.openai.azure.com` |

### Example with Ollama

```bash
# Start Ollama
ollama serve

# Use proxy
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

## Prompt injection

User messages, `tool`/`function` messages and the `tools` definitions of each request are
scanned before obfuscation (see the main README, "Prompt Injection Detection"). Warnings
name the rules that fired, never the text. Detection is a heuristic layer, not a
guarantee: keep confirmation for sensitive tool calls on the client side.

## Access control

| Configuration | Who can use the proxy | Key sent to the provider |
|---------------|-----------------------|--------------------------|
| `client-tokens` set | Clients sending one of the tokens | `provider.api-key` |
| No tokens, client sends `Authorization` | Anyone | The client's own header |
| No tokens, no header, `provider.api-key` set | Nobody (401) unless `allow-unauthenticated: true` | `provider.api-key` |
| No tokens, no header, no provider key | Anyone | None (local providers) |

Never enable `allow-unauthenticated` on a proxy reachable from outside a trusted network:
anyone could spend the server key.

## Configuration

All properties are optional. The standalone proxy enables its endpoints; when the module
is added to another application they stay off until `prompt-shield.proxy.enabled=true`.

```yaml
# application.yml
prompt-shield:
  service-keys-enabled: true   # false to disable service key detection
  storage:
    memory:
      ttl-hours: 24
  proxy:
    enabled: true
    base-path: /v1
    inject-system-prompt: true
    restore-on-response: true
    client-tokens: []
    allow-unauthenticated: false
    provider:
      base-url: https://api.openai.com
      api-key:                  # server-side key, used with client-tokens
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PROMPT_SHIELD_PROXY_PROVIDER_BASE_URL` | Provider URL | `https://api.openai.com` |
| `PROMPT_SHIELD_PROXY_PROVIDER_API_KEY` | Server-side provider key | - |
| `PROMPT_SHIELD_PROXY_CLIENT_TOKENS` | Comma-separated proxy tokens | - |
| `PROMPT_SHIELD_PROXY_ALLOW_UNAUTHENTICATED` | Anonymous use of the server key | `false` |
| `PROMPT_SHIELD_PROXY_ENABLED` | Enable proxy endpoints | `true` (standalone) |
| `PROMPT_SHIELD_PROXY_INJECT_SYSTEM_PROMPT` | Inject obfuscation instructions | `true` |
| `PROMPT_SHIELD_PROXY_RESTORE_ON_RESPONSE` | Restore values in response | `true` |
| `PROMPT_SHIELD_SERVICE_KEYS_ENABLED` | Enable service key detection | `true` |
| `PROMPT_SHIELD_INJECTION_ENABLED` | Scan user messages, tool messages and `tools` definitions for prompt injection | `true` |
| `PROMPT_SHIELD_INJECTION_USER_ACTION` | `monitor` or `block` (answers `400`, code `prompt_injection_detected`) | `monitor` |
| `PROMPT_SHIELD_INJECTION_TOOL_ACTION` | `monitor`, `block` or `quarantine` (tool message replaced by a notice) | `monitor` |
| `PROMPT_SHIELD_INJECTION_PROTECT_ON_INJECTION` | After a flagged tool message, keep placeholders in tool calls and URLs of the response | `true` |
| `SERVER_PORT` | Server port | `8080` |

## Docker Compose Examples

### OpenAI

```yaml
services:
  proxy:
    image: ploybot/prompt-shield-api-proxy:latest
    ports:
      - "8080:8080"
    environment:
      - PROMPT_SHIELD_PROXY_PROVIDER_BASE_URL=https://api.openai.com
```

### Ollama (Local)

```yaml
services:
  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama

  proxy:
    image: ploybot/prompt-shield-api-proxy:latest
    ports:
      - "8080:8080"
    environment:
      - PROMPT_SHIELD_PROXY_PROVIDER_BASE_URL=http://ollama:11434
    depends_on:
      - ollama

volumes:
  ollama_data:
```

## Building

```bash
# Build JAR
mvn clean package -DskipTests -pl prompt-shield-api-proxy -am

# Build Docker image
docker build -f Dockerfile -t ploybot/prompt-shield-proxy:latest ..
```

## License

Apache License 2.0
