package com.deividnn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deividnn.dto.ChatRequest;
import com.deividnn.provider.AiProvider;
import com.deividnn.provider.ChatClientResolver;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * Testes unitários do {@link ChatStreamingService}.
 *
 * <p>Simula a API fluente do {@link ChatClient} com Mockito para testar o roteamento de provedores,
 * passagem do {@code conversationId} para o advisor de memória, tratamento de erro no stream
 * e operações de consulta/limpeza de histórico sem chamar provedores externos.
 */
class ChatStreamingServiceTest {

    /** Mock do resolvedor que retorna o {@link ChatClient} de acordo com o {@link AiProvider}. */
    private ChatClientResolver clientResolver;

    /** Mock da memória de conversação para verificar delegação em {@code getHistory} e {@code clearHistory}. */
    private ChatMemory chatMemory;

    /** Mock do ponto de entrada fluente do Spring AI ({@code chatClient.prompt()}). */
    private ChatClient chatClient;

    /** Mock da especificação intermediária do prompt ({@code user}, {@code advisors}, {@code stream}). */
    private ChatClient.ChatClientRequestSpec requestSpec;

    /** Mock da etapa de resposta em streaming ({@code content()} que retorna {@link Flux}). */
    private ChatClient.StreamResponseSpec streamResponseSpec;

    /** Serviço sob teste instanciado com provedor padrão {@code "ollama"}. */
    private ChatStreamingService service;

    /**
     * Prepara todos os mocks da cadeia fluente do {@link ChatClient} antes de cada método de teste.
     * {@code @SuppressWarnings("unchecked")} suprime o aviso de generics ao usar {@code any(Consumer.class)}.
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        clientResolver = mock(ChatClientResolver.class);
        chatMemory = mock(ChatMemory.class);
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        // Encadeia os retornos da fluent API para permitir chamar prompt().user().advisors().stream().
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);

        service = new ChatStreamingService(clientResolver, chatMemory, "ollama");
    }

    /**
     * Verifica que, quando {@code provider} é {@code null}, o serviço utiliza o provedor padrão
     * ({@link AiProvider#OLLAMA}), envia a mensagem do usuário e repassa {@code ChatMemory.CONVERSATION_ID}
     * ao advisor de memória.
     *
     * <p>{@link CountDownLatch} sincroniza a thread de teste com a Virtual Thread assíncrona do serviço.
     */
    @Test
    @DisplayName("Deve usar o provedor padrão quando provider não for informado")
    void shouldUseDefaultProviderWhenRequestProviderIsNull() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        when(clientResolver.resolve(AiProvider.OLLAMA)).thenReturn(chatClient);
        when(streamResponseSpec.content()).thenReturn(
                Flux.just("Olá", " mundo").doFinally(signal -> completed.countDown())
        );

        SseEmitter emitter = service.streamChat(new ChatRequest("conv-1", "Oi", null));

        assertThat(emitter).isNotNull();
        // Aguarda até 5 segundos pela conclusão do Flux na Virtual Thread.
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        verify(clientResolver).resolve(AiProvider.OLLAMA);
        verify(requestSpec).user("Oi");

        // Captura o lambda passado para .advisors(...) e executa contra um mock para validar o parâmetro CONVERSATION_ID.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> advisorCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestSpec).advisors(advisorCaptor.capture());

        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        advisorCaptor.getValue().accept(advisorSpec);
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, "conv-1");
    }

    /**
     * Garante que um provedor explicitamente informado na requisição ({@code "gemini"})
     * sobrescreve o provedor padrão {@code "ollama"}.
     */
    @Test
    @DisplayName("Deve resolver provedor explícito informado na requisição")
    void shouldResolveExplicitProvider() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        when(clientResolver.resolve(AiProvider.GEMINI)).thenReturn(chatClient);
        when(streamResponseSpec.content()).thenReturn(
                Flux.just("Resposta Gemini").doFinally(signal -> completed.countDown())
        );

        SseEmitter emitter = service.streamChat(new ChatRequest("conv-2", "Pergunta", "gemini"));

        assertThat(emitter).isNotNull();
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        verify(clientResolver).resolve(AiProvider.GEMINI);
    }

    /**
     * Valida que um provedor inválido falha de forma síncrona com {@link IllegalArgumentException}
     * antes mesmo de iniciar o streaming assíncrono.
     */
    @Test
    @DisplayName("Deve lançar IllegalArgumentException para provedor inválido")
    void shouldThrowWhenProviderIsInvalid() {
        ChatRequest request = new ChatRequest("conv-3", "Olá", "desconhecido");

        assertThatThrownBy(() -> service.streamChat(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provedor não suportado: desconhecido");
    }

    /**
     * Verifica que uma exceção emitida durante o {@link Flux} assíncrono é capturada internamente
     * na Virtual Thread sem quebrar a chamada síncrona de {@code streamChat}.
     */
    @Test
    @DisplayName("Deve tratar erro no stream sem propagar exceção síncrona")
    void shouldHandleErrorWhenStreamFails() throws InterruptedException {
        CountDownLatch errored = new CountDownLatch(1);
        // AtomicReference permite compartilhar a exceção capturada na Virtual Thread com a thread de teste com segurança.
        AtomicReference<Throwable> capturedError = new AtomicReference<>();

        when(clientResolver.resolve(AiProvider.OPENAI)).thenReturn(chatClient);
        when(streamResponseSpec.content()).thenReturn(
                Flux.<String>error(new RuntimeException("Falha no provedor"))
                        .doOnError(ex -> {
                            capturedError.set(ex);
                            errored.countDown();
                        })
        );

        SseEmitter emitter = service.streamChat(new ChatRequest("conv-4", "Oi", "openai"));

        assertThat(emitter).isNotNull();
        assertThat(errored.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedError.get())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Falha no provedor");
    }

    /**
     * Confirma que {@link ChatStreamingService#getHistory(String)} delega diretamente
     * para {@link ChatMemory#get(String)} retornando as mensagens intactas.
     */
    @Test
    @DisplayName("Deve retornar histórico de mensagens da conversa")
    void shouldReturnConversationHistory() {
        List<Message> expected = List.of(
                new UserMessage("Oi"),
                new AssistantMessage("Olá! Como posso ajudar?")
        );
        when(chatMemory.get("conv-5")).thenReturn(expected);

        List<Message> history = service.getHistory("conv-5");

        assertThat(history).isEqualTo(expected);
        verify(chatMemory).get("conv-5");
    }

    /**
     * Confirma que {@link ChatStreamingService#clearHistory(String)} delega a limpeza
     * para {@link ChatMemory#clear(String)} usando o {@code conversationId} correto.
     */
    @Test
    @DisplayName("Deve limpar histórico de mensagens da conversa")
    void shouldClearConversationHistory() {
        service.clearHistory("conv-6");

        verify(chatMemory).clear("conv-6");
    }
}
