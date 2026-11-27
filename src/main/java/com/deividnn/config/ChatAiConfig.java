package com.deividnn.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Classe de configuração Spring responsável por criar a memória de conversação
 * e instanciar um {@link ChatClient} dedicado para cada provedor de IA suportado.
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
     * Registra o {@link ChatClient} nomeado {@code openAiChatClient} para chamadas à OpenAI.
     *
     * <p>O nome explícito no {@code @Bean} é necessário porque existem múltiplos beans do tipo
     * {@link ChatClient} no contexto e o {@code ChatClientResolver} seleciona cada um via {@code @Qualifier}.
     *
     * @param chatModel    modelo autoconfigurado pelo starter da OpenAI
     * @param chatMemory   memória de conversação compartilhada entre provedores
     * @param systemPrompt prompt de sistema lido de {@code app.ai.system-prompt}
     * @return cliente configurado para OpenAI
     */
    @Bean("openAiChatClient")
    ChatClient openAiChatClient(
            OpenAiChatModel chatModel,
            ChatMemory chatMemory,
            @Value("${app.ai.system-prompt}") String systemPrompt
    ) {
        return buildClient(chatModel, chatMemory, systemPrompt);
    }

    /**
     * Registra o {@link ChatClient} nomeado {@code geminiChatClient} para chamadas ao Google Gemini.
     *
     * @param chatModel    modelo autoconfigurado pelo starter Google GenAI
     * @param chatMemory   memória de conversação compartilhada
     * @param systemPrompt prompt de sistema padrão da aplicação
     * @return cliente configurado para Google Gemini
     */
    @Bean("geminiChatClient")
    ChatClient geminiChatClient(
            GoogleGenAiChatModel chatModel,
            ChatMemory chatMemory,
            @Value("${app.ai.system-prompt}") String systemPrompt
    ) {
        return buildClient(chatModel, chatMemory, systemPrompt);
    }

    /**
     * Registra o {@link ChatClient} nomeado {@code ollamaChatClient} para chamadas a modelos locais via Ollama.
     *
     * @param chatModel    modelo autoconfigurado pelo starter Ollama
     * @param chatMemory   memória de conversação compartilhada
     * @param systemPrompt prompt de sistema padrão da aplicação
     * @return cliente configurado para Ollama
     */
    @Bean("ollamaChatClient")
    ChatClient ollamaChatClient(
            OllamaChatModel chatModel,
            ChatMemory chatMemory,
            @Value("${app.ai.system-prompt}") String systemPrompt
    ) {
        return buildClient(chatModel, chatMemory, systemPrompt);
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
