# Chat Streaming AI

Aplicação Spring Boot (Spring AI) com streaming via SSE (`/api/chat/stream`), suporte a múltiplos provedores (**OpenAI**, **Google Gemini** e **Ollama**) e memória de conversação.

## Tecnologias e Versões das Bibliotecas

| Categoria | Biblioteca / Tecnologia | Versão | Uso no Projeto |
| :--- | :--- | :--- | :--- |
| Runtime | **Java (JDK / Eclipse Temurin)** | `25` | Linguagem base e Virtual Threads para streaming SSE |
| Build | **Apache Maven** | `3.9+` | Gerenciamento de dependências e empacotamento |
| Framework | **Spring Boot** (`spring-boot-starter-parent`) | `4.2.0` | Base da aplicação e autoconfiguração |
| Framework | **Spring Framework** (`spring-core`, `spring-webmvc`) | `7.1.0` | Core DI, REST controllers e `SseEmitter` |
| Web | **spring-boot-starter-webmvc** | `4.2.0` | Servidor embutido (Tomcat), REST API e SSE |
| Validação | **spring-boot-starter-validation** (`hibernate-validator`) | `4.2.0` (`9.1.6.Final`) | Bean Validation (`@Valid`, `@NotBlank`) |
| IA (BOM) | **Spring AI** (`spring-ai-bom`) | `2.1.0` | Alinhamento de versões dos starters de IA |
| IA (OpenAI) | **spring-ai-starter-model-openai** | `2.1.0` | Integração com modelos OpenAI (`gpt-4o-mini`) |
| IA (Gemini) | **spring-ai-starter-model-google-genai** | `2.1.0` | Integração com modelos Google Gemini (`gemini-2.5-flash`) |
| IA (Ollama) | **spring-ai-starter-model-ollama** | `2.1.0` | Integração com modelos locais Ollama (`qwen2.5:0.5b`) |
| Reativo | **Project Reactor** (`reactor-core`) | `3.9.0` | Fluxo reativo (`Flux<String>`) no streaming de tokens |
| Testes | **spring-boot-starter-test** | `4.2.0` | Infraestrutura de testes (`@SpringBootTest`, `@MockitoBean`) |
| Testes Web | **spring-boot-starter-webmvc-test** | `4.2.0` | `@WebMvcTest`, `@AutoConfigureMockMvc` e `MockMvc` |
| Testes | **JUnit Jupiter** (`junit-jupiter`) | `6.1.3` | Motor e anotações de testes unitários e parametrizados |
| Testes | **Mockito** (`mockito-core`) | `5.24.0` | Mocks de provedores, `ChatClient` e serviços |
| Testes | **AssertJ** (`assertj-core`) | `3.27.7` | Asserções fluentes nos testes |

## Estrutura do Projeto

```text
chat-streaming-ai/
├── .dockerignore                                        # Arquivos ignorados no contexto de build do container
├── .gitattributes                                       # Regras de atributos e normalização do Git
├── .gitignore                                           # Arquivos/pastas ignorados pelo Git (inclui target/)
├── Dockerfile                                           # Build multi-stage: Maven/Java 25 + Ollama + JRE 25
├── docker-entrypoint.sh                                 # Inicializa o Ollama, baixa o modelo padrão e sobe o Spring Boot
├── pom.xml                                              # Dependências Maven (Spring Boot 4.2.0 + Spring AI 2.1.0 + Java 25)
├── README.md                                            # Documentação do projeto
├── scripts/
│   ├── setup-linux.sh                                   # Instala e configura JDK 25 + Kiri no Linux
│   └── setup-windows.ps1                                # Instala e configura JDK 25 + Kiri no Windows
└── src/
    ├── main/
    │   ├── java/com/deividnn/
    │   │   ├── ChatStreamingApplication.java            # Ponto de entrada da aplicação Spring Boot
    │   │   ├── config/
    │   │   │   └── ChatAiConfig.java                    # Configuração de ChatMemory, ChatClients e modelos por provedor
    │   │   ├── controller/
    │   │   │   └── ChatController.java                  # Endpoints REST (/api/chat/stream, /{id}/history, DELETE /{id})
    │   │   ├── dto/
    │   │   │   ├── ChatRequest.java                     # Record de entrada validado (@NotBlank)
    │   │   │   └── ChatStreamChunk.java                 # Record serializado nos eventos SSE (message e done)
    │   │   ├── provider/
    │   │   │   ├── AiProvider.java                      # Enum de provedores suportados (OPENAI, GEMINI, OLLAMA) e aliases
    │   │   │   └── ChatClientResolver.java              # Resolve o ChatClient e o modelo ativo por provedor
    │   │   └── service/
    │   │       └── ChatStreamingService.java            # Orquestra streaming SSE com Virtual Threads, memória e logs
    │   └── resources/
    │       └── application.yml                          # Configurações da aplicação e dos provedores Spring AI
    └── test/
        └── java/com/deividnn/
            ├── ChatStreamingIntegrationTest.java        # Testes de integração end-to-end (@SpringBootTest + MockMvc)
            ├── controller/
            │   └── ChatControllerTest.java              # Testes unitários da camada web (@WebMvcTest)
            ├── provider/
            │   ├── AiProviderTest.java                  # Testes unitários de resolução de aliases e fallback
            │   └── ChatClientResolverTest.java          # Testes unitários do mapeamento de provedores
            └── service/
                └── ChatStreamingServiceTest.java        # Testes unitários do serviço de streaming e memória
```

