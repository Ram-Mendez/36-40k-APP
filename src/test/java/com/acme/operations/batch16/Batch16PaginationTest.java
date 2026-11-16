package com.acme.operations.batch16;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.operations.common.PageResponse;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Exercises API page metadata with in-memory data, without repository queries. */
class Batch16PaginationTest {
    @Test
    void twentyOneResultsNeedTwoPagesOfTwenty() {
        var items = IntStream.rangeClosed(1, 20).boxed().toList();
        var response = PageResponse.from(new PageImpl<>(items, PageRequest.of(0, 20), 21));

        assertThat(response.content()).containsExactlyElementsOf(items);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    void lastPageKeepsItsItemAndTheSameTotalPageCount() {
        var response = PageResponse.from(
                new PageImpl<>(List.of(21), PageRequest.of(1, 20), 21));

        assertThat(response.content()).containsExactly(21);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "20, 1", "40, 2"})
    void emptySmallAndExactPagesRemainConsistent(int total, int expectedPages) {
        var items = IntStream.rangeClosed(1, Math.min(total, 20)).boxed().toList();
        var response = PageResponse.from(new PageImpl<>(items, PageRequest.of(0, 20), total));
        assertThat(response.totalPages()).isEqualTo(expectedPages);
        assertThat(response.totalElements()).isEqualTo(total);
    }
}
