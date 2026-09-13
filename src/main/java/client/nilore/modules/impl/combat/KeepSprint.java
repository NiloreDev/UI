package client.nilore.modules.impl.combat;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.WorldChangeEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import net.minecraft.world.entity.Entity;

/**
 * KeepSprint
 *
 * <p>KillAura 攻击时保持疾跑。攻击前记录疾跑状态，攻击后如果疾跑被服务端/客户端打断，
 * 立即恢复疾跑（GrimAC 专用逻辑，攻击后立刻恢复，不做 Critical 窗口判断）。</p>
 *
 * <p>独立模块后，KillAura 通过 {@link #attack(Entity)} 调用，不再自己内联这段逻辑。</p>
 */
public class KeepSprint extends Module {
    public static KeepSprint INSTANCE;

    /** 攻击前是否处于疾跑状态。用于攻击后判断是否需要恢复。 */
    private boolean wasSprinting;

    public KeepSprint() {
        super("KeepSprint", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public String getModuleName() {
        return "KeepSprint";
    }

    @Override
    public String getDisplayName() {
        return "§fKeepSprint §7[GrimAC]";
    }

    /**
     * 后缀：固定显示 [GrimAC]
     */
    @Override
    public String getSuffix() {
        return "[GrimAC]";
    }

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
     *   <li>攻击后如果之前是疾跑、现在不是疾跑，就立即恢复疾跑（GrimAC 模式）</li>
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

        // 攻击后恢复疾跑
        this.restoreSprintIfNeeded();

        return true;
    }

    /**
     * 供 KillAura 在自定义攻击路径（如 onTickRot 里手动发 rotation 包）中调用，
     * 只做“攻击后恢复疾跑”，不重复执行 attack。
     *
     * <p>GrimAC 模式：攻击后立即恢复疾跑，不做额外判断。
     * 服务端对疾跑状态包敏感，越早恢复越不容易被标记。</p>
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

        // GrimAC 模式：攻击后立即恢复疾跑
        mc.player.setSprinting(true);
    }

    /** 重置记录状态。用于世界切换或模块禁用。 */
    public void reset() {
        this.wasSprinting = false;
    }
}