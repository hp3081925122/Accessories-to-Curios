package com.hp.accessoriestocurios.client;

import com.hp.accessoriestocurios.AccessoriesToCurios;
import com.hp.accessoriestocurios.bridge.BridgeRuntimeAccess;
import com.hp.accessoriestocurios.bridge.BridgeSlotValidator;
import com.hp.accessoriestocurios.bridge.SlotBridgeMap;
import com.hp.accessoriestocurios.config.BridgeConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.client.DefaultAccessoryRenderer;
import io.wispforest.accessories.api.client.EmptyRenderer;
import io.wispforest.accessories.api.components.AccessoriesDataComponents;
import io.wispforest.accessories.api.components.AccessoryItemAttributeModifiers;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.data.SlotTypeLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;

@Mod(value = AccessoriesToCurios.MOD_ID, dist = Dist.CLIENT)
public final class BridgeClientEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AccessoryRenderer SHARED_EMPTY_RENDERER = new EmptyRenderer();
    private static final Set<String> DUPLICATE_OPEN_KEY_NAMES = Set.of(
            "accessories.key.open_accessories_screen",
            "key.aether.open_accessories.desc"
    );

    public BridgeClientEvents(IEventBus modEventBus) {
        BridgeRuntimeAccess.bindClientAccess(
                () -> Minecraft.getInstance().level,
                () -> Minecraft.getInstance().player,
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    return mc.options != null && mc.options.advancedItemTooltips
                            ? TooltipFlag.ADVANCED
                            : TooltipFlag.NORMAL;
                },
                BridgeClientEvents::openCuriosScreen,
                BridgeClientEvents::normalizeTooltipComponents
        );
        NeoForge.EVENT_BUS.addListener(BridgeClientEvents::onGatherSkippedAttributeTooltips);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BridgeClientEvents::onScreenInitPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BridgeClientEvents::onClientTickPre);
    }

    public static boolean shouldHideAccessoriesUi() {
        return !BridgeConfig.CLIENT_SPEC.isLoaded() || BridgeConfig.CLIENT.hideAccessoriesUi.get();
    }

    public static boolean shouldRedirectOpenToCurios() {
        return !BridgeConfig.CLIENT_SPEC.isLoaded() || BridgeConfig.CLIENT.redirectOpenToCurios.get();
    }

    public static boolean openCuriosScreen() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return false;
        }
        ItemStack carried = player.containerMenu != null ? player.containerMenu.getCarried() : ItemStack.EMPTY;
        PacketDistributor.sendToServer(new CPacketOpenCurios(carried.copy()));
        return true;
    }

    public static KeyMapping createUnboundOpenKey() {
        return new KeyMapping(
                Accessories.MODID + ".key.open_accessories_screen",
                InputConstants.UNKNOWN.getValue(),
                Accessories.MODID + ".key.category.accessories"
        );
    }

    @Nullable
    public static AccessoryRenderer resolveOverrideRenderer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return SHARED_EMPTY_RENDERER;
        }
        if (stack.has(AccessoriesDataComponents.CUSTOM_RENDERER)
                || stack.has(AccessoriesDataComponents.RENDER_OVERRIDE)
                || AccessoriesRendererRegistry.hasRenderer(stack.getItem())) {
            return null;
        }
        if (CuriosRendererRegistry.getRenderer(stack.getItem()).isPresent()) {
            return SHARED_EMPTY_RENDERER;
        }
        if (!BridgeSlotValidator.isGenuineAccessoriesItem(stack)) {
            return SHARED_EMPTY_RENDERER;
        }
        if (BridgeConfig.CLIENT_SPEC.isLoaded() && !BridgeConfig.CLIENT.enableDefaultAccessoryRenderer.get()) {
            return SHARED_EMPTY_RENDERER;
        }
        return DefaultAccessoryRenderer.INSTANCE;
    }

    public static AccessoryRenderer filterItemRenderer(Item item, AccessoryRenderer original) {
        if (original != DefaultAccessoryRenderer.INSTANCE) {
            return original;
        }
        if (CuriosRendererRegistry.getRenderer(item).isPresent()) {
            return SHARED_EMPTY_RENDERER;
        }
        if (!BridgeSlotValidator.isGenuineAccessoriesItem(item.getDefaultInstance())) {
            return SHARED_EMPTY_RENDERER;
        }
        if (BridgeConfig.CLIENT_SPEC.isLoaded() && !BridgeConfig.CLIENT.enableDefaultAccessoryRenderer.get()) {
            return SHARED_EMPTY_RENDERER;
        }
        return original;
    }

    @Nullable
    public static String resolveFallbackSlotName(String curiosIdentifier, boolean isCosmetic) {
        String directKey = "curios.identifier." + curiosIdentifier;
        if (I18n.exists(directKey)) {
            return null;
        }
        Level level = Minecraft.getInstance().level;
        List<String> candidates = SlotBridgeMap.getCandidateAccessoriesSlots(curiosIdentifier, level);
        if (level != null) {
            Map<String, SlotType> slotTypes = SlotTypeLoader.getSlotTypes(level);
            for (String accSlot : candidates) {
                SlotType slotType = slotTypes.get(accSlot);
                if (slotType != null && I18n.exists(slotType.translation())) {
                    return prefixCosmetic(I18n.get(slotType.translation()), isCosmetic);
                }
            }
        }
        for (String accSlot : candidates) {
            String accKey = Accessories.translationKey("slot." + accSlot.replace(":", "."));
            if (I18n.exists(accKey)) {
                return prefixCosmetic(I18n.get(accKey), isCosmetic);
            }
        }
        return null;
    }

    private static String prefixCosmetic(String baseName, boolean isCosmetic) {
        if (isCosmetic) {
            return I18n.get("curios.cosmetic") + " " + baseName;
        }
        return baseName;
    }

    @Nullable
    private static String resolveAccessoriesTranslationKey(String curiosIdentifier) {
        Level level = Minecraft.getInstance().level;
        List<String> candidates = SlotBridgeMap.getCandidateAccessoriesSlots(curiosIdentifier, level);
        if (level != null) {
            Map<String, SlotType> slotTypes = SlotTypeLoader.getSlotTypes(level);
            for (String accSlot : candidates) {
                SlotType slotType = slotTypes.get(accSlot);
                if (slotType != null && I18n.exists(slotType.translation())) {
                    return slotType.translation();
                }
            }
        }
        for (String accSlot : candidates) {
            String accKey = Accessories.translationKey("slot." + accSlot.replace(":", "."));
            if (I18n.exists(accKey)) {
                return accKey;
            }
        }
        return null;
    }

    public static List<Component> normalizeTooltipComponents(List<Component> input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        List<Component> out = new ArrayList<>(input.size());
        for (Component comp : input) {
            out.add(rewriteComponent(comp));
        }
        return out;
    }

    private static Component rewriteComponent(Component comp) {
        MutableComponent rebuilt = null;
        if (comp.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            Object[] rawArgs = tc.getArgs();
            Object[] rewrittenArgs = rawArgs;
            boolean anyArgChanged = false;
            if (rawArgs.length > 0) {
                rewrittenArgs = new Object[rawArgs.length];
                for (int i = 0; i < rawArgs.length; i++) {
                    Object arg = rawArgs[i];
                    if (arg instanceof Component argComp) {
                        Component newArg = rewriteComponent(argComp);
                        rewrittenArgs[i] = newArg;
                        if (newArg != argComp) {
                            anyArgChanged = true;
                        }
                    } else {
                        rewrittenArgs[i] = arg;
                    }
                }
            }
            if (key.startsWith("curios.identifier.") && !I18n.exists(key)) {
                String id = key.substring("curios.identifier.".length());
                String accKey = resolveAccessoriesTranslationKey(id);
                if (accKey != null) {
                    rebuilt = Component.translatable(accKey).withStyle(comp.getStyle());
                }
            } else if (key.startsWith("curios.modifiers.") && !I18n.exists(key)) {
                String id = key.substring("curios.modifiers.".length());
                String accKey = resolveAccessoriesTranslationKey(id);
                if (accKey != null) {
                    rebuilt = Component.translatable(
                            AccessoriesToCurios.MOD_ID + ".modifiers.slot",
                            Component.translatable(accKey)
                    ).withStyle(ChatFormatting.GOLD);
                }
            } else if (anyArgChanged) {
                rebuilt = Component.translatable(key, rewrittenArgs).withStyle(comp.getStyle());
            }
        }

        List<Component> siblings = comp.getSiblings();
        if (rebuilt == null && siblings.isEmpty()) {
            return comp;
        }
        boolean anySiblingChanged = false;
        List<Component> newSiblings = new ArrayList<>(siblings.size());
        for (Component sib : siblings) {
            Component rewritten = rewriteComponent(sib);
            if (rewritten != sib) {
                anySiblingChanged = true;
            }
            newSiblings.add(rewritten);
        }
        if (rebuilt == null && !anySiblingChanged) {
            return comp;
        }
        MutableComponent base = rebuilt != null ? rebuilt : comp.plainCopy().withStyle(comp.getStyle());
        for (Component sib : newSiblings) {
            base.append(sib);
        }
        return base;
    }

    private static void onGatherSkippedAttributeTooltips(GatherSkippedAttributeTooltipsEvent event) {
        ItemStack stack = event.getStack();
        if (!stack.has(AccessoriesDataComponents.ATTRIBUTES)) {
            return;
        }
        AccessoryItemAttributeModifiers component = stack.getOrDefault(
                AccessoriesDataComponents.ATTRIBUTES,
                AccessoryItemAttributeModifiers.EMPTY
        );
        if (!component.showInTooltip()) {
            for (AccessoryItemAttributeModifiers.Entry entry : component.modifiers()) {
                event.skipId(entry.modifier().id());
            }
        }
    }

    private static void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (!shouldHideAccessoriesUi()) {
            return;
        }
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) {
            return;
        }
        for (GuiEventListener listener : new ArrayList<>(event.getListenersList())) {
            if (listener == null) {
                continue;
            }
            String className = listener.getClass().getName();
            if (className.equals("com.aetherteam.aether.client.gui.component.inventory.AccessoryButton")
                    || className.startsWith("io.wispforest.accessories.client.gui.")) {
                event.removeListener(listener);
                LOGGER.debug("[Accessories to Curios] Removed duplicate accessory UI widget {} from {}", className, screen.getClass().getSimpleName());
            }
        }
    }

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        if (!shouldHideAccessoriesUi()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.keyMappings == null) {
            return;
        }
        boolean foundDuplicate = false;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping != null && DUPLICATE_OPEN_KEY_NAMES.contains(mapping.getName())) {
                foundDuplicate = true;
                break;
            }
        }
        if (!foundDuplicate) {
            return;
        }
        List<KeyMapping> kept = new ArrayList<>(mc.options.keyMappings.length);
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping != null && DUPLICATE_OPEN_KEY_NAMES.contains(mapping.getName())) {
                mapping.setKey(InputConstants.UNKNOWN);
                while (mapping.consumeClick()) {
                }
                LOGGER.debug("[Accessories to Curios] Unbound and removed duplicate accessory keybind {}", mapping.getName());
            } else if (mapping != null) {
                kept.add(mapping);
            }
        }
        mc.options.keyMappings = kept.toArray(KeyMapping[]::new);
        KeyMapping.resetMapping();
    }
}
