package dev.fayber.seamless.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fayber.seamless.client.DirectPlacementClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.util.EasyPlaceUtils", remap = false)
public abstract class LitematicaRewriteDirectMixin {
    @WrapOperation(method = "handleEasyPlace", require = 0,
            at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/util/InventoryUtils;schematicWorldPickBlock(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Minecraft;)V"))
    private static void seamless$pick(ItemStack required, BlockPos pos, Level level,
                                     Minecraft mc, Operation<Void> original) {
        if (!DirectPlacementClient.prepare(required, mc)) original.call(required, pos, level, mc);
    }

    @WrapOperation(method = "handleEasyPlace", require = 0,
            at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/util/EntityUtils;getUsedHandForItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/InteractionHand;"))
    private static InteractionHand seamless$hand(Player player, ItemStack required,
                                                  Operation<InteractionHand> original) {
        return DirectPlacementClient.resolveHand(required, original.call(player, required));
    }
}
