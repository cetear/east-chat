package com.easychat.core.service.chat;
import com.easychat.common.exception.BusinessException;
import com.easychat.infra.coordination.MysqlLeaseService;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
@Component
public class SessionExecutionLock {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private MysqlLeaseService distributed;
    private final Set<Long> active=ConcurrentHashMap.newKeySet();
    public Guard acquire(Long id) {
        if(!active.add(id)) throw new BusinessException("409","Session has an active operation");
        try {return new Guard(id,distributed==null?null:distributed.acquire("session:"+id));}
        catch(RuntimeException e) {active.remove(id);throw e;}
    }
    public final class Guard implements AutoCloseable {
        private final Long id;private final MysqlLeaseService.Lease lease;
        private Guard(Long id,MysqlLeaseService.Lease lease) {this.id=id;this.lease=lease;}
        public void check() {if(lease!=null) lease.check();}
        public <T>T guarded(Supplier<T> work) {return lease==null?work.get():lease.guarded(work);}
        public void close() {try{if(lease!=null)lease.close();}finally{active.remove(id);}}
    }
}