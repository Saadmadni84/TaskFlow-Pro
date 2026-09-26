package com.taskflow.taskflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgresConnectionIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void verifiesDatabaseConnectionAndFlywayBaseline() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM schema_baseline")) {

            assertThat(connection.isValid(2)).isTrue();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);

            try (ResultSet rsTables = statement.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
                java.util.List<String> tables = new java.util.ArrayList<>();
                while (rsTables.next()) {
                    tables.add(rsTables.getString(1).toLowerCase());
                }
                assertThat(tables).contains("projects", "tasks", "task_dependencies");
            }
        }
    }
}
