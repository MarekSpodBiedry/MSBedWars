package com.msbedwars.client.display;

import com.msbedwars.client.Safe;
import com.msbedwars.client.config.ModConfig;
import com.msbedwars.client.data.PlayerDatabase;
import com.msbedwars.client.data.PlayerRecord;
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
	// Leaves 2 px between the frame and the content
	private static final int PADDING = 4;
	private static final int COLUMN_GAP = 6;
	private static final int GROUP_GAP = 3;
	private static final int HEAD = 8;
	private static final int BACKGROUND = 0xB0000000;
	// Frame color when we are not on a team yet (lobby, waiting room)
	private static final int NO_TEAM_FRAME = 0xFFAAAAAA;
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
	private final PlayerDatabase database;
	/** Last skin seen per player, so people who left the tab list keep their face. */
	private final Map<String, PlayerSkin> skins = new HashMap<>();

	private static StatsHud instance;
	/** True while the HUD editor is open: it draws the HUD itself, so the normal one stays hidden. */
	public static boolean editing;
	/** Set while the editor asks for a drawing: shows made-up players when there is nothing real to show. */
	private boolean preview;
	/** Where the table was last drawn, in GUI pixels: x, y, width, height. Null when it was not drawn. */
	private int[] bounds;

	public StatsHud(LobbyTracker lobby, PartyTracker party, StatsService stats, PlayerDatabase database) {
		instance = this;
		this.lobby = lobby;
		this.party = party;
		this.stats = stats;
		this.database = database;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		// A matrix pushed before a failure must still be popped, or the rest of the HUD draws scaled
		Safe.run("HUD", () -> {
			try {
				render(graphics);
			} finally {
				if (pushed) {
					graphics.pose().popMatrix();
					pushed = false;
				}
			}
		});
	}

	private boolean pushed;

	public static StatsHud instance() {
		return instance;
	}

	/** Draws the HUD for the editor, with made-up players when not in a game or lobby. */
	public void extractPreview(GuiGraphicsExtractor graphics) {
		preview = true;
		try {
			extractRenderState(graphics, null);
		} finally {
			preview = false;
		}
	}

	public int[] bounds() {
		return bounds;
	}

	private void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		ModConfig config = ModConfig.get();
		BedwarsPhase phase = lobby.phase();
		bounds = null;
		if (!preview && (editing || !config.enabled || !config.hud || client.options.hideGui || client.level == null)) return;

		List<List<Row>> groups;
		if (config.testMode || (preview && (client.level == null || !phase.active()))) {
			groups = testGroups(client);
		} else if (phase.inMatch()) {
			groups = matchGroups(client);
		} else if (phase == BedwarsPhase.LOBBY) {
			groups = new ArrayList<>(List.of(partyRows(client)));
		} else {
			return;
		}
		groups.removeIf(List::isEmpty);
		if (groups.isEmpty() && preview) groups = testGroups(client);
		if (groups.isEmpty()) return;
		draw(graphics, client.font, config, groups, frameColor(client, config));
	}

	// The frame takes the color of our own team; made-up test players put us on red
	private int frameColor(Minecraft client, ModConfig config) {
		if (config.testMode) return 0xFF000000 | ChatFormatting.RED.getColor();
		if (client.player == null) return NO_TEAM_FRAME;
		ChatFormatting team = lobby.roster().get(client.player.getGameProfile().name())
				.map(MatchRoster.Member::team).orElse(null);
		Integer rgb = team == null ? null : team.getColor();
		return rgb == null ? NO_TEAM_FRAME : 0xFF000000 | rgb;
	}

	private List<List<Row>> matchGroups(Minecraft client) {
		Map<ChatFormatting, List<Row>> byTeam = new LinkedHashMap<>();
		TEAM_ORDER.forEach(team -> byTeam.put(team, new ArrayList<>()));
		List<Row> noTeam = new ArrayList<>();
		BedwarsMode mode = lobby.mode();
		for (MatchRoster.Member member : lobby.roster().members()) {
			// A nicked party member we matched shows under their real name, with the nick's skin
			String realName = lobby.roster().statsName(member.name());
			Row row = row(realName, member.team(), mode, stats.get(realName).orElse(null), seenStars(realName),
					skin(client, member.name()), healthCell(client, member.name()));
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
			byTeam.get(player.team()).add(row(player.name(), player.team(), BedwarsMode.DOUBLES, player.lookup(), null, skin, health));
		}
		return new ArrayList<>(byTeam.values());
	}

	private Row row(Minecraft client, String name, ChatFormatting team, BedwarsMode mode) {
		return row(name, team, mode, stats.get(name).orElse(null), seenStars(name), skin(client, name), healthCell(client, name));
	}

	private Integer seenStars(String name) {
		return database.get(name).map(PlayerRecord::stars).orElse(null);
	}

	private static Row row(String name, ChatFormatting team, BedwarsMode mode,
	                       StatsLookup lookup, Integer seenStars, PlayerSkin skin, Cell health) {
		Integer teamRgb = team == null ? null : team.getColor();
		Cell nameCell = new Cell(name, teamRgb == null ? WHITE : 0xFF000000 | teamRgb);
		Cell stars = seenStars == null ? Cell.EMPTY : new Cell(StatText.stars(seenStars), StatColors.stars(seenStars));
		Cell fkdr = Cell.EMPTY, topMode = Cell.EMPTY;

		switch (lookup) {
			case StatsLookup.Found found -> {
				BedwarsStats s = found.stats();
				Integer shown = StatText.shownStars(seenStars, s);
				stars = new Cell(StatText.stars(shown), StatColors.stars(shown));
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

	private void draw(GuiGraphicsExtractor graphics, Font font, ModConfig config, List<List<Row>> groups, int frameColor) {
		boolean heads = config.hudHeads;
		boolean topModes = config.hudFkdr && config.hudTopMode;
		// The FKDR column has two sub-columns, number and bracket, so both line up: "12  (20 1s)"
		int nameWidth = 0, starsWidth = 0, numberWidth = 0, bracketWidth = 0, healthWidth = 0, rowCount = 0;
		for (List<Row> group : groups) {
			for (Row row : group) {
				nameWidth = Math.max(nameWidth, font.width(row.name().text()));
				if (config.hudStars) starsWidth = Math.max(starsWidth, font.width(row.stars().text()));
				if (config.hudFkdr) numberWidth = Math.max(numberWidth, font.width(row.fkdr().text()));
				if (topModes) bracketWidth = Math.max(bracketWidth, font.width(row.topMode().text()));
				if (config.hudHealth) healthWidth = Math.max(healthWidth, font.width(row.health().text()));
				rowCount++;
			}
		}
		int bracketX = numberWidth + StatText.HAIR_SPACE_WIDTH;
		int fkdrWidth = bracketWidth == 0 ? numberWidth : bracketX + bracketWidth;

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

		// Anchor + offset, like "top right corner, 4 px in": the table keeps its corner when the
		// window size or GUI scale changes. Kept fully on screen.
		float scale = config.hudScale;
		float shownWidth = tableWidth * scale;
		float shownHeight = tableHeight * scale;
		float shownX = config.hudAnchorX * graphics.guiWidth() + config.hudOffsetX - config.hudAnchorX * shownWidth;
		float shownY = config.hudAnchorY * graphics.guiHeight() + config.hudOffsetY - config.hudAnchorY * shownHeight;
		shownX = Math.max(0, Math.min(shownX, graphics.guiWidth() - shownWidth));
		shownY = Math.max(0, Math.min(shownY, graphics.guiHeight() - shownHeight));
		bounds = new int[]{Math.round(shownX), Math.round(shownY), Math.round(shownWidth), Math.round(shownHeight)};
		int left = Math.round(shownX / scale);
		int top = Math.round(shownY / scale);

		graphics.pose().pushMatrix();
		pushed = true;
		graphics.pose().scale(scale, scale);
		if (config.hudBorder) {
			roundedFrame(graphics, left, top, tableWidth, tableHeight, frameColor);
		} else {
			graphics.fill(left, top, left + tableWidth, top + tableHeight, BACKGROUND);
		}

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
				if (fkdrWidth > 0 && bracketWidth > 0 && !row.topMode().text().isEmpty()) {
					graphics.text(font, row.topMode().text(), x + bracketX, y, row.topMode().color(), true);
				}
				x = cell(graphics, font, row.fkdr(), x, y, fkdrWidth);
				cell(graphics, font, row.health(), x, y, healthWidth);
				y += rowHeight;
			}
			y += GROUP_GAP;
		}
		graphics.pose().popMatrix();
		pushed = false;
	}

	/**
	 * Background with its 4 corner pixels left out, and a 1 px line 1 px inside its edge whose
	 * corner pixels are also left out, so both read as slightly rounded.
	 */
	private static void roundedFrame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x + 1, y, x + w - 1, y + h, BACKGROUND);
		graphics.fill(x, y + 1, x + 1, y + h - 1, BACKGROUND);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, BACKGROUND);

		graphics.fill(x + 2, y + 1, x + w - 2, y + 2, color);
		graphics.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, color);
		graphics.fill(x + 1, y + 2, x + 2, y + h - 2, color);
		graphics.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, color);
	}

	// Draws one cell if its column is visible and returns where the next column starts
	private static int cell(GuiGraphicsExtractor graphics, Font font, Cell cell, int x, int y, int columnWidth) {
		if (columnWidth == 0) return x;
		graphics.text(font, cell.text(), x, y, cell.color(), true);
		return x + columnWidth + COLUMN_GAP;
	}
}
