package dev.arjun.urlshortener.domain;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class CounterIdGenerator implements IdGenerator {

    private final AtomicLong counter = new AtomicLong();

    @Override
    public String nextId() {
        long next = counter.incrementAndGet();
        return Base62.encode(next);
    }
}