package client.nilore.modules.impl.world;

import java.nio.file.Path;

import client.nilore.NiloreClient;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.protocol.heypixel.HeyPixelProtocolRuntime;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;

public final class Protocol extends Module {
    private final HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(
            mc, Path.of(NiloreClient.configDir));
    public final ModeSetting enabledHosts = new ModeSetting("Hosts", "pc.bjdmc.net,*.bjdmc.net");
    public final BooleanSetting traceLogger = new BooleanSetting("Trace Logger", false);
    public final BooleanSetting observeOnly = new BooleanSetting("Observe Only", true);
    public final BooleanSetting allowLiveSend = new BooleanSetting("Allow Live Send", false);
    public final BooleanSetting strictProviderGate = new BooleanSetting("Strict Provider Gate", true);

    public Protocol() {
        super("Protocol", Category.WORLD);
    }

    // ================================================================
    // getModuleName - 返回模块名称
    // ================================================================
    @Override
    public String getModuleName() {
        return "Protocol";
    }

    // ================================================================
    // getSuffix - 返回不带颜色代码的后缀（纯文本）
    // ================================================================
    @Override
    public String getSuffix() {
        String suffix = runtime.isActiveForCurrentServer() ? "HeyPixel" : "Idle";
        // 返回不带颜色代码的后缀，颜色由 ModuleListHud 控制
        return "[" + suffix + "]";
    }

    // ================================================================
    // getDisplayName - 覆盖主题颜色，全部显示为白色
    // ================================================================
    @Override
    public String getDisplayName() {
        String suffix = runtime.isActiveForCurrentServer() ? "HeyPixel" : "Idle";
        return "§fProtocol[" + suffix + "]";
    }

    @Override
    public void onEnable() {
        updateRuntimeSettings();
        runtime.start();
    }

    @Override
    public void onDisable() {
        runtime.stop();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        updateRuntimeSettings();
        runtime.tick();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isIncoming()) return;
        if (event.getPacket() instanceof ClientboundCustomPayloadPacket payload) {
            runtime.handle(payload);
        }
    }

    public HeyPixelProtocolRuntime getRuntime() {
        return runtime;
    }

    private void updateRuntimeSettings() {
        runtime.configure(
                enabledHosts.getValue(),
                traceLogger.getValue(),
                observeOnly.getValue(),
                allowLiveSend.getValue(),
                strictProviderGate.getValue()
        );
    }
}