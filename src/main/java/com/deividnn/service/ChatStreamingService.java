package com.deividnn.service;

import com.deividnn.dto.ChatRequest;
import com.deividnn.dto.ChatStreamChunk;
import com.deividnn.provider.AiProvider;
import com.deividnn.provider.ChatClientResolver;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Camada de serviço responsável por executar o streaming de respostas de IA via SSE
 * e gerenciar a leitura/limpeza da memória de conversas.
 */
@Service
public class ChatStreamingService {

    /** Logger SLF4J usado para registrar qual provedor e modelo responderam a cada chamada de streaming. */
    private static final Logger log = LoggerFactory.getLogger(ChatStreamingService.class);

    /** Resolve qual {@link ChatClient} (OpenAI, Gemini ou Ollama) deve atender cada requisição. */
    private final ChatClientResolver clientResolver;

    /** Armazena e recupera o histórico de mensagens por {@code conversationId}. */
    private final ChatMemory chatMemory;

    /** Provedor padrão aplicado quando a requisição não especifica o campo {@code provider}. */
    private final AiProvider defaultProvider;

    /**
     * Executor baseado em Virtual Threads (Java 25).
     *
     * <p>Por que usar {@link Executors#newVirtualThreadPerTaskExecutor()}: cada conexão SSE fica
     * aguardando chunks de I/O de rede dos LLMs; Virtual Threads permitem milhares de streams
     * simultâneos sem bloquear threads tradicionais do pool do Tomcat.
     */
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Construtor com injeção das dependências e do provedor padrão configurado no {@code application.yml}.
     *
     * @param clientResolver  resolvedor de clientes de IA
     * @param chatMemory      memória de conversação compartilhada
     * @param defaultProvider valor da propriedade {@code app.ai.default-provider} (fallback {@code "ollama"})
     */
    public ChatStreamingService(
            ChatClientResolver clientResolver,
            ChatMemory chatMemory,
            @Value("${app.ai.default-provider:ollama}") String defaultProvider
    ) {
        this.clientResolver = clientResolver;
        this.chatMemory = chatMemory;
        this.defaultProvider = AiProvider.fromNullable(defaultProvider, AiProvider.OLLAMA);
    }

    /**
     * Inicia o processamento assíncrono do prompt e retorna imediatamente um {@link SseEmitter}
     * para envio incremental dos tokens ao cliente HTTP.
     *
     * <p>A validação do provedor ocorre de forma síncrona antes de submeter a tarefa na Virtual Thread,
     * permitindo que erros de provedor inválido sejam convertidos em HTTP 400 pelo {@code @ExceptionHandler}.
     * O timeout {@code 0L} desativa o limite padrão do {@link SseEmitter}, evitando desconexão precoce
     * em respostas longas de LLMs.
     *
     * @param request dados da requisição validada do usuário
     * @return {@link SseEmitter} aberto para transmissão dos eventos {@code message}, {@code done} ou {@code error}
     */
    public SseEmitter streamChat(ChatRequest request) {
        AiProvider provider = AiProvider.fromNullable(request.provider(), defaultProvider);
        ChatClient chatClient = clientResolver.resolve(provider);
        String providerName = provider.name().toLowerCase();
        String model = clientResolver.resolveModel(provider);
        // Timeout 0L indica sem expiração fixa no emitter (controlado pelo ciclo de vida do stream).
        SseEmitter emitter = new SseEmitter(0L);

        log.info("Iniciando stream de chat - conversationId={}, provider={}, model={}",
                request.conversationId(), providerName, model);

        streamExecutor.submit(() -> {
            // Acumula todos os chunks recebidos para enviar o texto completo consolidado no evento final "done".
            StringBuilder fullResponse = new StringBuilder();
            try {
                chatClient.prompt()
                        // Define a mensagem do usuário que será enviada ao modelo.
                        .user(request.message())
                        // Passa o conversationId ao MessageChatMemoryAdvisor para isolar o histórico desta conversa.
                        .advisors(advisorSpec -> advisorSpec.param(
                                ChatMemory.CONVERSATION_ID,
                                request.conversationId()
                        ))
                        // Solicita resposta em modo streaming (Flux reativo) em vez de aguardar o texto completo.
                        .stream()
                        .content()
                        // Para cada fragmento recebido do modelo, acumula o texto e envia um evento SSE nomeado "message".
                        .doOnNext(chunk -> {
                            fullResponse.append(chunk);
                            sendEvent(
                                    emitter,
                                    "message",
                                    new ChatStreamChunk(
                                            request.conversationId(),
                                            providerName,
                                            chunk,
                                            false
                                    )
                            );
                        })
                        // Ao concluir o fluxo sem erros, envia o evento "done" com o texto completo, loga sucesso e fecha o emitter.
                        .doOnComplete(() -> {
                            log.info("Resposta concluída - conversationId={}, provider={}, model={}",
                                    request.conversationId(), providerName, model);
                            sendEvent(
                                    emitter,
                                    "done",
                                    new ChatStreamChunk(
                                            request.conversationId(),
                                            providerName,
                                            fullResponse.toString(),
                                            true
                                    )
                            );
                            emitter.complete();
                        })
                        // Em caso de falha no Flux reativo, loga o erro, notifica o cliente com evento "error" e encerra.
                        .doOnError(ex -> {
                            log.error("Erro no stream de chat - conversationId={}, provider={}, model={}, erro={}",
                                    request.conversationId(), providerName, model, ex.getMessage());
                            sendError(emitter, ex.getMessage());
                            emitter.completeWithError(ex);
                        })
                        // Bloqueia apenas a Virtual Thread dedicada até o último evento do Flux ser processado.
                        .blockLast();
            } catch (Exception ex) {
                // Captura falhas síncronas ou propagadas pelo blockLast() para garantir encerramento limpo do SSE.
                log.error("Falha ao processar stream de chat - conversationId={}, provider={}, model={}, erro={}",
                        request.conversationId(), providerName, model, ex.getMessage());
                sendError(emitter, ex.getMessage());
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    /**
     * Consulta todas as mensagens atualmente armazenadas na memória para a conversa informada.
     *
     * @param conversationId identificador único da conversa
     * @return lista de mensagens em ordem cronológica
     */
    public List<Message> getHistory(String conversationId) {
        return chatMemory.get(conversationId);
    }

    /**
     * Remove todas as mensagens da memória associadas à conversa informada.
     *
     * @param conversationId identificador único da conversa
     */
    public void clearHistory(String conversationId) {
        chatMemory.clear(conversationId);
    }

    /**
     * Envia um evento SSE estruturado em JSON pelo {@link SseEmitter}.
     *
     * <p>Caso ocorra {@link IOException} (por exemplo, cliente fechou a aba/conexão),
     * encerra o emitter com erro para interromper o envio.
     *
     * @param emitter   canal SSE ativo
     * @param eventName nome do evento SSE ({@code "message"} ou {@code "done"})
     * @param payload   dados serializados como {@code application/json}
     */
    private void sendEvent(SseEmitter emitter, String eventName, ChatStreamChunk payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * Tenta enviar um evento SSE nomeado {@code "error"} com a descrição da falha ocorrida.
     *
     * <p>Ignora {@link IOException} porque, se o cliente já tiver desconectado, não há mais canal
     * aberto para entregar a notificação de erro.
     *
     * @param emitter canal SSE ativo
     * @param message mensagem de erro da exceção (substituída por texto padrão se for {@code null})
     */
    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(message == null ? "Erro durante o streaming" : message));
        } catch (IOException ignored) {
            // Cliente já desconectou
        }
    }
}
