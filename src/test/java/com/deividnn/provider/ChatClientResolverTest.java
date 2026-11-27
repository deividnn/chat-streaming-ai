package com.deividnn.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Testes unitários do {@link ChatClientResolver}.
 *
 * <p>Garante que cada valor de {@link AiProvider} seja roteado para a instância exata
 * do {@link ChatClient} injetado no construtor, sem depender do contexto Spring.
 */
class ChatClientResolverTest {

    /** Mock que representa o cliente configurado para OpenAI. */
    private ChatClient openAiChatClient;

    /** Mock que representa o cliente configurado para Google Gemini. */
    private ChatClient geminiChatClient;

    /** Mock que representa o cliente configurado para Ollama. */
    private ChatClient ollamaChatClient;

    /** Instância real da classe sob teste (SUT - System Under Test). */
    private ChatClientResolver resolver;

    /**
     * Executado antes de cada teste ({@code @BeforeEach}) para recriar mocks limpos
     * e evitar vazamento de estado entre cenários.
     */
    @BeforeEach
    void setUp() {
        openAiChatClient = mock(ChatClient.class);
        geminiChatClient = mock(ChatClient.class);
        ollamaChatClient = mock(ChatClient.class);
        resolver = new ChatClientResolver(openAiChatClient, geminiChatClient, ollamaChatClient);
    }

    /**
     * Valida com {@code isSameAs} (identidade de referência) que o resolver devolve
     * exatamente o bean esperado para cada provedor, prevenindo trocas acidentais no mapa.
     */
    @Test
    @DisplayName("Deve resolver o ChatClient correspondente para cada provedor")
    void shouldResolveClientForEachProvider() {
        assertThat(resolver.resolve(AiProvider.OPENAI)).isSameAs(openAiChatClient);
        assertThat(resolver.resolve(AiProvider.GEMINI)).isSameAs(geminiChatClient);
        assertThat(resolver.resolve(AiProvider.OLLAMA)).isSameAs(ollamaChatClient);
    }
}
