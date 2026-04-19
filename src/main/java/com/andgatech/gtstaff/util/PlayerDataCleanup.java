package com.andgatech.gtstaff.util;

import java.io.File;
import java.util.UUID;

public final class PlayerDataCleanup {

    private static final String[] PLAYERDATA_NAME_EXTENSIONS = { "baub", "baubback", "thaum", "thaumback", "tf",
        "tfback" };

    private PlayerDataCleanup() {}

    public static int purgeBotFiles(File saveRoot, String botName, UUID profileId) {
        if (saveRoot == null) {
            return 0;
        }

        String normalizedBotName = normalizeBotName(botName);
        String profileName = profileId == null ? null : profileId.toString();
        if (normalizedBotName == null && profileName == null) {
            return 0;
        }

        return deletePlayerdataFiles(new File(saveRoot, "playerdata"), normalizedBotName, profileName)
            + deleteSingleNamedFile(new File(saveRoot, "serverutilities/players"), normalizedBotName, "dat")
            + deleteUuidNamedFiles(new File(saveRoot, "stats"), profileName);
    }

    private static String normalizeBotName(String botName) {
        String normalizedBotName = botName == null ? "" : botName.trim();
        return normalizedBotName.isEmpty() ? null : normalizedBotName;
    }

    private static int deletePlayerdataFiles(File directory, String botName, String profileName) {
        int deleted = deleteSingleNamedFile(directory, profileName, "dat");
        if (botName == null) {
            return deleted;
        }
        for (String extension : PLAYERDATA_NAME_EXTENSIONS) {
            deleted += deleteSingleNamedFile(directory, botName, extension);
        }
        return deleted;
    }

    private static int deleteUuidNamedFiles(File directory, String profileName) {
        if (directory == null || !directory.isDirectory() || profileName == null) {
            return 0;
        }

        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            return 0;
        }

        int deleted = 0;
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            String baseName = baseName(file);
            if (!profileName.equalsIgnoreCase(baseName)) {
                continue;
            }
            deleteFile(file);
            deleted++;
        }
        return deleted;
    }

    private static int deleteSingleNamedFile(File directory, String baseName, String extension) {
        if (directory == null || !directory.isDirectory() || baseName == null || extension == null) {
            return 0;
        }
        File target = new File(directory, baseName + "." + extension);
        if (!target.exists()) {
            return 0;
        }
        deleteFile(target);
        return 1;
    }

    private static String baseName(File file) {
        String name = file.getName();
        int extensionIndex = name.lastIndexOf('.');
        return extensionIndex >= 0 ? name.substring(0, extensionIndex) : name;
    }

    private static void deleteFile(File file) {
        if (!file.delete() && file.exists()) {
            throw new IllegalStateException("Unable to delete fake player data file " + file.getAbsolutePath());
        }
    }
}
