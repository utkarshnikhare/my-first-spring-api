package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.Kitchen;
import com.example.my_first_spring_api.model.Product;
import com.example.my_first_spring_api.model.RecurringSchedule;
import com.example.my_first_spring_api.model.Occurrence;
import com.example.my_first_spring_api.model.SellerApprovalStatus;
import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.KitchenRepository;
import com.example.my_first_spring_api.repository.OccurrenceRepository;
import com.example.my_first_spring_api.repository.ProductRepository;
import com.example.my_first_spring_api.repository.RecurringScheduleRepository;
import com.example.my_first_spring_api.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** HTTP cover: sellers read/patch ONLY their own schedules (403 otherwise). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recurring-schedule-http;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class RecurringScheduleHttpTest {

    @Autowired private WebApplicationContext ctx;
    @Autowired private UserRepository users;
    @Autowired private KitchenRepository kitchens;
    @Autowired private ProductRepository products;
    @Autowired private RecurringScheduleRepository schedules;
    @Autowired private OccurrenceRepository occurrences;

    private MockMvc mvc;

    private final class Browser {
        MockHttpSession session;
        String csrf;

        MockHttpServletRequestBuilder req(MockHttpServletRequestBuilder b) {
            if (session != null) b = b.session(session);
            if (csrf != null) {
                b = b.cookie(new Cookie("XSRF-TOKEN", csrf))
                         .header("X-XSRF-TOKEN", csrf);
            }
            return b;
        }

        MvcResult call(MockHttpServletRequestBuilder b) throws Exception {
            MvcResult r = mvc.perform(req(b)).andReturn();
            Object used = r.getRequest().getSession(false);
            if (used != null) session = (MockHttpSession) used;
            jakarta.servlet.http.Cookie[] cookies = r.getResponse().getCookies();
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie c : cookies) {
                    if ("XSRF-TOKEN".equals(c.getName()) && c.getValue() != null) csrf = c.getValue();
                }
            }
            return r;
        }
    }

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(ctx).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    private User seller(String tag) {
        String s = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        // Deterministic-but-unique 10-digit mobile per call: hash the UUID, not
        // the tag, so two sellers created in one test can never collide (a
        // collision silently reuses one User row and breaks ownership asserts).
        String digits = String.format("%010d", Math.abs((long) s.hashCode() * 31 + System.nanoTime() % 1_000_000) % 10_000_000_000L);
        User u = users.save(new User(tag + s, digits, null, UserRole.SELLER));
        u.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);
        return users.save(u);
    }

    private Browser browserAs(User u) {
        Browser b = new Browser();
        b.session = new MockHttpSession();
        b.session.setAttribute(BuyerService.BUYER_SESSION_KEY, u.getId());
        return b;
    }

    private RecurringSchedule scheduleFor(User owner) {
        String k = UUID.randomUUID().toString().substring(0, 6);
        Kitchen kitchen = kitchens.save(new Kitchen("k" + k, "Kitchen", "", null, owner));
        Product product = new Product(kitchen, "Poha", "", BigDecimal.valueOf(25), null);
        product.setAvailableDate(LocalDate.now().plusDays(1));
        product.setOrderWindowEnd("13:00");
        product.setReadyByTime("3:00 PM");
        product = products.save(product);
        RecurringSchedule sch = new RecurringSchedule(product, LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(15),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        sch.setDefaultQuantity(10);
        sch.setDefaultOrderCloseTime("13:00");
        sch.setDefaultReadyByTime("3:00 PM");
        sch = schedules.save(sch);
        Occurrence first = new Occurrence(sch, LocalDate.now().plusDays(1));
        first.setQuantity(10);
        first.setOrderCloseTime("13:00");
        first.setReadyByTime("3:00 PM");
        occurrences.save(first);
        Occurrence second = new Occurrence(sch, LocalDate.now().plusDays(2));
        second.setQuantity(10);
        second.setOrderCloseTime("13:00");
        second.setReadyByTime("3:00 PM");
        occurrences.save(second);
        return sch;
    }

    @Test
    void sellerListsOwnSchedulesButCannotReadAnotherSellersOccurrences() throws Exception {
        User owner = seller("Owner");
        User stranger = seller("Stranger");
        RecurringSchedule sch = scheduleFor(owner);

        Browser mine = browserAs(owner);
        assertThat(mine.call(mine.req(get("/api/seller/schedules"))).getResponse().getStatus()).isEqualTo(200);
        String rows = mine.call(mine.req(get("/api/seller/schedules/" + sch.getId() + "/occurrences")))
                .getResponse().getContentAsString();
        assertThat(rows).contains("\"quantity\":10");

        Browser other = browserAs(stranger);
        String strangerList = other.call(other.req(get("/api/seller/schedules")))
                .getResponse().getContentAsString();
        assertThat(strangerList).doesNotContain("\"scheduleId\":" + sch.getId());
        int forbidden = other.call(other.req(get("/api/seller/schedules/" + sch.getId() + "/occurrences")))
                .getResponse().getStatus();
        assertThat(forbidden).isEqualTo(403);
    }

    @Test
    void patchTouchesOnlyTheTargetOccurrence() throws Exception {
        User owner = seller("Owner");
        RecurringSchedule sch = scheduleFor(owner);
        java.util.List<Occurrence> rows = occurrences.findByScheduleIdOrderByOccurrenceDateAsc(sch.getId());
        Long firstId = rows.get(0).getId();
        Long secondId = rows.get(1).getId();

        Browser mine = browserAs(owner);
        // First GET mints the XSRF cookie the Browser replays; without it the
        // CSRF filter rejects the PATCH with 403 before ownership is checked.
        assertThat(mine.call(mine.req(get("/api/seller/schedules"))).getResponse().getStatus()).isEqualTo(200);
        MvcResult patched = mine.call(mine.req(patch("/api/seller/schedules/occurrences/" + firstId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":25,\"readyByTime\":\"4:00 PM\"}")));
        int patchedStatus = patched.getResponse().getStatus();
        assertThat(patchedStatus)
                .as("PATCH body was " + patched.getResponse().getContentAsString())
                .isEqualTo(200);
        assertThat(patched.getResponse().getContentAsString()).contains("\"quantity\":25");

        String sibling = mine.call(mine.req(get("/api/seller/schedules/occurrences/" + secondId)))
                .getResponse().getContentAsString();
        assertThat(sibling).contains("\"quantity\":10").contains("3:00 PM");
    }

    @Test
    void strangerCannotPatchAnotherSellersOccurrence() throws Exception {
        User owner = seller("Owner");
        User stranger = seller("Stranger");
        RecurringSchedule sch = scheduleFor(owner);
        Long occurrenceId = occurrences.findByScheduleIdOrderByOccurrenceDateAsc(sch.getId()).get(0).getId();

        Browser other = browserAs(stranger);
        assertThat(other.call(other.req(get("/api/seller/schedules"))).getResponse().getStatus()).isEqualTo(200);
        int status = other.call(other.req(patch("/api/seller/schedules/occurrences/" + occurrenceId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":99}")))
                .getResponse().getStatus();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void ongoingExtensionRouteChecksOwnershipAndIsRetrySafe() throws Exception {
        User owner = seller("Owner");
        User stranger = seller("Stranger");
        RecurringSchedule sch = scheduleFor(owner);
        sch.setOngoing(true);
        sch.setEndDate(LocalDate.now().plusDays(1));
        schedules.save(sch);

        Browser other = browserAs(stranger);
        other.call(other.req(get("/api/seller/schedules")));
        assertThat(other.call(other.req(post("/api/seller/schedules/" + sch.getId() + "/extend")))
            .getResponse().getStatus()).isEqualTo(403);

        Browser mine = browserAs(owner);
        mine.call(mine.req(get("/api/seller/schedules")));
        MvcResult first = mine.call(mine.req(post("/api/seller/schedules/" + sch.getId() + "/extend")));
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        int count = occurrences.findByScheduleId(sch.getId()).size();
        MvcResult retry = mine.call(mine.req(post("/api/seller/schedules/" + sch.getId() + "/extend")));
        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(occurrences.findByScheduleId(sch.getId())).hasSize(count);
    }

    @Test
    void manageScheduleGetAndDirectPutContractWorkAndCanSetNoLimit() throws Exception {
        User owner = seller("Owner");
        RecurringSchedule sch = scheduleFor(owner);
        Browser mine = browserAs(owner);
        mine.call(mine.req(get("/api/seller/schedules")));

        MvcResult card = mine.call(mine.req(get("/api/seller/schedules/" + sch.getId())));
        assertThat(card.getResponse().getStatus()).isEqualTo(200);
        assertThat(card.getResponse().getContentAsString())
                .contains("\"productId\":" + sch.getProduct().getId())
                .contains("\"productName\":\"Poha\"");

        String body = "{\"startDate\":\"" + sch.getStartDate() + "\","
                + "\"endDate\":\"" + sch.getEndDate() + "\","
                + "\"recurrenceWeekdays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\","
                + "\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"],"
                + "\"clearDefaultQuantity\":true,\"defaultOrderCloseTime\":\"14:00\","
                + "\"defaultReadyByTime\":\"4:00 PM\",\"ongoing\":false}";
        MvcResult updated = mine.call(mine.req(put("/api/seller/schedules/" + sch.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body)));

        assertThat(updated.getResponse().getStatus())
                .as(updated.getResponse().getContentAsString()).isEqualTo(200);
        RecurringSchedule saved = schedules.findById(sch.getId()).orElseThrow();
        assertThat(saved.getDefaultQuantity()).isNull();
        assertThat(saved.getDefaultOrderCloseTime()).isEqualTo("14:00");
    }
}

