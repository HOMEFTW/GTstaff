package com.andgatech.gtstaff.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDataCleanupTest {

    @Test
    void purgeBotFilesRemovesMatchingPlayerdataServerutilitiesAndStatsFiles(@TempDir File tempDir) throws IOException {
        UUID profileId = UUID.randomUUID();
        File playerdataDir = new File(tempDir, "playerdata");
        File serverUtilitiesPlayersDir = new File(tempDir, "serverutilities/players");
        File statsDir = new File(tempDir, "stats");
        assertTrue(playerdataDir.mkdirs());
        assertTrue(serverUtilitiesPlayersDir.mkdirs());
        assertTrue(statsDir.mkdirs());

        File playerdataUuidDat = writeFile(new File(playerdataDir, profileId + ".dat"));
        File playerdataBaub = writeFile(new File(playerdataDir, "UiBot.baub"));
        File playerdataBaubback = writeFile(new File(playerdataDir, "UiBot.baubback"));
        File playerdataThaum = writeFile(new File(playerdataDir, "UiBot.thaum"));
        File playerdataThaumback = writeFile(new File(playerdataDir, "UiBot.thaumback"));
        File playerdataTf = writeFile(new File(playerdataDir, "UiBot.tf"));
        File playerdataTfback = writeFile(new File(playerdataDir, "UiBot.tfback"));
        File playerdataOtherUuidDat = writeFile(new File(playerdataDir, UUID.randomUUID() + ".dat"));
        File playerdataOtherBaub = writeFile(new File(playerdataDir, "OtherBot.baub"));
        File playerdataOtherTf = writeFile(new File(playerdataDir, "OtherBot.tf"));
        File playerdataNameDatShouldStay = writeFile(new File(playerdataDir, "UiBot.dat"));

        File serverUtilitiesBotDat = writeFile(new File(serverUtilitiesPlayersDir, "UiBot.dat"));
        File serverUtilitiesOtherDat = writeFile(new File(serverUtilitiesPlayersDir, "OtherBot.dat"));
        File serverUtilitiesJsonShouldStay = writeFile(new File(serverUtilitiesPlayersDir, "UiBot.json"));

        File statsUuidJson = writeFile(new File(statsDir, profileId + ".json"));
        File statsUuidDat = writeFile(new File(statsDir, profileId + ".dat"));
        File statsOtherUuidJson = writeFile(new File(statsDir, UUID.randomUUID() + ".json"));
        File statsNameJsonShouldStay = writeFile(new File(statsDir, "UiBot.json"));

        int deleted = PlayerDataCleanup.purgeBotFiles(tempDir, "UiBot", profileId);

        assertEquals(10, deleted);
        assertFalse(playerdataUuidDat.exists());
        assertFalse(playerdataBaub.exists());
        assertFalse(playerdataBaubback.exists());
        assertFalse(playerdataThaum.exists());
        assertFalse(playerdataThaumback.exists());
        assertFalse(playerdataTf.exists());
        assertFalse(playerdataTfback.exists());
        assertFalse(serverUtilitiesBotDat.exists());
        assertFalse(statsUuidJson.exists());
        assertFalse(statsUuidDat.exists());
        assertTrue(playerdataOtherUuidDat.exists());
        assertTrue(playerdataOtherBaub.exists());
        assertTrue(playerdataOtherTf.exists());
        assertTrue(playerdataNameDatShouldStay.exists());
        assertTrue(serverUtilitiesOtherDat.exists());
        assertTrue(serverUtilitiesJsonShouldStay.exists());
        assertTrue(statsOtherUuidJson.exists());
        assertTrue(statsNameJsonShouldStay.exists());
    }

    @Test
    void purgeBotFilesIgnoresBlankBotNameAndMissingProfile(@TempDir File tempDir) {
        assertEquals(0, PlayerDataCleanup.purgeBotFiles(tempDir, "   ", null));
    }

    private static File writeFile(File file) throws IOException {
        Files.write(file.toPath(), "test".getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
