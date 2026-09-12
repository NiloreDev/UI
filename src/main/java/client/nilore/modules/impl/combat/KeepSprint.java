package client.nilore.modules.impl.combat;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.WorldChangeEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.ModeSetting;
import net.minecraft.world.entity.Entity;

/**
 * KeepSprint
 *
 * <p>KillAura 攻击时保持疾跑。攻击前记录疾跑状态，攻击后如果疾跑被服务端/客户端打断，
 * 且不在 Critical 松疾跑窗口内，就恢复疾跑。</p>
 *
 * <p>独立模块后，KillAura 通过 {@link #attack(Entity)} 调用，不再自己内联这段逻辑。</p>
 */
public class KeepSprint extends Module {
    public static KeepSprint INSTANCE;

    /** 模式选择：Default（原版逻辑） / GrimAC（GrimAC 专用逻辑） */
    public final ModeSetting mode = new ModeSetting(
            "Mode", "GrimAC", "GrimAC"
    ).withDefault("GrimAC");

    /** 攻击前是否处于疾跑状态。用于攻击后判断是否需要恢复。 */
    private boolean wasSprinting;

    public KeepSprint() {
        super("KeepSprint", Category.COMBAT);
        INSTANCE = this;
        this.registerSetting(mode);
    }

    @Override
    public String getModuleName() {
        return "KeepSprint";
    }

    @Override
    public String getDisplayName() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return "§fKeepSprint";
        }
        return "§fKeepSprint §7[" + modeName + "]";
    }

    /**
     * 后缀：显示当前模式，格式 [GrimAC] / [Default]
     */
    @Override
    public String getSuffix() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) return null;
        return "[" + modeName + "]";
    }

    // ===== 模式判断 =====


    @Override
    public void onEnable() {
        this.wasSprinting = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.wasSprinting = false;
        super.onDisable();
    }

    @Override
    public void onTick() {

    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        this.wasSprinting = false;
    }

    /**
     * KillAura 攻击实体时调用。
     *
     * <p>流程：
     * <ol>
     *   <li>记录攻击前的疾跑状态</li>
     *   <li>执行原版攻击 {@code mc.gameMode.attack(player, entity)}</li>
     *   <li>攻击后如果之前是疾跑、现在不是疾跑、且不在 Critical 松疾跑窗口，就恢复疾跑</li>
     * </ol>
     * </p>
     *
     * @param entity 攻击目标
     * @return 是否成功执行攻击
     */
    public boolean attack(Entity entity) {
        if (mc.player == null || mc.gameMode == null) {
            return false;
        }
        if (entity == null) {
            return false;
        }

        // 攻击前记录疾跑状态
        this.wasSprinting = mc.player.isSprinting();

        // 执行原版攻击
        mc.gameMode.attack(mc.player, entity);

        // 攻击后恢复疾跑（排除 Critical 松疾跑窗口）
        this.restoreSprintIfNeeded();

        return true;
    }

    /**
     * 供 KillAura 在自定义攻击路径（如 onTickRot 里手动发 rotation 包）中调用，
     * 只做“攻击后恢复疾跑”，不重复执行 attack。
     */
    public void restoreSprintIfNeeded() {
        if (mc.player == null) {
            return;
        }
        // 之前没疾跑 → 不恢复
        if (!this.wasSprinting) {
            return;
        }
        // 现在还在疾跑 → 不用恢复
        if (mc.player.isSprinting()) {
            return;
        }
        // Critical 松疾跑窗口内 → 不恢复（避免和 Critical 互相拉扯）
        if (this.criticalReleaseWindow()) {
            return;
        }

        // ===== GrimAC 模式：额外的疾跑恢复条件 =====
        if (this.isGrimACMode()) {
            // GrimAC 模式：攻击后立即恢复疾跑，不做额外判断
            // （GrimAC 对疾跑状态包敏感，越早恢复越不容易被标记）
            mc.player.setSprinting(true);
            return;
        }

        // ===== Default 模式：原逻辑 =====
        mc.player.setSprinting(true);
    }

    private boolean isGrimACMode() {
        return false;
    }

    /**
     * Critical 松疾跑窗口：目标 hurtTime ∈ [2,8] 时，Critical 会主动松疾跑，
     * KeepSprint 在窗口内不恢复，避免互相拉扯。
     */
    private boolean criticalReleaseWindow() {
        return Critical.INSTANCE != null
                && Critical.INSTANCE.isEnabled()
                && Critical.INSTANCE.isReleaseWindow();
    }

    /** 重置记录状态。用于世界切换或模块禁用。 */
    public void reset() {
        this.wasSprinting = false;
    }
}