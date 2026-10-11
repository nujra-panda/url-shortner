package dev.arjun.urlshortner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShortenRequest(
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "^https?://\\S+$", message = "must be an http:// or https:// URL")
        String longUrl) {
}
