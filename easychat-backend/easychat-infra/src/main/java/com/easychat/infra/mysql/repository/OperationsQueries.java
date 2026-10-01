package com.easychat.infra.mysql.repository;
import com.easychat.infra.mysql.mapper.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.util.*;
@Repository @RequiredArgsConstructor
public class OperationsQueries {
 private final OperationsMapper operations;
 private final KnowledgeDocMapper documents;
 private final ChatEventOutboxMapper outbox;
 private final ExecutionLeaseMapper leases;
 public boolean ping(){return operations.ping()==1;}
 public List<Map<String,Object>> documents(){return documents.counts();}
 public List<Map<String,Object>> outbox(){return outbox.counts();}
 public long activeLeases(){return leases.activeCount();}
}
