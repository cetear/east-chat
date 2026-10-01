package com.easychat.core.router;

import com.easychat.llm.provider.LLMProvider;
import lombok.Getter;
import java.util.concurrent.atomic.AtomicInteger;

/** A route owns configuration; refreshed routes share the live circuit state. */
@Getter
public class ProviderWrapper {
    private final LLMProvider provider;
    private final int priority, weight, circuitBreakerThreshold, maxRetry;
    private final long circuitBreakerWindowMs;
    @lombok.Setter private boolean vision = true, visionTools = true;
    private volatile State state = new State();
    private static final class State {
        final AtomicInteger failures = new AtomicInteger();
        long lastFailure;
        boolean probe;
        long epoch;
        long successes,errors,elapsedNanos;
    }
    public ProviderWrapper(LLMProvider provider,int priority,int weight,int threshold,int windowSeconds) {
        this(provider,priority,weight,threshold,windowSeconds,0);
    }
    public ProviderWrapper(LLMProvider provider,int priority,int weight,int threshold,int windowSeconds,int maxRetry) {
        if(weight<1 || threshold<1 || windowSeconds<0 || maxRetry<0 || maxRetry>3)
            throw new IllegalArgumentException("Invalid route weight, breaker or retry configuration (maxRetry: 0-3)");
        this.provider=provider;this.priority=priority;this.weight=weight;this.circuitBreakerThreshold=threshold;
        this.circuitBreakerWindowMs=windowSeconds*1000L;this.maxRetry=maxRetry;
    }
    /** Observation only; does not consume the half-open permit. */
    public boolean isAvailable() {
        State s=state;
        synchronized(s) { return s.failures.get()<circuitBreakerThreshold || (!s.probe && System.currentTimeMillis()-s.lastFailure>=circuitBreakerWindowMs); }
    }
    public Permit acquire() {
        State s=state;
        synchronized(s) {
            boolean probe=s.failures.get()>=circuitBreakerThreshold;
            if(probe) {
                if(s.probe || System.currentTimeMillis()-s.lastFailure<circuitBreakerWindowMs) return null;
                s.probe=true;
            }
            return new Permit(s,s.epoch,probe);
        }
    }
    public final class Permit {
        private final State owner;
        private final long epoch;
        private final boolean probe;
        private boolean finished;
        private final long started=System.nanoTime();
        private Permit(State owner,long epoch,boolean probe) {this.owner=owner;this.epoch=epoch;this.probe=probe;}
        public void success() { finish(1); }
        public void failure() { finish(0); }
        public void cancel() { finish(2); }
        private void finish(int outcome) {
            synchronized(owner) {
                if(finished) return;
                finished=true;
                if(outcome==1)owner.successes++;if(outcome==0)owner.errors++;
                if(outcome!=2)owner.elapsedNanos+=System.nanoTime()-started;
                if(owner.epoch!=epoch)return;
                if(outcome==2) {if(probe) owner.probe=false;return;}
                if(outcome==1) {owner.failures.set(0);owner.probe=false;if(probe) owner.epoch++;return;}
                owner.lastFailure=System.currentTimeMillis();owner.failures.incrementAndGet();owner.probe=false;
                if(owner.failures.get()>=circuitBreakerThreshold) owner.epoch++;
            }
        }
    }
    public void releaseCancelled() {
        State s=state;
        synchronized(s) { s.probe=false; }
    }
    public void recordSuccess() {
        State s=state;
        synchronized(s) { s.failures.set(0);s.probe=false; }
    }
    public void recordFailure() {
        State s=state;
        synchronized(s) { s.lastFailure=System.currentTimeMillis();s.failures.incrementAndGet();s.probe=false; }
    }
    public void inheritState(ProviderWrapper previous) { state=previous.state; }
    public java.util.Map<String,Object> diagnostics() {
        State s=state;synchronized(s){return java.util.Map.of("providerCode",provider.getProviderCode(),"priority",priority,"weight",weight,"maxRetry",maxRetry,
            "circuit",getCircuitStatus(),"consecutiveFailures",s.failures.get(),"successes",s.successes,"errors",s.errors,
            "averageLatencyMs",s.successes+s.errors==0?0:s.elapsedNanos/1_000_000.0/(s.successes+s.errors));}
    }
    public AtomicInteger getFailCount() { return state.failures; }
    public long getLastFailTimeMs() { synchronized(state) { return state.lastFailure; } }
    public String getCircuitStatus() {
        State s=state;
        synchronized(s) { return s.probe ? "HALF_OPEN" : s.failures.get()>=circuitBreakerThreshold ? "OPEN" : "CLOSED"; }
    }
}