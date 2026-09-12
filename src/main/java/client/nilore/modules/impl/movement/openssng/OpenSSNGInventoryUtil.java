package client.nilore.modules.impl.movement.openssng;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class OpenSSNGInventoryUtil {
    private static final Minecraft mc = Minecraft.getInstance();
    private OpenSSNGInventoryUtil() {}

    public static boolean isFullBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi) || mc.level == null || mc.player == null) return false;
        Block block = bi.getBlock();
        if (block instanceof FallingBlock) return false;
        BlockState state = block.defaultBlockState();
        return !state.getCollisionShape(mc.level, BlockPos.ZERO, CollisionContext.of(mc.player)).isEmpty();
    }

    public static void swapInventoryToHotbar(int inventorySlot, int hotbarSlot) {
        if (mc.player == null || mc.gameMode == null) return;
        int menuSlot = inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, menuSlot, hotbarSlot, ClickType.SWAP, mc.player);
    }
}
