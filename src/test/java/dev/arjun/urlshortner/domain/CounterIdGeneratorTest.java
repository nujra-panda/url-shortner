package dev.arjun.urlshortner.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class CounterIdGeneratorTest {

    private final CounterIdGenerator generator = new CounterIdGenerator();

    @Test
    void generatesSequentialBase62Ids() {
        assertThat(generator.nextId()).isEqualTo("1");
        assertThat(generator.nextId()).isEqualTo("2");
        assertThat(generator.nextId()).isEqualTo("3");
    }

    @Test
    void neverReturnsDuplicateIdsUnderConcurrency() {
        int threads = 200;
        int idsPerThread = 1_000;
        Set<String> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch startSignal = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    startSignal.await();
                    for (int i = 0; i < idsPerThread; i++) {
                        ids.add(generator.nextId());
                    }
                    return null;
                });
            }
            startSignal.countDown();
        }

        assertThat(ids).hasSize(threads * idsPerThread);
    }
}