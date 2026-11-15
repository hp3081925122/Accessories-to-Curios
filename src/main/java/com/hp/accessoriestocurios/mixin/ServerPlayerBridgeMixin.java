package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.BridgeCommonEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ServerPlayer.class)
public abstract class ServerPlayerBridgeMixin {
    @ModifyVariable(
            method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private MenuProvider accessoriesToCurios$redirectAccessoryMenus(@Nullable MenuProvider provider) {
        return BridgeCommonEvents.wrapMenuProviderIfNeeded(
                (ServerPlayer) (Object) this,
                provider,
                false
        );
    }
}
