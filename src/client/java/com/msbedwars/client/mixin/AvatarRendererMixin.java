package com.msbedwars.client.mixin;

import com.msbedwars.client.display.NameTagStats;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void msbedwars$addStatsLine(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (entity instanceof Player player) {
			NameTagStats.apply(player.getGameProfile().name(), state);
		}
	}
}
