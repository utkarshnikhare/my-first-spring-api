
package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JacksonProbeTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bootManagedJacksonMapperSerializesJavaTimeValues() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "date", LocalDate.of(2026, 10, 8),
                "time", LocalTime.of(13, 30)));

        assertThat(json)
                .contains("\"date\":\"2026-10-08\"")
                .contains("\"time\":\"13:30:00\"");
    }
}
