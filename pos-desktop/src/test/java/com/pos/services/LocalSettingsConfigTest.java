package com.pos.services;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalSettingsConfigTest {
    @TempDir Path directory;
    @Test void localPreferencesRoundTripIntoAnExplicitWritableUserDirectory() throws Exception {
        Path config=directory.resolve("user/standard/app.config"),legacy=directory.resolve("install/config/app.config");
        var settings=new SettingsService(config,legacy);
        String folder=directory.resolve("Inventories and counts é").toString();
        assertTrue(settings.saveLocalSetting("inventory.savePath",folder));
        assertEquals(folder,new SettingsService(config,legacy).getInventorySavePath());
        assertFalse(Files.readString(config).contains(folder));assertFalse(Files.exists(legacy));
    }
    @Test void legacyEncryptedPreferencesAreRetainedWhenSavingToTheUserDirectory() throws Exception {
        Path legacy=directory.resolve("install/config/app.config"),user=directory.resolve("user/app.config");
        var old=new SettingsService(legacy,directory.resolve("missing"));assertTrue(old.saveLocalSetting("email.username","test@example.test"));
        byte[] original=Files.readAllBytes(legacy);
        var migrated=new SettingsService(user,legacy);assertEquals("test@example.test",migrated.getLocalSetting("email.username"));
        assertTrue(migrated.saveLocalSetting("stocktaking.savePath",directory.resolve("counts").toString()));
        assertEquals("test@example.test",new SettingsService(user,legacy).getLocalSetting("email.username"));
        assertArrayEquals(original,Files.readAllBytes(legacy));
    }
}
