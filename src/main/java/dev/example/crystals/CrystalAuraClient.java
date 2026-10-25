package dev.example.crystals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only visuals around other players within RANGE blocks of you
 * (and around yourself when "Show on myself" is on - the default, so it also works in singleplayer).
 *   K           - toggle orbiting crystals
 *   J           - toggle atmosphere: pink fog, haze hugging the player, sparkles
 *   Right Shift - settings screen (number of crystals, fog density)
 * Nothing is sent to the server and nobody else can see it.
 *
 * Written against Fabric API + Yarn mappings for Minecraft 1.21.4.
 */
public class CrystalAuraClient implements ClientModInitializer {

    private static final double RANGE = 24.0;

    // Crystals (the count comes from CrystalConfig)
    private static final double ORBIT_RADIUS = 0.95;
    private static final double HEIGHT = 0.35;
    private static final double WIDTH = 0.17;
    private static final int POINTS_PER_EDGE = 3;
    private static final int CRYSTAL_INTERVAL_TICKS = 3;

    // Gradient: bottom of each crystal -> top, plus a fade-to color over each particle's life
    private static final int GRADIENT_STEPS = 6;
    private static final int BOTTOM_FROM = 0xFF2E9A; // hot pink
    private static final int TOP_FROM    = 0xFFC2EE; // light pink
    private static final int BOTTOM_TO   = 0xB0207A; // deep magenta
    private static final int TOP_TO      = 0xFF7AD9; // bright pink

    // Atmosphere (base amounts per emission, multiplied by the fog density setting)
    private static final int FOG_COLOR = 0xFFA6E0;
    private static final float FOG_SIZE = 3.0f;
    private static final float HUG_SIZE = 1.2f;
    private static final double BASE_FOG_PUFFS = 3;
    private static final double BASE_HUG_PUFFS = 2;
    private static final double BASE_SPARKLES = 3;
    private static final int FOG_INTERVAL_TICKS = 4;

    private static final int MAX_TARGETS = 10;

    private final DustColorTransitionParticleEffect[] gradient =
            new DustColorTransitionParticleEffect[GRADIENT_STEPS];
    private DustParticleEffect fog;
    private DustParticleEffect hug;

    private KeyBinding crystalKey;
    private KeyBinding fogKey;
    private KeyBinding settingsKey;
    private boolean crystalsOn = false;
    private boolean fogOn = false;
    private int tickCounter = 0;
    private double angle = 0;

