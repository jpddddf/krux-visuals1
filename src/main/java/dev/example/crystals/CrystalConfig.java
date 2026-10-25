package dev.example.crystals;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Settings saved to .minecraft/config/crystalaura.properties */
public final class CrystalConfig {

    public static final int MIN_CRYSTALS = 1;
    public static final int MAX_CRYSTALS = 6;
    public static final double MAX_FOG = 3.0; // 300%

    public static int crystalCount = 2;
    public static double fogDensity = 1.0;    // 1.0 = 100%
    public static boolean showSelf = true;    // draw effects around yourself too

    private CrystalConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("crystalaura.properties");
    }

    public static void load() {
        Path p = file();
        if (!Files.exists(p)) return;
        try (var in = Files.newInputStream(p)) {
            Properties props = new Properties();
            props.load(in);
            int c = Integer.parseInt(props.getProperty("crystalCount", "2"));
            double f = Double.parseDouble(props.getProperty("fogDensity", "1.0"));
            showSelf = Boolean.parseBoolean(props.getProperty("showSelf", "true"));
            crystalCount = Math.max(MIN_CRYSTALS, Math.min(MAX_CRYSTALS, c));
            fogDensity = Math.max(0.0, Math.min(MAX_FOG, f));
        } catch (Exception ignored) {
            // keep defaults if the file is missing or broken
        }
    }

    public static void save() {
        try (var out = Files.newOutputStream(file())) {
            Properties props = new Properties();
            props.setProperty("crystalCount", Integer.toString(crystalCount));
            props.setProperty("fogDensity", Double.toString(fogDensity));
            props.setProperty("showSelf", Boolean.toString(showSelf));
            props.store(out, "Crystal Aura settings");
        } catch (IOException ignored) {
            // not fatal
        }
    }
}
