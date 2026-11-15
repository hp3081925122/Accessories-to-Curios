package com.hp.accessoriestocurios.bridge;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.SoundEventData;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.events.OnDropCallback;
import io.wispforest.accessories.api.events.extra.AllowWalkingOnSnow;
import io.wispforest.accessories.api.events.extra.EndermanMasked;
import io.wispforest.accessories.api.events.extra.PiglinNeutralInducer;
import io.wispforest.accessories.api.slot.ExtraSlotTypeProperties;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.api.slot.SlotType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotAttribute;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;

public final class BridgedCurio implements ICurio {
    private final ItemStack stack;

    public BridgedCurio(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public ItemStack getStack() {
        return this.stack;
    }

    private Accessory accessory() {
        return AccessoriesAPI.getOrDefaultAccessory(this.stack);
    }

    @Override
    public void curioTick(SlotContext slotContext) {
        if (slotContext.cosmetic()) {
            return;
        }
        LivingEntity entity = slotContext.entity();
        if (entity == null) {
            return;
        }
        SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
        this.accessory().tick(this.stack, ref);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack) {
        if (slotContext.cosmetic() || slotContext.entity() == null) {
            return;
        }
        SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
        this.accessory().onEquip(this.stack, ref);
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack) {
        if (slotContext.cosmetic() || slotContext.entity() == null) {
            return;
        }
        SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
        this.accessory().onUnequip(this.stack, ref);
        BridgeCommonEvents.reapplyRemainingExclusiveModifiers(
                slotContext.entity(),
                this.stack,
                slotContext.identifier(),
                slotContext.index()
        );
    }

    @Override
    public boolean canEquip(SlotContext slotContext) {
        LivingEntity entity = slotContext.entity();
        if ("curio".equals(slotContext.identifier())) {
            Level level = BridgeRuntimeAccess.resolveLevel(entity);
            if (BridgeSlotValidator.onlyMatchesUniqueSlots(this.stack, level, entity)) {
                return false;
            }
        }
        if (entity == null) {
            return true;
        }
        SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
        return AccessoriesAPI.canEquip(this.stack, ref);
    }

    @Override
    public boolean canUnequip(SlotContext slotContext) {
        if (slotContext.entity() == null) {
            return true;
        }
        SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
        return AccessoriesAPI.canUnequip(this.stack, ref);
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext) {
        LivingEntity entity = slotContext.entity();
        boolean isClient = entity != null && entity.level().isClientSide();
        String accSlot = SlotBridgeMap.resolveContainerSlotName(entity, slotContext.identifier());
        if (!"curio".equals(accSlot) && !ExtraSlotTypeProperties.getProperty(accSlot, isClient).allowEquipFromUse()) {
            return false;
        }
        return this.accessory().canEquipFromUse(this.stack);
    }

    @Override
    public void onEquipFromUse(SlotContext slotContext) {
        LivingEntity entity = slotContext.entity();
        if (entity == null) {
            return;
        }
        SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
        this.accessory().onEquipFromUse(this.stack, ref);
    }

    @Override
    public SoundInfo getEquipSound(SlotContext slotContext) {
        SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
        SoundEventData sound = this.accessory().getEquipSound(this.stack, ref);
        if (sound != null) {
            return new SoundInfo(sound.event().value(), sound.volume(), sound.pitch());
        }
        return ICurio.super.getEquipSound(slotContext);
    }

    @Override
    public void curioBreak(SlotContext slotContext) {
        if (slotContext.entity() != null) {
            SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
            this.accessory().onBreak(this.stack, ref);
        } else {
            ICurio.super.curioBreak(slotContext);
        }
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext, ResourceLocation id) {
        Multimap<Holder<Attribute>, AttributeModifier> result = LinkedHashMultimap.create();
        LivingEntity entity = slotContext.entity();
        boolean isTooltipQuery = entity == null || slotContext.index() < 0;
        LivingEntity contextEntity = entity != null ? entity : BridgeRuntimeAccess.getClientPlayer();
        String accSlot = SlotBridgeMap.resolveContainerSlotName(contextEntity, slotContext.identifier());
        int slotIndex = Math.max(0, slotContext.index());

        AccessoryAttributeBuilder builder = AccessoriesAPI.getAttributeModifiers(
                this.stack,
                contextEntity,
                accSlot,
                slotIndex,
                isTooltipQuery
        );

        Multimap<Holder<Attribute>, AttributeModifier> regularModifiers = builder.getAttributeModifiers(true);
        result.putAll(regularModifiers);

        for (Map.Entry<String, AttributeModifier> entry : builder.getSlotModifiers().entries()) {
            String targetCuriosSlot = SlotBridgeMap.toCuriosSlot(entry.getKey());
            result.put(SlotAttribute.getOrCreate(targetCuriosSlot), entry.getValue());
        }

        return result;
    }

    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, Item.TooltipContext context) {
        return BridgeRuntimeAccess.normalizeCurioTooltips(tooltips);
    }

