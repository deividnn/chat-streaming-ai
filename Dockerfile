# ==============================================================================
# Stage 1: Build da aplicação Spring Boot com Maven + Java 25
# ==============================================================================
FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /app

# Baixa as dependências antes de copiar o código para aproveitar o cache de camadas
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia o código-fonte e gera o JAR executável
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Fonte oficial dos binários do Ollama
# ==============================================================================
FROM ollama/ollama:latest AS ollama

# ==============================================================================
# Stage 3: Runtime leve com JRE 25 (Ubuntu Noble / glibc) + Ollama embutido
# Nota: usa imagem baseada em glibc (noble) em vez de Alpine porque o binário
# oficial do Ollama depende de glibc.
# ==============================================================================
FROM eclipse-temurin:25-jre-noble
WORKDIR /app

# Instala apenas curl (usado para aguardar o servidor Ollama ficar pronto no startup)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Cria usuário não-root para executar a aplicação e o Ollama com segurança
RUN groupadd --system spring && useradd --system --gid spring --create-home spring

# Copia o binário e as bibliotecas de execução do Ollama a partir da imagem oficial
# (em /usr/bin/ollama para resolver automaticamente /usr/lib/ollama)
COPY --from=ollama /bin/ollama /usr/bin/ollama
COPY --from=ollama /usr/lib/ollama /usr/lib/ollama

# Copia o JAR compilado e o script de inicialização
COPY --from=build /app/target/*.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

# Garante fim de linha LF (evita erro em builds feitos no Windows) e permissão de execução
RUN sed -i 's/\r$//' /app/docker-entrypoint.sh \
    && chmod +x /app/docker-entrypoint.sh \
    && mkdir -p /home/spring/.ollama \
    && chown -R spring:spring /app /home/spring/.ollama

USER spring:spring

# 8080: API Spring Boot | 11434: Ollama embutido (opcionalmente acessível)
EXPOSE 8080 11434

# Configurações padrão de execução (Ollama embutido)
ENV OPENAI_API_KEY="" \
    GEMINI_API_KEY="" \
    # Habilita o Ollama embutido neste container (use "false" ao conectar em um Ollama externo)
    OLLAMA_EMBEDDED="true" \
    # URL apontando para o Ollama local dentro do próprio container
    OLLAMA_BASE_URL="http://localhost:11434" \
    # Modelo padrão (~398 MB). Alternativa: llama3.2:1b (~1.3 GB)
    OLLAMA_MODEL="qwen2.5:0.5b" \
    # Otimizações de memória para o Ollama em desenvolvimento
    OLLAMA_HOST="0.0.0.0:11434" \
    OLLAMA_MODELS="/home/spring/.ollama/models" \
    OLLAMA_NUM_PARALLEL="1" \
    OLLAMA_MAX_LOADED_MODELS="1" \
    OLLAMA_KEEP_ALIVE="5m"

# Volume para persistir os modelos baixados entre reinicializações do container
VOLUME ["/home/spring/.ollama"]

ENTRYPOINT ["/app/docker-entrypoint.sh"]
