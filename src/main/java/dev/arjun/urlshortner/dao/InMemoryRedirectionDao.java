package dev.arjun.urlshortner.dao;

import dev.arjun.urlshortner.model.Redirection;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryRedirectionDao implements RedirectionDao {

    private final Map<String, Redirection> store = new ConcurrentHashMap<>();

    @Override
    public void save(Redirection redirection) {
        Redirection existing = store.putIfAbsent(redirection.id(), redirection);
        if (existing != null) {
            throw new IllegalStateException("Id already exists: " + redirection.id());
        }
    }

    @Override
    public Optional<Redirection> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}