    @Override
    public List<Component> getAttributesTooltip(List<Component> tooltips, Item.TooltipContext context) {
        List<Component> normalized = BridgeRuntimeAccess.normalizeCurioTooltips(tooltips);
        LivingEntity clientPlayer = BridgeRuntimeAccess.getClientPlayer();
        Level level = BridgeRuntimeAccess.resolveLevel(clientPlayer);
        TooltipFlag flag = BridgeRuntimeAccess.getClientTooltipFlag();
        List<Component> extra = new ArrayList<>();
        List<SlotType> slotTypes = new ArrayList<>();
        if (level != null) {
            for (SlotType st : AccessoriesAPI.getStackSlotTypes(level, clientPlayer, this.stack)) {
                if (!BridgeSlotValidator.isInternalArmorProxySlot(st.name())) {
                    slotTypes.add(st);
                }
            }
        }
        if (slotTypes.isEmpty()) {
            slotTypes.add(SlotBridgeMap.resolveOrSynthesizeSlotType("curio", "curio", level));
        }
        for (SlotType slotType : slotTypes) {
            List<Component> slotExtra = new ArrayList<>();
            this.accessory().getAttributesTooltip(this.stack, slotType, slotExtra, context, flag);
            for (Component line : slotExtra) {
                if (!extra.contains(line)) {
                    extra.add(line);
                }
            }
        }
        if (extra.isEmpty()) {
            return normalized;
        }
        List<Component> combined = new ArrayList<>(normalized);
        combined.addAll(extra);
        return combined;
    }

    @Override
    public DropRule getDropRule(SlotContext slotContext, DamageSource source, boolean recentlyHit) {
        SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
        io.wispforest.accessories.api.DropRule baseRule = this.accessory().getDropRule(this.stack, ref, source);
        io.wispforest.accessories.api.DropRule resolved = OnDropCallback.getAlternativeRule(baseRule, this.stack, ref, source);
        if (resolved == io.wispforest.accessories.api.DropRule.DEFAULT
                && slotContext.entity() != null
                && io.wispforest.accessories.Accessories.RULE_KEEP_ACCESSORY_INVENTORY != null
                && slotContext.entity().level().getGameRules().getBoolean(io.wispforest.accessories.Accessories.RULE_KEEP_ACCESSORY_INVENTORY)) {
            return DropRule.ALWAYS_KEEP;
        }
        return SlotBridgeMap.toCuriosDropRule(resolved);
    }

    @Override
    public boolean makesPiglinsNeutral(SlotContext slotContext) {
        SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
        Accessory acc = this.accessory();
        if (acc instanceof PiglinNeutralInducer inducer) {
            TriState state = inducer.makePiglinsNeutral(this.stack, ref);
            if (state != TriState.DEFAULT) {
                return state.orElse(false);
            }
        }
        TriState state = PiglinNeutralInducer.EVENT.invoker().makePiglinsNeutral(this.stack, ref);
        if (state != TriState.DEFAULT) {
            return state.orElse(false);
        }
        return ICurio.super.makesPiglinsNeutral(slotContext);
    }

    @Override
    public boolean canWalkOnPowderedSnow(SlotContext slotContext) {
        SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
        Accessory acc = this.accessory();
        if (acc instanceof AllowWalkingOnSnow snow) {
            TriState state = snow.allowWalkingOnSnow(this.stack, ref);
            if (state != TriState.DEFAULT) {
                return state.orElse(false);
            }
        }
        TriState state = AllowWalkingOnSnow.EVENT.invoker().allowWalkingOnSnow(this.stack, ref);
        if (state != TriState.DEFAULT) {
            return state.orElse(false);
        }
        return ICurio.super.canWalkOnPowderedSnow(slotContext);
    }

    @Override
    public boolean isEnderMask(SlotContext slotContext, EnderMan enderMan) {
        SlotReference ref = SlotBridgeMap.toSlotReference(slotContext);
        Accessory acc = this.accessory();
        if (acc instanceof EndermanMasked masked) {
            TriState state = masked.isEndermanMasked(enderMan, this.stack, ref);
            if (state != TriState.DEFAULT) {
                return state.orElse(false);
            }
        }
        TriState state = EndermanMasked.EVENT.invoker().isEndermanMasked(enderMan, this.stack, ref);
        if (state != TriState.DEFAULT) {
            return state.orElse(false);
        }
        return ICurio.super.isEnderMask(slotContext, enderMan);
    }
}
