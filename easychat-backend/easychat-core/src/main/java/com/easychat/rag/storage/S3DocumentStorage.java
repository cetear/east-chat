package com.easychat.rag.storage;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
/** Path-style S3 PUT/GET/DELETE, signed payloads, no SDK or presigned public URLs. */
public class S3DocumentStorage implements DocumentStorage {
    private final String endpoint,bucket,region,access,secret;
    private final HttpClient client;
    public S3DocumentStorage(String endpoint,String bucket,String region,String access,String secret,HttpClient client) {
        this.client=client;
        URI uri=URI.create(endpoint);
        if(uri.getHost()==null || uri.getRawQuery()!=null || uri.getFragment()!=null || uri.getUserInfo()!=null || !List.of("http","https").contains(uri.getScheme()) || !bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))throw new IllegalArgumentException("Invalid S3 endpoint or bucket");
        if(access==null||access.isBlank()||secret==null||secret.isBlank())throw new IllegalArgumentException("S3 credentials required");
        this.endpoint=endpoint.replaceAll("/+$","");this.bucket=bucket;this.region=region;this.access=access;this.secret=secret;
    }
    public String save(byte[] content,String dataset,String name) {
        String key=com.easychat.common.util.DatasetId.normalize(dataset)+"/"+name;
        validateKey(key);exchange("PUT",key,content);return "s3://"+bucket+"/"+key;
    }
    public LocalFile open(String reference) {
        String key=key(reference);Path temp=null;
        try{byte[] bytes=exchange("GET",key,new byte[0]);temp=Files.createTempFile("easychat-s3-","."+key.substring(key.lastIndexOf('.')+1));Files.write(temp,bytes);return new LocalFile(temp,true);}
        catch(java.io.IOException e){if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){}throw new IllegalStateException("S3 materialization failed",e);}
    }
    public void delete(String reference) {exchange("DELETE",key(reference),new byte[0]);}
    private String key(String reference) {
        String prefix="s3://"+bucket+"/";if(!reference.startsWith(prefix))throw new IllegalArgumentException("Unexpected S3 bucket");
        String key=reference.substring(prefix.length());validateKey(key);return key;
    }
    private void validateKey(String key) {if(!key.matches("[A-Za-z0-9_-]{1,64}/[A-Za-z0-9-]+\\.[a-z0-9]{1,20}"))throw new IllegalArgumentException("Invalid S3 key");}
    private byte[] exchange(String method,String key,byte[] body) {
        try {
            URI uri=URI.create(endpoint+"/"+bucket+"/"+key);
            String date=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
            String day=date.substring(0,8),hash=sha(body),scope=day+"/"+region+"/s3/aws4_request";
            String headers="host:"+uri.getRawAuthority()+"\nx-amz-content-sha256:"+hash+"\nx-amz-date:"+date+"\n";
            String signed="host;x-amz-content-sha256;x-amz-date";
            String canonical=method+"\n"+uri.getRawPath()+"\n\n"+headers+"\n"+signed+"\n"+hash;
            String value="AWS4-HMAC-SHA256\n"+date+"\n"+scope+"\n"+sha(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] signing=hmac(hmac(hmac(hmac(("AWS4"+secret).getBytes(StandardCharsets.UTF_8),day),region),"s3"),"aws4_request");
            String authorization="AWS4-HMAC-SHA256 Credential="+access+"/"+scope+", SignedHeaders="+signed+", Signature="+HexFormat.of().formatHex(hmac(signing,value));
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("x-amz-content-sha256",hash).header("x-amz-date",date).header("Authorization",authorization)
                .method(method,body.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response=com.easychat.common.util.BoundedHttp.send(client,request,20*1024*1024,Duration.ofSeconds(60));
            if(response.statusCode()==404 && "DELETE".equals(method))return new byte[0];
            if(response.statusCode()/100!=2)throw new IllegalStateException("S3 HTTP "+response.statusCode());
            return response.body();
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException();}
        catch(Exception e){if(e instanceof RuntimeException r)throw r;throw new IllegalStateException("S3 request failed",e);}
    }
    private static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    private static byte[] hmac(byte[] key,String value)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));}
}
