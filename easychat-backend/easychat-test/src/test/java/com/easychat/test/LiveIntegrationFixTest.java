package com.easychat.test;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PathVariable;
import com.easychat.api.controller.*;
import com.easychat.api.config.ApiExceptionHandler;
import com.easychat.core.service.model.ModelManageService;
import com.easychat.api.facade.AgentFacade;
import com.easychat.common.domain.chat.ChatSession;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class LiveIntegrationFixTest {
 @Test void allPathVariablesHaveExplicitNames(){
  int count=0;
  for(var type:java.util.List.of(ChatController.class,ModelController.class))for(var method:type.getDeclaredMethods())for(var parameter:method.getParameters()){
   var variable=parameter.getAnnotation(PathVariable.class);if(variable!=null){assertFalse(variable.value().isBlank());assertTrue(parameter.isNamePresent());count++;}
  }
  assertEquals(19,count);
 }
 @Test void sessionRoutesBindThroughSpringMvc() throws Exception {
  var controller=new ChatController();var facade=mock(AgentFacade.class);ReflectionTestUtils.setField(controller,"agentFacade",facade);
  var session=new ChatSession();session.setSessionCode("s");when(facade.getSession("s")).thenReturn(session);when(facade.updateSession(eq("s"),any())).thenReturn(session);
  var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
  mvc.perform(get("/api/session/s")).andExpect(status().isOk());mvc.perform(get("/api/session/s/messages")).andExpect(status().isOk());
  mvc.perform(put("/api/session/s").contentType("application/json").content("{\"title\":\"x\"}")).andExpect(status().isOk());
  mvc.perform(put("/api/session/s/max-rounds").contentType("application/json").content("{\"maxRounds\":100}")).andExpect(status().isOk());
  mvc.perform(delete("/api/session/s")).andExpect(status().isNoContent());verify(facade).deleteSession("s");verify(facade).history("s");
 }
 @Test void modelRoutesBindThroughSpringMvc() throws Exception {
  var controller=new ModelController();var service=mock(ModelManageService.class);ReflectionTestUtils.setField(controller,"modelManageService",service);
  when(service.getModel(1L)).thenReturn(new com.easychat.common.domain.model.ModelDefinition());when(service.getModelByCode("m")).thenReturn(new com.easychat.common.domain.model.ModelDefinition());
  when(service.getProvider(1L)).thenReturn(new com.easychat.common.domain.model.ProviderAccount());when(service.getProviderByCode("p")).thenReturn(new com.easychat.common.domain.model.ProviderAccount());
  var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
  for(String path:java.util.List.of("/model/1","/model/code/m","/model/provider/1","/model/provider/code/p","/model/provider/p/models","/model/m/providers"))mvc.perform(get(path)).andExpect(status().isOk());
  for(String path:java.util.List.of("/model/m","/model/provider/p","/model/provider/p/models/m")){mvc.perform(put(path).contentType("application/json").content("{}")).andExpect(status().isOk());mvc.perform(delete(path)).andExpect(status().isOk());}
  verify(service).updateModelProvider(eq("p"),eq("m"),any());verify(service).deleteModelProvider("p","m");verify(service).deleteProvider("p");verify(service).deleteModel("m");
 }
 @Test void unexpectedFailureLogsCauseAndReturnsCorrelationWithoutDetails(){
  var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
  try{var failure=new IllegalStateException("internal diagnostic");var response=new ApiExceptionHandler().failure(failure);assertEquals(500,response.getStatusCode().value());assertEquals(java.util.Map.of("error","Request failed"),response.getBody());String id=response.getHeaders().getFirst("X-Error-Id");assertNotNull(id);assertTrue(appender.list.get(0).getFormattedMessage().contains(id));assertNotNull(appender.list.get(0).getThrowableProxy());}finally{logger.detachAppender(appender);}
 }
 @Test void configuredCaMustBeValidAndCannotDisableHostnameChecks(@TempDir Path root)throws Exception{
  var config=new com.easychat.infra.es.EsConfig();ReflectionTestUtils.setField(config,"uris","https://localhost:9200");ReflectionTestUtils.setField(config,"username","");ReflectionTestUtils.setField(config,"password","");
  var file=root.resolve("bad-ca.pem");Files.writeString(file,"not a certificate");ReflectionTestUtils.setField(config,"caCertificate",file.toString());assertThrows(RuntimeException.class,config::elasticsearchTransport);
  ReflectionTestUtils.setField(config,"trustAll",true);assertThrows(IllegalArgumentException.class,config::elasticsearchTransport);
 }
 @Test void closedSessionPrecedesModelValidationAndNewInvalidModelCreatesNothing(){
  var builder=new com.easychat.core.service.chat.ChatContextBuilder();var sessions=mock(com.easychat.core.service.chat.SessionUseCase.class);var models=mock(com.easychat.common.port.ModelCatalogRepository.class);
  ReflectionTestUtils.setField(builder,"sessionUseCase",sessions);ReflectionTestUtils.setField(builder,"modelCatalogRepository",models);
  var command=new com.easychat.core.service.chat.ChatCommand();command.setUserMessage("q");command.setModelCode("missing");command.setSessionCode("s");
  var session=new ChatSession();session.setStatus(0);when(sessions.getSession(eq("s"),any())).thenReturn(session);
  var error=assertThrows(com.easychat.common.exception.BusinessException.class,()->builder.resolveSession(command));assertEquals("409",error.getCode());verifyNoInteractions(models);
  command.setSessionCode(null);assertThrows(com.easychat.common.exception.BusinessException.class,()->builder.resolveSession(command));verify(sessions,never()).createSession(any());
 }
 @Test void malformedCaFingerprintFailsBeforeConnecting(){
  var config=new com.easychat.infra.es.EsConfig();ReflectionTestUtils.setField(config,"uris","https://localhost:9200");ReflectionTestUtils.setField(config,"username","");ReflectionTestUtils.setField(config,"password","");ReflectionTestUtils.setField(config,"caFingerprint","bad");
  assertThrows(IllegalArgumentException.class,config::elasticsearchTransport);
 }
}
