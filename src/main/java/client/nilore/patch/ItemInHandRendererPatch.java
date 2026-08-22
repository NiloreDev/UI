package client.nilore.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.WrapInvoke;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import client.nilore.ClientBase;
import client.nilore.NiloreClient;
import client.nilore.asm.Invocation;
import client.nilore.event.impl.UpdateHeldItemEvent;
import client.nilore.modules.impl.render.ItemAnimationModifier;
import client.nilore.modules.impl.render.OldHitting;

@Patch(ItemInHandRenderer.class)
public class ItemInHandRendererPatch {
    @WrapInvoke(
            method = "tick",
            desc = "()V",
            target = "net/minecraft/world/entity/LivingEntity/getMainHandItem",
            targetDesc = "()Lnet/minecraft/world/item/ItemStack;"
    )
    public static ItemStack onGetMainHandItem(ItemInHandRenderer renderer, Invocation<ItemInHandRenderer, ItemStack> original) {
        UpdateHeldItemEvent event = new UpdateHeldItemEvent(InteractionHand.MAIN_HAND, ClientBase.mc.player.getMainHandItem());
        if (NiloreClient.isReady()) {
            NiloreClient.getInstance().getEventBus().call(event);
        }
        return event.getItemStack();
    }

    @Inject(
            method = "renderArmWithItem",
            desc = "(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderArmWithItem(
            ItemInHandRenderer renderer,
            AbstractClientPlayer player,
            float partialTicks,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equippedProgress,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callbackInfo) {
        if (!NiloreClient.isReady()) {
            return;
        }

        // ===== 只处理主手 =====
        if (hand != InteractionHand.MAIN_HAND) {
            return;
        }

        // ===== 1. ItemAnimationModifier：所有物品（速度/大小/偏移） =====
        if (ItemAnimationModifier.INSTANCE != null && ItemAnimationModifier.INSTANCE.isEnabled()) {
            swingProgress = ItemAnimationModifier.INSTANCE.getModifiedProgress(swingProgress);
            ItemAnimationModifier.INSTANCE.applyModifiers(poseStack, swingProgress, equippedProgress,
                    player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1);
        }

        // ===== 2. OldHitting：剑的防砍动画 =====
        if (OldHitting.INSTANCE == null || !OldHitting.INSTANCE.isEnabled()) {
            return;
        }

        boolean isSword = stack.getItem() instanceof SwordItem;
        boolean useKeyHeld = ClientBase.mc.options.keyUse.isDown() && ClientBase.mc.player.getOffhandItem().isEmpty();
        boolean killAuraAttacking = OldHitting.INSTANCE.isKillAuraAttacking();

        if (isSword && (useKeyHeld || killAuraAttacking)) {
            callbackInfo.cancel();
            OldHitting.INSTANCE.applyHitAnimation(poseStack, swingProgress, player.getMainArm(), equippedProgress);

            boolean rightHand = player.getMainArm() == HumanoidArm.RIGHT;
            renderer.renderItem(
                    player, stack,
                    rightHand ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !rightHand, poseStack, bufferSource, packedLight);
        }
    }
}