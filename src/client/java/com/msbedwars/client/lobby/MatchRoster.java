package com.msbedwars.client.lobby;

import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Everyone seen in the current match, our own team included, in the order they showed up.
 * Players stay listed after they leave the tab list (final kill, disconnect), and each one
 * is looked up only once per match. Only touched from the client thread.
 */
public final class MatchRoster {
	/**
	 * @param team          team color once the game has started, null in the waiting room
	 * @param matchesBefore earlier matches we shared with this player, 0 for someone new
	 */
	public record Member(String name, ChatFormatting team, int matchesBefore) {
		public boolean known() {
			return matchesBefore > 0;
		}
	}

	private final Map<String, Member> members = new LinkedHashMap<>();

	public boolean contains(String name) {
		return members.containsKey(key(name));
	}

	public void add(String name, int matchesBefore) {
		members.putIfAbsent(key(name), new Member(name, null, matchesBefore));
	}

	public void setTeam(String name, ChatFormatting team) {
		members.computeIfPresent(key(name), (k, member) ->
				member.team() == team ? member : new Member(member.name(), team, member.matchesBefore()));
	}

	public Optional<Member> get(String name) {
		return Optional.ofNullable(members.get(key(name)));
	}

	public boolean containsAny(Collection<String> names) {
		return names.stream().anyMatch(name -> members.containsKey(key(name)));
	}

	public List<Member> members() {
		return new ArrayList<>(members.values());
	}

	public boolean isEmpty() {
		return members.isEmpty();
	}

	void clear() {
		members.clear();
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
