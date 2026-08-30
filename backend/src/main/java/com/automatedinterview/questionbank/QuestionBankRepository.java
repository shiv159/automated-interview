package com.automatedinterview.questionbank;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionBankRepository {
    private final JdbcClient jdbc;

    public QuestionBankRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<QuestionBankController.QuestionSummary> listQuestions() {
        return jdbc.sql("""
                SELECT id, stem, origin, status, type, primary_skill, secondary_skills, difficulty,
                       tags, rubric, ideal_answer, updated_at
                FROM question
                ORDER BY origin, type, primary_skill NULLS LAST, difficulty NULLS LAST, id
                """)
            .query((rs, row) -> new QuestionBankController.QuestionSummary(
                rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("origin"),
                rs.getString("status"), rs.getString("type"), rs.getString("primary_skill"),
                rs.getString("secondary_skills"), rs.getString("difficulty"), rs.getString("tags"),
                rs.getString("rubric"), rs.getString("ideal_answer"),
                rs.getTimestamp("updated_at").toInstant()))
            .list();
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
