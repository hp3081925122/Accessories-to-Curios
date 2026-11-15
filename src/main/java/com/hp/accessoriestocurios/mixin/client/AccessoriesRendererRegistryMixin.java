package com.hp.accessoriestocurios.mixin.client;

import com.hp.accessoriestocurios.client.BridgeClientEvents;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AccessoriesRendererRegistry.class, remap = false)
public abstract class AccessoriesRendererRegistryMixin {
    @Inject(
            method = "getRenderer(Lnet/minecraft/world/item/ItemStack;)Lio/wispforest/accessories/api/client/AccessoryRenderer;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void accessoriesToCurios$resolveStackRenderer(
            ItemStack stack,
            CallbackInfoReturnable<AccessoryRenderer> cir
    ) {
        AccessoryRenderer override = BridgeClientEvents.resolveOverrideRenderer(stack);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    @Inject(
            method = "getRenderer(Lnet/minecraft/world/item/Item;)Lio/wispforest/accessories/api/client/AccessoryRenderer;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void accessoriesToCurios$filterItemRenderer(
            Item item,
            CallbackInfoReturnable<AccessoryRenderer> cir
    ) {
        cir.setReturnValue(BridgeClientEvents.filterItemRenderer(item, cir.getReturnValue()));
    }
}
