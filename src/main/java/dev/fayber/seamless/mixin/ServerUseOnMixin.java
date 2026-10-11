package dev.fayber.seamless.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fayber.seamless.DirectPlacementServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerUseOnMixin {
    @WrapMethod(method = "useItemOn")
    private InteractionResult seamless$directItem(ServerPlayer player, Level level, ItemStack stack,
            InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
        if (hand != InteractionHand.MAIN_HAND) return original.call(player, level, stack, hand, hit);
        return DirectPlacementServer.useOn(player, stack, selected ->
                original.call(player, level, selected, hand, hit));
    }
}
