package com.deividnn.config;

import com.deividnn.provider.AiProvider;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Classe de configuração Spring responsável por criar a memória de conversação
 * e instanciar os {@link ChatClient}s disponíveis para os provedores de IA ativos.
 *
 * <p>{@code @Configuration} indica ao Spring que os métodos anotados com {@code @Bean}
 * devem ser registrados no container de injeção de dependências.
 */
@Configuration
public class ChatAiConfig {

    /**
     * Cria o bean de memória de conversas usando janela deslizante de mensagens em memória.
     *
     * <p>Por que usar {@link MessageWindowChatMemory}: mantém automaticamente apenas as últimas
     * {@code maxMessages} mensagens por {@code conversationId}, evitando crescimento ilimitado
     * do contexto enviado aos LLMs.
     *
     * @param maxMessages limite de mensagens por conversa lido da propriedade
     *                    {@code app.ai.memory-max-messages} (valor padrão {@code 20} se não configurado)
     * @return instância compartilhada de {@link ChatMemory} usada por todos os provedores
     */
    @Bean
    ChatMemory chatMemory(@Value("${app.ai.memory-max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .build();
    }

    /**
     * Monta o mapa de {@link ChatClient} apenas para os provedores cujos {@code ChatModel}
     * foram efetivamente autoconfigurados no contexto.
     *
     * <p>Por que usar {@link ObjectProvider}: permite inicializar a aplicação usando apenas Ollama
     * sem exigir chaves configuradas para OpenAI ou Google Gemini, ao mesmo tempo em que registra
     * automaticamente OpenAI/Gemini assim que seus provedores forem habilitados.
     *
     * @param openAiChatModel      provedor opcional do modelo OpenAI
     * @param googleGenAiChatModel provedor opcional do modelo Google Gemini
     * @param ollamaChatModel      provedor opcional do modelo Ollama
     * @param chatMemory           memória de conversação compartilhada
     * @param systemPrompt         prompt de sistema lido de {@code app.ai.system-prompt}
     * @return mapa imutável de provedor para {@link ChatClient}
     */
    @Bean
    Map<AiProvider, ChatClient> chatClients(
            ObjectProvider<OpenAiChatModel> openAiChatModel,
            ObjectProvider<GoogleGenAiChatModel> googleGenAiChatModel,
            ObjectProvider<OllamaChatModel> ollamaChatModel,
            ChatMemory chatMemory,
            @Value("${app.ai.system-prompt}") String systemPrompt
    ) {
        Map<AiProvider, ChatClient> clients = new EnumMap<>(AiProvider.class);
        openAiChatModel.ifAvailable(
                model -> clients.put(AiProvider.OPENAI, buildClient(model, chatMemory, systemPrompt))
        );
        googleGenAiChatModel.ifAvailable(
                model -> clients.put(AiProvider.GEMINI, buildClient(model, chatMemory, systemPrompt))
        );
        ollamaChatModel.ifAvailable(
                model -> clients.put(AiProvider.OLLAMA, buildClient(model, chatMemory, systemPrompt))
        );
        return Map.copyOf(clients);
    }

    /**
     * Mapeia o nome do modelo configurado para cada provedor de IA, utilizado para observabilidade/logs
     * das chamadas de streaming.
     *
     * @param openAiModel modelo configurado em {@code spring.ai.openai.chat.options.model}
     * @param geminiModel modelo configurado em {@code spring.ai.google.genai.chat.options.model}
     * @param ollamaModel modelo configurado em {@code spring.ai.ollama.chat.options.model}
     * @return mapa imutável de provedor para nome do modelo
     */
    @Bean
    Map<AiProvider, String> providerModels(
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String openAiModel,
            @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}") String geminiModel,
            @Value("${spring.ai.ollama.chat.options.model:qwen2.5:0.5b}") String ollamaModel
    ) {
        return Map.of(
                AiProvider.OPENAI, openAiModel,
                AiProvider.GEMINI, geminiModel,
                AiProvider.OLLAMA, ollamaModel
        );
    }

    /**
     * Método auxiliar que padroniza a construção de qualquer {@link ChatClient}.
     *
     * <p>Centraliza o {@code defaultSystem(systemPrompt)} e o {@link MessageChatMemoryAdvisor}
     * para garantir que todos os provedores compartilhem a mesma persona e persistam/recuperem
     * o histórico automaticamente a cada interação.
     *
     * @param chatModel    implementação concreta do modelo de chat
     * @param chatMemory   repositório de memória de conversa
     * @param systemPrompt instrução inicial enviada ao modelo
     * @return instância configurada de {@link ChatClient}
     */
    private ChatClient buildClient(
            org.springframework.ai.chat.model.ChatModel chatModel,
            ChatMemory chatMemory,
            String systemPrompt
    ) {
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
