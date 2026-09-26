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
import java.util.Set;

/**
 * Knows who is in our party by reading party messages in chat, including /pl output when
 * the player types it. Never sends /pl or any other command itself: automated commands
 * could count as a macro on Hypixel. Party members jump to the front of the stats queue.
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
	private static final int OFFLINE_DOT = TextColor.fromLegacyFormat(ChatFormatting.RED).getValue();

	private final LobbyTracker lobby;
	private final StatsService stats;
	/** Online members including us. Empty when not in a party. */
	private List<String> members = List.of();
	private List<String> pending;
	private int ticks;

	public PartyTracker(LobbyTracker lobby, StatsService stats) {
		this.lobby = lobby;
		this.stats = stats;
	}

	public void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) Safe.run("party chat reading", () -> StyledLine.split(message).forEach(this::readLine));
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> Safe.run("party tracking", this::onTick));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> members = List.of());
	}

	public List<String> members() {
		return members;
	}

	private void onTick() {
		if (++ticks < 20) return;
		ticks = 0;
		BedwarsPhase phase = lobby.phase();
		if (!ModConfig.get().fetchParty || !(phase == BedwarsPhase.LOBBY || phase == BedwarsPhase.PREGAME)) return;
		for (String member : members) {
			boolean missing = stats.get(member).map(lookup -> lookup instanceof StatsLookup.Failed).orElse(true);
			if (missing) stats.requestFirst(member);
		}
	}

	// Names are found by color: Hypixel writes party messages in yellow and player names in
	// their rank color (gray, lime, cyan or gold).
	private void readLine(StyledLine line) {
		String text = line.text().trim();
		if (text.isEmpty()) return;
		// Party chat is "Party > name: message" and never changes who is in the party
		if (text.startsWith("Party >")) return;

		if (text.startsWith("You are not currently in a party") || text.startsWith("You are not in a party")) {
			members = List.of();
			return;
		}
		if (text.startsWith("Party Members (")) {
			pending = new ArrayList<>();
			return;
		}
		for (String label : new String[]{"Party Leader:", "Party Moderators:", "Party Members:"}) {
			if (text.startsWith(label)) {
				if (pending == null) pending = new ArrayList<>();
				pending.addAll(line.names(true));
				members = List.copyOf(pending);
				return;
			}
		}

		if (text.startsWith("You left the party") || text.contains("disbanded")
				|| text.startsWith("You have been kicked from the party")) {
			members = List.of();
		} else if (text.startsWith("You'll be partying with")) {
			addMembers(line.names(false));
		} else if (text.startsWith("You have joined") && text.contains("party")) {
			// Only names the leader; the rest shows up once the player types /pl
			addMembers(line.names(false));
		} else if (text.contains("joined the party")) {
			addMembers(line.names(false));
		} else if (text.contains("left the party") || text.contains("removed from the party")
				|| text.contains("removed from your party")) {
			removeMembers(line.names(false));
		}
	}

	// Being in a party means we are in it too, so our own name comes along with the first member
	private void addMembers(List<String> names) {
		List<String> next = new ArrayList<>(members);
		Minecraft client = Minecraft.getInstance();
		if (next.isEmpty() && client.player != null) next.add(client.player.getGameProfile().name());
		for (String name : names) {
			if (next.stream().noneMatch(name::equalsIgnoreCase)) next.add(name);
		}
		members = List.copyOf(next);
	}

	private void removeMembers(List<String> names) {
		List<String> next = new ArrayList<>(members);
		next.removeIf(member -> names.stream().anyMatch(member::equalsIgnoreCase));
		// Only us left means no party
		members = next.size() <= 1 ? List.of() : List.copyOf(next);
	}

	/** One line of a chat message with the text color of every character. */
	private record StyledLine(String text, int[] colors) {
		private static final Set<Integer> NAME_COLORS = Set.of(
				TextColor.fromLegacyFormat(ChatFormatting.GRAY).getValue(),
				TextColor.fromLegacyFormat(ChatFormatting.GREEN).getValue(),
				TextColor.fromLegacyFormat(ChatFormatting.AQUA).getValue(),
				TextColor.fromLegacyFormat(ChatFormatting.GOLD).getValue());

		int colorAt(int index) {
			return index < colors.length ? colors[index] : -1;
		}

		/**
		 * Player names in this line: runs of name characters written in a rank color, outside
		 * rank tags like "[MVP+]". With {@code onlineOnly}, names followed by a red dot are skipped.
		 */
		List<String> names(boolean onlineOnly) {
			List<String> names = new ArrayList<>();
			int bracketDepth = 0;
			int i = 0;
			while (i < text.length()) {
				char c = text.charAt(i);
				if (c == '[') bracketDepth++;
				if (c == ']') bracketDepth = Math.max(0, bracketDepth - 1);
				if (!isNameChar(c) || bracketDepth > 0) {
					i++;
					continue;
				}
				int start = i;
				boolean rankColored = true;
				while (i < text.length() && isNameChar(text.charAt(i))) {
					rankColored &= NAME_COLORS.contains(colorAt(i));
					i++;
				}
				String word = text.substring(start, i);
				if (rankColored && word.length() <= 16 && !(onlineOnly && offlineDotAfter(i))) {
					names.add(word);
				}
			}
			return names;
		}

		private boolean offlineDotAfter(int from) {
			int dot = text.indexOf('●', from);
			return dot >= 0 && colorAt(dot) == OFFLINE_DOT;
		}

		private static boolean isNameChar(char c) {
			return c == '_' || c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
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
						if (code == ChatFormatting.RESET) color = styleColor == null ? -1 : styleColor.getValue();
						else if (code != null && code.isColor()) color = TextColor.fromLegacyFormat(code).getValue();
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
