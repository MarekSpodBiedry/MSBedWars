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
		if (!config.enabled || !config.hud || client.options.hideGui || client.level == null) return;

		List<List<Row>> groups;
		if (config.testMode) {
			groups = testGroups(client);
		} else if (phase.inMatch()) {
			groups = matchGroups(client);
		} else if (phase == BedwarsPhase.LOBBY) {
			groups = new ArrayList<>(List.of(partyRows(client)));
		} else {
			return;
		}
		groups.removeIf(List::isEmpty);
		if (groups.isEmpty()) return;
		draw(graphics, client.font, config, groups);
	}

	private List<List<Row>> matchGroups(Minecraft client) {
		Map<ChatFormatting, List<Row>> byTeam = new LinkedHashMap<>();
		TEAM_ORDER.forEach(team -> byTeam.put(team, new ArrayList<>()));
		List<Row> noTeam = new ArrayList<>();
		BedwarsMode mode = lobby.mode();
		for (MatchRoster.Member member : lobby.roster().members()) {
			Row row = row(client, member.name(), member.team(), mode);
			if (member.team() == null) noTeam.add(row);
			else byTeam.computeIfAbsent(member.team(), team -> new ArrayList<>()).add(row);
		}
		List<List<Row>> groups = new ArrayList<>(byTeam.values());
		groups.add(noTeam);
		return groups;
	}

	private List<Row> partyRows(Minecraft client) {
		List<String> names = party.members();
		if (names.isEmpty() && client.player != null) names = List.of(client.player.getGameProfile().name());
		List<Row> rows = new ArrayList<>();
		for (String name : names) {
			rows.add(row(client, name, null, BedwarsMode.OVERALL));
		}
		return rows;
	}

	// Made-up doubles match: 8 teams of 2, with every kind of row the HUD can show
	private List<List<Row>> testGroups(Minecraft client) {
		String self = client.player == null ? "You" : client.player.getGameProfile().name();
		Map<ChatFormatting, List<Row>> byTeam = new LinkedHashMap<>();
		TEAM_ORDER.forEach(team -> byTeam.put(team, new ArrayList<>()));
		for (TestPlayers.Player player : TestPlayers.doublesMatch(self)) {
			PlayerSkin skin = player.name().equals(self) ? skin(client, self) : DefaultPlayerSkin.get(TestPlayers.uuid(player.name()));
			Cell health = player.health() < 0 ? Cell.EMPTY : new Cell(String.valueOf(player.health()), StatColors.health(player.health()));
			byTeam.get(player.team()).add(row(player.name(), player.team(), BedwarsMode.DOUBLES, player.lookup(), skin, health));
		}
		return new ArrayList<>(byTeam.values());
	}

	private Row row(Minecraft client, String name, ChatFormatting team, BedwarsMode mode) {
		return row(name, team, mode, stats.get(name).orElse(null), skin(client, name), healthCell(client, name));
	}

	private static Row row(String name, ChatFormatting team, BedwarsMode mode,
	                       StatsLookup lookup, PlayerSkin skin, Cell health) {
		Integer teamRgb = team == null ? null : team.getColor();
		Cell nameCell = new Cell(name, teamRgb == null ? WHITE : 0xFF000000 | teamRgb);
		Cell stars = Cell.EMPTY, fkdr = Cell.EMPTY, topMode = Cell.EMPTY;

		switch (lookup) {
			case StatsLookup.Found found -> {
				BedwarsStats s = found.stats();
				stars = new Cell(StatText.stars(s.stars()), StatColors.stars(s.stars()));
				double current = s.mode(mode).fkdr();
				fkdr = new Cell(StatText.fkdr(current), StatColors.fkdr(current));
				topMode = StatText.topMode(s, mode).map(text -> new Cell(text, GRAY)).orElse(Cell.EMPTY);
			}
			case StatsLookup.Nicked ignored -> stars = new Cell("NICK", 0xFFFF5555);
			case StatsLookup.Failed ignored -> fkdr = new Cell("?", GRAY);
			case StatsLookup.Loading ignored -> fkdr = new Cell("...", GRAY);
			case null -> {
			}
		}
		return new Row(skin, nameCell, stars, fkdr, topMode, health);
	}

	private static Cell healthCell(Minecraft client, String name) {
		Integer health = StatText.tabHealth(client, name);
		return health == null ? Cell.EMPTY : new Cell(String.valueOf(health), StatColors.health(health));
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
		boolean topModes = config.hudFkdr && config.hudTopMode;
		int nameWidth = 0, starsWidth = 0, fkdrWidth = 0, healthWidth = 0, rowCount = 0;
		for (List<Row> group : groups) {
			for (Row row : group) {
				nameWidth = Math.max(nameWidth, font.width(row.name().text()));
				if (config.hudStars) starsWidth = Math.max(starsWidth, font.width(row.stars().text()));
				if (config.hudFkdr) fkdrWidth = Math.max(fkdrWidth, fkdrWidth(font, row, topModes));
				if (config.hudHealth) healthWidth = Math.max(healthWidth, font.width(row.health().text()));
				rowCount++;
			}
		}

		int rowHeight = font.lineHeight + 1;
		int[] widths = {heads ? HEAD : 0, nameWidth, starsWidth, fkdrWidth, healthWidth};
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
				if (fkdrWidth > 0 && topModes && !row.topMode().text().isEmpty()) {
					int after = x + font.width(row.fkdr().text());
					graphics.text(font, row.topMode().text(), after, y, row.topMode().color(), true);
				}
				x = cell(graphics, font, row.fkdr(), x, y, fkdrWidth);
				cell(graphics, font, row.health(), x, y, healthWidth);
				y += rowHeight;
			}
			y += GROUP_GAP;
		}
		graphics.pose().popMatrix();
	}

	// FKDR and the top mode bracket share one column: "12(20 1s)"
	private static int fkdrWidth(Font font, Row row, boolean topModes) {
		if (!topModes || row.topMode().text().isEmpty()) return font.width(row.fkdr().text());
		return font.width(row.fkdr().text() + row.topMode().text());
	}

	// Draws one cell if its column is visible and returns where the next column starts
	private static int cell(GuiGraphicsExtractor graphics, Font font, Cell cell, int x, int y, int columnWidth) {
		if (columnWidth == 0) return x;
		graphics.text(font, cell.text(), x, y, cell.color(), true);
		return x + columnWidth + COLUMN_GAP;
	}
}
