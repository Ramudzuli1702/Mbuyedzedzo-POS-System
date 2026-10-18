package com.pos.services;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SheetExportSettingsTest {
    @TempDir Path directory;
    SettingsService settings(Map<String,String> values){return new SettingsService(){@Override public String getLocalSetting(String key){return values.getOrDefault(key,"");}};}
    @Test void inventoryAndStocktakingUseTheirOwnConfiguredFolders() throws Exception {
        var settings=settings(Map.of("inventory.savePath",directory.resolve("inventory sheets").toString(),"stocktaking.savePath",directory.resolve("counts").toString()));
        assertEquals(directory.resolve("inventory sheets").toFile(),settings.sheetExportDirectory(false));
        assertEquals(directory.resolve("counts").toFile(),settings.sheetExportDirectory(true));
        assertTrue(Files.isDirectory(directory.resolve("counts")));assertTrue(Files.isDirectory(directory.resolve("inventory sheets")));
    }
    @Test void relativeOrMissingPreferencesFallBackToAbsoluteSeparateFolders(){
        var settings=settings(Map.of("inventory.savePath","relative/"));
        assertTrue(Path.of(settings.getInventorySavePath()).isAbsolute());assertTrue(Path.of(settings.getStocktakeSavePath()).isAbsolute());
        assertNotEquals(settings.getInventorySavePath(),settings.getStocktakeSavePath());
    }
}
