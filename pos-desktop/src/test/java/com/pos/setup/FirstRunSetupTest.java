package com.pos.setup;

import com.pos.setup.FirstRunSetup.PasswordRequest;
import com.pos.setup.FirstRunSetup.SetupException;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class FirstRunSetupTest {
    private static final String CHOSEN = "My chosen 'password;with:quotes\\9!";

    @Test void installsThenSetsAndVerifiesTheChosenPasswordBeforeSaving() throws Exception {
        Fake ops = new Fake();
        List<PasswordRequest> prompts = new ArrayList<>();
        FirstRunSetup.run(s -> {}, request -> { prompts.add(request); return CHOSEN; }, ops);
        assertEquals(List.of(new PasswordRequest(true, false)), prompts);
        assertEquals(CHOSEN, ops.password);
        assertEquals(CHOSEN, ops.saved);
        assertNull(ops.recovery);
        assertEquals(List.of("remember", "install", "change", "save", "clear"), ops.events);
        assertNotEquals(CHOSEN, ops.installerPassword);
        assertTrue(ops.installerPassword.matches("Aa1[0-9a-f]{28}!"));
    }

    @Test void existingServerPasswordIsNeverChangedOrReinstalled() throws Exception {
        Fake ops = existing("Existing-password!");
        FirstRunSetup.run(s -> {}, request -> {
            assertFalse(request.newPassword());
            return "Existing-password!";
        }, ops);
        assertEquals(List.of("start", "save", "clear"), ops.events);
        assertEquals("Existing-password!", ops.password);
    }

    @Test void retriesWrongExistingPasswordAndAllowsShortLegacyPasswords() throws Exception {
        Fake ops = existing("old");
        List<PasswordRequest> prompts = new ArrayList<>();
        FirstRunSetup.run(s -> {}, request -> {
            prompts.add(request);
            return prompts.size() == 1 ? "wrong" : "old";
        }, ops);
        assertEquals(List.of(new PasswordRequest(false, false), new PasswordRequest(false, true)), prompts);
        assertEquals("old", ops.saved);
        assertFalse(ops.events.contains("change"));
    }

    @Test void cancellationDoesNotInstallOrSaveAnything() {
        Fake ops = new Fake();
        assertThrows(SetupException.class, () -> FirstRunSetup.run(s -> {}, request -> null, ops));
        assertTrue(ops.events.isEmpty());
    }

    @Test void invalidNewPasswordDoesNotInstall() {
        Fake ops = new Fake();
        assertThrows(SetupException.class, () -> FirstRunSetup.run(s -> {}, request -> "short", ops));
        assertTrue(ops.events.isEmpty());
    }

    @Test void cancelledExistingConnectionDoesNotResetPassword() {
        Fake ops = existing("Existing-password!");
        assertThrows(SetupException.class, () -> FirstRunSetup.run(s -> {}, request -> null, ops));
        assertEquals(List.of("start"), ops.events);
    }

    @Test void partialInstallationResumesWithoutReinstalling() throws Exception {
        Fake ops = new Fake();
        ops.failInstallation = true;
        assertThrows(SetupException.class, () -> FirstRunSetup.run(s -> {}, r -> CHOSEN, ops));
        assertNotNull(ops.recovery);
        assertNull(ops.saved);
        ops.failInstallation = false;
        FirstRunSetup.run(s -> {}, request -> {
            assertTrue(request.newPassword());
            return CHOSEN;
        }, ops);
        assertEquals(1, ops.events.stream().filter("install"::equals).count());
        assertEquals(CHOSEN, ops.saved);
        assertNull(ops.recovery);
    }

    @Test void failedSaveAfterPasswordChangeCanReconnectUsingChosenPassword() throws Exception {
        Fake ops = new Fake();
        ops.failSave = true;
        assertThrows(SetupException.class, () -> FirstRunSetup.run(s -> {}, r -> CHOSEN, ops));
        assertEquals(CHOSEN, ops.password);
        assertNotNull(ops.recovery);
        ops.failSave = false;
        FirstRunSetup.run(s -> {}, request -> {
            assertFalse(request.newPassword());
            return CHOSEN;
        }, ops);
        assertEquals(1, ops.events.stream().filter("change"::equals).count());
        assertEquals(CHOSEN, ops.saved);
    }

    @Test void retryBeforeServiceRegistrationPreservesThePendingInstallerCredential() throws Exception {
        Fake ops = new Fake();
        ops.recovery = "Aa1" + "a".repeat(28) + "!";
        String pending = ops.recovery;
        FirstRunSetup.run(s -> {}, request -> CHOSEN, ops);
        assertEquals(pending, ops.installerPassword);
        assertEquals(CHOSEN, ops.saved);
    }

    @Test void passwordChangeFailureRetainsRecoveryAndDoesNotSaveConfiguration() {
        Fake ops = new Fake();
        ops.failChange = true;
        assertThrows(SetupException.class, () -> FirstRunSetup.run(s -> {}, r -> CHOSEN, ops));
        assertNotNull(ops.recovery);
        assertNull(ops.saved);
    }

    @Test void failedPasswordVerificationDoesNotSaveConfiguration() {
        Fake ops = new Fake();
        ops.ignoreChange = true;
        assertThrows(SetupException.class, () -> FirstRunSetup.run(s -> {}, r -> CHOSEN, ops));
        assertNull(ops.saved);
        assertNotNull(ops.recovery);
    }

    @Test void unsecuredExistingServerGetsAChosenPasswordWithoutReinstallation() throws Exception {
        Fake ops = existing("");
        FirstRunSetup.run(s -> {}, request -> {
            assertTrue(request.newPassword());
            return CHOSEN;
        }, ops);
        assertEquals(List.of("start", "change", "save", "clear"), ops.events);
    }

    @Test void validatesLengthAndControlCharactersWithoutRestrictingNormalSymbols() {
        assertNotNull(FirstRunSetup.passwordProblem("short"));
        assertNotNull(FirstRunSetup.passwordProblem("a".repeat(129)));
        assertNotNull(FirstRunSetup.passwordProblem("password\n9"));
        assertNull(FirstRunSetup.passwordProblem(CHOSEN));
        assertNull(FirstRunSetup.passwordProblem("a".repeat(128)));
    }

    @Test void powershellPathsWithSpacesAndApostrophesAreQuoted() {
        assertEquals("'C:\\Users\\Shop''s PC\\setup.ps1'",
                WindowsMySqlProvisioner.psQuote("C:\\Users\\Shop's PC\\setup.ps1"));
    }

    private static Fake existing(String password) {
        Fake ops = new Fake(); ops.exists = true; ops.password = password; return ops;
    }

    private static final class Fake implements FirstRunSetup.Operations {
        boolean exists, failInstallation, failSave, failChange, ignoreChange;
        String password, recovery, saved, installerPassword;
        List<String> events = new ArrayList<>();
        public boolean serverExists() { return exists; }
        public void startExisting(Consumer<String> progress) { events.add("start"); }
        public void install(String initial, Consumer<String> progress) throws SetupException {
            events.add("install"); installerPassword = initial; exists = true; password = initial;
            if (failInstallation) throw new SetupException("Installation interrupted");
        }
        public boolean accepts(String supplied) { return exists && java.util.Objects.equals(password, supplied); }
        public String recoveryPassword() { return recovery; }
        public void rememberRecoveryPassword(String value) { events.add("remember"); recovery = value; }
        public void clearRecoveryPassword() { events.add("clear"); recovery = null; }
        public void changePassword(String current, String value) throws SetupException {
            events.add("change");
            if (failChange) throw new SetupException("Password policy");
            if (!ignoreChange) password = value;
        }
        public void save(String value) throws SetupException {
            events.add("save");
            if (failSave) throw new SetupException("Disk full");
            saved = value;
        }
    }
}
