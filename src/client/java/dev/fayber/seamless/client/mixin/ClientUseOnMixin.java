package dev.fayber.seamless.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fayber.seamless.client.DirectPlacementClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MultiPlayerGameMode.class)
public abstract class ClientUseOnMixin {
    @WrapMethod(method = "useItemOn")
    private InteractionResult seamless$useFromBox(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
            Operation<InteractionResult> original) {
        return DirectPlacementClient.useOn(player, hand, hit, () -> original.call(player, hand, hit));
    }
}
