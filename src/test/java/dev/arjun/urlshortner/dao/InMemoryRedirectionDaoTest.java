package dev.arjun.urlshortner.dao;

import dev.arjun.urlshortner.model.Redirection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRedirectionDaoTest {

    private final InMemoryRedirectionDao dao = new InMemoryRedirectionDao();

    @Test
    void findsSavedRedirection() {
        Redirection redirection = new Redirection("21", "https://google.com");

        dao.save(redirection);

        assertThat(dao.findById("21")).contains(redirection);
    }

    @Test
    void returnsEmptyForUnknownId() {
        assertThat(dao.findById("does-not-exist")).isEmpty();
    }

    @Test
    void rejectsDuplicateId() {
        dao.save(new Redirection("21", "https://alice.com"));

        assertThatThrownBy(() -> dao.save(new Redirection("21", "https://bob.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("21");

        assertThat(dao.findById("21").get().longUrl()).isEqualTo("https://alice.com");
    }
}