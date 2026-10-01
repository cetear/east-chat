package com.easychat.infra.coordination;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.easychat.infra.mysql.mapper.ExecutionLeaseMapper;
import com.easychat.infra.mysql.mapper.ChatMessageMapper;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
@Service
@ConditionalOnProperty(name="easychat.distributed.enabled",havingValue="true")
public class MysqlLeaseService {
    private final ExecutionLeaseMapper leases;
    private final ChatMessageMapper messages;
    private final TransactionTemplate tx;
    private final Map<String,Lease> held=new ConcurrentHashMap<>();
    public MysqlLeaseService(ExecutionLeaseMapper leases,ChatMessageMapper messages,PlatformTransactionManager manager) {this.leases=leases;this.messages=messages;tx=new TransactionTemplate(manager);}
    public Lease acquire(String key) {
        String token=UUID.randomUUID().toString();
        leases.ensure(key);
        if(leases.acquire(key,token)!=1)
            throw new com.easychat.common.exception.BusinessException("409","Resource is busy");
        Lease lease=new Lease(key,token);held.put(key,lease);return lease;
    }
    @Scheduled(fixedDelay=10000,scheduler="leaseScheduler") public void renew() {
        held.values().forEach(l->{if(l.lost)return;try {if(leases.renew(l.key,l.token)!=1) l.lost=true;}catch(Exception e){l.lost=true;}});
    }
    @Scheduled(fixedDelay=30000) public void recoverInterruptedChats() {
        messages.recoverInterrupted();
    }
    public final class Lease implements AutoCloseable {
        private final String key,token;
        private volatile boolean lost;
        private Lease(String key,String token) {this.key=key;this.token=token;}
        public void check() {
            if(lost || leases.valid(key,token)!=1)
                throw new IllegalStateException("Lease lost; stale worker cannot publish");
        }
        public <T>T guarded(Supplier<T> action) {
            return tx.execute(status->{
                var tokens=leases.lockOwner(key);
                if(lost || tokens.size()!=1 || !token.equals(tokens.get(0))) throw new IllegalStateException("Lease lost");
                return action.get();
            });
        }
        public void close() {
            held.remove(key,this);
            leases.release(key,token);
        }
    }
}
