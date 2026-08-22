package client.nilore.modules.impl.movement;

import java.util.HashMap;

import client.nilore.event.impl.MotionEvent;
import net.minecraft.client.KeyMapping;
import client.nilore.event.impl.RotationEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.player.InventoryManager;
import client.nilore.event.EventTarget;

public class Sprint
extends Module {
    // private final HashMap<String, String> keyMappings = new HashMap<>();
    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        this.setEnabled(true);
    }

    @EventTarget
    public void onRotation(RotationEvent rotationEvent) {
        if (InventoryManager.isPerformingAction) {
            return;
        }
        mc.options.toggleSprint().set(false);
        KeyMapping.set(mc.options.keySprint.getKey(), true);
    }

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getModuleName() {
        return "";
    }
}