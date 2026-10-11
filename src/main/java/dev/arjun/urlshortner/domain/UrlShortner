package dev.arjun.urlshortner.domain;

import dev.arjun.urlshortner.dao.RedirectionDao;
import dev.arjun.urlshortner.model.Redirection;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UrlShortner {

    private final IdGenerator idGenerator;
    private final RedirectionDao redirectionDao;

    public UrlShortner(IdGenerator idGenerator, RedirectionDao redirectionDao) {
        this.idGenerator = idGenerator;
        this.redirectionDao = redirectionDao;
    }

    public Redirection shorten(String longUrl) {
        String id = idGenerator.nextId();
        Redirection redirection = new Redirection(id, longUrl);
        redirectionDao.save(redirection);
        return redirection;
    }

    public Optional<Redirection> resolve(String id) {
        return redirectionDao.findById(id);
    }
}