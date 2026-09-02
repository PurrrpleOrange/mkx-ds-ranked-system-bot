package com.mkx.ranked.integration;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
abstract class PostgreSqlIntegrationTestSupport {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("mkx_ranked_test")
            .withUsername("mkx")
            .withPassword("mkx");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("discord.token", () -> "integration-test-token");
    }

    @MockitoBean
    JDA jda;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE matches, season_players, players, seasons RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("ALTER SEQUENCE season_number_seq RESTART WITH 1");
    }
}
