package dev.arjun.urlshortner.dao;

import dev.arjun.urlshortner.model.Redirection;

import java.util.Optional;

public interface RedirectionDao {

    void save(Redirection redirection);

    Optional<Redirection> findById(String id);
}