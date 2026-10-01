package com.easychat.llm.provider;
import com.easychat.llm.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
public class OllamaProvider implements LLMProvider {
    private final String code,endpoint,key,model;
    private final int timeout;
    private final ObjectMapper json;
    private final HttpClient client;
    public OllamaProvider(String code,String base,String key,String model,int timeout,ObjectMapper json,HttpClient client) {
        this.json=json;this.client=client;
        this.code=code;this.endpoint=base.replaceAll("/+$","")+"/api/chat";this.key=key;this.model=model;this.timeout=timeout;
    }
    public boolean supportsRemoteImages(){return false;}
    public String getProviderCode(){return code;}
    public String chatMessages(List<ChatMessage> messages,LLMCallOptions options){return streamMessages(messages,options).collectList().map(parts->String.join("",parts)).block();}
    public Flux<String> streamMessages(List<ChatMessage> messages,LLMCallOptions options) {
        return Flux.<String>create(sink->{
            AtomicReference<InputStream> stream=new AtomicReference<>();
            try {
                var request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofMillis(timeout)).header("Content-Type","application/json");
                if(key!=null&&!key.isBlank()) request.header("Authorization","Bearer "+key);
                var future=client.sendAsync(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload(messages,options)))).build(),HttpResponse.BodyHandlers.ofInputStream());
                sink.onCancel(()->{future.cancel(true);close(stream.get());});
                var response=future.join();stream.set(response.body());
                if(sink.isCancelled()){close(response.body());return;}
                try(var reader=new BufferedReader(new InputStreamReader(response.body(),java.nio.charset.StandardCharsets.UTF_8))) {
                    if(response.statusCode()/100!=2) throw new IllegalStateException("Ollama HTTP "+response.statusCode());
                    boolean done=false;String line;
                    while(!sink.isCancelled() && (line=reader.readLine())!=null) {
                        if(line.isBlank())continue;
                        var item=json.readTree(line);
                        if(item.has("error")) throw new IllegalStateException("Ollama generation failed");
                        String token=item.path("message").path("content").asText("");if(!token.isEmpty())sink.next(token);
                        if(item.path("done").asBoolean()) {
                            Integer input=item.hasNonNull("prompt_eval_count")?item.get("prompt_eval_count").intValue():null;
                            Integer output=item.hasNonNull("eval_count")?item.get("eval_count").intValue():null;
                            options.getUsageConsumer().accept(new ModelUsage(input,output,input==null||output==null?null:input+output,item.path("done_reason").asText()));
                            done=true;break;
                        }
                    }
                    if(!sink.isCancelled()) {if(!done)throw new IllegalStateException("Ollama stream ended before completion");sink.complete();}
                }
            }catch(Exception e){if(!sink.isCancelled())sink.error(e);}
        }).subscribeOn(Schedulers.boundedElastic()).timeout(Duration.ofMillis(timeout));
    }
    private Map<String,Object> payload(List<ChatMessage> messages,LLMCallOptions options) {
        List<Map<String,Object>> converted=new ArrayList<>();
        for(var message:messages) {
            Map<String,Object> item=new LinkedHashMap<>();
            if(message instanceof SystemMessage s){item.put("role",com.easychat.common.constant.MessageRole.SYSTEM.getValue());item.put("content",s.text());}
            else if(message instanceof AiMessage a){item.put("role",com.easychat.common.constant.MessageRole.ASSISTANT.getValue());item.put("content",a.text());}
            else if(message instanceof UserMessage u){
                item.put("role",com.easychat.common.constant.MessageRole.USER.getValue());StringBuilder text=new StringBuilder();List<String> images=new ArrayList<>();
                for(var content:u.contents()) {
                    if(content instanceof TextContent t)text.append(t.text());
                    else if(content instanceof ImageContent i){
                        String data=i.image().base64Data();
                        if(data==null && i.image().url()!=null && i.image().url().toString().startsWith("data:image/"))data=i.image().url().toString().split(",",2)[1];
                        if(data==null)throw new IllegalArgumentException("Ollama requires base64 image data; remote image URLs are unsupported");
                        images.add(data);
                    }else throw new IllegalArgumentException("Unsupported Ollama content");
                }
                item.put("content",text.toString());if(!images.isEmpty())item.put("images",images);
            }else throw new IllegalArgumentException("Unsupported Ollama message");
            converted.add(item);
        }
        Map<String,Object> settings=new LinkedHashMap<>();
        if(options.getTemperature()!=null)settings.put("temperature",options.getTemperature());
        if(options.getTopP()!=null)settings.put("top_p",options.getTopP());
        if(options.getMaxTokens()!=null)settings.put("num_predict",options.getMaxTokens());
        if(options.getStop()!=null)settings.put("stop",options.getStop());if(options.getSeed()!=null)settings.put("seed",options.getSeed());
        return Map.of("model",options.getModelName()==null?model:options.getModelName(),"messages",converted,"options",settings,"stream",true);
    }
    private static void close(InputStream in){try{if(in!=null)in.close();}catch(IOException ignored){}}
}