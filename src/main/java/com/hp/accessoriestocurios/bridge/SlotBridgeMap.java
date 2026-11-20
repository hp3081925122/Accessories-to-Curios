package com.hp.accessoriestocurios.bridge;

import com.hp.accessoriestocurios.config.BridgeConfig;
import io.wispforest.accessories.api.DropRule;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.api.slot.UniqueSlotHandling;
import io.wispforest.accessories.data.EntitySlotLoader;
import io.wispforest.accessories.data.SlotTypeLoader;
import io.wispforest.accessories.impl.SlotTypeImpl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICurio;

public final class SlotBridgeMap {
    public static final String COLON_TOKEN = "__";

    private static final Map<String, String> BUILTIN_ACC_TO_CURIOS = Map.of(
            "hat", "head",
            "hand", "hands",
            "wrist", "bracelet",
            "shoes", "feet",
            "any", "curio"
    );

    private static final Map<String, String> BUILTIN_CURIOS_TO_ACC = Map.of(
            "head", "hat",
            "hands", "hand",
            "bracelet", "wrist",
            "feet", "shoes",
            "curio", "any"
    );

    private static final MappingSnapshot EMPTY_SNAPSHOT = new MappingSnapshot(Map.of(), Map.of(), Map.of());
    private static volatile MappingSnapshot cachedSnapshot;
    private static final Map<String, String> SANITIZED_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> TO_CURIOS_CACHE = new ConcurrentHashMap<>();

    private record MappingSnapshot(
            Map<String, String> accToCurios,
            Map<String, String> firstCuriosToAcc,
            Map<String, List<String>> allCuriosToAcc
    ) {
    }

    private SlotBridgeMap() {
    }

    public static void invalidateCache() {
        cachedSnapshot = null;
        TO_CURIOS_CACHE.clear();
    }

    private static MappingSnapshot getSnapshot() {
        if (!BridgeConfig.COMMON_SPEC.isLoaded()) {
            return EMPTY_SNAPSHOT;
        }
        MappingSnapshot snapshot = cachedSnapshot;
        if (snapshot != null) {
            return snapshot;
        }
        List<? extends String> entries = BridgeConfig.COMMON.customSlotMappings.get();
        if (entries.isEmpty()) {
            cachedSnapshot = EMPTY_SNAPSHOT;
            return EMPTY_SNAPSHOT;
        }
        Map<String, String> accToCur = new LinkedHashMap<>();
        for (String raw : entries) {
            if (raw == null) {
                continue;
            }
            int idx = raw.indexOf('=');
            if (idx <= 0 || idx >= raw.length() - 1) {
                continue;
            }
            String acc = raw.substring(0, idx).trim();
            String cur = sanitizeCuriosId(raw.substring(idx + 1).trim());
            if (!acc.isEmpty() && !cur.isEmpty()) {
                accToCur.put(acc, cur);
            }
        }
        if (accToCur.isEmpty()) {
            cachedSnapshot = EMPTY_SNAPSHOT;
            return EMPTY_SNAPSHOT;
        }
        Map<String, String> firstCurToAcc = new LinkedHashMap<>();
        Map<String, List<String>> multiCurToAcc = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : accToCur.entrySet()) {
            firstCurToAcc.putIfAbsent(entry.getValue(), entry.getKey());
            multiCurToAcc.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        Map<String, List<String>> immutableMulti = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : multiCurToAcc.entrySet()) {
            immutableMulti.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        MappingSnapshot built = new MappingSnapshot(
                Map.copyOf(accToCur),
                Map.copyOf(firstCurToAcc),
                Map.copyOf(immutableMulti)
        );
        cachedSnapshot = built;
        return built;
    }

    public static Map<String, String> getCustomAccToCurios() {
        return getSnapshot().accToCurios();
    }

    public static String toCuriosSlot(String accessoriesSlot) {
        if (accessoriesSlot == null || accessoriesSlot.isEmpty()) {
            return "curio";
        }
        String cached = TO_CURIOS_CACHE.get(accessoriesSlot);
        if (cached != null) {
            return cached;
        }
        String custom = getSnapshot().accToCurios().get(accessoriesSlot);
        String resolved;
        if (custom != null) {
            resolved = custom;
        } else {
            String builtin = BUILTIN_ACC_TO_CURIOS.get(accessoriesSlot);
            resolved = builtin != null ? builtin : sanitizeCuriosId(accessoriesSlot);
        }
        if (BridgeConfig.COMMON_SPEC.isLoaded()) {
            TO_CURIOS_CACHE.put(accessoriesSlot, resolved);
        }
        return resolved;
    }

    public static String toAccessoriesSlot(String curiosSlot) {
        if (curiosSlot == null || curiosSlot.isEmpty()) {
            return "any";
        }
        String custom = getSnapshot().firstCuriosToAcc().get(curiosSlot);
        if (custom != null) {
            return custom;
        }
        String builtin = BUILTIN_CURIOS_TO_ACC.get(curiosSlot);
        if (builtin != null) {
            return builtin;
        }
        int tokenIdx = curiosSlot.indexOf(COLON_TOKEN);
        if (tokenIdx > 0 && tokenIdx < curiosSlot.length() - COLON_TOKEN.length()) {
            return curiosSlot.substring(0, tokenIdx) + ":" + curiosSlot.substring(tokenIdx + COLON_TOKEN.length());
        }
        return curiosSlot;
    }

