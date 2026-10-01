package com.easychat.llm.client;
import reactor.core.publisher.Flux;
import dev.langchain4j.data.message.*;
import java.util.*;
public interface LLMClient {
 default String chatMessages(List<ChatMessage> messages,LLMCallOptions options){throw new UnsupportedOperationException("Structured messages not supported by this provider");}
 default Flux<String> streamMessages(List<ChatMessage> messages,LLMCallOptions options){return Flux.error(new UnsupportedOperationException("Structured messages not supported by this provider"));}
 default String chat(String prompt){return chat(prompt,new LLMCallOptions());}
 default String chat(String prompt,LLMCallOptions options){return chat(prompt,options,List.of());}
 default String chat(String prompt,LLMCallOptions options,List<String> images){return chatMessages(messages(prompt,images),options==null?new LLMCallOptions():options);}
 default Flux<String> streamChat(String prompt){return streamChat(prompt,new LLMCallOptions());}
 default Flux<String> streamChat(String prompt,LLMCallOptions options){return streamChat(prompt,options,List.of());}
 default Flux<String> streamChat(String prompt,LLMCallOptions options,List<String> images){return streamMessages(messages(prompt,images),options==null?new LLMCallOptions():options);}
 static List<ChatMessage> messages(String prompt,List<String> images){List<Content> content=new ArrayList<>();if(prompt!=null&&!prompt.isBlank())content.add(TextContent.from(prompt));if(images!=null)images.forEach(i->content.add(ImageContent.from(i)));if(content.isEmpty())throw new IllegalArgumentException("Message is empty");return List.of(UserMessage.from(content));}
}
