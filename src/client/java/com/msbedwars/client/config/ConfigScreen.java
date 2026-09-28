package com.msbedwars.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleConsumer;

/**
 * Settings screen, opened from Mod Menu or with /msb. Two columns of on/off buttons under
 * section titles, two sliders, and Done. Every change is saved right away.
 */
public final class ConfigScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int COLUMN_GAP = 10;
	private static final int ROW_GAP = 4;
	private static final int SECTION_GAP = 14;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GOLD = 0xFFFFAA00;

	private record SectionTitle(Component text, int y) {
	}

	private final Screen parent;
	private final List<SectionTitle> sectionTitles = new ArrayList<>();
	private int y;

	public ConfigScreen(Screen parent) {
		super(Component.literal("MSBedWars settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ModConfig config = ModConfig.get();
		sectionTitles.clear();
		y = 32;

		section("General");
		row(toggle("Mod enabled", "Turns the whole mod on or off.", config.enabled, v -> config.enabled = v),
				toggle("Look up players", "Looks up everyone in your match on hypixel.net.",
						config.fetchGamePlayers, v -> config.fetchGamePlayers = v));
		row(toggle("Party from /pl", "Reads your party from /pl and party messages, and looks them up first while queueing.",
						config.fetchParty, v -> config.fetchParty = v),
				toggle("Stats above heads", "Shows stars, FKDR and health above each player's name.",
						config.nametags, v -> config.nametags = v));

		section("HUD");
		row(toggle("HUD", "The stats table in the top right corner.", config.hud, v -> config.hud = v),
				toggle("Heads", "Player heads in the first column.", config.hudHeads, v -> config.hudHeads = v));
		row(toggle("Stars", "Bed Wars stars, learned from chat.", config.hudStars, v -> config.hudStars = v),
				toggle("FKDR", "Final kills per final death in the mode you are playing.", config.hudFkdr, v -> config.hudFkdr = v));
		row(toggle("Top mode", "Most played mode's FKDR in brackets, when it is higher than this mode's.",
						config.hudTopMode, v -> config.hudTopMode = v),
				toggle("Health", "Health from the tab list.", config.hudHealth, v -> config.hudHealth = v));
		row(slider("HUD size", 0.5, 1.5, config.hudScale, value -> String.format(Locale.ROOT, "%d%%", Math.round(value * 100)),
						value -> config.hudScale = (float) value),
				slider("Top mode gap", 0, 1, config.topModeDifference,
						value -> String.format(Locale.ROOT, "+%d%%", Math.round(value * 100)),
						value -> config.topModeDifference = value));

		section("Sidebar");
		row(toggle("Clean sidebar", "Redraws Hypixel's Bed Wars sidebar shorter: 2K 3F 1B, map and mode on one line, no web address.",
						config.sidebar, v -> config.sidebar = v),
				toggle("24-hour clock", "Time on the date line as 13:05 instead of 1:05 PM.", config.clock24h, v -> config.clock24h = v));
		row(Button.builder(Component.literal("Accent color: ")
								.append(Component.literal("■ " + ColorPickerScreen.hex(config.accentColor))
										.withStyle(style -> style.withColor(config.accentColor))),
						button -> minecraft.setScreen(new ColorPickerScreen(this, "Accent color", config.accentColor, color -> {
							config.accentColor = color;
							config.save();
						})))
				.size(BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.literal("Color of the letters the mod adds to the sidebar (K, F, B, FKDR). Gray by default.")))
				.build());

		section("Development");
		row(toggle("Test mode", "Shows made-up players everywhere so you can check the layout. Looks nothing up.",
						config.testMode, v -> config.testMode = v),
				toggle("Capture", "Writes chat, sidebar and tab list changes to msbedwars/capture.log.",
						config.capture, v -> config.capture = v));

		y += SECTION_GAP - ROW_GAP;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private void section(String title) {
		if (!sectionTitles.isEmpty()) y += SECTION_GAP - ROW_GAP;
		sectionTitles.add(new SectionTitle(Component.literal(title), y));
		y += font.lineHeight + 4;
	}

	private void row(AbstractWidget left, AbstractWidget right) {
		int leftX = width / 2 - BUTTON_WIDTH - COLUMN_GAP / 2;
		left.setPosition(leftX, y);
		addRenderableWidget(left);
		if (right != null) {
			right.setPosition(leftX + BUTTON_WIDTH + COLUMN_GAP, y);
			addRenderableWidget(right);
		}
		y += BUTTON_HEIGHT + ROW_GAP;
	}

	private void row(AbstractWidget left) {
		row(left, null);
	}

	private CycleButton<Boolean> toggle(String label, String tooltip, boolean value, Consumer<Boolean> setter) {
		return CycleButton.onOffBuilder(value)
				.withTooltip(v -> Tooltip.create(Component.literal(tooltip)))
				.create(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, Component.literal(label), (button, newValue) -> {
					setter.accept(newValue);
					ModConfig.get().save();
				});
	}

	private Slider slider(String label, double min, double max, double value,
	                      DoubleFunction<String> format, DoubleConsumer setter) {
		return new Slider(label, min, max, value, format, setter);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 12, WHITE);
		for (SectionTitle section : sectionTitles) {
			graphics.centeredText(font, section.text(), width / 2, section.y(), GOLD);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	/** Slider over a range, snapping to 5 % steps, saving when released or moved with keys. */
	private static final class Slider extends AbstractSliderButton {
		private static final double STEP = 0.05;
		private final String label;
		private final double min;
		private final double max;
		private final DoubleFunction<String> format;
		private final DoubleConsumer setter;

		Slider(String label, double min, double max, double value,
		       DoubleFunction<String> format, DoubleConsumer setter) {
			super(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, Component.empty(), (value - min) / (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.format = format;
			this.setter = setter;
			updateMessage();
		}

		private double current() {
			double raw = min + value * (max - min);
			return Math.round(raw / STEP) * STEP;
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + format.apply(current())));
		}

		@Override
		protected void applyValue() {
			setter.accept(current());
			ModConfig.get().save();
		}
	}
}
