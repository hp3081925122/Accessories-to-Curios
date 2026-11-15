package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.BridgeSlotValidator;
import com.hp.accessoriestocurios.bridge.BridgedCurio;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.mixin.CuriosImplMixinHooks;

@Mixin(value = CuriosImplMixinHooks.class, remap = false)
public abstract class CuriosImplMixinHooksMixin {
    @Unique
    private static final ResourceLocation CURIOS_NONE =
            new ResourceLocation(CuriosApi.MODID, "none");

    @Inject(method = "testCurioPredicates", at = @At("RETURN"), cancellable = true)
    private static void accessoriesToCurios$fallbackTest(
            Set<ResourceLocation> predicates,
            SlotResult slotResult,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValueZ()) {
            return;
        }
        if (predicates.contains(CURIOS_NONE) || predicates.contains(BridgeSlotValidator.PREDICATE_ID)) {
            return;
        }
        if (BridgeSlotValidator.testCurioSlot(slotResult)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getCurio", at = @At("RETURN"), cancellable = true)
    private static void accessoriesToCurios$fallbackGetCurio(
            ItemStack stack,
            CallbackInfoReturnable<LazyOptional<ICurio>> cir
    ) {
        if (cir.getReturnValue().isPresent() || stack == null || stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (item instanceof ICurioItem || CuriosImplMixinHooks.getCurioFromRegistry(item).isPresent()) {
            return;
        }
        if (BridgeSlotValidator.isGenuineAccessoriesItem(stack)) {
            cir.setReturnValue(LazyOptional.of(() -> new BridgedCurio(stack)));
        }
    }
}
