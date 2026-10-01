package com.easychat.rag.pipeline;
import org.springframework.stereotype.Component;
import com.easychat.infra.coordination.MysqlLeaseService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
@Component
public class DocumentOperationLock {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private MysqlLeaseService distributed;
    private final ReentrantLock[] locks=java.util.stream.IntStream.range(0,64).mapToObj(i -> new ReentrantLock()).toArray(ReentrantLock[]::new);
    public Guard acquire(String code) {
        ReentrantLock lock=locks[Math.floorMod(code.hashCode(),locks.length)];lock.lock();
        try {return new Guard(lock,distributed==null?null:distributed.acquire(code.startsWith("upload:")?code:"doc:"+code));}
        catch(RuntimeException e) {lock.unlock();throw e;}
    }
    public static final class Guard implements AutoCloseable {
        private final ReentrantLock lock;private final MysqlLeaseService.Lease lease;
        private Guard(ReentrantLock lock,MysqlLeaseService.Lease lease) {this.lock=lock;this.lease=lease;}
        public void check() {if(lease!=null) lease.check();}
        public <T>T guarded(Supplier<T> work) {return lease==null?work.get():lease.guarded(work);}
        public void close() {try{if(lease!=null)lease.close();}finally{lock.unlock();}}
    }
}