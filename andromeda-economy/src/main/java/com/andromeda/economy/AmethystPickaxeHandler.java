package com.andromeda.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AmethystPickaxeHandler {

    // Prevents recursive triggering when the 9x9 breaks surrounding blocks
    private static final Set<UUID> ACTIVE = new HashSet<>();

    // Purple in ARGB int: A=255, R=191, G=51, B=255
    private static final DustParticleOptions PREVIEW_DUST =
        new DustParticleOptions((255 << 24) | (191 << 16) | (51 << 8) | 255, 1.0f);

    private AmethystPickaxeHandler() {}

    // ── Block break ───────────────────────────────────────────────────────────

    /** Called from PlayerBlockBreakEvents.AFTER — breaks the remaining 80 blocks of the 9x9. */
    public static void onBlockBroken(ServerLevel level, ServerPlayer player,
                                     BlockPos center, BlockState originalState) {
        if (!AmethystPickaxe.is(player.getMainHandItem())) return;
        if (!ACTIVE.add(player.getUUID())) return; // already processing for this player

        try {
            ItemStack tool = player.getMainHandItem();
            Direction.Axis axis = dominantAxis(player.getLookAngle());

            for (BlockPos pos : getPattern(center, axis)) {
                if (pos.equals(center)) continue; // already broken by the player
                breakWithFortune(level, player, pos, tool);
            }

            // One satisfying sound at the center when all blocks are gone
            level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.BLOCKS, 1.3f, 0.85f);

        } finally {
            ACTIVE.remove(player.getUUID());
        }
    }

    private static void breakWithFortune(ServerLevel level, ServerPlayer player,
                                          BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        // Skip unbreakable blocks (bedrock, end portal frame, etc.)
        if (state.getDestroyProgress(player, level, pos) <= 0) return;

        BlockEntity be = level.getBlockEntity(pos);
        level.removeBlock(pos, false);                             // silent removal
        Block.dropResources(state, level, pos, be, player, tool); // fortune-aware drops
    }

    // ── Preview particles ─────────────────────────────────────────────────────

    /** Call every 3 ticks — sends purple dust particles outlining the 9x9 area. */
    public static void tickParticles(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!AmethystPickaxe.is(player.getMainHandItem())) continue;

            HitResult hit = player.pick(5.0, 0f, false);
            if (hit.getType() != HitResult.Type.BLOCK) continue;

            BlockPos target = ((BlockHitResult) hit).getBlockPos();
            ServerLevel level = (ServerLevel) player.level();
            Direction.Axis axis = dominantAxis(player.getLookAngle());

            for (BlockPos pos : getPattern(target, axis)) {
                if (level.getBlockState(pos).isAir()) continue;
                level.sendParticles(player, PREVIEW_DUST, false, false,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    2, 0.45, 0.45, 0.45, 0.0);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns all 81 BlockPos in the 9x9 plane centred on {@code center}.
     * The plane is perpendicular to the given axis (the axis the player is looking along).
     */
    static List<BlockPos> getPattern(BlockPos center, Direction.Axis axis) {
        List<BlockPos> list = new ArrayList<>(9);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                list.add(switch (axis) {
                    case X -> center.offset(0, a, b); // east/west face  → YZ plane
                    case Z -> center.offset(a, b, 0); // north/south face → XY plane
                    case Y -> center.offset(a, 0, b); // floor/ceiling   → XZ plane
                });
            }
        }
        return list;
    }

    /** Returns the Direction.Axis the player is looking along most strongly. */
    static Direction.Axis dominantAxis(Vec3 look) {
        double ax = Math.abs(look.x), ay = Math.abs(look.y), az = Math.abs(look.z);
        if (ay > ax && ay > az) return Direction.Axis.Y;
        if (ax > az)            return Direction.Axis.X;
        return Direction.Axis.Z;
    }
}
