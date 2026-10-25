package dev.example.crystals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/** In-game settings: number of crystals and fog density. Opens with Right Shift. */
public class SettingsScreen extends Screen {

    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Text.literal("Crystal Aura"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 200;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 40;
        addDrawableChild(new CrystalSlider(x, y, w, 20));
        addDrawableChild(new FogSlider(x, y + 26, w, 20));
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x, y + 62, w, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, this.height / 2 - 62, 0xFFFFFF);
    }

    @Override
    public void removed() {
        CrystalConfig.save();
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    private static class CrystalSlider extends SliderWidget {
        CrystalSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(),
                    (CrystalConfig.crystalCount - CrystalConfig.MIN_CRYSTALS)
                            / (double) (CrystalConfig.MAX_CRYSTALS - CrystalConfig.MIN_CRYSTALS));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Crystals: " + CrystalConfig.crystalCount));
        }

        @Override
        protected void applyValue() {
            CrystalConfig.crystalCount = CrystalConfig.MIN_CRYSTALS
                    + (int) Math.round(this.value * (CrystalConfig.MAX_CRYSTALS - CrystalConfig.MIN_CRYSTALS));
        }
    }

    private static class FogSlider extends SliderWidget {
        FogSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), CrystalConfig.fogDensity / CrystalConfig.MAX_FOG);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Fog density: " + Math.round(CrystalConfig.fogDensity * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            CrystalConfig.fogDensity = Math.round(this.value * CrystalConfig.MAX_FOG * 20) / 20.0;
        }
    }
}
