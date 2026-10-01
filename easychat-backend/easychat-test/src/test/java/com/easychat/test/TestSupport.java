package com.easychat.test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.easychat.integration.JsonHttpService;
import org.springframework.test.util.ReflectionTestUtils;
final class TestSupport {
 static final ObjectMapper JSON = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
 static final java.net.http.HttpClient HTTP = new com.easychat.integration.HttpTransportConfiguration().configuredHttpClient();
 static void setField(Object target,String name,Object value) {
  for(var field:target.getClass().getDeclaredFields()) {
   if(field.getType()==ObjectMapper.class) ReflectionTestUtils.setField(target,field.getName(),JSON);
   if(field.getType()==java.net.http.HttpClient.class) ReflectionTestUtils.setField(target,field.getName(),HTTP);
   if(field.getType()==JsonHttpService.class) ReflectionTestUtils.setField(target,field.getName(),new JsonHttpService(JSON,HTTP));
  }
  ReflectionTestUtils.setField(target,name,value);
 }
 static org.mybatis.spring.SqlSessionTemplate mappers(javax.sql.DataSource source) {
  try {
   var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
   factory.setDataSource(source);
   var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();
   config.addMapper(com.easychat.infra.mysql.mapper.ExecutionLeaseMapper.class);
   config.addMapper(com.easychat.infra.mysql.mapper.ChatEventOutboxMapper.class);
   config.addMapper(com.easychat.infra.mysql.mapper.ChatMessageMapper.class);
   config.addMapper(com.easychat.infra.mysql.mapper.ChatSessionMapper.class);
   config.addMapper(com.easychat.infra.mysql.mapper.KnowledgeDocMapper.class);
   config.addMapper(com.easychat.infra.mysql.mapper.OperationsMapper.class);
   factory.setConfiguration(config);
   return new org.mybatis.spring.SqlSessionTemplate(factory.getObject());
  }catch(Exception e){throw new IllegalStateException(e);}
 }
}

