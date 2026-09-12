package client.nilore.modules.impl.render;

import client.nilore.gui.MaterialClickGui;
import client.nilore.gui.NewClickGui;
import client.nilore.gui.OldClickGui;
import client.nilore.gui.PanelClickGui;
import client.nilore.gui.SuperSoftClickGui;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClickGui extends Module {
    public static final Logger LOGGER = LogManager.getLogger(ClickGui.class);

    // SuperSoft is now the default visual style.
    public final ModeSetting styleSetting = new ModeSetting(
            "Mode", "SuperSoft", "Old", "Panel", "New", "Material3"
    ).withDefault("SuperSoft");

    public final ModeSetting materialTheme = new ModeSetting("Material Theme", "Dark", "Light")
            .withVisibility(() -> this.styleSetting.is("Material3"));

    public final NumberSetting panelOpacity = new NumberSetting("Panel Opacity", 80, 20, 100, 1)
            ;

    public ClickGui() {
        super("ClickGui", Category.RENDER, 344);
        this.registerSetting(styleSetting, materialTheme, panelOpacity);
    }

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getModuleName() {
        return "";
    }

    @Override
    protected void onEnable() {
        try {
            if (this.styleSetting.is("SuperSoft")) {
                mc.setScreen(new SuperSoftClickGui());
            } else if (this.styleSetting.is("Old")) {
                mc.setScreen(new OldClickGui());
            } else if (this.styleSetting.is("Panel")) {
                float opacity = this.panelOpacity.getValue().floatValue() / 100.0f;
                PanelClickGui.panelClickGui.setOpacity(opacity);
                mc.setScreen(PanelClickGui.panelClickGui);
            } else if (this.styleSetting.is("Material3")) {
                mc.setScreen(MaterialClickGui.instance);
            } else {
                mc.setScreen(new NewClickGui());
            }
            LOGGER.info("ClickGUI opened successfully");
        } catch (Exception exception) {
            LOGGER.error("Error opening ClickGUI", exception);
        } finally {
            this.setEnabled(false);
        }
    }

    @Override
    public void onTick() {
    }
}