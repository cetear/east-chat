package com.easychat.api.config;
import com.easychat.common.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
@Component
@org.springframework.core.annotation.Order(-100)
public class IdentityFilter extends OncePerRequestFilter {
    @Value("${easychat.auth.enabled:false}") private boolean enabled;
    @Value("${easychat.auth.introspection-url:}") private String endpoint;
    @org.springframework.beans.factory.annotation.Autowired private HttpClient client;
    @org.springframework.beans.factory.annotation.Autowired private ObjectMapper json;
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws java.io.IOException,ServletException {
        try {
            Actor actor=Actor.demo();
            if(enabled) {
                String token=request.getHeader("Authorization");
                if(token==null || !token.startsWith("Bearer ") || token.length()>8192) {reject(response,401);return;}
                try {
                    if(endpoint==null || endpoint.isBlank()) {reject(response,503);return;}
                    var answer=com.easychat.common.util.BoundedHttp.send(client,HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(5))
                        .header("Authorization",token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),64*1024,Duration.ofSeconds(5));
                    if(answer.statusCode()!=200) {reject(response,answer.statusCode()==401||answer.statusCode()==403?401:503);return;}
                    var body=json.readTree(answer.body());
                    if(!body.path("active").asBoolean(false)) {reject(response,401);return;}
                    Set<String> roles=new HashSet<>();body.path("roles").forEach(r->roles.add(r.asText()));
                    actor=new Actor(body.path("userId").asText(),roles);
                } catch(Exception e) {if(e instanceof InterruptedException) Thread.currentThread().interrupt();reject(response,503);return;}
            }
            if(!actor.admin() && !actor.roles().contains("user")) {reject(response,403);return;}
            String path=request.getServletPath();
            if(path==null || path.isEmpty())path=org.springframework.web.util.UriUtils.decode(request.getRequestURI().substring(request.getContextPath().length()),java.nio.charset.StandardCharsets.UTF_8);
            if((path.startsWith("/model")||path.startsWith("/es")||path.startsWith("/api/ops"))&&!actor.admin()) {reject(response,403);return;}
            ActorContext.set(actor);chain.doFilter(request,response);
        } finally {ActorContext.clear();}
    }
    private void reject(HttpServletResponse response,int status) throws java.io.IOException {
        response.setStatus(status);response.setContentType("application/json");response.getWriter().write("{\"error\":\""+(status==401?"Authentication required":status==403?"Forbidden":"Identity service unavailable")+"\"}");
    }
}
