package com.deividnn.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO imutável (Java {@code record}) que representa o corpo JSON da requisição enviada para {@code POST /api/chat/stream}.
 *
 * <p>Por que usar {@code record}: reduz boilerplate (gera construtor, getters, {@code equals},
 * {@code hashCode} e {@code toString} automaticamente) e garante imutabilidade dos dados de entrada.
 *
 * @param conversationId identificador único da conversa usado para isolar o histórico na {@code ChatMemory};
 *                       obrigatório ({@code @NotBlank}) para impedir conversas sem identificação
 * @param message        texto/pergunta enviada pelo usuário ao modelo de IA;
 *                       obrigatório ({@code @NotBlank}) para evitar chamadas vazias aos provedores
 * @param provider       nome opcional do provedor desejado ({@code "openai"}, {@code "gemini"}, {@code "ollama"}
 *                       ou aliases); quando omitido ou vazio, o serviço aplica o provedor padrão configurado
 */
public record ChatRequest(
        @NotBlank(message = "conversationId é obrigatório")
        String conversationId,

        @NotBlank(message = "message é obrigatória")
        String message,

        String provider // opcional: "openai", "gemini" ou "ollama"
) {
}
