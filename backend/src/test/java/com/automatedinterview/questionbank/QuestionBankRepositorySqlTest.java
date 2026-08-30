package com.automatedinterview.questionbank;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class QuestionBankRepositorySqlTest {
    @Test
    void listQuerySupportsFiltersAndPagination() {
        String query = QuestionBankRepository.listQuery();

        assertTrue(query.contains("LIMIT :size OFFSET :offset"));
        assertTrue(query.contains("LOWER(stem) LIKE"));
        assertTrue(query.contains("primary_skill = :skill"));
        assertTrue(query.contains("origin = :origin"));
    }

    @Test
    void rejectsPageOffsetsThatOverflowIntegerRange() {
        assertThrows(IllegalArgumentException.class, () -> QuestionBankController.paginationOffset(Integer.MAX_VALUE, 100));
    }
}
