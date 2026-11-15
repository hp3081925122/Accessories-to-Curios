package com.hp.accessoriestocurios.bridge;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

public final class BridgeRuntimeAccess {
    private static volatile Supplier<Level> clientLevelSupplier = () -> null;
    private static volatile Supplier<Player> clientPlayerSupplier = () -> null;
    private static volatile Supplier<TooltipFlag> clientTooltipFlagSupplier = () -> TooltipFlag.NORMAL;
    private static volatile BooleanSupplier openCuriosClientHandler = () -> false;
    private static volatile UnaryOperator<List<Component>> tooltipNormalizer = list -> list;

    private BridgeRuntimeAccess() {
    }

    public static void bindClientAccess(
            Supplier<Level> levelSupplier,
            Supplier<Player> playerSupplier,
            Supplier<TooltipFlag> tooltipFlagSupplier,
            BooleanSupplier openCuriosHandler,
            UnaryOperator<List<Component>> normalizer
    ) {
        clientLevelSupplier = levelSupplier;
        clientPlayerSupplier = playerSupplier;
        clientTooltipFlagSupplier = tooltipFlagSupplier;
        openCuriosClientHandler = openCuriosHandler;
        tooltipNormalizer = normalizer;
    }

    public static List<Component> normalizeCurioTooltips(List<Component> tooltips) {
        return tooltipNormalizer.apply(tooltips);
    }

    @Nullable
    public static Level getClientLevel() {
        return clientLevelSupplier.get();
    }

    @Nullable
    public static Player getClientPlayer() {
        return clientPlayerSupplier.get();
    }

    public static TooltipFlag getClientTooltipFlag() {
        return clientTooltipFlagSupplier.get();
    }

    public static boolean openCuriosScreenOnClient() {
        return openCuriosClientHandler.getAsBoolean();
    }

    @Nullable
    public static Level resolveLevel(@Nullable LivingEntity entity) {
        if (entity != null) {
            return entity.level();
        }
        Level clientLevel = clientLevelSupplier.get();
        if (clientLevel != null) {
            return clientLevel;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.overworld() : null;
    }
}
