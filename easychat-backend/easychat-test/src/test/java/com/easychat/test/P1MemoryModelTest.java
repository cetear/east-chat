package com.easychat.test;
import com.easychat.common.domain.chat.ChatMessage;
import com.easychat.common.port.ModelCatalogRepository;
import com.easychat.core.router.ProviderRegistry;
import com.easychat.core.service.model.*;
import com.easychat.common.entity.*;
import com.easychat.infra.mysql.mapper.*;
import com.easychat.infra.mysql.repository.DbConversationMemory;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class P1MemoryModelTest {
    @Test void configurationRefreshOnlyAfterCommitAndRejectInvalidJson() {
        var service=new ModelManageService();var catalog=mock(ModelCatalogRepository.class);var registry=mock(ProviderRegistry.class);
        TestSupport.setField(service,"modelCatalogRepository",catalog);TestSupport.setField(service,"registry",registry);
        var command=new ModelCommand();command.setModelCode("m");command.setModelName("m");command.setDefaultConfig("[]");
        assertThrows(IllegalArgumentException.class,()->service.addModel(command));verify(catalog,never()).insertModel(any());
        command.setDefaultConfig("{\"stop\":[\"end\"]}");TransactionSynchronizationManager.initSynchronization();
        try {service.addModel(command);verify(registry,never()).reload();TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());verify(registry).reload();}
        finally {TransactionSynchronizationManager.clearSynchronization();}
    }
    @Test void memoryFiltersSuccessAndCursorAndDeletesSummary() {
        var memory=new DbConversationMemory();var messages=mock(ChatMessageMapper.class);var summaries=mock(ChatSessionMemoryMapper.class);
        TestSupport.setField(memory,"chatMessageMapper",messages);TestSupport.setField(memory,"chatSessionMemoryMapper",summaries);
        var repository=mock(com.easychat.common.port.ChatMessageRepository.class);TestSupport.setField(memory,"messages",repository);
        var saved=new ChatSessionMemoryDO();saved.setCoveredMessageId(12L);when(summaries.selectOne(any())).thenReturn(saved);
        var a=new ChatMessage();a.setId(14L);var b=new ChatMessage();b.setId(13L);
        when(messages.selectList(any())).thenAnswer(i->{QueryWrapper<?> q=i.getArgument(0);String sql=q.getSqlSegment();
            assertTrue(sql.contains("status ="));assertTrue(sql.contains("id >"));assertTrue(sql.contains("LIMIT 4"));
            assertTrue(q.getParamNameValuePairs().containsValue(12L));return new ArrayList<>(List.of(a,b));});
        assertEquals(List.of(b,a),memory.getRecentMessages(1L,2));memory.clear(1L);
        verify(repository).deleteBySessionId(1L);verify(summaries).delete(any());
    }
}