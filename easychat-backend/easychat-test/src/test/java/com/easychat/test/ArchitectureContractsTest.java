package com.easychat.test;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import com.easychat.core.service.OperationsService;
import com.easychat.infra.mysql.repository.OperationsQueries;
import com.easychat.core.router.ProviderRegistry;
import com.easychat.api.dto.*;
import com.easychat.core.service.chat.ChatResult;
import com.easychat.llm.client.*;
import dev.langchain4j.data.message.*;
import reactor.core.publisher.Flux;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ArchitectureContractsTest {
 @Test void operationsPreservesFlagDependentFieldsAndErrors(){
  for(int flags=0;flags<8;flags++) {
   var queries=mock(OperationsQueries.class);when(queries.ping()).thenReturn(true);var registry=mock(ProviderRegistry.class);
   var env=new MockEnvironment().withProperty("easychat.es.enabled",""+((flags&1)!=0)).withProperty("easychat.events.outbox.enabled",""+((flags&2)!=0)).withProperty("easychat.distributed.enabled",""+((flags&4)!=0));
   var service=new OperationsService(queries,registry,env);var result=service.status();assertEquals("up",result.get("database"));assertTrue(result.containsKey("routes"));assertEquals((flags&1)!=0,result.get("esEnabled"));assertEquals((flags&1)!=0,result.containsKey("documents"));assertEquals((flags&2)!=0,result.containsKey("outbox"));assertEquals((flags&4)!=0,result.containsKey("activeLeases"));
   when(queries.ping()).thenThrow(new IllegalStateException("database down"));assertThrows(IllegalStateException.class,service::status);
  }
 }
 @Test void synchronousDtoKeepsWireShapeAndOptionalSourcesString() throws Exception {
  var result=new ChatResult();result.setContent("hello");result.setSessionCode("s");result.setProviderCode("p");result.setFinishReason("stop");result.setUsage(Map.of("complete",false));
  var json=TestSupport.JSON.valueToTree(ChatResponse.from(result));assertEquals(Set.of("content","sessionId","providerCode","finishReason","usage"),TestSupport.JSON.convertValue(json,Map.class).keySet());
  result.setSources("[]");result.setWarning("fallback");json=TestSupport.JSON.valueToTree(ChatResponse.from(result));assertTrue(json.get("sources").isTextual());assertEquals("fallback",json.get("warning").asText());
  var session=new com.easychat.common.domain.chat.ChatSession();session.setOwnerId("private");session.setSessionCode("s");assertFalse(TestSupport.JSON.valueToTree(SessionView.from(session)).has("ownerId"));
 }
 @Test void legacyModelEntryPreservesOptionsImagesUsageAndCancellation(){
  var options=new LLMCallOptions();options.setTemperature(0.2);var usage=new java.util.concurrent.atomic.AtomicReference<ModelUsage>();options.setUsageConsumer(usage::set);var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
  LLMClient provider=new LLMClient(){
   private void check(List<ChatMessage> messages,LLMCallOptions actual){assertSame(options,actual);var user=(UserMessage)messages.get(0);assertEquals(2,user.contents().size());assertTrue(user.contents().get(1) instanceof ImageContent);actual.getUsageConsumer().accept(new ModelUsage(1,2,3,"stop"));}
   public String chatMessages(List<ChatMessage> messages,LLMCallOptions actual){check(messages,actual);return "answer";}
   public Flux<String> streamMessages(List<ChatMessage> messages,LLMCallOptions actual){check(messages,actual);return Flux.<String>never().doOnCancel(()->cancelled.set(true));}
  };
  assertEquals("answer",provider.chat("q",options,List.of("https://example.test/image.png")));assertEquals(3,usage.get().totalTokens());
  var subscription=provider.streamChat("q",options,List.of("https://example.test/image.png")).subscribe();subscription.dispose();assertTrue(cancelled.get());
  assertThrows(UnsupportedOperationException.class,()->new LLMClient(){}.chat("q",options));
  assertEquals(java.net.http.HttpClient.Redirect.NEVER,TestSupport.HTTP.followRedirects());
 }
}
