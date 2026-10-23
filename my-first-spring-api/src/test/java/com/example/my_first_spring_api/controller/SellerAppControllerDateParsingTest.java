package com.example.my_first_spring_api.controller;

import com.example.my_first_spring_api.service.BuyerService;
import com.example.my_first_spring_api.service.SellerAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SellerAppControllerDateParsingTest {

    @Mock
    private SellerAppService sellerAppService;
    @Mock
    private BuyerService authService;

    private SellerAppController controller;

    @BeforeEach
    void setUp() {
        controller = new SellerAppController(sellerAppService, authService);
    }

    @Test
    void parseDateReturnsNullForAll() {
        LocalDate result = ReflectionTestUtils.invokeMethod(controller, "parseDate", "all");
        assertThat(result).isNull();

        LocalDate resultUpper = ReflectionTestUtils.invokeMethod(controller, "parseDate", "ALL");
        assertThat(resultUpper).isNull();
    }

    @Test
    void parseDateHandlesTodayTomorrowYesterdayAndIso() {
        LocalDate today = ReflectionTestUtils.invokeMethod(controller, "parseDate", "today");
        assertThat(today).isEqualTo(LocalDate.now());

        LocalDate tomorrow = ReflectionTestUtils.invokeMethod(controller, "parseDate", "tomorrow");
        assertThat(tomorrow).isEqualTo(LocalDate.now().plusDays(1));

        LocalDate yesterday = ReflectionTestUtils.invokeMethod(controller, "parseDate", "yesterday");
        assertThat(yesterday).isEqualTo(LocalDate.now().minusDays(1));

        LocalDate specific = ReflectionTestUtils.invokeMethod(controller, "parseDate", "2026-10-25");
        assertThat(specific).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void parseDateFallsBackToTodayOnMalformedOrBlankInput() {
        LocalDate blank = ReflectionTestUtils.invokeMethod(controller, "parseDate", "   ");
        assertThat(blank).isEqualTo(LocalDate.now());

        LocalDate nullVal = ReflectionTestUtils.invokeMethod(controller, "parseDate", (String) null);
        assertThat(nullVal).isEqualTo(LocalDate.now());

        LocalDate invalid = ReflectionTestUtils.invokeMethod(controller, "parseDate", "not-a-date");
        assertThat(invalid).isEqualTo(LocalDate.now());
    }
}
