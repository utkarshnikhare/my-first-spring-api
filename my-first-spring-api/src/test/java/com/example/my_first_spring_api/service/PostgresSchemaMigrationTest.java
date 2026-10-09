package com.example.my_first_spring_api.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.my_first_spring_api.SecurityConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "SOCIOMART_DB_JDBC_URL=jdbc:h2:mem:postgres_schema_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "SOCIOMART_DB_USERNAME=sa",
                "SOCIOMART_DB_PASSWORD=",
                "spring.datasource.url=jdbc:h2:mem:postgres_schema_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.defer-datasource-initialization=false"
        })
@ActiveProfiles("postgres-demo")
class PostgresSchemaMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Test
    void initialMigrationCreatesSchemaThatMatchesAllJpaEntities() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(SecurityConfig.isDemoEnvironment(environment)).isTrue();

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'PUBLIC' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                Integer.class);
        Assertions.assertNotNull(tableCount);
        assertThat(tableCount).isEqualTo(23);

        Integer foreignKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_schema = 'PUBLIC' AND constraint_type = 'FOREIGN KEY'",
                Integer.class);
        Assertions.assertNotNull(foreignKeyCount);
        assertThat(foreignKeyCount).isGreaterThanOrEqualTo(20);
    }
}
