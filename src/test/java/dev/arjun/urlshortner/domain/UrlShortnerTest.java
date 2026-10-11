package dev.arjun.urlshortner.domain;

import dev.arjun.urlshortner.dao.InMemoryRedirectionDao;
import dev.arjun.urlshortner.model.Redirection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UrlShortnerTest {

    private final InMemoryRedirectionDao dao = new InMemoryRedirectionDao();

    @Test
    void shortenUsesGeneratedIdAndKeepsLongUrl() {
        UrlShortner shortener = new UrlShortner(() -> "abc", dao);

        Redirection redirection = shortener.shorten("https://google.com");

        assertThat(redirection.id()).isEqualTo("abc");
        assertThat(redirection.longUrl()).isEqualTo("https://google.com");
    }

    @Test
    void shortenedUrlCanBeResolved() {
        UrlShortner shortener = new UrlShortner(new CounterIdGenerator(), dao);

        Redirection created = shortener.shorten("https://google.com");

        assertThat(shortener.resolve(created.id())).contains(created);
    }

    @Test
    void resolvingUnknownIdReturnsEmpty() {
        UrlShortner shortener = new UrlShortner(new CounterIdGenerator(), dao);

        assertThat(shortener.resolve("nope")).isEmpty();
    }

    @Test
    void duplicateIdFailsAndKeepsOriginalLink() {
        UrlShortner shortener = new UrlShortner(() -> "same", dao);
        shortener.shorten("https://alice.com");

        assertThatThrownBy(() -> shortener.shorten("https://bob.com"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(shortener.resolve("same").get().longUrl()).isEqualTo("https://alice.com");
    }

    @Test
    void resolvingNeverGeneratesAnId() {
        IdGenerator idGenerator = mock(IdGenerator.class);
        UrlShortner shortener = new UrlShortner(idGenerator, dao);

        shortener.resolve("anything");

        verify(idGenerator, never()).nextId();
    }
}