# Prompt Shield - API Proxy

OpenAI-compatible API proxy with automatic PII obfuscation. Protect sensitive data before sending to any AI provider.

## Features

- **Transparent proxy**: Client sends API key and model, proxy forwards to provider
- **Automatic obfuscation**: DNI, NIE, email, phone and custom types are obfuscated
- **Automatic restoration**: Original values are restored in the AI response
- **Tool call support**: Tool call arguments are also obfuscated/restored
- **Streaming support**: Server-Sent Events (SSE) for streaming responses
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
java -jar target/prompt-shield-api-proxy-1.0.1-SNAPSHOT.jar --server.port=8081
```

## How It Works

The proxy is **transparent** - it doesn't store API keys or models. The client sends everything:

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

1. Client sends request with `Authorization: Bearer sk-xxx` header
2. Proxy extracts API key from header
3. Proxy obfuscates PII in messages (DNI, email, phone, etc.)
4. Proxy forwards request to provider with client's API key
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
      {"role": "user", "content": "Mi DNI es 12345678Z y mi email es juan@test.com"}
    ]
  }'
```

**Response:**
```json
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": "El DNI 12345678Z es válido. El email juan@test.com está correcto."
    }
  }]
}
```

### Streaming

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-your-api-key" \
  -d '{
    "model": "gpt-4o-mini",
    "stream": true,
    "messages": [
      {"role": "user", "content": "Mi DNI es 12345678Z"}
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

| Type | Pattern | Example |
|------|---------|---------|
| DNI | `\d{8}[A-Za-z]` | 12345678Z |
| NIE | `[XYZxyz]\d{7}[A-Za-z]` | X1234567A |
| EMAIL | `[\w.+-]+@[\w.-]+\.\w{2,}` | user@email.com |
| TELEFONO | `\d{9}` | 612345678 |

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
      {"role": "user", "content": "Mi DNI es 12345678Z"}
    ]
  }'
```

## Configuration

All properties are optional. The proxy works with zero configuration.

```yaml
# application.yml
prompt-shield:
  proxy:
    enabled: true
    base-path: /v1
    inject-system-prompt: true
    restore-on-response: true
    provider:
      base-url: https://api.openai.com  # Default provider
      api-key:                           # Optional default key
      model: gpt-4o-mini                 # Optional default model
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PROMPT_SHIELD_PROXY_PROVIDER_BASE_URL` | Provider URL | `https://api.openai.com` |
| `PROMPT_SHIELD_PROXY_PROVIDER_API_KEY` | Default API key | - |
| `PROMPT_SHIELD_PROXY_PROVIDER_MODEL` | Default model | `gpt-4o-mini` |
| `PROMPT_SHIELD_PROXY_ENABLED` | Enable proxy | `true` |
| `PROMPT_SHIELD_PROXY_INJECT_SYSTEM_PROMPT` | Inject obfuscation instructions | `true` |
| `PROMPT_SHIELD_PROXY_RESTORE_ON_RESPONSE` | Restore values in response | `true` |
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
