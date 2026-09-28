package com.msbedwars.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Picks an RGB color: preset swatches, hue / saturation / brightness sliders and a hex box,
 * all kept in sync, with a preview of sidebar text in the chosen color. Done saves, Cancel
 * leaves the old color.
 */
public final class ColorPickerScreen extends Screen {
	private static final int WIDTH = 204;
	private static final int ROW_HEIGHT = 20;
	private static final int GAP = 4;
	private static final int SWATCH = 22;
	private static final int[] PRESETS = {
			0xAAAAAA, 0x2CDA02, 0xFFFFFF, 0xFFAA00, 0xFFFF55, 0x55FFFF, 0x5555FF, 0xFF55FF, 0xFF5555};
	private static final String[] PRESET_NAMES = {
			"Gray (Minecraft)", "MSBedWars green", "White", "Gold", "Yellow", "Aqua", "Blue", "Pink", "Red"};

	private final Screen parent;
	private final IntConsumer onDone;
	private float hue;
	private float saturation;
	private float brightness;
	private int rgb;
	private HsvSlider hueSlider;
	private HsvSlider saturationSlider;
	private HsvSlider brightnessSlider;
	private EditBox hexBox;
	private boolean syncing;
	private int previewTop;

	public ColorPickerScreen(Screen parent, String title, int rgb, IntConsumer onDone) {
		super(Component.literal(title));
		this.parent = parent;
		this.onDone = onDone;
		setRgb(rgb);
	}

	@Override
	protected void init() {
		int left = width / 2 - WIDTH / 2;
		int y = 36;

		// Presets: a row of colored squares
		int swatchGap = (WIDTH - PRESETS.length * SWATCH) / (PRESETS.length - 1);
		for (int i = 0; i < PRESETS.length; i++) {
			int color = PRESETS[i];
			Button swatch = Button.builder(Component.literal("■").withStyle(style -> style.withColor(color)), button -> {
						setRgb(color);
						syncWidgets();
					})
					.bounds(left + i * (SWATCH + swatchGap), y, SWATCH, ROW_HEIGHT)
					.tooltip(Tooltip.create(Component.literal(PRESET_NAMES[i] + "  " + hex(color))))
					.build();
			addRenderableWidget(swatch);
		}
		y += ROW_HEIGHT + GAP * 2;

		hueSlider = addRenderableWidget(new HsvSlider(left, y, "Hue", hue, value -> {
			hue = (float) value;
			fromHsv();
		}));
		y += ROW_HEIGHT + GAP;
		saturationSlider = addRenderableWidget(new HsvSlider(left, y, "Saturation", saturation, value -> {
			saturation = (float) value;
			fromHsv();
		}));
		y += ROW_HEIGHT + GAP;
		brightnessSlider = addRenderableWidget(new HsvSlider(left, y, "Brightness", brightness, value -> {
			brightness = (float) value;
			fromHsv();
		}));
		y += ROW_HEIGHT + GAP * 2;

		hexBox = new EditBox(font, left + 44, y, WIDTH - 44, ROW_HEIGHT, Component.literal("Hex color"));
		hexBox.setMaxLength(7);
		hexBox.setValue(hex(rgb));
		hexBox.setResponder(text -> {
			if (syncing) return;
			String digits = text.startsWith("#") ? text.substring(1) : text;
			if (digits.matches("[0-9a-fA-F]{6}")) {
				setRgb(Integer.parseInt(digits, 16));
				syncWidgets();
			}
		});
		addRenderableWidget(hexBox);
		y += ROW_HEIGHT + GAP * 2;

		previewTop = y;
		y += 32 + GAP * 2;

		int half = (WIDTH - GAP) / 2;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
			onDone.accept(rgb);
			onClose();
		}).bounds(left, y, half, ROW_HEIGHT).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
				.bounds(left + half + GAP, y, half, ROW_HEIGHT).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int left = width / 2 - WIDTH / 2;
		graphics.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
		graphics.text(font, "Hex", left, hexBox.getY() + 6, 0xFFAAAAAA, false);

		// Sidebar-like preview: numbers keep Hypixel's colors, the added letters use the pick
		graphics.fill(left, previewTop, left + WIDTH, previewTop + 32, 0x88000000);
		graphics.text(font, preview(new String[][]{{"2", "K "}, {"3", "F "}, {"1", "B"}}, 0x55FF55), left + 6, previewTop + 5, -1, false);
		graphics.text(font, preview(new String[][]{{"205k", " Tokens"}, {" · 39/99", " Tickets"}}, 0x00AA00), left + 6, previewTop + 18, -1, false);
		graphics.fill(left + WIDTH - 26, previewTop + 6, left + WIDTH - 6, previewTop + 26, 0xFF000000 | rgb);
	}

	private Component preview(String[][] parts, int numberColor) {
		MutableComponent line = Component.empty();
		for (String[] part : parts) {
			line.append(Component.literal(part[0]).withStyle(style -> style.withColor(numberColor)));
			line.append(Component.literal(part[1]).withStyle(style -> style.withColor(rgb)));
		}
		return line;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private void setRgb(int color) {
		rgb = color & 0xFFFFFF;
		float[] hsv = rgbToHsv(rgb);
		hue = hsv[0];
		saturation = hsv[1];
		brightness = hsv[2];
	}

	private void fromHsv() {
		if (syncing) return;
		rgb = hsvToRgb(hue, saturation, brightness);
		syncing = true;
		hexBox.setValue(hex(rgb));
		syncing = false;
	}

	// After a preset or a typed hex: move the sliders and the box to the new color
	private void syncWidgets() {
		syncing = true;
		hueSlider.set(hue);
		saturationSlider.set(saturation);
		brightnessSlider.set(brightness);
		if (!hexBox.getValue().equalsIgnoreCase(hex(rgb))) hexBox.setValue(hex(rgb));
		syncing = false;
	}

	static String hex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	private static float[] rgbToHsv(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		float h = 0;
		if (d > 0) {
			if (max == r) h = ((g - b) / d) % 6;
			else if (max == g) h = (b - r) / d + 2;
			else h = (r - g) / d + 4;
			h /= 6;
			if (h < 0) h += 1;
		}
		return new float[]{h, max == 0 ? 0 : d / max, max};
	}

	private static int hsvToRgb(float h, float s, float v) {
		float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c;
		float r, g, b;
		switch ((int) (h * 6) % 6) {
			case 0 -> { r = c; g = x; b = 0; }
			case 1 -> { r = x; g = c; b = 0; }
			case 2 -> { r = 0; g = c; b = x; }
			case 3 -> { r = 0; g = x; b = c; }
			case 4 -> { r = x; g = 0; b = c; }
			default -> { r = c; g = 0; b = x; }
		}
		return Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
	}

	/** Slider from 0 to 1 that shows its value as a percentage, or degrees for hue. */
	private static final class HsvSlider extends AbstractSliderButton {
		private final String label;
		private final DoubleConsumer onChange;

		HsvSlider(int x, int y, String label, double value, DoubleConsumer onChange) {
			super(x, y, WIDTH, ROW_HEIGHT, Component.empty(), value);
			this.label = label;
			this.onChange = onChange;
			updateMessage();
		}

		void set(double newValue) {
			value = Math.max(0, Math.min(1, newValue));
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			String shown = label.equals("Hue")
					? Math.round(value * 360) + "°"
					: Math.round(value * 100) + "%";
			setMessage(Component.literal(label + ": " + shown));
		}

		@Override
		protected void applyValue() {
			onChange.accept(value);
		}
	}
}
