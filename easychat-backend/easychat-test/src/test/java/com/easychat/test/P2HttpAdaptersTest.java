package com.easychat.test;
import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.easychat.llm.provider.OllamaProvider;
import com.easychat.llm.client.LLMCallOptions;
import com.easychat.rag.storage.S3DocumentStorage;
import com.easychat.rag.pipeline.parser.HttpOcrService;
import com.easychat.media.MediaTranscriber;
import com.easychat.rag.retriever.*;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class P2HttpAdaptersTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private void respond(HttpExchange e,String body)throws java.io.IOException {byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();}
    @Test void serviceTransportRejectsOversizedAndStalledBodies() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var sent=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        server.createContext("/large",e->respond(e,"x".repeat(1024)));
        server.createContext("/stalled",e->{
            e.sendResponseHeaders(200,100);e.getResponseBody().write(1);e.getResponseBody().flush();sent.countDown();
            try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}finally{e.close();}
        });server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();var client=java.net.http.HttpClient.newHttpClient();
            var large=java.net.http.HttpRequest.newBuilder(URI.create(base+"/large")).build();
            assertThrows(java.io.IOException.class,()->com.easychat.common.util.BoundedHttp.send(client,large,100,java.time.Duration.ofSeconds(2)));
            var stalled=java.net.http.HttpRequest.newBuilder(URI.create(base+"/stalled")).build();
            assertThrows(java.net.http.HttpTimeoutException.class,()->com.easychat.common.util.BoundedHttp.send(client,stalled,1024,java.time.Duration.ofSeconds(1)));
            assertEquals(0,sent.getCount());
        }finally{release.countDown();server.stop(0);}
    }
    @Test void ollamaNativeStreamReportsRealUsageAndDetectsTruncatedCompletion()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var truncated=new AtomicBoolean();
        server.createContext("/api/chat",e->{
            var request=JSON.readTree(e.getRequestBody());assertEquals("m",request.get("model").asText());assertEquals("user",request.get("messages").get(0).get("role").asText());
            respond(e,"{\"message\":{\"content\":\"hello\"},\"done\":false}\n"+(truncated.get()?"":"{\"message\":{\"content\":\" world\"},\"done\":true,\"prompt_eval_count\":4,\"eval_count\":2,\"done_reason\":\"stop\"}\n"));
        });server.start();
        try {
            var provider=new OllamaProvider("local","http://127.0.0.1:"+server.getAddress().getPort(),null,"m",5000,TestSupport.JSON,TestSupport.HTTP);var options=new LLMCallOptions();
            var usage=new AtomicReference<com.easychat.llm.client.ModelUsage>();options.setUsageConsumer(usage::set);
            assertEquals(List.of("hello"," world"),provider.streamChat("q",options).collectList().block());assertEquals(6,usage.get().totalTokens());
            truncated.set(true);assertThrows(IllegalStateException.class,()->provider.streamMessages(List.of(UserMessage.from("q")),options).blockLast());
        }finally{server.stop(0);}
    }
    @Test void s3StoresRetrievesAndDeletesPrivateObjects()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var bytes=new AtomicReference<byte[]>();
        server.createContext("/bucket",e->{
            assertTrue(e.getRequestHeaders().getFirst("Authorization").startsWith("AWS4-HMAC-SHA256 Credential=test/"));
            byte[] body=e.getRequestBody().readAllBytes();
            try{assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body)),e.getRequestHeaders().getFirst("x-amz-content-sha256"));}catch(java.security.NoSuchAlgorithmException ex){throw new RuntimeException(ex);}
            if(e.getRequestMethod().equals("PUT")){bytes.set(body);respond(e,"");}
            else if(e.getRequestMethod().equals("DELETE")){bytes.set(null);respond(e,"");}
            else {byte[] value=bytes.get();e.sendResponseHeaders(200,value.length);e.getResponseBody().write(value);e.close();}
        });server.start();
        try {
            var storage=new S3DocumentStorage("http://127.0.0.1:"+server.getAddress().getPort(),"bucket","us-east-1","test","secret",TestSupport.HTTP);
            String ref=storage.save("fact".getBytes(),"alpha","123.txt");java.nio.file.Path file;
            try(var local=storage.open(ref)){file=local.path();assertEquals("fact",java.nio.file.Files.readString(file));}assertFalse(java.nio.file.Files.exists(file));
            assertThrows(IllegalArgumentException.class,()->storage.open("s3://other/alpha/123.txt"));storage.delete(ref);assertNull(bytes.get());
        }finally{server.stop(0);}
    }
    @Test void ocrTranscriptionAndRerankUseExplicitContracts()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ocr",e->{assertEquals("image/png",JSON.readTree(e.getRequestBody()).get("mimeType").asText());respond(e,"{\"text\":\"scanned fact\"}");});
        server.createContext("/audio",e->{assertTrue(JSON.readTree(e.getRequestBody()).has("audioBase64"));respond(e,"{\"text\":\"spoken fact\"}");});
        server.createContext("/rerank",e->{assertEquals(2,JSON.readTree(e.getRequestBody()).get("documents").size());respond(e,"{\"indices\":[1,0]}");});server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            var ocr=new HttpOcrService();TestSupport.setField(ocr,"endpoint",base+"/ocr");assertEquals("scanned fact",ocr.recognize(new byte[]{1}));
            var media=new MediaTranscriber();TestSupport.setField(media,"endpoint",base+"/audio");assertEquals("spoken fact",media.transcribe(new byte[]{1},"a.wav"));assertThrows(IllegalArgumentException.class,()->media.transcribe(new byte[]{1},"bad.exe"));
            var rank=new HttpReranker();TestSupport.setField(rank,"endpoint",base+"/rerank");var a=new Retriever.Document();a.setContent("a");var b=new Retriever.Document();b.setContent("b");
            assertEquals(List.of(b,a),rank.rank("q",List.of(a,b)));
        }finally{server.stop(0);}
    }
}
