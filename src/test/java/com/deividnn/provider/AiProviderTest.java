package com.deividnn.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testes unitários da enumeração {@link AiProvider}.
 *
 * <p>Valida de forma isolada (sem subir contexto Spring) a regra de conversão de strings,
 * aliases e tratamento de fallback/erro no método {@link AiProvider#fromNullable(String, AiProvider)}.
 */
class AiProviderTest {

    /**
     * Garante que entradas nulas, vazias ou compostas apenas por espaços/quebras de linha
     * retornem exatamente o provedor de fallback informado.
     *
     * <p>{@code @ParameterizedTest} com {@code @NullAndEmptySource} e {@code @ValueSource}
     * evita duplicar vários métodos de teste para cada variação de entrada em branco.
     *
     * @param input valor nulo ou em branco injetado pelo JUnit 5 em cada execução
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Deve retornar fallback quando valor for nulo ou em branco")
    void shouldReturnFallbackWhenNullOrBlank(String input) {
        assertThat(AiProvider.fromNullable(input, AiProvider.OLLAMA)).isEqualTo(AiProvider.OLLAMA);
        assertThat(AiProvider.fromNullable(input, AiProvider.OPENAI)).isEqualTo(AiProvider.OPENAI);
    }

    /**
     * Valida que todos os aliases suportados são convertidos para o {@link AiProvider} correto,
     * independentemente de letras maiúsculas/minúsculas ou espaços extras.
     *
     * <p>{@code @CsvSource} agrupa cada par {@code entrada -> esperado} de forma declarativa.
     *
     * @param input    string enviada como provedor
     * @param expected enum esperado após normalização
     */
    @ParameterizedTest
    @CsvSource({
            "openai, OPENAI",
            "OPENAI, OPENAI",
            "  OpenAi  , OPENAI",
            "gemini, GEMINI",
            "google, GEMINI",
            "google-genai, GEMINI",
            "  GOOGLE-GENAI , GEMINI",
            "ollama, OLLAMA",
            "local, OLLAMA",
            "  Local , OLLAMA"
    })
    @DisplayName("Deve resolver aliases suportados ignorando maiúsculas/minúsculas e espaços")
    void shouldResolveSupportedAliases(String input, AiProvider expected) {
        assertThat(AiProvider.fromNullable(input, AiProvider.OLLAMA)).isEqualTo(expected);
    }

    /**
     * Verifica que um nome de provedor desconhecido dispara {@link IllegalArgumentException}
     * com a mensagem esperada, contrato utilizado pelo {@code @ExceptionHandler} do controller.
     */
    @Test
    @DisplayName("Deve lançar IllegalArgumentException para provedor não suportado")
    void shouldThrowExceptionForUnsupportedProvider() {
        assertThatThrownBy(() -> AiProvider.fromNullable("anthropic", AiProvider.OLLAMA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provedor não suportado: anthropic");
    }
}
