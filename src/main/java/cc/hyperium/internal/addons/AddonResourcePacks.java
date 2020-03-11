package cc.hyperium.internal.addons;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AddonResourcePacks {
    public static List<File> resourcePacks = new ArrayList<>();
    public static void addResourcePack(InputStream inputStream) throws IOException {
        File resourcepackFile = File.createTempFile("AddonResourcePack", "tmp");
        Files.copy(inputStream, resourcepackFile.toPath());
        resourcePacks.add(resourcepackFile);
    }

    public static void deleteTemps() {
        for (File file : resourcePacks) {
            file.delete();
        }
    }
}
