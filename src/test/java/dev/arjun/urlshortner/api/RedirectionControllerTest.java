package dev.arjun.urlshortner.api;

import dev.arjun.urlshortner.domain.UrlShortner;
import dev.arjun.urlshortner.model.Redirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedirectionController.class)
class RedirectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlShortner urlShortner;

    @Test
    void shortenReturns201WithShortUrl() throws Exception {
        when(urlShortner.shorten("https://google.com"))
                .thenReturn(new Redirection("21", "https://google.com"));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"longUrl": "https://google.com"}
                                  """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/21"))
                .andExpect(jsonPath("$.id").value("21"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost/21"));
    }

    @Test
    void redirectReturns302ToLongUrl() throws Exception {
        when(urlShortner.resolve("21"))
                .thenReturn(Optional.of(new Redirection("21", "https://google.com")));

        mockMvc.perform(get("/21"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://google.com"));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        when(urlShortner.resolve("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "google.com",
            "ftp://files.example.com",
            "javascript:alert(1)",
            "https://has spaces.com"
    })
    void invalidUrlReturns400(String longUrl) throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"longUrl": "%s"}
                                  """.formatted(longUrl)))
                .andExpect(status().isBadRequest());

        verify(urlShortner, never()).shorten(any());
    }

    @Test
    void tooLongUrlReturns400() throws Exception {
        String tooLong = "https://example.com/" + "a".repeat(2048);

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"longUrl": "%s"}
                                  """.formatted(tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingLongUrlReturns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}