## Pré-requisitos

- **Java 25**
- **Maven 3.9+**
- **Kiri** (container runtime compatível com Docker)

### Instalação automática (JDK 25 + Kiri)

- **Windows (PowerShell)**:
  ```powershell
  # Instala JDK 25 (via winget) + Kiri e configura JAVA_HOME/PATH
  .\scripts\setup-windows.ps1
  ```
- **Linux (Debian/Ubuntu ou Fedora/RHEL)**:
  ```bash
  chmod +x scripts/setup-linux.sh
  ./scripts/setup-linux.sh
  ```

## Executar os testes

```bash
mvn clean test
```

### Estrutura de testes

- **Unitários**:
  - `src/test/java/com/deividnn/provider/AiProviderTest.java`
  - `src/test/java/com/deividnn/provider/ChatClientResolverTest.java`
  - `src/test/java/com/deividnn/service/ChatStreamingServiceTest.java`
  - `src/test/java/com/deividnn/controller/ChatControllerTest.java`
- **Integração**:
  - `src/test/java/com/deividnn/ChatStreamingIntegrationTest.java`

## Subir o projeto com Kiri (com Ollama)

O [Dockerfile](Dockerfile) já vem configurado com **Ollama embutido** (`OLLAMA_NUM_PARALLEL=1`, `OLLAMA_MAX_LOADED_MODELS=1`, `OLLAMA_KEEP_ALIVE=5m`) e baixa automaticamente o modelo configurado em `OLLAMA_MODEL` na primeira execução (padrão: `qwen2.5:0.5b`, ~398 MB).

> **Dica de modelos:**
> - `qwen2.5:0.5b` (~398 MB) — padrão ultraleve para máquinas com pouca RAM.
> - `llama3.2:1b` (~1,3 GB) — alternativa equilibrada (bom português e rápido em CPU).
> - `tinyllama` (~637 MB) — alternativa leve para testes rápidos.

### Build e Run com Kiri (API + Ollama no mesmo container)

```bash
# 1. Construir a imagem a partir do Dockerfile
kiri build -t chat-streaming-ai .

# 2. Subir o container com volume persistente para o modelo
# Nota: -p 127.0.0.1:8080:8080 faz o bind em IPv4 evitando travamento em IPv6 (::1) ao chamar localhost no Windows
kiri run -d --name chat-streaming-ai -p 127.0.0.1:8080:8080 \
  -v ollama_data:/home/spring/.ollama \
  -e OLLAMA_MODEL="qwen2.5:0.5b" \
  chat-streaming-ai

# Acompanhar os logs (mostra o download inicial do modelo, a subida do Spring Boot e qual provider/model respondeu)
kiri logs -f chat-streaming-ai

# Parar e remover o container (o modelo permanece salvo no volume ollama_data)
kiri stop chat-streaming-ai && kiri rm chat-streaming-ai
```

- Por padrão, apenas o provedor **Ollama** é inicializado (`AI_CHAT_MODEL=ollama`), dispensando chaves da OpenAI ou Google Gemini. Para usar outro modelo no Ollama (ex.: `llama3.2:1b`), ajuste `-e OLLAMA_MODEL="llama3.2:1b"`.
- Com `-p 127.0.0.1:8080:8080`, a API fica acessível normalmente em **`http://localhost:8080`**. Para gerar uma URL pública temporária pelo Kiri, execute `kiri share chat-streaming-ai`.

## Testando a API no container do Kiri

### 1. Chat com streaming (SSE)

**PowerShell (`curl.exe`):**
```powershell
'{"conversationId":"conv-1","message":"Responda em uma frase curta: o que é Java?","provider":"ollama"}' |
  curl.exe -N -X POST http://localhost:8080/api/chat/stream `
    -H "Content-Type: application/json" `
    --data-binary "@-"
```

**Linux / macOS / Git Bash (`curl`):**
```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"conv-1","message":"Responda em uma frase curta: o que é Java?","provider":"ollama"}'
```

Exemplo de resposta SSE (o evento final `done` traz o texto completo consolidado):
```text
event:message
data: {"conversationId":"conv-1","provider":"ollama","content":"Java","done":false}

event:done
data: {"conversationId":"conv-1","provider":"ollama","content":"Java é uma linguagem de programação orientada a objetos.","done":true}
```

### 2. Consultar histórico e limpar conversa

```powershell
# Consultar histórico salvo na memória (HTTP 200)
curl.exe http://localhost:8080/api/chat/conv-1/history

# Limpar histórico da conversa (HTTP 204)
curl.exe -X DELETE http://localhost:8080/api/chat/conv-1
```

### 3. Verificar nos logs qual provider e model responderam

```bash
kiri logs -f chat-streaming-ai
```

Exemplo de log gerado a cada chamada:
```text
INFO ... c.deividnn.service.ChatStreamingService : Iniciando stream de chat - conversationId=conv-1, provider=ollama, model=qwen2.5:0.5b
INFO ... c.deividnn.service.ChatStreamingService : Resposta concluída - conversationId=conv-1, provider=ollama, model=qwen2.5:0.5b
```
