package dev.arjun.urlshortner.api;

import dev.arjun.urlshortner.domain.UrlShortner;
import dev.arjun.urlshortner.dto.ShortenRequest;
import dev.arjun.urlshortner.dto.ShortenResponse;
import dev.arjun.urlshortner.model.Redirection;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@RestController
public class RedirectionController {

    private final UrlShortner urlShortner;

    public RedirectionController(UrlShortner urlShortner) {
        this.urlShortner = urlShortner;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        Redirection redirection = urlShortner.shorten(request.longUrl());

        URI shortUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/{id}")
                .buildAndExpand(redirection.id())
                .toUri();

        return ResponseEntity.created(shortUrl)
                .body(new ShortenResponse(redirection.id(), shortUrl.toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Void> redirect(@PathVariable String id) {
        Optional<Redirection> redirection = urlShortner.resolve(id);

        if (redirection.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirection.get().longUrl())
                .build();
    }
}