    @Override
    public void onInitializeClient() {
        CrystalConfig.load();

        crystalKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crystalaura.crystals", GLFW.GLFW_KEY_K, "category.crystalaura"));
        fogKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crystalaura.fog", GLFW.GLFW_KEY_J, "category.crystalaura"));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crystalaura.settings", GLFW.GLFW_KEY_RIGHT_SHIFT, "category.crystalaura"));

        for (int s = 0; s < GRADIENT_STEPS; s++) {
            double t = s / (double) (GRADIENT_STEPS - 1);
            gradient[s] = new DustColorTransitionParticleEffect(
                    lerpColor(BOTTOM_FROM, TOP_FROM, t),
                    lerpColor(BOTTOM_TO, TOP_TO, t),
                    0.75f);
        }
        fog = new DustParticleEffect(FOG_COLOR, FOG_SIZE);
        hug = new DustParticleEffect(FOG_COLOR, HUG_SIZE);

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        while (settingsKey.wasPressed()) {
            mc.setScreen(new SettingsScreen(null));
        }
        while (crystalKey.wasPressed()) {
            crystalsOn = !crystalsOn;
            mc.player.sendMessage(Text.literal("Crystals " + (crystalsOn ? "on" : "off")), true);
        }
        while (fogKey.wasPressed()) {
            fogOn = !fogOn;
            mc.player.sendMessage(Text.literal("Atmosphere " + (fogOn ? "on" : "off")), true);
        }
        if (!crystalsOn && !fogOn) return;

        tickCounter++;
        boolean doCrystals = crystalsOn && tickCounter % CRYSTAL_INTERVAL_TICKS == 0;
        boolean doFog = fogOn && tickCounter % FOG_INTERVAL_TICKS == 0;
        if (!doCrystals && !doFog) return;
        if (doCrystals) angle += 0.5;

        int drawn = 0;
        for (AbstractClientPlayerEntity other : mc.world.getPlayers()) {
            if (other == mc.player && !CrystalConfig.showSelf) continue;
            if (other.isSpectator() || other.isInvisible()) continue;
            if (other.squaredDistanceTo(mc.player) > RANGE * RANGE) continue;
            if (doCrystals) drawCrystals(mc.world, other);
            if (doFog) drawFog(mc.world, other);
            if (++drawn >= MAX_TARGETS) break;
        }
    }

    /** base * density, with the fractional part handled randomly so low densities still work. */
    private static int amount(ClientWorld world, double base) {
        double v = base * CrystalConfig.fogDensity;
        int whole = (int) Math.floor(v);
        if (world.random.nextDouble() < v - whole) whole++;
        return whole;
    }

    // ---------------------------------------------------------------- atmosphere

    private void drawFog(ClientWorld world, AbstractClientPlayerEntity target) {
        var rnd = world.random;

        int puffs = amount(world, BASE_FOG_PUFFS);
        for (int i = 0; i < puffs; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = 0.6 + rnd.nextDouble() * 1.8;
            world.addParticle(fog,
                    target.getX() + Math.cos(a) * r,
                    target.getY() + 0.1 + rnd.nextDouble() * 1.6,
                    target.getZ() + Math.sin(a) * r,
                    (rnd.nextDouble() - 0.5) * 0.01, 0.002, (rnd.nextDouble() - 0.5) * 0.01);
        }

        int hugs = amount(world, BASE_HUG_PUFFS);
        for (int i = 0; i < hugs; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = 0.35 + rnd.nextDouble() * 0.3;
            world.addParticle(hug,
                    target.getX() + Math.cos(a) * r,
                    target.getY() + 0.1 + rnd.nextDouble() * 1.7,
                    target.getZ() + Math.sin(a) * r,
                    0, 0.001, 0);
        }

        int sparkles = amount(world, BASE_SPARKLES);
        for (int i = 0; i < sparkles; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = 0.8 + rnd.nextDouble() * 3.0;
            world.addParticle(ParticleTypes.END_ROD,
                    target.getX() + Math.cos(a) * r,
                    target.getY() + rnd.nextDouble() * 3.0,
                    target.getZ() + Math.sin(a) * r,
                    (rnd.nextDouble() - 0.5) * 0.01,
                    (rnd.nextDouble() - 0.5) * 0.01,
                    (rnd.nextDouble() - 0.5) * 0.01);
        }
    }

    // ------------------------------------------------------------ crystals

    private void drawCrystals(ClientWorld world, AbstractClientPlayerEntity target) {
        int count = CrystalConfig.crystalCount;
        Vec3d base = target.getPos().add(0, 1.0, 0);
        double bob = Math.sin(angle * 1.5) * 0.12;

        for (int i = 0; i < count; i++) {
            double orbit = angle + i * (2 * Math.PI / count);
            Vec3d center = base.add(
                    Math.cos(orbit) * ORBIT_RADIUS,
                    bob + Math.sin(angle + i) * 0.08,
                    Math.sin(orbit) * ORBIT_RADIUS);

            drawOctahedron(world, center, angle * 2 + i);
            world.addParticle(ParticleTypes.END_ROD, center.x, center.y, center.z, 0, 0, 0);
        }
    }

    /** Wireframe octahedron (simple crystal shape), spinning around Y. */
    private void drawOctahedron(ClientWorld world, Vec3d c, double spin) {
        Vec3d top = new Vec3d(0, HEIGHT, 0);
        Vec3d bottom = new Vec3d(0, -HEIGHT, 0);
        Vec3d[] eq = new Vec3d[4];
        for (int k = 0; k < 4; k++) {
            double a = spin + k * Math.PI / 2;
            eq[k] = new Vec3d(Math.cos(a) * WIDTH, 0, Math.sin(a) * WIDTH);
        }
        for (int k = 0; k < 4; k++) {
            line(world, c, top, eq[k]);
            line(world, c, bottom, eq[k]);
            line(world, c, eq[k], eq[(k + 1) % 4]);
        }
    }

    private void line(ClientWorld world, Vec3d c, Vec3d a, Vec3d b) {
        for (int s = 0; s <= POINTS_PER_EDGE; s++) {
            double f = s / (double) POINTS_PER_EDGE;
            Vec3d p = a.multiply(1 - f).add(b.multiply(f));

            double t = Math.max(0, Math.min(1, (p.y + HEIGHT) / (2 * HEIGHT)));
            DustColorTransitionParticleEffect effect = gradient[(int) Math.round(t * (GRADIENT_STEPS - 1))];

            world.addParticle(effect, c.x + p.x, c.y + p.y, c.z + p.z, 0, 0, 0);
        }
    }

    private static int lerpColor(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
