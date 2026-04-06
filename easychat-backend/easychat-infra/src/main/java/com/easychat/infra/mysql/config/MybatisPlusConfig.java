package com.easychat.infra.mysql.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.easychat.infra.mysql.mapper")
public class MybatisPlusConfig {
}
