package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.Paint;
import client.nilore.render.Renderer;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.settings.impl.TextSetting;

import java.awt.Color;

public class IQBoost extends Module {
    public static IQBoost INSTANCE;

    public final NumberSetting value = new NumberSetting("IQBoost", 431, 0, 9178, 1);
    public final NumberSetting fontSize = new NumberSetting("Font Size", 13, 6, 100, 1);
    public final TextSetting suffix = new TextSetting("Suffix", " ");

    public final ModeSetting colorMode = new ModeSetting(
            "Color Mode",
            "White", "Red", "Green", "Blue", "Yellow", "Cyan", "Purple",
            "Rainbow", "Gradient", "Color Fusion"
    ).withDefault("White");

    public final NumberSetting gradAR = new NumberSetting("Grad A R", 255, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));
    public final NumberSetting gradAG = new NumberSetting("Grad A G", 255, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));
    public final NumberSetting gradAB = new NumberSetting("Grad A B", 255, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));

    public final NumberSetting gradBR = new NumberSetting("Grad B R", 85, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));
    public final NumberSetting gradBG = new NumberSetting("Grad B G", 255, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));
    public final NumberSetting gradBB = new NumberSetting("Grad B B", 255, 0, 255, 1,
            () -> colorMode.is("Gradient") || colorMode.is("Color Fusion"));

    public final NumberSetting fusionSpeed = new NumberSetting("Fusion Speed", 1.0, 0.1, 10.0, 0.1,
            () -> colorMode.is("Color Fusion"));

    public final NumberSetting offsetX = new NumberSetting("X", 10, 0, 900, 1);
    public final NumberSetting offsetY = new NumberSetting("Y", 10, 0, 700, 1);

    public IQBoost() {
        super("IQBoost", Category.RENDER);
        INSTANCE = this;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;

        int displayValue = value.getValue().intValue();
        String text = "IQBoost " + displayValue + suffix.getValue();

        float x = offsetX.getValue().floatValue();
        float y = offsetY.getValue().floatValue();

        int color = resolveColor();

        FontRenderer font = FontPresets.axiformaBold(fontSize.getValue().floatValue());
        Renderer.render(event.guiGraphics(), drawContext -> {
            try (Paint paint = new Paint()) {
                paint.setColor(color);
                drawContext.drawString(text, x, y, font, paint);
            }
        });
    }

    /**
     * 根据当前 Color Mode 解析最终颜色（ARGB）。
     */
    private int resolveColor() {
        String mode = colorMode.getValue();
        long now = System.currentTimeMillis();

        switch (mode) {
            case "Red":    return 0xFFFF5555;
            case "Green":  return 0xFF55FF55;
            case "Blue":   return 0xFF5555FF;
            case "Yellow": return 0xFFFFFF55;
            case "Cyan":   return 0xFF55FFFF;
            case "Purple": return 0xFFAA55FF;

            case "Rainbow": {
                float hue = (now % 3000L) / 3000.0f;
                return Color.HSBtoRGB(hue, 0.8f, 1.0f);
            }

            case "Gradient": {
                int ar = gradAR.getValue().intValue();
                int ag = gradAG.getValue().intValue();
                int ab = gradAB.getValue().intValue();
                int br = gradBR.getValue().intValue();
                int bg = gradBG.getValue().intValue();
                int bb = gradBB.getValue().intValue();

                float t = (now % 2000L) / 2000.0f;
                int r = Math.round(ar + (br - ar) * t);
                int g = Math.round(ag + (bg - ag) * t);
                int b = Math.round(ab + (bb - ab) * t);
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }

            case "Color Fusion": {
                int ar = gradAR.getValue().intValue();
                int ag = gradAG.getValue().intValue();
                int ab = gradAB.getValue().intValue();
                int br = gradBR.getValue().intValue();
                int bg = gradBG.getValue().intValue();
                int bb = gradBB.getValue().intValue();

                float speed = fusionSpeed.getValue().floatValue();
                double phase = (now / 1000.0) * speed;

                float t = (float) ((Math.sin(phase) + 1.0) / 2.0);
                int r = Math.round(ar + (br - ar) * t);
                int g = Math.round(ag + (bg - ag) * t);
                int b = Math.round(ab + (bb - ab) * t);
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }

            case "White":
            default:
                return 0xFFFFFFFF;
        }
    }

    @Override
    public String getDisplayName() {
        return "§fIQBoost";
    }

    @Override
    public String getModuleName() {
        return "IQBoost";
    }

    @Override
    public void onTick() {
    }
}