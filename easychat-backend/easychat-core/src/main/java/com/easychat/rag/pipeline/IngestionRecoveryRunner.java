package com.easychat.rag.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用启动后恢复未完成的入库任务（PENDING / INDEXING）。
 */
@Slf4j
@Component
@Order(10)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="easychat.es.enabled",havingValue="true",matchIfMissing=true)
public class IngestionRecoveryRunner implements ApplicationRunner {

    @Autowired
    private DocumentIngestionService ingestionService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int pending = ingestionService.recoverInterrupted();
            if (pending > 0) {
                log.info("recovered {} pending knowledge docs", pending);
            }
        } catch (Exception e) {
            log.warn("ingestion recovery failed: {}", e.getMessage());
        }
    }
}
