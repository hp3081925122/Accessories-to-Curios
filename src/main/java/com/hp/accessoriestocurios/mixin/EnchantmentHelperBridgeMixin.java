package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.BridgeCommonEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EnchantmentHelper.class, priority = 500)
public abstract class EnchantmentHelperBridgeMixin {
    @Inject(
            method = "getEnchantmentLevel(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/entity/LivingEntity;)I",
            at = @At("HEAD")
    )
    private static void accessoriesToCurios$enterLooting(
            Enchantment enchantment,
            LivingEntity entity,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (enchantment == Enchantments.MOB_LOOTING) {
            BridgeCommonEvents.enterLootingCalculation();
        }
    }

    @Inject(
            method = "getEnchantmentLevel(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/entity/LivingEntity;)I",
            at = @At("RETURN")
    )
    private static void accessoriesToCurios$exitLooting(
            Enchantment enchantment,
            LivingEntity entity,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (enchantment == Enchantments.MOB_LOOTING) {
            BridgeCommonEvents.exitLootingCalculation();
        }
    }
}
