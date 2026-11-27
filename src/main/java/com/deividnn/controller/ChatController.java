package com.deividnn.controller;

import com.deividnn.dto.ChatRequest;
import com.deividnn.service.ChatStreamingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller REST que expõe os endpoints HTTP de chat em streaming (SSE) e gerenciamento de histórico.
 *
 * <p>{@code @RestController} serializa automaticamente os retornos na resposta HTTP, e
 * {@code @RequestMapping("/api/chat")} define o prefixo comum de todas as rotas desta classe.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /**
     * Serviço de domínio responsável por orquestrar o streaming e o acesso à memória de conversas.
     * Declarado como {@code final} para garantir imutabilidade após injeção via construtor.
     */
    private final ChatStreamingService chatStreamingService;

    /**
     * Construtor com injeção de dependência recomendada pelo Spring (dispensa {@code @Autowired}
     * quando há um único construtor e facilita testes unitários).
     *
     * @param chatStreamingService serviço de streaming de chat
     */
    public ChatController(ChatStreamingService chatStreamingService) {
        this.chatStreamingService = chatStreamingService;
    }

    /**
     * Inicia uma resposta em tempo real via Server-Sent Events (SSE) para a mensagem enviada.
     *
     * <p>{@code produces = MediaType.TEXT_EVENT_STREAM_VALUE} informa ao cliente e ao Spring MVC
     * que a resposta será um fluxo contínuo de eventos ({@code text/event-stream}).
     * {@code @Valid} aciona as validações definidas em {@link ChatRequest} antes de entrar no método.
     *
     * @param request payload JSON contendo {@code conversationId}, {@code message} e {@code provider} opcional
     * @return {@link SseEmitter} que envia os chunks conforme o modelo gera tokens
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        return chatStreamingService.streamChat(request);
    }

    /**
     * Retorna o histórico de mensagens armazenado para uma conversa específica.
     *
     * <p>Mapeia cada {@link Message} do Spring AI para um {@link Map} simples com chaves
     * {@code "role"} (papel da mensagem, ex.: {@code user} ou {@code assistant}) e {@code "text"}
     * (conteúdo textual), evitando expor detalhes internos do framework na API pública.
     *
     * @param conversationId identificador único da conversa extraído da URL ({@code @PathVariable})
     * @return lista ordenada de mensagens formatadas como JSON
     */
    @GetMapping("/{conversationId}/history")
    public List<Map<String, Object>> history(@PathVariable String conversationId) {
        return chatStreamingService.getHistory(conversationId).stream()
                .map((Message msg) -> Map.<String, Object>of(
                        "role", msg.getMessageType().getValue(),
                        "text", msg.getText()
                ))
                .toList();
    }

    /**
     * Remove todo o histórico em memória associado ao identificador de conversa informado.
     *
     * <p>Retorna HTTP {@code 204 No Content} (padrão REST para operações {@code DELETE} concluídas
     * sem corpo de resposta).
     *
     * @param conversationId identificador único da conversa a ser limpa
     * @return resposta vazia com status 204
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> clear(@PathVariable String conversationId) {
        chatStreamingService.clearHistory(conversationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Intercepta {@link IllegalArgumentException} lançadas pelos endpoints deste controller
     * (por exemplo, quando o usuário solicita um provedor de IA não suportado).
     *
     * <p>Converte a exceção em HTTP {@code 400 Bad Request} com payload JSON estruturado
     * {@code {"error": "<mensagem>"}}, em vez de retornar erro genérico 500.
     *
     * @param ex exceção capturada durante o processamento da requisição
     * @return resposta 400 contendo a mensagem descritiva do erro
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
