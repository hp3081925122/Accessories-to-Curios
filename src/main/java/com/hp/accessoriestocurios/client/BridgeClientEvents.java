package com.hp.accessoriestocurios.client;

import com.hp.accessoriestocurios.AccessoriesToCurios;
import com.hp.accessoriestocurios.bridge.BridgeRuntimeAccess;
import com.hp.accessoriestocurios.bridge.BridgeSlotValidator;
import com.hp.accessoriestocurios.bridge.SlotBridgeMap;
import com.hp.accessoriestocurios.config.BridgeConfig;
import com.hp.accessoriestocurios.mixin.client.AccessoriesRendererRegistryAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.client.DefaultAccessoryRenderer;
import io.wispforest.accessories.api.components.AccessoriesDataComponents;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.data.SlotTypeLoader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
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
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.common.network.NetworkHandler;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;

public final class BridgeClientEvents {
    public enum RendererDecision {
        DELEGATE,
        SUPPRESS_NULL,
        USE_DEFAULT
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> DUPLICATE_OPEN_KEY_NAMES = Set.of(
            "accessories.key.open_accessories_screen",
            "key.aether.open_accessories.desc"
    );
    private static final Map<Class<?>, List<Field>> ACCESSORY_BUTTON_FIELD_CACHE = new ConcurrentHashMap<>();

    private BridgeClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
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
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, BridgeClientEvents::onScreenInitPost);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BridgeClientEvents::onClientTick);
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
        NetworkHandler.INSTANCE.sendToServer(new CPacketOpenCurios(carried.copy()));
        return true;
    }

    public static KeyMapping createUnboundOpenKey() {
        return new KeyMapping(
                Accessories.MODID + ".key.open_accessories_screen",
                InputConstants.UNKNOWN.getValue(),
                Accessories.MODID + ".key.category.accessories"
        );
    }

    public static RendererDecision decideStackRenderer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return RendererDecision.SUPPRESS_NULL;
        }
        if (AccessoriesDataComponents.has(AccessoriesDataComponents.RENDER_OVERRIDE, stack)
                || AccessoriesRendererRegistryAccessor.accessoriesToCurios$getRenderers().containsKey(stack.getItem())) {
            return RendererDecision.DELEGATE;
        }
        if (CuriosRendererRegistry.getRenderer(stack.getItem()).isPresent()) {
            return RendererDecision.SUPPRESS_NULL;
        }
        if (!BridgeSlotValidator.isGenuineAccessoriesItem(stack)) {
            return RendererDecision.SUPPRESS_NULL;
        }
        if (BridgeConfig.CLIENT_SPEC.isLoaded() && !BridgeConfig.CLIENT.enableDefaultAccessoryRenderer.get()) {
            return RendererDecision.SUPPRESS_NULL;
        }
        return RendererDecision.USE_DEFAULT;
    }

    @Nullable
    public static AccessoryRenderer filterItemRenderer(Item item, @Nullable AccessoryRenderer original) {
        if (original != DefaultAccessoryRenderer.INSTANCE) {
            return original;
        }
        if (AccessoriesRendererRegistryAccessor.accessoriesToCurios$getRenderers().containsKey(item)) {
            return original;
        }
        if (CuriosRendererRegistry.getRenderer(item).isPresent()) {
            return null;
        }
        if (!BridgeSlotValidator.isGenuineAccessoriesItem(item.getDefaultInstance())) {
            return null;
        }
        if (BridgeConfig.CLIENT_SPEC.isLoaded() && !BridgeConfig.CLIENT.enableDefaultAccessoryRenderer.get()) {
            return null;
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
            String accKey = Accessories.translation("slot." + accSlot.replace(":", "."));
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
            String accKey = Accessories.translation("slot." + accSlot.replace(":", "."));
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

    private static List<Field> getAccessoryButtonFields(Class<?> screenClass) {
        return ACCESSORY_BUTTON_FIELD_CACHE.computeIfAbsent(screenClass, cls -> {
            List<Field> found = new ArrayList<>();
            for (Class<?> cur = cls; cur != null && cur != Screen.class && cur != Object.class; cur = cur.getSuperclass()) {
                for (Field field : cur.getDeclaredFields()) {
                    if (Button.class.isAssignableFrom(field.getType()) && field.getName().endsWith("accessoryButton")) {
                        field.setAccessible(true);
                        found.add(field);
                    }
                }
            }
            return List.copyOf(found);
        });
    }

    private static void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (!shouldHideAccessoriesUi()) {
            return;
        }
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) {
            return;
        }
        for (Field field : getAccessoryButtonFields(screen.getClass())) {
            try {
                Object val = field.get(screen);
                if (val instanceof GuiEventListener buttonListener) {
                    event.removeListener(buttonListener);
                    LOGGER.debug("[Accessories to Curios] Removed injected accessoryButton from {}", screen.getClass().getSimpleName());
                }
            } catch (IllegalAccessException ignored) {
            }
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

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !shouldHideAccessoriesUi()) {
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
