package client.nilore.modules.impl.movement.openssng;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record OpenSSNGBlockData(BlockPos pos, Direction facing) {
    public BlockPos placePos() { return pos.relative(facing); }
}
