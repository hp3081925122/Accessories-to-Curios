package com.hp.accessoriestocurios.mixin.client;

import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AccessoriesRendererRegistry.class, remap = false)
public interface AccessoriesRendererRegistryAccessor {
    @Accessor("RENDERERS")
    static Map<Item, Supplier<AccessoryRenderer>> accessoriesToCurios$getRenderers() {
        throw new AssertionError();
    }
}