    public static String sanitizeCuriosId(String rawId) {
        String cached = SANITIZED_ID_CACHE.get(rawId);
        if (cached != null) {
            return cached;
        }
        String replaced = rawId.toLowerCase(Locale.ROOT).replace(":", COLON_TOKEN);
        StringBuilder sb = new StringBuilder(replaced.length());
        for (int i = 0; i < replaced.length(); i++) {
            char c = replaced.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String result = sb.isEmpty() ? "curio" : sb.toString();
        SANITIZED_ID_CACHE.put(rawId, result);
        return result;
    }

    public static List<String> getCandidateAccessoriesSlots(String curiosSlot, @Nullable Level level) {
        Set<String> candidates = new LinkedHashSet<>(
                getSnapshot().allCuriosToAcc().getOrDefault(curiosSlot, List.of())
        );
        String mapped = toAccessoriesSlot(curiosSlot);
        if (!"any".equals(mapped)) {
            candidates.add(mapped);
        }
        if (!"curio".equals(curiosSlot)) {
            candidates.add(curiosSlot);
        }
        if (level != null) {
            for (String accKey : SlotTypeLoader.getSlotTypes(level).keySet()) {
                if (toCuriosSlot(accKey).equals(curiosSlot)) {
                    candidates.add(accKey);
                }
            }
        }
        return new ArrayList<>(candidates);
    }

    public static String resolveContainerSlotName(@Nullable LivingEntity entity, String curiosSlot) {
        if ("curio".equals(curiosSlot) || curiosSlot == null || curiosSlot.isEmpty()) {
            return "curio";
        }
        if (entity != null) {
            Map<String, SlotType> entitySlots = EntitySlotLoader.getEntitySlots(entity);
            String primary = toAccessoriesSlot(curiosSlot);
            if (entitySlots.containsKey(primary)) {
                return primary;
            }
            if (entitySlots.containsKey(curiosSlot)) {
                return curiosSlot;
            }
            for (String accSlot : entitySlots.keySet()) {
                if (toCuriosSlot(accSlot).equals(curiosSlot)) {
                    return accSlot;
                }
            }
        }
        Level level = BridgeRuntimeAccess.resolveLevel(entity);
        if (level != null) {
            Map<String, SlotType> allSlots = SlotTypeLoader.getSlotTypes(level);
            String primary = toAccessoriesSlot(curiosSlot);
            if (allSlots.containsKey(primary)) {
                return primary;
            }
            if (allSlots.containsKey(curiosSlot)) {
                return curiosSlot;
            }
            for (String accSlot : allSlots.keySet()) {
                if (toCuriosSlot(accSlot).equals(curiosSlot)) {
                    return accSlot;
                }
            }
        }
        String fallback = toAccessoriesSlot(curiosSlot);
        return "any".equals(fallback) ? "curio" : fallback;
    }

    public static SlotReference toSlotReference(SlotContext slotContext) {
        String slotName = resolveContainerSlotName(slotContext.entity(), slotContext.identifier());
        int index = Math.max(0, slotContext.index());
        return SlotReference.of(slotContext.entity(), slotName, index);
    }

    public static SlotType resolveOrSynthesizeSlotType(String slotName, String curiosSlot, @Nullable Level level) {
        if (level != null) {
            Map<String, SlotType> slotTypes = SlotTypeLoader.getSlotTypes(level);
            SlotType existing = slotTypes.get(slotName);
            if (existing != null) {
                return existing;
            }
            for (String candidate : getCandidateAccessoriesSlots(curiosSlot, level)) {
                SlotType found = slotTypes.get(candidate);
                if (found != null) {
                    return found;
                }
            }
        }
        boolean isClient = level == null || level.isClientSide();
        Optional<ISlotType> curiosTypeOpt = CuriosApi.getSlot(curiosSlot, isClient);
        ResourceLocation icon = curiosTypeOpt.map(ISlotType::getIcon)
                .orElseGet(() -> new ResourceLocation(CuriosApi.MODID, "slot/empty_curio_slot"));
        int order = curiosTypeOpt.map(ISlotType::getOrder).orElse(1000);
        int size = curiosTypeOpt.map(ISlotType::getSize).orElse(1);
        DropRule dropRule = curiosTypeOpt.map(t -> toAccessoriesDropRule(t.getDropRule())).orElse(DropRule.DEFAULT);
        Optional<String> translation = UniqueSlotHandling.isUniqueSlot(slotName)
                ? Optional.empty()
                : Optional.of("curios.identifier." + curiosSlot);
        return new SlotTypeImpl(slotName, translation, icon, order, size, Set.of(), dropRule);
    }

    public static ICurio.DropRule toCuriosDropRule(DropRule rule) {
        if (rule == null) {
            return ICurio.DropRule.DEFAULT;
        }
        return switch (rule) {
            case KEEP -> ICurio.DropRule.ALWAYS_KEEP;
            case DROP -> ICurio.DropRule.ALWAYS_DROP;
            case DESTROY -> ICurio.DropRule.DESTROY;
            case DEFAULT -> ICurio.DropRule.DEFAULT;
        };
    }

    public static DropRule toAccessoriesDropRule(ICurio.DropRule rule) {
        if (rule == null) {
            return DropRule.DEFAULT;
        }
        return switch (rule) {
            case ALWAYS_KEEP -> DropRule.KEEP;
            case ALWAYS_DROP -> DropRule.DROP;
            case DESTROY -> DropRule.DESTROY;
            case DEFAULT -> DropRule.DEFAULT;
        };
    }
}
