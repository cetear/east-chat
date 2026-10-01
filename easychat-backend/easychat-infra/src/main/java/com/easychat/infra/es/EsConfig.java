package com.easychat.infra.es;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.*;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import javax.net.ssl.*;
import java.net.URI;
import java.security.cert.X509Certificate;
@Configuration
@ConditionalOnProperty(name="easychat.es.enabled",havingValue="true",matchIfMissing=true)
public class EsConfig {
    @Value("${spring.elasticsearch.uris}") private String uris;
    @Value("${spring.elasticsearch.username:}") private String username;
    @Value("${spring.elasticsearch.password:}") private String password;
    @Value("${spring.elasticsearch.ssl.ca-fingerprint:}") private String caFingerprint;
    @Value("${spring.elasticsearch.ssl.ca-certificate:}") private String caCertificate;
    @Value("${spring.elasticsearch.ssl.trust-all:false}") private boolean trustAll;
    @Value("${spring.elasticsearch.connection-timeout:5000}") private int connectTimeout;
    @Value("${spring.elasticsearch.socket-timeout:30000}") private int socketTimeout;
    @Bean(destroyMethod="close")
    public ElasticsearchTransport elasticsearchTransport() throws Exception {
        URI uri=URI.create(uris);
        if (uri.getHost()==null || !java.util.List.of("http","https").contains(uri.getScheme())) throw new IllegalArgumentException("Invalid ES URI");
        var credentials=new BasicCredentialsProvider();
        if (!username.isBlank()) credentials.setCredentials(AuthScope.ANY,new UsernamePasswordCredentials(username,password));
        boolean hasCa = caCertificate != null && !caCertificate.isBlank();
        boolean hasFingerprint = caFingerprint != null && !caFingerprint.isBlank();
        if ((trustAll ? 1 : 0) + (hasCa ? 1 : 0) + (hasFingerprint ? 1 : 0) > 1)
            throw new IllegalArgumentException("Configure only one ES trust option: CA certificate, CA fingerprint or trust-all");
        SSLContext sslContext = null;
        if (hasCa) {
            try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of(caCertificate))) {
                sslContext = co.elastic.clients.transport.TransportUtils.sslContextFromHttpCaCrt(input);
            }
        }
        if (hasFingerprint) {
            String fingerprint = caFingerprint.replace(":", "").trim();
            if (!fingerprint.matches("(?i)[0-9a-f]{64}"))
                throw new IllegalArgumentException("ES CA fingerprint must be a SHA-256 hex digest");
            sslContext = co.elastic.clients.transport.TransportUtils.sslContextFromCaFingerprint(fingerprint);
        }
        if (trustAll) {
            sslContext=SSLContext.getInstance("TLS");
            sslContext.init(null,new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
                public void checkClientTrusted(X509Certificate[] c,String a){}
                public void checkServerTrusted(X509Certificate[] c,String a){}
            }},null);
        }
        final SSLContext configured=sslContext;
        var rest=RestClient.builder(new HttpHost(uri.getHost(),uri.getPort()>0?uri.getPort():9200,uri.getScheme()))
            .setRequestConfigCallback(b -> b.setConnectTimeout(connectTimeout).setSocketTimeout(socketTimeout))
            .setHttpClientConfigCallback(b -> {
                b.setDefaultCredentialsProvider(credentials);
                if(configured!=null) b.setSSLContext(configured);
                if(trustAll) b.setSSLHostnameVerifier((h,s)->true);
                return b;
            }).build();
        return new RestClientTransport(rest,new JacksonJsonpMapper());
    }
    @Bean @Primary
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) { return new ElasticsearchClient(transport); }
}
