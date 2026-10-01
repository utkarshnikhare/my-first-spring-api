package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.User;
import com.example.my_first_spring_api.model.UserRole;
import com.example.my_first_spring_api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Guards the two safety gaps the Platform Console exposed in {@link AdminService}:
 *
 * <ul>
 *   <li>{@code createAdmin} accepted any string as a mobile number, so the console
 *       could create an unusable admin (the column is UNIQUE but nullable) or
 *       trigger a constraint-violation 500 on a repeat submission.</li>
 *   <li>{@code demoteAdmin} had no last-admin guard, so a single mis-click could
 *       leave the platform with no administrator account.</li>
 * </ul>
 *
 * <p>It also pins the SELLER refusal: changing that role would orphan the
 * seller's kitchen and every offering under it.
 */
class AdminServiceAdminManagementTest {

    @Mock private UserRepository userRepository;
    @Mock private AnalyticsService analyticsService;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private KitchenRepository kitchenRepository;
    @Mock private EnquiryRepository enquiryRepository;
    @Mock private FavouriteRepository favouriteRepository;
    @Mock private SocietyDirectory societyDirectory;

    @InjectMocks private AdminService adminService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Echo the argument back so the service's return value is the same instance.
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User existingUser(Long id, String name, String mobile, UserRole role) {
        User user = new User(name, mobile, null, role);
        user.setId(id);
        when(userRepository.findByMobileNumber(mobile)).thenReturn(Optional.of(user));
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    // ---------------- createAdmin: mobile validation ----------------

    @Test
    void createAdminRejectsNullMobile() {
        assertThatThrownBy(() -> adminService.createAdmin("Admin", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid 10-digit mobile");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createAdminRejectsMalformedMobiles() {
        for (String bad : new String[]{"", "   ", "12345", "1234567890", "98765432101", "987654321a"}) {
            assertThatThrownBy(() -> adminService.createAdmin("Admin", bad))
                    .as("mobile '%s' must be rejected", bad)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("valid 10-digit mobile");
        }
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).findByMobileNumber(any());
    }

    @Test
    void createAdminTrimsSurroundingWhitespace() {
        User created = adminService.createAdmin("Trimmed Admin", " 9876543210 ");

        assertThat(created.getMobileNumber()).isEqualTo("9876543210");
        assertThat(created.getRole()).isEqualTo(UserRole.ADMIN);
    }

    // ---------------- createAdmin: role guards ----------------

    @Test
    void createAdminRejectsSellerTarget() {
        existingUser(5L, "Aarti", "9100000001", UserRole.SELLER);

        assertThatThrownBy(() -> adminService.createAdmin("Promoted", "9100000001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to a Seller")
                .hasMessageContaining("detach their kitchen");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createAdminRejectsAlreadySuperAdminTarget() {
        existingUser(1L, "Super Admin", "9000000001", UserRole.SUPER_ADMIN);

        assertThatThrownBy(() -> adminService.createAdmin("X", "9000000001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already a Super Admin");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createAdminPromotesAnExistingBuyer() {
        User buyer = existingUser(9L, "Buyer", "9876543210", UserRole.BUYER);

        User result = adminService.createAdmin("New Admin Name", "9876543210");

        assertThat(result.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(result.getName()).isEqualTo("New Admin Name");
        assertThat(buyer.getRole()).isEqualTo(UserRole.ADMIN);
        verify(userRepository, times(1)).save(buyer);
    }

    @Test
    void createAdminCreatesANewAccountWhenTheMobileIsUnknown() {
        when(userRepository.findByMobileNumber("9777777777")).thenReturn(Optional.empty());

        User result = adminService.createAdmin("Fresh Admin", "9777777777");

        assertThat(result.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(result.getMobileNumber()).isEqualTo("9777777777");
        assertThat(result.getName()).isEqualTo("Fresh Admin");
        verify(userRepository, times(1)).save(result);
    }

    @Test
    void createAdminFallsBackToAPlaceholderNameWhenNameIsBlank() {
        when(userRepository.findByMobileNumber("9777777778")).thenReturn(Optional.empty());

        User result = adminService.createAdmin("   ", "9777777778");

        assertThat(result.getName()).isEqualTo("Admin");
    }

    // ---------------- demoteAdmin: safeguards ----------------

    @Test
    void demoteAdminRejectsTheLastRemainingAdmin() {
        existingUser(2L, "Platform Admin", "9000000002", UserRole.ADMIN);
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> adminService.demoteAdmin(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last Admin account");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void demoteAdminAllowsWhenAnotherAdminExists() {
        User admin = existingUser(2L, "Platform Admin", "9000000002", UserRole.ADMIN);
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(2L);

        User result = adminService.demoteAdmin(2L);

        assertThat(result.getRole()).isEqualTo(UserRole.BUYER);
        verify(userRepository, times(1)).save(admin);
    }

    @Test
    void demoteAdminNeverDemotesASuperAdmin() {
        existingUser(1L, "Super Admin", "9000000001", UserRole.SUPER_ADMIN);

        assertThatThrownBy(() -> adminService.demoteAdmin(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Super Admin accounts cannot be demoted");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void demoteAdminRejectsANonAdminAccount() {
        existingUser(7L, "Seller", "9100000002", UserRole.SELLER);

        assertThatThrownBy(() -> adminService.demoteAdmin(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not an admin");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void demoteAdminRejectsAnUnknownUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.demoteAdmin(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("404");
    }

    @Test
    void lastAdminGuardFiresBeforeAnyWrite() {
        User target = existingUser(2L, "Platform Admin", "9000000002", UserRole.ADMIN);
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> adminService.demoteAdmin(2L))
                .isInstanceOf(IllegalArgumentException.class);

        // Unchanged in memory as well as unpersisted.
        assertThat(target.getRole()).isEqualTo(UserRole.ADMIN);
        verify(userRepository, never()).save(any(User.class));
    }
}
