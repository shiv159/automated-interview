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
        assertTrue(query.contains("primary_skill = CAST(:skill AS text)"));
        assertTrue(query.contains("origin = CAST(:origin AS text)"));
        assertTrue(query.contains("CAST(:search AS text) IS NULL"));
        assertTrue(query.contains("CAST(:skill AS text) IS NULL"));
        assertTrue(query.contains("CAST(:difficulty AS text) IS NULL"));
        assertTrue(query.contains("CAST(:origin AS text) IS NULL"));
    }

    @Test
    void rejectsPageOffsetsThatOverflowIntegerRange() {
        assertThrows(IllegalArgumentException.class, () -> QuestionBankController.paginationOffset(Integer.MAX_VALUE, 100));
    }
}
