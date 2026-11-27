package com.deividnn.dto;

/**
 * DTO imutável (Java {@code record}) serializado em JSON dentro de cada evento SSE enviado ao cliente.
 *
 * <p>Padroniza o contrato de saída tanto para fragmentos parciais ({@code event:message}) quanto
 * para o evento final de encerramento ({@code event:done}).
 *
 * @param conversationId identificador da conversa à qual este fragmento pertence, útil quando o cliente
 *                       mantém múltiplas sessões simultâneas
 * @param provider       nome em minúsculas do provedor que gerou o chunk (ex.: {@code "ollama"}, {@code "openai"}, {@code "gemini"})
 * @param content        fragmento de texto gerado pelo modelo nesta iteração (vazio no evento final {@code done})
 * @param done           flag booleana que indica se o streaming foi concluído ({@code false} durante envio de chunks, {@code true} no término)
 */
public record ChatStreamChunk(
        String conversationId,
        String provider,
        String content,
        boolean done
) {
}
