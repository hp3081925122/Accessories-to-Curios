package com.hp.accessoriestocurios.bridge;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;

public final class MenuRedirectingProvider implements MenuProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MenuProvider delegate;
    private boolean redirected;

    public MenuRedirectingProvider(MenuProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Component getDisplayName() {
        if (this.redirected) {
            return Component.translatable("container.crafting");
        }
        return this.delegate.getDisplayName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        AbstractContainerMenu created = this.delegate.createMenu(containerId, playerInventory, player);
        if (BridgeCommonEvents.shouldRedirectOpenedMenu(player, created)) {
            this.redirected = true;
            LOGGER.debug(
                    "[Accessories to Curios] Redirected accessory menu {} to CuriosContainer for player {}",
                    created.getClass().getName(),
                    player.getScoreboardName()
            );
            return new CuriosContainer(containerId, playerInventory);
        }
        return created;
    }
}
