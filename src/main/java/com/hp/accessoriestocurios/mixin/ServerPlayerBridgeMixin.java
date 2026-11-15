package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.BridgeCommonEvents;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ServerPlayer.class, remap = false)
public abstract class ServerPlayerBridgeMixin {
    @ModifyVariable(
            method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false
    )
    private MenuProvider accessoriesToCurios$redirectAccessoryMenus(
            @Nullable MenuProvider provider,
            @Local(argsOnly = true) @Nullable Consumer<RegistryFriendlyByteBuf> extraDataWriter
    ) {
        return BridgeCommonEvents.wrapMenuProviderIfNeeded(
                (ServerPlayer) (Object) this,
                provider,
                extraDataWriter != null
        );
    }
}
