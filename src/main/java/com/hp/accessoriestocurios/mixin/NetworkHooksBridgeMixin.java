package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.BridgeCommonEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = NetworkHooks.class, remap = false)
public abstract class NetworkHooksBridgeMixin {
    @ModifyVariable(
            method = "openScreen(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/MenuProvider;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false
    )
    private static MenuProvider accessoriesToCurios$redirectNetworkHooksMenus(
            @Nullable MenuProvider provider,
            ServerPlayer player,
            @Nullable MenuProvider originalSupplier
    ) {
        return BridgeCommonEvents.wrapMenuProviderIfNeeded(player, provider, false);
    }
}
