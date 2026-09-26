package com.msbedwars.client.party;

import com.msbedwars.client.Safe;
import com.msbedwars.client.config.ModConfig;
import com.msbedwars.client.lobby.BedwarsPhase;
import com.msbedwars.client.lobby.LobbyTracker;
import com.msbedwars.client.stats.StatsLookup;
import com.msbedwars.client.stats.StatsService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Knows who is in our party by reading the chat output of /pl. Sends /pl by itself in the
 * Bed Wars lobby and waiting room when the party may have changed, hides that output,
 * and moves party members to the front of the stats queue.
 *
 * Expected /pl output (one message or several):
 * <pre>
 * Party Members (3)
 * Party Leader: [MVP+] Name ●
 * Party Moderators: Name2 ●
 * Party Members: [VIP] Name3 ● Name4 ●
 * </pre>
 * The dot is green for online members and red for offline ones.
 */
public final class PartyTracker {
	private static final long AUTO_LIST_COOLDOWN_MS = 15_000;
	private static final long HIDE_WINDOW_MS = 3_000;
	private static final int OFFLINE_DOT = TextColor.fromLegacyFormat(ChatFormatting.RED).getValue();

	private final LobbyTracker lobby;
	private final StatsService stats;
	/** Online members including us. Empty when not in a party. */
	private List<String> members = List.of();
	private List<String> pending;
	/** True until we have read /pl since the last party change. */
	private boolean stale = true;
	private long lastAutoList;
	private long hideUntil;
	private int ticks;

	public PartyTracker(LobbyTracker lobby, StatsService stats) {
		this.lobby = lobby;
		this.stats = stats;
	}

	public void register() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
				Safe.check("party chat reading", () -> onGameMessage(message, overlay), true));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Safe.run("party tracking", () -> onTick(client)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			members = List.of();
			stale = true;
		});
	}

	public List<String> members() {
		return members;
	}

	private void onTick(Minecraft client) {
		if (++ticks < 20) return;
		ticks = 0;
		ModConfig config = ModConfig.get();
		BedwarsPhase phase = lobby.phase();
		if (!config.fetchParty || !(phase == BedwarsPhase.LOBBY || phase == BedwarsPhase.PREGAME)) return;

		long now = System.currentTimeMillis();
		if (stale && now - lastAutoList > AUTO_LIST_COOLDOWN_MS && client.getConnection() != null) {
			// Cleared on send, not on a readable answer, so an unexpected /pl format can never cause a /pl loop
			stale = false;
			lastAutoList = now;
			hideUntil = now + HIDE_WINDOW_MS;
			client.getConnection().sendCommand("pl");
		}
		for (String member : members) {
			boolean missing = stats.get(member).map(lookup -> lookup instanceof StatsLookup.Failed).orElse(true);
			if (missing) stats.requestFirst(member);
		}
	}

	/** @return false to hide the message */
	private boolean onGameMessage(Component message, boolean overlay) {
		if (overlay) return true;
		boolean partOfList = false;
		for (StyledLine line : StyledLine.split(message)) {
			partOfList |= readLine(line);
		}
		boolean hide = partOfList && ModConfig.get().hideAutoPartyList && System.currentTimeMillis() < hideUntil;
		return !hide;
	}

	// Returns true when the line belongs to /pl output
	private boolean readLine(StyledLine line) {
		String text = line.text().trim();
		if (text.isEmpty()) return false;
		if (text.chars().allMatch(c -> c == '-' || c == '▬')) return true;

		if (text.startsWith("You are not currently in a party") || text.startsWith("You are not in a party")) {
			setMembers(List.of());
			return true;
		}
		if (text.startsWith("Party Members (")) {
			pending = new ArrayList<>();
			return true;
		}
		for (String label : new String[]{"Party Leader:", "Party Moderators:", "Party Members:"}) {
			if (text.startsWith(label) && pending != null) {
				pending.addAll(onlineNames(line, line.text().indexOf(label) + label.length()));
				setMembers(List.copyOf(pending));
				return true;
			}
		}

		// Party chat is "Party > name: message" and never changes who is in the party
		if (!text.startsWith("Party >") && changesParty(text)) {
			if (text.startsWith("You left the party") || text.contains("disbanded")
					|| text.startsWith("You have been kicked from the party")) {
				setMembers(List.of());
			} else {
				stale = true;
			}
		}
		return false;
	}

	private void setMembers(List<String> names) {
		members = names;
		stale = false;
	}

	private static boolean changesParty(String text) {
		return text.contains("joined the party") || text.contains("left the party")
				|| text.contains("removed from the party") || text.contains("disbanded")
				|| text.contains("kicked") && text.contains("party") || text.contains("transferred the party");
	}

	// "[MVP+] Name ● Name2 ●": the name is the last word before each dot, skipped if the dot is red
	private static List<String> onlineNames(StyledLine line, int from) {
		List<String> names = new ArrayList<>();
		String text = line.text();
		int start = from;
		for (int dot = text.indexOf('●', from); dot >= 0; dot = text.indexOf('●', dot + 1)) {
			String[] words = text.substring(start, dot).trim().split("\\s+");
			String name = words[words.length - 1];
			if (!name.isEmpty() && !name.startsWith("[") && line.colorAt(dot) != OFFLINE_DOT) {
				names.add(name);
			}
			start = dot + 1;
		}
		return names;
	}

	/** One line of a chat message with the text color of every character. */
	private record StyledLine(String text, int[] colors) {
		int colorAt(int index) {
			return index < colors.length ? colors[index] : -1;
		}

		static List<StyledLine> split(Component message) {
			StringBuilder text = new StringBuilder();
			List<Integer> colors = new ArrayList<>();
			message.visit((Style style, String part) -> {
				TextColor styleColor = style.getColor();
				int color = styleColor == null ? -1 : styleColor.getValue();
				for (int i = 0; i < part.length(); i++) {
					char c = part.charAt(i);
					// Old-style "§c" codes inside the text: apply the color, keep the code out of the text
					if (c == '§' && i + 1 < part.length()) {
						ChatFormatting code = ChatFormatting.getByCode(part.charAt(++i));
						if (code != null && code.isColor()) color = TextColor.fromLegacyFormat(code).getValue();
						continue;
					}
					text.append(c);
					colors.add(color);
				}
				return Optional.empty();
			}, Style.EMPTY);

			List<StyledLine> lines = new ArrayList<>();
			int start = 0;
			for (int i = 0; i <= text.length(); i++) {
				if (i == text.length() || text.charAt(i) == '\n') {
					int[] lineColors = colors.subList(start, i).stream().mapToInt(Integer::intValue).toArray();
					lines.add(new StyledLine(text.substring(start, i), lineColors));
					start = i + 1;
				}
			}
			return lines;
		}
	}
}
