package client.nilore.modules.impl.movement;

import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.utils.animation.SpringAnimation;

public class NoJumpDelay extends Module {
    public static NoJumpDelay INSTANCE;

    public NoJumpDelay() {
        super("NoJumpDelay", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getModuleName() {
        return "";
    }

    public static NoJumpDelay getInstance() {
        return INSTANCE;
    }
}