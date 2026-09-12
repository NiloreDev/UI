package client.nilore.modules.impl.combat;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.antikb.NavenVelocityMode;
import client.nilore.modules.impl.combat.antikb.NoXZMode;
import client.nilore.settings.impl.ModeSetting;

/**
 * Critical: 每 tick 从 KillAura 取目标(target), 当目标处于受击硬直窗口
 * 时松疾跑; 疾跑重启交给 KeepSprint/移动 Sprint 模块。
 * KillAura 的 KeepSprint 通过 {@link #isReleaseWindow()} 兼容: 窗口内不主动恢复疾跑。
 *
 * <p>模式:
 * <ul>
 *   <li><b>Default</b> - 原版窗口: hurtTime ∈ [7,8] ∪ [0,3]</li>
 *   <li><b>GrimAC</b> - GrimAC 专用窗口: hurtTime ∈ [6,9]</li>
 * </ul>
 * </p>
 */
public class Critical extends Module {
    public static Critical INSTANCE;

    /** 模式选择: Default（原版） / GrimAC（GrimAC 专用） */
    public final ModeSetting mode = new ModeSetting(
            "Mode", "GrimAC", "GrimAC"
    ).withDefault("GrimAC");

    public Critical() {
        super("Critical", Category.COMBAT);
        INSTANCE = this;
        this.registerSetting(mode);
    }

    // ===== 模式判断 =====
    public boolean isGrimACMode() {
        return this.mode.is("GrimAC");
    }

    public boolean isDefaultMode() {
        return this.mode.is("GrimAC");
    }

    @Override
    public String getSuffix() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) return null;
        return "[" + modeName + "]";
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) {
            return;
        }
        if (this.isReleaseWindow()) {
            mc.options.keySprint.setDown(false);
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
        }
    }

    /**
     * 松疾跑窗口: KillAura 目标存在且 LivingEntity.hurtTime 落在模式对应的区间。
     * 供 KillAura KeepSprint 在攻击后决定是否恢复疾跑(窗口内不恢复)。
     */
    public boolean isReleaseWindow() {
        // res 对齐: 击退收放(NavenVelocity)进行中 Critical 停手, 避免松疾跑打断放包
        if (NavenVelocityMode.handlingVelocity) return false;
        if (NoXZMode.handlingVelocity) return false;
        if (mc.player == null) return false;
        Entity target = KillAura.target;
        if (!(target instanceof LivingEntity living)) {
            return false;
        }

        // —— 玩家自身处于不可触发 crit 状态, 逐一显式 return false ——
        if (mc.player.onGround()) return false;
        if (mc.player.isInWater() || mc.player.isInLava()) return false;
        if (mc.player.isUsingItem()) return false;
        if (mc.player.isShiftKeyDown()) return false;
        if (mc.player.isFallFlying()) return false;
        if (mc.player.isPassenger()) return false;
        if (mc.player.onClimbable()) return false;
        if (mc.player.hasEffect(MobEffects.BLINDNESS)) return false;
        if (mc.player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) return false;
        if (mc.player.hasEffect(MobEffects.LEVITATION)) return false;

        int hurtTime = living.hurtTime;

        // ===== GrimAC 模式: GrimAC 专用窗口 =====
        if (this.isGrimACMode()) {
            // GrimAC 对 Critical 的判定窗口: hurtTime ∈ [6,9]
            return hurtTime >= 6 && hurtTime <= 9;
        }

        // ===== Default 模式: 原版窗口 =====
        return hurtTime >= 7 || hurtTime <= 3;
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
    public void onTick() {

    }
}