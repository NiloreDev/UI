package client.nilore.patch;

import asm.patchify.annotation.Patch;
import asm.patchify.annotation.Transform;
import client.nilore.ClientBase;
import client.nilore.NiloreClient;
import client.nilore.event.impl.CameraPitchEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

@Patch(HumanoidModel.class)
public class HumanoidModelPatch {

    public static CameraPitchEvent onPitchRender(LivingEntity entity, float pitch) {
        if (NiloreClient.isReady()
                && entity == ClientBase.mc.player
                && ClientBase.mc.level != null) {
            return (CameraPitchEvent) NiloreClient.instance
                    .getEventBus()
                    .call(new CameraPitchEvent(pitch));
        }
        return new CameraPitchEvent(pitch);
    }

    public static void applyThirdPersonBlock(HumanoidModel<?> model, LivingEntity entity) {
        if (ClientBase.mc.player == null || entity != ClientBase.mc.player) {
            return;
        }

        if (!shouldBlock()) {
            return;
        }

        model.rightArm.xRot = -0.85F;
        model.rightArm.yRot = -0.35F;
        model.rightArm.zRot = 0.15F;

        model.leftArm.xRot = -0.95F;
        model.leftArm.yRot = 0.45F;
        model.leftArm.zRot = -0.12F;
    }

    private static boolean shouldBlock() {
        try {
            Class<?> clazz = Class.forName(
                    "client.nilore.modules.impl.render.Animations"
            );

            Object instance = clazz.getField("INSTANCE").get(null);

            if (instance != null) {
                return (boolean) clazz
                        .getMethod("shouldThirdPersonBlock")
                        .invoke(instance);
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    @Transform(method = "setupAnim",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V")
    public static void transformSetupAnim(MethodNode methodNode) {

        InsnList prelude = new InsnList();

        int local = 7;

        prelude.add(new VarInsnNode(Opcodes.ALOAD, 1));
        prelude.add(new VarInsnNode(Opcodes.FLOAD, 6));
        prelude.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(HumanoidModelPatch.class),
                "onPitchRender",
                "(Lnet/minecraft/world/entity/LivingEntity;F)Lclient/nilore/event/impl/CameraPitchEvent;",
                false
        ));
        prelude.add(new VarInsnNode(Opcodes.ASTORE, local));

        for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
            if (insn instanceof VarInsnNode var && var.var == 6) {
                InsnList replacement = new InsnList();

                replacement.add(new VarInsnNode(
                        Opcodes.ALOAD,
                        local
                ));

                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "client/nilore/event/impl/CameraPitchEvent",
                        "getPitch",
                        "()F",
                        false
                ));

                methodNode.instructions.insert(insn, replacement);
                methodNode.instructions.remove(insn);
            }
        }

        methodNode.instructions.insert(prelude);

        InsnList end = new InsnList();

        end.add(new VarInsnNode(Opcodes.ALOAD, 0));
        end.add(new VarInsnNode(Opcodes.ALOAD, 1));

        end.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(HumanoidModelPatch.class),
                "applyThirdPersonBlock",
                "(Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/world/entity/LivingEntity;)V",
                false
        ));

        methodNode.instructions.add(end);
    }
}
