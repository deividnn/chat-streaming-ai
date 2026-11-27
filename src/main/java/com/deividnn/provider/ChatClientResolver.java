package com.deividnn.provider;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Componente responsável por mapear um {@link AiProvider} para a instância correspondente de {@link ChatClient}
 * e informar o modelo configurado para fins de log/observabilidade.
 *
 * <p>Aplica o padrão Strategy/Registry: desacopla o {@code ChatStreamingService} dos detalhes
 * de qual provedor concreto será chamado em tempo de execução, aceitando provedores opcionais
 * (por exemplo, rodar apenas com Ollama sem configurar chaves da OpenAI ou Google Gemini).
 */
@Component
public class ChatClientResolver {

    /**
     * Mapa imutável ({@link Map#copyOf}) associando apenas os provedores efetivamente configurados
     * ao seu respectivo {@link ChatClient}. A imutabilidade torna consultas concorrentes seguras.
     */
    private final Map<AiProvider, ChatClient> clients;

    /**
     * Mapa imutável com o nome do modelo configurado para cada provedor (ex.: {@code qwen2.5:0.5b},
     * {@code gpt-4o-mini}, {@code gemini-2.5-flash}), usado para logar qual modelo respondeu.
     */
    private final Map<AiProvider, String> models;

    /**
     * Construtor utilizado pelo Spring ({@code @Autowired}) recebendo o mapa de clientes ativos
     * e os nomes dos modelos configurados em {@code ChatAiConfig}.
     *
     * @param clients mapa dos provedores habilitados e seus respectivos {@link ChatClient}
     * @param models  mapa dos provedores e seus respectivos nomes de modelo
     */
    @Autowired
    public ChatClientResolver(Map<AiProvider, ChatClient> clients, Map<AiProvider, String> models) {
        this.clients = Map.copyOf(clients);
        this.models = Map.copyOf(models);
    }

    /**
     * Construtor direto (usado em testes unitários) que registra no mapa somente os {@link ChatClient} não nulos.
     *
     * @param openAiChatClient cliente configurado para OpenAI (pode ser {@code null})
     * @param geminiChatClient cliente configurado para Google Gemini (pode ser {@code null})
     * @param ollamaChatClient cliente configurado para Ollama (pode ser {@code null})
     */
    public ChatClientResolver(
            ChatClient openAiChatClient,
            ChatClient geminiChatClient,
            ChatClient ollamaChatClient
    ) {
        Map<AiProvider, ChatClient> configured = new EnumMap<>(AiProvider.class);
        if (openAiChatClient != null) {
            configured.put(AiProvider.OPENAI, openAiChatClient);
        }
        if (geminiChatClient != null) {
            configured.put(AiProvider.GEMINI, geminiChatClient);
        }
        if (ollamaChatClient != null) {
            configured.put(AiProvider.OLLAMA, ollamaChatClient);
        }
        this.clients = Map.copyOf(configured);
        this.models = Map.of();
    }

    /**
     * Obtém o {@link ChatClient} correspondente ao provedor solicitado.
     *
     * @param provider provedor de IA desejado
     * @return {@link ChatClient} configurado para o provedor
     * @throws IllegalStateException caso o provedor solicitado não esteja configurado na aplicação
     */
    public ChatClient resolve(AiProvider provider) {
        ChatClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalStateException("ChatClient não configurado para: " + provider);
        }
        return client;
    }

    /**
     * Retorna o nome do modelo configurado para o provedor informado (ou {@code "unknown"} se não definido).
     *
     * @param provider provedor de IA
     * @return nome do modelo configurado
     */
    public String resolveModel(AiProvider provider) {
        return models.getOrDefault(provider, "unknown");
    }
}
