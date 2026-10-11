package dev.arjun.urlshortener.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62Test {

    @ParameterizedTest
    @CsvSource({
            "0,    0",
            "9,    9",
            "10,   a",
            "35,   z",
            "36,   A",
            "61,   Z",
            "62,   10",
            "125,  21",
            "3843, ZZ",
            "3844, 100"
    })
    void encodesKnownValues(long number, String expected) {
        assertThat(Base62.encode(number)).isEqualTo(expected);
    }

    @Test
    void alphabetNeverChanges() {
        String allSingleDigits = LongStream.range(0, 62)
                .mapToObj(Base62::encode)
                .collect(Collectors.joining());

        assertThat(allSingleDigits)
                .isEqualTo("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
    }

    @Test
    void differentNumbersGiveDifferentIds() {
        Set<String> ids = new HashSet<>();
        for (long n = 0; n < 100_000; n++) {
            ids.add(Base62.encode(n));
        }

        assertThat(ids).hasSize(100_000);
    }

    @Test
    void largestLongFitsInElevenCharacters() {
        assertThat(Base62.encode(Long.MAX_VALUE)).hasSize(11);
    }

    @Test
    void rejectsNegativeNumbers() {
        assertThatThrownBy(() -> Base62.encode(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }
}