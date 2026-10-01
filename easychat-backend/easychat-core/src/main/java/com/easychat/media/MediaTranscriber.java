package com.easychat.media;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
@Service
public class MediaTranscriber {
    @org.springframework.beans.factory.annotation.Autowired private com.easychat.integration.JsonHttpService http;
    @Value("${easychat.media.transcription-url:}") private String endpoint;
    @Value("${easychat.media.token:}") private String token;
    @Value("${easychat.media.ffmpeg:ffmpeg}") private String ffmpeg;
    public String transcribe(byte[] data,String name) {
        if(data==null || data.length==0 || data.length>20*1024*1024)throw new IllegalArgumentException("Media must be 1 byte to 20MB");
        String extension=name==null?"":name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(!Set.of("wav","mp3","m4a","ogg","flac","mp4","webm","mov","mkv").contains(extension))throw new IllegalArgumentException("Unsupported media type");
        Path directory=null;Process process=null;
        try {
            String mime="audio/"+extension;
            if(Set.of("mp4","webm","mov","mkv").contains(extension)) {
                directory=Files.createTempDirectory("easychat-media-");Path input=directory.resolve("input."+extension);Path output=directory.resolve("audio.wav");
                Files.write(input,data);
                process=new ProcessBuilder(ffmpeg,"-nostdin","-v","error","-protocol_whitelist","file,pipe","-i",input.toString(),"-vn","-fs","20971520","-ar","16000","-ac","1",output.toString())
                    .redirectErrorStream(true).redirectOutput(directory.resolve("ffmpeg.log").toFile()).start();
                if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor();throw new IllegalStateException("Video extraction timed out");}
                if(process.exitValue()!=0 || !Files.exists(output))throw new IllegalArgumentException("Video has no readable audio track");
                if(Files.size(output)>=20*1024*1024)throw new IllegalArgumentException("Extracted audio reaches 20MB limit; refusing truncated transcription");
                data=Files.readAllBytes(output);mime="audio/wav";
            }
            var response=http.post(endpoint,token,Map.of("audioBase64",Base64.getEncoder().encodeToString(data),"mimeType",mime));
            if(!response.path("text").isTextual())throw new IllegalStateException("Transcription response must contain text");
            return response.get("text").asText();
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException();}
        catch(java.io.IOException e){throw new IllegalStateException("Media processing unavailable",e);}
        finally {
            if(process!=null&&process.isAlive())process.destroyForcibly();
            if(directory!=null)try(var files=Files.list(directory)){for(var file:files.toList())Files.deleteIfExists(file);Files.deleteIfExists(directory);}catch(java.io.IOException ignored){}
        }
    }
}
