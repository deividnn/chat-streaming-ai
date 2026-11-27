package com.deividnn.provider;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Componente responsável por mapear um {@link AiProvider} para a instância correspondente de {@link ChatClient}.
 *
 * <p>Aplica o padrão Strategy/Registry: desacopla o {@code ChatStreamingService} dos detalhes
 * de qual provedor concreto será chamado em tempo de execução.
 */
@Component
public class ChatClientResolver {

    /**
     * Mapa imutável ({@link Map#of}) associando cada provedor suportado ao seu respectivo {@link ChatClient}.
     * A imutabilidade torna consultas concorrentes seguras sem necessidade de sincronização.
     */
    private final Map<AiProvider, ChatClient> clients;

    /**
     * Construtor que recebe os três {@link ChatClient} qualificados pelo nome definido em {@code ChatAiConfig}.
     *
     * <p>{@code @Qualifier} é indispensável para desfazer a ambiguidade entre os múltiplos beans
     * do tipo {@link ChatClient}.
     *
     * @param openAiChatClient cliente configurado para OpenAI
     * @param geminiChatClient cliente configurado para Google Gemini
     * @param ollamaChatClient cliente configurado para Ollama
     */
    public ChatClientResolver(
            @Qualifier("openAiChatClient") ChatClient openAiChatClient,
            @Qualifier("geminiChatClient") ChatClient geminiChatClient,
            @Qualifier("ollamaChatClient") ChatClient ollamaChatClient
    ) {
        this.clients = Map.of(
                AiProvider.OPENAI, openAiChatClient,
                AiProvider.GEMINI, geminiChatClient,
                AiProvider.OLLAMA, ollamaChatClient
        );
    }

    /**
     * Obtém o {@link ChatClient} correspondente ao provedor solicitado.
     *
     * @param provider provedor de IA desejado
     * @return {@link ChatClient} configurado para o provedor
     * @throws IllegalStateException caso algum novo valor de {@link AiProvider} não tenha sido registrado no mapa
     */
    public ChatClient resolve(AiProvider provider) {
        ChatClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalStateException("ChatClient não configurado para: " + provider);
        }
        return client;
    }
}
