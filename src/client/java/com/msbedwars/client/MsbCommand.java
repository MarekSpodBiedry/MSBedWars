package com.msbedwars.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.msbedwars.client.config.ConfigScreen;
import com.msbedwars.client.data.PlayerDatabase;
import com.msbedwars.client.data.PlayerRecord;
import com.msbedwars.client.stats.StatsLookup;
import com.msbedwars.client.stats.StatsService;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * /msb                  opens the settings screen
 * /msb stats PLAYER     prints what the mod knows about a player, for debugging
 */
final class MsbCommand {
	private MsbCommand() {
	}

	static void register(StatsService stats, PlayerDatabase database) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommands.literal("msb")
						.executes(context -> openSettings())
						.then(ClientCommands.literal("stats")
								.then(ClientCommands.argument("player", StringArgumentType.word())
										.executes(context -> showStats(context, stats, database))))));
	}

	// The chat screen closes right after a command runs, so the settings open one tick later
	private static int openSettings() {
		Minecraft client = Minecraft.getInstance();
		client.schedule(() -> client.setScreen(new ConfigScreen(null)));
		return 1;
	}

	private static int showStats(CommandContext<FabricClientCommandSource> context, StatsService stats, PlayerDatabase database) {
		String name = StringArgumentType.getString(context, "player");
		stats.requestFirst(name);
		String text = stats.get(name).map(MsbCommand::describe).orElse("not requested");
		int together = database.get(name).map(PlayerRecord::matchesTogether).orElse(0);
		Integer stars = database.get(name).map(PlayerRecord::stars).orElse(null);
		context.getSource().sendFeedback(Component.literal(name + ": " + (stars == null ? "?" : stars) + " stars (from chat) | "
				+ text + " | matches together: " + together));
		return 1;
	}

	private static String describe(StatsLookup lookup) {
		return switch (lookup) {
			case StatsLookup.Loading ignored -> "loading, run the command again in a moment";
			case StatsLookup.Nicked ignored -> "no hypixel.net profile (nicked?)";
			case StatsLookup.Failed failed -> "failed: " + failed.reason();
			case StatsLookup.Found found -> String.format("FKDR %.2f, WLR %.2f",
					found.stats().overall().fkdr(), found.stats().overall().wlr());
		};
	}
}
