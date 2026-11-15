package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.BridgeCommonEvents;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EnchantmentHelper.class, priority = 500, remap = false)
public abstract class EnchantmentHelperBridgeMixin {
    @Inject(
            method = "getEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;)I",
            at = @At("HEAD")
    )
    private static void accessoriesToCurios$enterLooting(
            Holder<Enchantment> enchantment,
            LivingEntity entity,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (enchantment.is(Enchantments.LOOTING)) {
            BridgeCommonEvents.enterLootingCalculation();
        }
    }

    @Inject(
            method = "getEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;)I",
            at = @At("RETURN")
    )
    private static void accessoriesToCurios$exitLooting(
            Holder<Enchantment> enchantment,
            LivingEntity entity,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (enchantment.is(Enchantments.LOOTING)) {
            BridgeCommonEvents.exitLootingCalculation();
        }
    }
}
