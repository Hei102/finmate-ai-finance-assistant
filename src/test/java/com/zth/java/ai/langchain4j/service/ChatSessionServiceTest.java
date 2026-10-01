package com.zth.java.ai.langchain4j.service;

import com.zth.java.ai.langchain4j.entity.FinanceChatSession;
import com.zth.java.ai.langchain4j.repository.FinanceChatSessionRepository;
import com.zth.java.ai.langchain4j.store.MongoChatMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证标题去重重构后，默认标题、自动命名和用户隔离仍保持原有行为。 */
class ChatSessionServiceTest {
    private final FinanceChatSessionRepository repository = mock(FinanceChatSessionRepository.class);
    private final MongoChatMemoryStore memory = mock(MongoChatMemoryStore.class);
    private final ChatSessionService service = new ChatSessionService(repository, memory);

    @BeforeEach
    void saveReturnsEntity() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void blankTitleUsesDefault() {
        assertThat(service.create(1L, null).getTitle()).isEqualTo("新的聊天");
        assertThat(service.create(1L, " \n ").getTitle()).isEqualTo("新的聊天");
    }

    @Test
    void manualTitleAndFirstMessageUseSameWhitespaceAndLengthRules() {
        String text = "  abc\n  def  ";
        assertThat(service.create(1L, text).getTitle()).isEqualTo("abc def");
        assertThat(service.resolveForMessage(1L, null, text).getTitle()).isEqualTo("abc def");
        String longTitle = "1234567890123456789012345";
        assertThat(service.create(1L, longTitle).getTitle()).isEqualTo("12345678901234567890...");
        assertThat(service.resolveForMessage(1L, null, longTitle).getTitle()).isEqualTo("12345678901234567890...");
    }

    @Test
    void firstMessageRenamesDefaultButKeepsCustomTitle() {
        FinanceChatSession session = new FinanceChatSession();
        session.setTitle("新的聊天");
        when(repository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        assertThat(service.resolveForMessage(1L, 3L, "了解 ETF").getTitle()).isEqualTo("了解 ETF");
        assertThat(service.resolveForMessage(1L, 3L, "了解 PE").getTitle()).isEqualTo("了解 ETF");
    }

    @Test
    void rejectsForeignSessionBeforeReadingMessages() {
        assertThatThrownBy(() -> service.listMessages(1L, 99L)).hasMessageContaining("不属于当前账号");
        verifyNoInteractions(memory);
    }
}
