package dev.arjun.urlshortener.dao;

import dev.arjun.urlshortener.model.Redirection;

import java.util.Optional;

public interface RedirectionDao {

    void save(Redirection redirection);

    Optional<Redirection> findById(String id);
}