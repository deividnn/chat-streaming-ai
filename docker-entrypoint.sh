#!/usr/bin/env bash
# ==============================================================================
# Script de inicialização do container:
# 1. Quando OLLAMA_EMBEDDED=true, sobe o servidor Ollama em segundo plano,
#    aguarda a API ficar disponível e baixa o modelo leve configurado (OLLAMA_MODEL)
#    caso ainda não exista no volume.
# 2. Inicia a aplicação Spring Boot (app.jar) como processo principal (exec).
# ==============================================================================
set -euo pipefail

if [ "${OLLAMA_EMBEDDED:-true}" = "true" ]; then
    echo "[entrypoint] Iniciando Ollama em segundo plano (modelo: ${OLLAMA_MODEL:-qwen2.5:0.5b})..."
    ollama serve &
    OLLAMA_PID=$!

    # Garante o encerramento limpo do Ollama caso o container seja parado
    trap 'kill "${OLLAMA_PID}" 2>/dev/null || true' EXIT INT TERM

    # Aguarda até 30 segundos pela inicialização da API HTTP do Ollama
    for i in $(seq 1 30); do
        if curl -fsS http://localhost:11434/api/tags >/dev/null 2>&1; then
            break
        fi
        sleep 1
    done

    MODEL="${OLLAMA_MODEL:-qwen2.5:0.5b}"
    if ! ollama list | awk 'NR>1 {print $1}' | grep -Fxq "${MODEL}"; then
        echo "[entrypoint] Baixando modelo leve '${MODEL}' (somente na primeira execução)..."
        ollama pull "${MODEL}"
    else
        echo "[entrypoint] Modelo '${MODEL}' já disponível em cache."
    fi
fi

echo "[entrypoint] Iniciando Chat Streaming AI na porta 8080..."
exec java -jar /app/app.jar
