package com.msbedwars.client;

import com.msbedwars.client.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * With the "capture" switch on, records what the server shows so parsers can be matched to
 * the real thing: every chat message with its colors, and the sidebar and tab list whenever
 * they change. Written to .minecraft/msbedwars/capture.log. Local only, never sent anywhere.
 */
public final class CaptureLog {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private final Path file;
	private String lastSidebar = "";
	private String lastTab = "";
	private int ticks;

	public CaptureLog(Path file) {
		this.file = file;
	}

	/** Register before any chat listener that may hide messages, so capture still sees them. */
	public void register() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (ModConfig.get().capture) Safe.run("capture", () -> write("CHAT" + (overlay ? " (action bar)" : ""), describe(message)));
			return true;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++ticks < 20 || !ModConfig.get().capture) return;
			ticks = 0;
			Safe.run("capture", () -> captureBoards(client));
		});
	}

	private void captureBoards(Minecraft client) {
		if (client.level == null || client.getConnection() == null) return;
		Scoreboard scoreboard = client.level.getScoreboard();

		StringBuilder sidebar = new StringBuilder();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective != null) {
			sidebar.append("title: ").append(describe(objective.getDisplayName())).append('\n');
			for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
				if (entry.isHidden()) continue;
				PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
				sidebar.append("  [").append(entry.value()).append("] ")
						.append(describe(PlayerTeam.formatNameForTeam(team, entry.ownerName()))).append('\n');
			}
		}
		Objective below = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
		if (below != null) sidebar.append("below name objective: ").append(describe(below.getDisplayName())).append('\n');
		String sidebarText = sidebar.toString();
		if (!sidebarText.equals(lastSidebar)) {
			lastSidebar = sidebarText;
			write("SIDEBAR", sidebarText.isEmpty() ? "(none)" : sidebarText);
		}

		StringBuilder tab = new StringBuilder();
		Objective tabObjective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
		if (tabObjective != null) tab.append("tab objective: ").append(describe(tabObjective.getDisplayName())).append('\n');
		for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
			String name = info.getProfile().name();
			PlayerTeam team = scoreboard.getPlayersTeam(name);
			tab.append("  ").append(name)
					.append(" uuid-v").append(info.getProfile().id().version())
					.append(" team=").append(team == null ? "-" : team.getName() + "/" + team.getColor().getName());
			if (info.getTabListDisplayName() != null) tab.append(" shown=").append(describe(info.getTabListDisplayName()));
			tab.append('\n');
		}
		String tabText = tab.toString();
		if (!tabText.equals(lastTab)) {
			lastTab = tabText;
			write("TAB", tabText);
		}
	}

	// Plain text, then the same text split into colored pieces: [#FF5555]Name
	private static String describe(Component component) {
		List<String> pieces = new ArrayList<>();
		component.visit((Style style, String part) -> {
			TextColor color = style.getColor();
			pieces.add("[" + (color == null ? "none" : color.serialize()) + "]" + part);
			return Optional.empty();
		}, Style.EMPTY);
		return ChatFormatting.stripFormatting(component.getString()) + "\n    pieces: " + String.join("", pieces);
	}

	private synchronized void write(String kind, String text) {
		String entry = LocalTime.now().format(TIME) + " " + kind + "\n" + text.strip() + "\n\n";
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			MSBedWarsClient.LOG.warn("Could not write {}", file, e);
		}
	}
}
