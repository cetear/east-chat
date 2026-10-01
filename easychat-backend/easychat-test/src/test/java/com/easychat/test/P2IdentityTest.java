package com.easychat.test;
import com.easychat.api.config.IdentityFilter;
import com.easychat.common.security.*;
import com.easychat.core.service.chat.SessionUseCase;
import com.easychat.common.domain.chat.ChatSession;
import com.easychat.common.port.ChatSessionRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class P2IdentityTest {
    @Test void verifiedIdentityIsUsedAndAdminPathsRejectOrdinaryUsers() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/identity",exchange->{
            assertEquals("POST",exchange.getRequestMethod());assertEquals("Bearer test-token",exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body="{\"active\":true,\"userId\":\"alice\",\"roles\":[\"user\"]}".getBytes();exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {
            var filter=new IdentityFilter();TestSupport.setField(filter,"enabled",true);TestSupport.setField(filter,"endpoint","http://127.0.0.1:"+server.getAddress().getPort()+"/identity");
            var request=new MockHttpServletRequest("POST","/api/chat");request.addHeader("Authorization","Bearer test-token");var response=new MockHttpServletResponse();
            var called=new AtomicBoolean();filter.doFilter(request,response,(req,res)->{called.set(true);assertEquals("alice",ActorContext.current().userId());});assertTrue(called.get());assertEquals("demo",ActorContext.current().userId());
            for(String path:List.of("/model/provider/list","/model;x/provider/list","/es/getDocuments","/api/ops/status")) {
                var denied=new MockHttpServletRequest("GET",path);denied.addHeader("Authorization","Bearer test-token");var result=new MockHttpServletResponse();filter.doFilter(denied,result,(req,res)->fail("Admin endpoint reached"));assertEquals(403,result.getStatus());
            }
            var noToken=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("GET","/api/sessions"),noToken,(req,res)->fail());assertEquals(401,noToken.getStatus());
        }finally{server.stop(0);}
    }
    @Test void sessionOwnershipCannotBeOverriddenByKnowingItsCode() {
        var repo=mock(ChatSessionRepository.class);var session=new ChatSession();session.setOwnerId("alice");session.setSessionCode("known");
        when(repo.findBySessionCode("known")).thenReturn(session);when(repo.findAll()).thenReturn(List.of(session));
        var service=new SessionUseCase();TestSupport.setField(service,"chatSessionRepository",repo);
        var bob=new Actor("bob",Set.of("user"));
        assertThrows(com.easychat.common.exception.BusinessException.class,()->service.getSession("known",bob));
        assertSame(session,service.getSession("known",new Actor("alice",Set.of("user"))));
        ActorContext.set(bob);try{assertTrue(service.getSessions().isEmpty());}finally{ActorContext.clear();}
    }
}