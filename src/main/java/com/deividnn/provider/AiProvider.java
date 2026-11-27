package com.deividnn.provider;

import java.util.Locale;

/**
 * Enumeração dos provedores de IA suportados pela aplicação.
 *
 * <p>Usar um {@code enum} em vez de {@code String} solta garante segurança de tipos no roteamento
 * interno (como chave do mapa em {@link ChatClientResolver}) e centraliza a normalização de aliases.
 */
public enum AiProvider {
    /** Provedor OpenAI (modelos GPT). */
    OPENAI,
    /** Provedor Google GenAI (modelos Gemini). */
    GEMINI,
    /** Provedor Ollama (modelos executados localmente). */
    OLLAMA;

    /**
     * Converte uma string opcional recebida da API ou configuração em um {@link AiProvider}.
     *
     * <p>Aceita valores nulos/em branco retornando {@code fallback} e reconhece aliases amigáveis
     * ignorando espaços e diferenças entre maiúsculas/minúsculas ({@link Locale#ROOT} evita bugs
     * dependentes do idioma do sistema operacional).
     *
     * @param value    valor textual informado (pode ser {@code null} ou em branco)
     * @param fallback provedor padrão retornado quando {@code value} não for informado
     * @return provedor correspondente
     * @throws IllegalArgumentException quando {@code value} não corresponde a nenhum provedor suportado
     */
    public static AiProvider fromNullable(String value, AiProvider fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "openai" -> OPENAI;
            case "gemini", "google", "google-genai" -> GEMINI;
            case "ollama", "local" -> OLLAMA;
            default -> throw new IllegalArgumentException("Provedor não suportado: " + value);
        };
    }
}
