package com.zth.java.ai.langchain4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot 启动入口：启动接口服务，并提供 resources/static 下的前端页面。 */
@SpringBootApplication
public class APP {
    public static void main(String[] args) {
        SpringApplication.run(APP.class, args);
    }
}
