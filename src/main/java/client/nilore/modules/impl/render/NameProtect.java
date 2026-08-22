package client.nilore.modules.impl.render;

import java.util.ArrayList;
import java.util.Random;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import client.nilore.event.impl.ChatReceiveEvent;
import client.nilore.event.impl.DisconnectEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.TextSetting;
import client.nilore.event.EventTarget;

public class NameProtect extends Module {
    public static NameProtect INSTANCE;
    private final ModeSetting modeSetting = new ModeSetting("Mode", "Fixed", "Random", "Hidden", "Custom").withDefault("Hidden");
    private final TextSetting customName = new TextSetting("Custom Name", "Player");
    private String cachedRandomName = null;
    private final Random random = new Random();

    public NameProtect() {
        super("NameProtect", Category.RENDER);
        this.setEnabled(true);
        INSTANCE = this;
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        if (this.modeSetting.is("Random")) {
            this.cachedRandomName = null;
        }
    }

    public static String replacePlayerName(String string) {
        if (INSTANCE == null) {
            return string;
        }
        if (mc.player == null) {
            return string;
        }
        String realName = mc.player.getName().getString();
        String displayName = INSTANCE.getDisplayName();
        if (displayName != null && !displayName.equals(realName) && string.contains(realName)) {
            return StringUtils.replace(string, realName, displayName);
        }
        return string;
    }

    public static String getProtectedName() {
        if (INSTANCE == null || mc.player == null) {
            return mc.player != null ? mc.player.getName().getString() : "Player";
        }
        String realName = mc.player.getName().getString();
        String displayName = INSTANCE.getDisplayName();
        if (displayName != null && !displayName.equals(realName)) {
            return displayName;
        }
        return realName;
    }

    public String getDisplayName() {
        String mode = this.modeSetting.getValue();
        if (mode.equals("Hidden")) {
            return "Hidden";
        }
        if (mode.equals("Custom")) {
            String name = this.customName.getValue().trim();
            return name.isEmpty() ? "Dev" : name;
        }
        if (mode.equals("Random")) {
            return this.cachedRandomName;
        }
        if (mode.equals("Fixed")) {
            return "Fixed";
        }
        return mc.player != null ? mc.player.getName().getString() : "Dev";
    }

    private String generateRandomName() {
        if (mc.getConnection() == null) {
            return null;
        }
        ArrayList<PlayerInfo> players = new ArrayList<>(mc.getConnection().getOnlinePlayers());
        ArrayList<String> names = new ArrayList<>();
        String realName = mc.player.getName().getString();
        for (PlayerInfo playerInfo : players) {
            String name = playerInfo.getProfile().getName();
            if (name.equals(realName)) continue;
            names.add(name);
        }
        if (names.isEmpty()) {
            return null;
        }
        if (this.cachedRandomName == null || !names.contains(this.cachedRandomName)) {
            this.cachedRandomName = names.get(this.random.nextInt(names.size()));
        }
        return this.cachedRandomName;
    }

    @EventTarget
    public void onChatReceive(ChatReceiveEvent chatReceiveEvent) {
        chatReceiveEvent.setComponent(Component.literal(NameProtect.replacePlayerName(chatReceiveEvent.getComponent().getString())));
    }

    @Override
    public String getModuleName() {
        return "";
    }

    @Override
    public String getSuffix() {
        String mode = this.modeSetting.getValue();
        if (mode.equals("Custom")) {
            String name = this.customName.getValue().trim();
            return name.isEmpty() ? "Dev" : name;
        }
        return mode;
    }
}