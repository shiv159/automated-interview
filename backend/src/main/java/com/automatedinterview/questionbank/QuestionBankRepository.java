package com.automatedinterview.questionbank;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionBankRepository {
    private static final String LIST_QUERY = """
            SELECT id, stem, origin, status, type, primary_skill, secondary_skills, difficulty,
                   tags, rubric, ideal_answer, updated_at
            FROM question
            WHERE (CAST(:search AS text) IS NULL OR LOWER(stem) LIKE LOWER(CAST(:search AS text)))
              AND (CAST(:skill AS text) IS NULL OR primary_skill = CAST(:skill AS text) OR (CAST(:skill AS text) = 'BEHAVIORAL' AND type = 'BEHAVIORAL'))
              AND (CAST(:difficulty AS text) IS NULL OR difficulty = CAST(:difficulty AS text))
              AND (CAST(:origin AS text) IS NULL OR origin = CAST(:origin AS text))
            ORDER BY origin, type, primary_skill NULLS LAST, difficulty NULLS LAST, id
            LIMIT :size OFFSET :offset
            """;
    private final JdbcClient jdbc;

    public QuestionBankRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<QuestionBankController.QuestionSummary> listQuestions(Filter filter, int size, long offset) {
        return jdbc.sql(LIST_QUERY).params(filter.params(size, offset)).query(this::summary).list();
    }

    public List<QuestionBankController.QuestionSummary> listQuestions(Filter filter, int max) {
        return listQuestions(filter, max, 0);
    }

    public long countQuestions(Filter filter) {
        return jdbc.sql("""
                SELECT count(*) FROM question
                WHERE (CAST(:search AS text) IS NULL OR LOWER(stem) LIKE LOWER(CAST(:search AS text)))
                  AND (CAST(:skill AS text) IS NULL OR primary_skill = CAST(:skill AS text) OR (CAST(:skill AS text) = 'BEHAVIORAL' AND type = 'BEHAVIORAL'))
                  AND (CAST(:difficulty AS text) IS NULL OR difficulty = CAST(:difficulty AS text))
                  AND (CAST(:origin AS text) IS NULL OR origin = CAST(:origin AS text))
                """).params(filter.params(0, 0)).query(Long.class).single();
    }

    public long countActiveQuestions(Filter filter) {
        return jdbc.sql("""
                SELECT count(*) FROM question
                WHERE status = 'ACTIVE'
                  AND (CAST(:search AS text) IS NULL OR LOWER(stem) LIKE LOWER(CAST(:search AS text)))
                  AND (CAST(:skill AS text) IS NULL OR primary_skill = CAST(:skill AS text) OR (CAST(:skill AS text) = 'BEHAVIORAL' AND type = 'BEHAVIORAL'))
                  AND (CAST(:difficulty AS text) IS NULL OR difficulty = CAST(:difficulty AS text))
                  AND (CAST(:origin AS text) IS NULL OR origin = CAST(:origin AS text))
                """).params(filter.params(0, 0)).query(Long.class).single();
    }

    public long countSkillAreas(Filter filter) {
        return jdbc.sql("""
                SELECT count(DISTINCT CASE WHEN type = 'BEHAVIORAL' THEN 'BEHAVIORAL' ELSE primary_skill END)
                FROM question
                WHERE (CAST(:search AS text) IS NULL OR LOWER(stem) LIKE LOWER(CAST(:search AS text)))
                  AND (CAST(:skill AS text) IS NULL OR primary_skill = CAST(:skill AS text) OR (CAST(:skill AS text) = 'BEHAVIORAL' AND type = 'BEHAVIORAL'))
                  AND (CAST(:difficulty AS text) IS NULL OR difficulty = CAST(:difficulty AS text))
                  AND (CAST(:origin AS text) IS NULL OR origin = CAST(:origin AS text))
                """).params(filter.params(0, 0)).query(Long.class).single();
    }

    private QuestionBankController.QuestionSummary summary(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new QuestionBankController.QuestionSummary(
            rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("origin"),
            rs.getString("status"), rs.getString("type"), rs.getString("primary_skill"),
            rs.getString("secondary_skills"), rs.getString("difficulty"), rs.getString("tags"),
            rs.getString("rubric"), rs.getString("ideal_answer"), rs.getTimestamp("updated_at").toInstant());
    }

    public static String listQuery() {
        return LIST_QUERY;
    }

    public record Filter(String search, String skill, String difficulty, String origin) {
        public java.util.Map<String, Object> params(int size, long offset) {
            var values = new java.util.HashMap<String, Object>();
            values.put("search", search == null || search.isBlank() ? null : "%" + search.strip() + "%");
            values.put("skill", skill); values.put("difficulty", difficulty); values.put("origin", origin);
            values.put("size", size); values.put("offset", offset);
            return values;
        }
    }

    public List<QuestionBankController.CoverageBucket> coverage() {
        return jdbc.sql("""
                SELECT type, primary_skill, difficulty, status, count(*) AS total
                FROM question
                GROUP BY type, primary_skill, difficulty, status
                ORDER BY type, primary_skill NULLS LAST, difficulty NULLS LAST, status
                """)
            .query((rs, row) -> new QuestionBankController.CoverageBucket(
                rs.getString("type"), rs.getString("primary_skill"), rs.getString("difficulty"),
                rs.getString("status"), rs.getLong("total")))
            .list();
    }
}
