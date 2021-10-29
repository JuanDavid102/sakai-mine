package edu.uc.ltigradebook.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.ksu.lti.launch.model.InstitutionRole;
import edu.uc.ltigradebook.entity.AccountPreference;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    @InjectMocks
    SecurityService securityService;

    @Mock
    AccountService accountService;

    @Test
    public void testIsAdminUser() {
        assertTrue(securityService.isAdminUser(null, Arrays.asList(InstitutionRole.Administrator)));
        assertFalse(securityService.isAdminUser(null, Arrays.asList(InstitutionRole.Faculty)));
        assertFalse(securityService.isAdminUser(null, Arrays.asList(InstitutionRole.Alumni)));
        assertFalse(securityService.isAdminUser(null, Arrays.asList(InstitutionRole.Instructor)));
        assertFalse(securityService.isAdminUser(null, Arrays.asList(InstitutionRole.Student)));
        assertFalse(securityService.isAdminUser(null, Arrays.asList(InstitutionRole.Staff)));
    }

    @Test
    public void testIsStudent() {
        assertTrue(securityService.isStudent(Arrays.asList(InstitutionRole.Student)));
        assertTrue(securityService.isStudent(Arrays.asList(InstitutionRole.Learner)));
        assertFalse(securityService.isStudent(Arrays.asList(InstitutionRole.Administrator)));
        assertFalse(securityService.isStudent(Arrays.asList(InstitutionRole.Faculty)));
        assertFalse(securityService.isStudent(Arrays.asList(InstitutionRole.Alumni)));
        assertFalse(securityService.isStudent(Arrays.asList(InstitutionRole.Instructor)));
        assertFalse(securityService.isStudent(Arrays.asList(InstitutionRole.Staff)));
    }

    @Test
    public void testIsFaculty() {
        assertTrue(securityService.isFaculty(Arrays.asList(InstitutionRole.Administrator)));
        assertTrue(securityService.isFaculty(Arrays.asList(InstitutionRole.Instructor)));
        assertTrue(securityService.isFaculty(Arrays.asList(InstitutionRole.TeachingAssistant)));
        assertFalse(securityService.isFaculty(Arrays.asList(InstitutionRole.Faculty)));
        assertFalse(securityService.isFaculty(Arrays.asList(InstitutionRole.Alumni)));
        assertFalse(securityService.isFaculty(Arrays.asList(InstitutionRole.Student)));
        assertFalse(securityService.isFaculty(Arrays.asList(InstitutionRole.Staff)));
    }

    @Test
    public void testIsTeachingAssistant() {
        assertTrue(securityService.isTeachingAssistant(Arrays.asList(InstitutionRole.TeachingAssistant)));
        assertFalse(securityService.isTeachingAssistant(Arrays.asList(InstitutionRole.Administrator)));
        assertFalse(securityService.isTeachingAssistant(Arrays.asList(InstitutionRole.Faculty)));
        assertFalse(securityService.isTeachingAssistant(Arrays.asList(InstitutionRole.Alumni)));
        assertFalse(securityService.isTeachingAssistant(Arrays.asList(InstitutionRole.Instructor)));
        assertFalse(securityService.isTeachingAssistant(Arrays.asList(InstitutionRole.Student)));
        assertFalse(securityService.isTeachingAssistant(Arrays.asList(InstitutionRole.Staff)));
    }

    @Test
    public void testIsMissingBannerConfig() {
        when(accountService.getAccountPreferences(anyLong())).thenReturn(Optional.empty());
        assertFalse(securityService.isBannerEnabled(0));
    }

    @Test
    public void testIsBannerEnabled() {
        AccountPreference accountPreference = new AccountPreference();
        accountPreference.setBannerEnabled(true);
        when(accountService.getAccountPreferences(anyLong())).thenReturn(Optional.of(accountPreference));
        assertTrue(securityService.isBannerEnabled(0));
    }
    
    @Test
    public void testIsBannerDisabled() {
        AccountPreference accountPreference = new AccountPreference();
        accountPreference.setBannerEnabled(false);
        when(accountService.getAccountPreferences(anyLong())).thenReturn(Optional.of(accountPreference));
        assertFalse(securityService.isBannerEnabled(0));
    }

    @Test
    public void testIsBannerEnabledByDates() {
        AccountPreference accountPreference = new AccountPreference();
        accountPreference.setBannerEnabled(false);
        // Enabled for now +/- 12 hours
        accountPreference.setBannerFromDate(Instant.now().minusSeconds(3600 * 12));
        accountPreference.setBannerUntilDate(Instant.now().plusSeconds(3600 * 12));
        when(accountService.getAccountPreferences(anyLong())).thenReturn(Optional.of(accountPreference));
        assertTrue(securityService.isBannerEnabled(0));
    }

    @Test
    public void testIsBannerDisabledByFutureDates() {
        AccountPreference accountPreference = new AccountPreference();
        accountPreference.setBannerEnabled(false);
        // Disabled for now + 12 and 24 hours
        accountPreference.setBannerFromDate(Instant.now().plusSeconds(3600 * 12));
        accountPreference.setBannerUntilDate(Instant.now().plusSeconds(3600 * 24));
        when(accountService.getAccountPreferences(anyLong())).thenReturn(Optional.of(accountPreference));
        assertFalse(securityService.isBannerEnabled(0));
    }

    @Test
    public void testIsBannerDisabledByPastDates() {
        AccountPreference accountPreference = new AccountPreference();
        accountPreference.setBannerEnabled(false);
        // Disabled for now - 12 and 24 hours
        accountPreference.setBannerFromDate(Instant.now().minusSeconds(3600 * 24));
        accountPreference.setBannerUntilDate(Instant.now().minusSeconds(3600 * 12));
        when(accountService.getAccountPreferences(anyLong())).thenReturn(Optional.of(accountPreference));
        assertFalse(securityService.isBannerEnabled(0));
    }

    @Test
    public void testIsBannerEnabledFromDate() {
        AccountPreference accountPreference = new AccountPreference();
        accountPreference.setBannerEnabled(false);
        // Enabled 10 seconds before now.
        accountPreference.setBannerFromDate(Instant.now().minusSeconds(10));
        when(accountService.getAccountPreferences(anyLong())).thenReturn(Optional.of(accountPreference));
        assertTrue(securityService.isBannerEnabled(0));
    }

    @Test
    public void testIsBannerEnabledUntilDate() {
        AccountPreference accountPreference = new AccountPreference();
        accountPreference.setBannerEnabled(false);
        // Enabled 10 seconds after now.
        accountPreference.setBannerUntilDate(Instant.now().plusSeconds(10));
        when(accountService.getAccountPreferences(anyLong())).thenReturn(Optional.of(accountPreference));
        assertTrue(securityService.isBannerEnabled(0));
    }

}
