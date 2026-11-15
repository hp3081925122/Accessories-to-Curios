package com.hp.accessoriestocurios.mixin;

import com.mojang.logging.LogUtils;
import io.wispforest.owo.network.neoforge.SidedPacketCodec;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NetworkRegistry.class, remap = false)
public abstract class NetworkRegistryOwoFixMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getCodec", at = @At("RETURN"), cancellable = true)
    private static void accessoriesToCurios$unpackSidedPacketCodec(
            ResourceLocation id,
            ConnectionProtocol protocol,
            PacketFlow flow,
            CallbackInfoReturnable<StreamCodec<? super FriendlyByteBuf, ? extends CustomPacketPayload>> cir
    ) {
        if (cir.getReturnValue() instanceof SidedPacketCodec<?> sided) {
            LOGGER.debug("[Accessories to Curios] Unpacked SidedPacketCodec for payload {} ({})", id, flow);
            cir.setReturnValue((StreamCodec) sided.getCodec(flow));
        }
    }
}
