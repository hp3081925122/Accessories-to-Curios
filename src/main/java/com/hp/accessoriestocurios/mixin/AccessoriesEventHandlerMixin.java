package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.LegacyDataMigrator;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.impl.AccessoriesCapabilityImpl;
import io.wispforest.accessories.impl.AccessoriesEventHandler;
import io.wispforest.accessories.networking.AccessoriesNetworking;
import io.wispforest.accessories.networking.client.SyncData;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AccessoriesEventHandler.class, remap = false)
public abstract class AccessoriesEventHandlerMixin {
    @Inject(method = "onLivingEntityTick", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$onLivingTick(LivingEntity entity, CallbackInfo ci) {
        if (!entity.isRemoved() && !entity.level().isClientSide()) {
            LegacyDataMigrator.migrateEntityIfNeeded(entity);
        }
        ci.cancel();
    }

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$onDeath(
            LivingEntity entity,
            DamageSource source,
            CallbackInfoReturnable<Collection<ItemStack>> cir
    ) {
        cir.setReturnValue(List.of());
    }

    @Inject(method = "attemptEquipFromUse", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$suppressDuplicateEquipFromUse(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir
    ) {
        cir.setReturnValue(InteractionResultHolder.pass(player.getItemInHand(hand)));
    }

    @Inject(method = "entityLoad", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$suppressEntityLoadSync(LivingEntity entity, Level level, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "onTracking", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$suppressTrackingSync(
            LivingEntity entity,
            ServerPlayer serverPlayer,
            CallbackInfo ci
    ) {
        ci.cancel();
    }

    @Inject(method = "revalidatePlayersOnReload", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$suppressReloadRevalidate(PlayerList playerList, CallbackInfo ci) {
        AccessoriesEventHandler.dataReloadOccurred = false;
        ci.cancel();
    }

    @Inject(method = "revalidatePlayer", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$suppressPlayerRevalidate(ServerPlayer player, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "dataSync", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$syncSlotDefinitionsOnly(
            @Nullable PlayerList list,
            @Nullable ServerPlayer player,
            CallbackInfo ci
    ) {
        SyncData syncPacket = SyncData.create();
        if (list != null && !list.getPlayers().isEmpty()) {
            for (ServerPlayer entry : list.getPlayers()) {
                AccessoriesNetworking.sendToPlayer(entry, syncPacket);
            }
        } else if (player != null) {
            AccessoriesNetworking.sendToPlayer(player, syncPacket);
        }
        AccessoriesEventHandler.dataReloadOccurred = false;
        AccessoriesCapabilityImpl.clearValidationCache(false);
        ci.cancel();
    }

    @Inject(method = "getTooltipData", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$onlyExtraTooltip(
            @Nullable LivingEntity entity,
            ItemStack stack,
            List<Component> tooltip,
            Item.TooltipContext tooltipContext,
            TooltipFlag tooltipType,
            CallbackInfo ci
    ) {
        Accessory accessory = AccessoriesAPI.getOrDefaultAccessory(stack);
        if (accessory != null) {
            accessory.getExtraTooltip(stack, tooltip, tooltipContext, tooltipType);
        }
        ci.cancel();
    }
}
