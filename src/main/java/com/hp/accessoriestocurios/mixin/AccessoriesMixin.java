package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.config.BridgeConfig;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.menu.AccessoriesMenuVariant;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.common.inventory.container.CuriosContainerProvider;
import top.theillusivec4.curios.common.network.server.SPacketGrabbedItem;

@Mixin(value = Accessories.class, remap = false)
public abstract class AccessoriesMixin {
    @Inject(
            method = "openAccessoriesMenu(Lnet/minecraft/world/entity/player/Player;Lio/wispforest/accessories/menu/AccessoriesMenuVariant;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void accessoriesToCurios$redirectMenuOpen(
            Player player,
            AccessoriesMenuVariant variant,
            @Nullable LivingEntity targetEntity,
            @Nullable ItemStack carriedStack,
            CallbackInfo ci
    ) {
        if (BridgeConfig.COMMON_SPEC.isLoaded() && !BridgeConfig.COMMON.disableAccessoriesMenu.get()) {
            return;
        }
        ItemStack stack = carriedStack != null ? carriedStack.copy() : ItemStack.EMPTY;
        if (player instanceof ServerPlayer serverPlayer) {
            if (targetEntity == null || targetEntity == player) {
                if (carriedStack != null && serverPlayer.containerMenu != null) {
                    serverPlayer.containerMenu.setCarried(ItemStack.EMPTY);
                }
                serverPlayer.openMenu(new CuriosContainerProvider());
                if (!stack.isEmpty() && serverPlayer.containerMenu != null) {
                    serverPlayer.containerMenu.setCarried(stack);
                    PacketDistributor.sendToPlayer(serverPlayer, new SPacketGrabbedItem(stack));
                }
            } else if (!stack.isEmpty()) {
                if (serverPlayer.containerMenu != null) {
                    serverPlayer.containerMenu.setCarried(ItemStack.EMPTY);
                }
                ItemHandlerHelper.giveItemToPlayer(serverPlayer, stack);
                if (serverPlayer.containerMenu != null) {
                    serverPlayer.containerMenu.broadcastFullState();
                }
            }
        } else if (!stack.isEmpty() && !player.level().isClientSide()) {
            if (player.containerMenu != null) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
            }
            ItemHandlerHelper.giveItemToPlayer(player, stack);
        }
        ci.cancel();
    }
}
