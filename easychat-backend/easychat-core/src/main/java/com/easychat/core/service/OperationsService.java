package com.easychat.core.service;
import com.easychat.infra.mysql.repository.OperationsQueries;
import com.easychat.core.router.ProviderRegistry;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.*;
@Service @RequiredArgsConstructor
public class OperationsService {
 private final OperationsQueries queries;
 private final ProviderRegistry routes;
 private final Environment env;
 public Map<String,Object> status(){
  Map<String,Object> result=new LinkedHashMap<>();result.put("routes",routes.diagnostics());
  result.put("database",queries.ping()?"up":"down");
  if(env.getProperty("easychat.es.enabled",Boolean.class,true))result.put("documents",queries.documents());
  if(env.getProperty("easychat.events.outbox.enabled",Boolean.class,false))result.put("outbox",queries.outbox());
  if(env.getProperty("easychat.distributed.enabled",Boolean.class,false))result.put("activeLeases",queries.activeLeases());
  result.put("esEnabled",env.getProperty("easychat.es.enabled",Boolean.class,true));return result;
 }
}
