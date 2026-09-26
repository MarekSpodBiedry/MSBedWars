package com.msbedwars.client.display;

import com.msbedwars.client.config.ModConfig;
import com.msbedwars.client.lobby.BedwarsPhase;
import com.msbedwars.client.lobby.LobbyTracker;
import com.msbedwars.client.lobby.MatchRoster;
import com.msbedwars.client.party.PartyTracker;
import com.msbedwars.client.stats.BedwarsMode;
import com.msbedwars.client.stats.BedwarsStats;
import com.msbedwars.client.stats.StatsLookup;
import com.msbedwars.client.stats.StatsService;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Table in the top right corner: head, name, stars, FKDR in the current mode (plus the most
 * played mode's FKDR in brackets when it differs enough), health from the tab list.
 * In a match it lists everyone grouped by team; in the Bed Wars lobby it lists the party.
 */
public final class StatsHud implements HudElement {
	private static final int SCREEN_MARGIN = 4;
	private static final int PADDING = 3;
	private static final int COLUMN_GAP = 6;
	private static final int GROUP_GAP = 3;
	private static final int HEAD = 8;
	private static final int BACKGROUND = 0xB0000000;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int WHITE = 0xFFFFFFFF;
	private static final List<ChatFormatting> TEAM_ORDER = List.of(ChatFormatting.RED, ChatFormatting.BLUE,
			ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.AQUA, ChatFormatting.WHITE,
			ChatFormatting.LIGHT_PURPLE, ChatFormatting.GRAY);

	private record Cell(String text, int color) {
		static final Cell EMPTY = new Cell("", WHITE);
	}

	private record Row(PlayerSkin skin, Cell name, Cell stars, Cell fkdr, Cell topMode, Cell health) {
	}

	private final LobbyTracker lobby;
	private final PartyTracker party;
	private final StatsService stats;
	/** Last skin seen per player, so people who left the tab list keep their face. */
	private final Map<String, PlayerSkin> skins = new HashMap<>();

	public StatsHud(LobbyTracker lobby, PartyTracker party, StatsService stats) {
		this.lobby = lobby;
		this.party = party;
		this.stats = stats;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		ModConfig config = ModConfig.get();
		BedwarsPhase phase = lobby.phase();
		if (!config.enabled || !config.hud || !phase.active() || client.options.hideGui || client.level == null) return;

		List<List<Row>> groups = phase.inMatch() ? matchGroups(client, config) : new ArrayList<>(List.of(partyRows(client, config)));
		groups.removeIf(List::isEmpty);
		if (groups.isEmpty()) return;
		draw(graphics, client.font, config, groups);
	}

	private List<List<Row>> matchGroups(Minecraft client, ModConfig config) {
		Map<ChatFormatting, List<Row>> byTeam = new LinkedHashMap<>();
		TEAM_ORDER.forEach(team -> byTeam.put(team, new ArrayList<>()));
		List<Row> noTeam = new ArrayList<>();
		BedwarsMode mode = lobby.mode();
		for (MatchRoster.Member member : lobby.roster().members()) {
			Row row = row(client, config, member.name(), member.team(), mode);
			if (member.team() == null) noTeam.add(row);
			else byTeam.computeIfAbsent(member.team(), team -> new ArrayList<>()).add(row);
		}
		List<List<Row>> groups = new ArrayList<>(byTeam.values());
		groups.add(noTeam);
		return groups;
	}

	private List<Row> partyRows(Minecraft client, ModConfig config) {
		List<String> names = party.members();
		if (names.isEmpty() && client.player != null) names = List.of(client.player.getGameProfile().name());
		List<Row> rows = new ArrayList<>();
		for (String name : names) {
			rows.add(row(client, config, name, null, BedwarsMode.OVERALL));
		}
		return rows;
	}

	private Row row(Minecraft client, ModConfig config, String name, ChatFormatting team, BedwarsMode mode) {
		Integer teamRgb = team == null ? null : team.getColor();
		Cell nameCell = new Cell(name, teamRgb == null ? WHITE : 0xFF000000 | teamRgb);
		Cell stars = Cell.EMPTY, fkdr = Cell.EMPTY, topMode = Cell.EMPTY;

		StatsLookup lookup = stats.get(name).orElse(null);
		switch (lookup) {
			case StatsLookup.Found found -> {
				BedwarsStats s = found.stats();
				stars = new Cell(s.stars() + "✫", StatColors.stars(s.stars()));
				double current = s.mode(mode).fkdr();
				fkdr = new Cell(format(current), StatColors.fkdr(current));
				topMode = topModeCell(config, s, mode, current);
			}
			case StatsLookup.Nicked ignored -> stars = new Cell("NICK", 0xFFFF5555);
			case StatsLookup.Failed ignored -> fkdr = new Cell("?", GRAY);
			case StatsLookup.Loading ignored -> fkdr = new Cell("...", GRAY);
			case null -> {
			}
		}
		return new Row(skin(client, name), nameCell, stars, fkdr, topMode, healthCell(client, name));
	}

	private static Cell topModeCell(ModConfig config, BedwarsStats s, BedwarsMode current, double currentFkdr) {
		if (current == BedwarsMode.OVERALL) return Cell.EMPTY;
		BedwarsMode top = s.mostPlayedMode().orElse(current);
		if (top == current) return Cell.EMPTY;
		double topFkdr = s.mode(top).fkdr();
		boolean differs = currentFkdr == 0
				? topFkdr > 0
				: Math.abs(topFkdr - currentFkdr) / currentFkdr > config.topModeDifference;
		return differs ? new Cell("(" + format(topFkdr) + " " + top.shortName() + ")", GRAY) : Cell.EMPTY;
	}

	private static Cell healthCell(Minecraft client, String name) {
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective tabObjective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
		if (tabObjective == null) return Cell.EMPTY;
		ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), tabObjective);
		if (score == null) return Cell.EMPTY;
		return new Cell(String.valueOf(score.value()), StatColors.health(score.value()));
	}

	private PlayerSkin skin(Minecraft client, String name) {
		ClientPacketListener connection = client.getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(name);
		if (info != null) {
			PlayerSkin skin = info.getSkin();
			skins.put(name.toLowerCase(Locale.ROOT), skin);
			return skin;
		}
		return skins.getOrDefault(name.toLowerCase(Locale.ROOT), DefaultPlayerSkin.getDefaultSkin());
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, ModConfig config, List<List<Row>> groups) {
		boolean heads = config.hudHeads;
		int nameWidth = 0, starsWidth = 0, fkdrWidth = 0, topWidth = 0, healthWidth = 0, rowCount = 0;
		for (List<Row> group : groups) {
			for (Row row : group) {
				nameWidth = Math.max(nameWidth, font.width(row.name().text()));
				if (config.hudStars) starsWidth = Math.max(starsWidth, font.width(row.stars().text()));
				if (config.hudFkdr) fkdrWidth = Math.max(fkdrWidth, font.width(row.fkdr().text()));
				if (config.hudFkdr && config.hudTopMode) topWidth = Math.max(topWidth, font.width(row.topMode().text()));
				if (config.hudHealth) healthWidth = Math.max(healthWidth, font.width(row.health().text()));
				rowCount++;
			}
		}

		int rowHeight = font.lineHeight + 1;
		int[] widths = {heads ? HEAD : 0, nameWidth, starsWidth, fkdrWidth, topWidth, healthWidth};
		int tableWidth = 0;
		int columns = 0;
		for (int width : widths) {
			if (width > 0) {
				tableWidth += width;
				columns++;
			}
		}
		tableWidth += Math.max(0, columns - 1) * COLUMN_GAP + PADDING * 2;
		int tableHeight = rowCount * rowHeight + (groups.size() - 1) * GROUP_GAP + PADDING * 2 - 1;

		float scale = config.hudScale;
		int left = Math.round((graphics.guiWidth() - SCREEN_MARGIN) / scale) - tableWidth;
		int top = Math.round(SCREEN_MARGIN / scale);

		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		graphics.fill(left, top, left + tableWidth, top + tableHeight, BACKGROUND);

		int y = top + PADDING;
		for (List<Row> group : groups) {
			for (Row row : group) {
				int x = left + PADDING;
				if (heads) {
					PlayerFaceExtractor.extractRenderState(graphics, row.skin(), x, y, HEAD);
					x += HEAD + COLUMN_GAP;
				}
				x = cell(graphics, font, row.name(), x, y, nameWidth);
				x = cell(graphics, font, row.stars(), x, y, starsWidth);
				x = cell(graphics, font, row.fkdr(), x, y, fkdrWidth);
				x = cell(graphics, font, row.topMode(), x, y, topWidth);
				cell(graphics, font, row.health(), x, y, healthWidth);
				y += rowHeight;
			}
			y += GROUP_GAP;
		}
		graphics.pose().popMatrix();
	}

	// Draws one cell if its column is visible and returns where the next column starts
	private static int cell(GuiGraphicsExtractor graphics, Font font, Cell cell, int x, int y, int columnWidth) {
		if (columnWidth == 0) return x;
		graphics.text(font, cell.text(), x, y, cell.color(), true);
		return x + columnWidth + COLUMN_GAP;
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
