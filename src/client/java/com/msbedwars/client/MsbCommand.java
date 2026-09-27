package com.msbedwars.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.msbedwars.client.config.ModConfig;
import com.msbedwars.client.data.PlayerDatabase;
import com.msbedwars.client.data.PlayerRecord;
import com.msbedwars.client.stats.StatsLookup;
import com.msbedwars.client.stats.StatsService;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * /msb                  lists every feature switch and whether it is on
 * /msb toggle NAME      flips one switch
 * /msb stats PLAYER     prints what the mod knows about a player
 */
final class MsbCommand {
	private MsbCommand() {
	}

	static void register(StatsService stats, PlayerDatabase database) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommands.literal("msb")
						.executes(MsbCommand::listToggles)
						.then(ClientCommands.literal("toggle")
								.then(ClientCommands.argument("setting", StringArgumentType.word())
										.suggests((context, builder) -> {
											ModConfig.toggleNames().forEach(builder::suggest);
											return builder.buildFuture();
										})
										.executes(MsbCommand::toggle)))
						.then(ClientCommands.literal("stats")
								.then(ClientCommands.argument("player", StringArgumentType.word())
										.executes(context -> showStats(context, stats, database))))));
	}

	private static int listToggles(CommandContext<FabricClientCommandSource> context) {
		ModConfig config = ModConfig.get();
		context.getSource().sendFeedback(Component.literal("MSBedWars settings (/msb toggle <name>):").withStyle(ChatFormatting.GOLD));
		for (String name : ModConfig.toggleNames()) {
			boolean on = config.isOn(name);
			context.getSource().sendFeedback(Component.literal(" " + name + ": ")
					.append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)));
		}
		return 1;
	}

	private static int toggle(CommandContext<FabricClientCommandSource> context) {
		String name = StringArgumentType.getString(context, "setting");
		if (!ModConfig.toggleNames().contains(name)) {
			context.getSource().sendError(Component.literal("No setting called " + name + ". Type /msb to see them all."));
			return 0;
		}
		boolean on = ModConfig.get().flip(name);
		context.getSource().sendFeedback(Component.literal(name + " is now ")
				.append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)));
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
