# Chat Streaming AI

Aplicação Spring Boot (Spring AI) com streaming via SSE (`/api/chat/stream`), suporte a múltiplos provedores (**OpenAI**, **Google Gemini** e **Ollama**) e memória de conversação.

## Pré-requisitos

- **Java 25**
- **Maven 3.9+**
- **Kiri** (container runtime compatível com Docker)

## Executar os testes

```powershell
# Caso o JDK 25 não seja o padrão no PATH (PowerShell)
$env:JAVA_HOME = "C:\Users\deivi\Downloads\OpenJDK25U-jdk_x64_windows_hotspot_25.0.5.1_2\jdk-25.0.5.1+2"
mvn test
```

### Estrutura de testes

- **Unitários**:
  - `src/test/java/com/deividnn/provider/AiProviderTest.java`
  - `src/test/java/com/deividnn/provider/ChatClientResolverTest.java`
  - `src/test/java/com/deividnn/service/ChatStreamingServiceTest.java`
  - `src/test/java/com/deividnn/controller/ChatControllerTest.java`
- **Integração**:
  - `src/test/java/com/deividnn/ChatStreamingIntegrationTest.java`

## Subir o projeto com Kiri

### Opção 1: Kiri Compose (sobe a aplicação + Ollama)

```bash
# Subir em segundo plano fazendo o build da imagem
kiri compose up --build -d

# Acompanhar os logs
kiri compose logs -f chat-streaming-ai

# Parar e remover os containers
kiri compose down
```

### Opção 2: Build e Run direto com Kiri

```bash
# 1. Construir a imagem a partir do Dockerfile
kiri build -t chat-streaming-ai .

# 2. Subir o container
kiri run -d --name chat-streaming-ai -p 8080:8080 \
  -e OPENAI_API_KEY="sua-chave-openai" \
  -e GEMINI_API_KEY="sua-chave-gemini" \
  -e OLLAMA_BASE_URL="http://host.docker.internal:11434" \
  chat-streaming-ai

# Ver logs
kiri logs -f chat-streaming-ai

# Parar e remover
kiri stop chat-streaming-ai && kiri rm chat-streaming-ai
```

## Exemplo de chamada SSE

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"conv-1","message":"Olá, tudo bem?","provider":"ollama"}'
```
