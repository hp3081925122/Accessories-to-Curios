package com.hp.accessoriestocurios.bridge;

import com.mojang.logging.LogUtils;
import io.wispforest.accessories.impl.AccessoriesCapabilityImpl;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class AccessoriesCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private static final Field VALID_SLOT_TYPES_FIELD = findDeclaredField(AccessoriesHolderImpl.class, "validSlotTypes");
    @Nullable
    private static final Field LEGACY_VALID_SLOT_CONTAINERS_FIELD = VALID_SLOT_TYPES_FIELD == null
            ? findMutableDeclaredField(AccessoriesHolderImpl.class, "validSlotContainers")
            : null;
    @Nullable
    private static final Method CLEAR_VALIDATION_CACHE_METHOD = findClearValidationCacheMethod();

    private AccessoriesCompat() {
    }

    public static void resetHolderValidSlotView(AccessoriesHolderImpl holder) {
        if (holder == null) {
            return;
        }
        try {
            if (VALID_SLOT_TYPES_FIELD != null) {
                VALID_SLOT_TYPES_FIELD.set(holder, null);
            } else if (LEGACY_VALID_SLOT_CONTAINERS_FIELD != null) {
                LEGACY_VALID_SLOT_CONTAINERS_FIELD.set(holder, null);
            }
        } catch (Throwable throwable) {
            LOGGER.warn("[Accessories to Curios] Failed to reset AccessoriesHolderImpl valid slot view", throwable);
        }
    }

    public static void clearValidationCache(boolean isClientSide) {
        if (CLEAR_VALIDATION_CACHE_METHOD == null) {
            return;
        }
        try {
            CLEAR_VALIDATION_CACHE_METHOD.invoke(null, isClientSide);
        } catch (Throwable throwable) {
            LOGGER.warn("[Accessories to Curios] Failed to invoke AccessoriesCapabilityImpl.clearValidationCache", throwable);
        }
    }

    @Nullable
    private static Field findDeclaredField(Class<?> owner, String fieldName) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            return null;
        } catch (Throwable throwable) {
            LOGGER.warn("[Accessories to Curios] Failed to inspect field {}.{}", owner.getSimpleName(), fieldName, throwable);
            return null;
        }
    }

    @Nullable
    private static Field findMutableDeclaredField(Class<?> owner, String fieldName) {
        Field field = findDeclaredField(owner, fieldName);
        if (field != null && Modifier.isFinal(field.getModifiers())) {
            return null;
        }
        return field;
    }

    @Nullable
    private static Method findClearValidationCacheMethod() {
        try {
            Method method = AccessoriesCapabilityImpl.class.getDeclaredMethod("clearValidationCache", boolean.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Throwable throwable) {
            LOGGER.warn("[Accessories to Curios] Failed to inspect AccessoriesCapabilityImpl.clearValidationCache", throwable);
            return null;
        }
    }
}
