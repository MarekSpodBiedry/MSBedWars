package com.msbedwars.client.config;

import com.msbedwars.client.display.StatsHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Drag the HUD anywhere. While dragging, the HUD is pinned to the nearest of 9 screen points
 * (corners, edge middles, center; a 25 px dead zone counts as middle) with an offset from it,
 * so it keeps its place when the window size or GUI scale changes. Scroll over it to resize,
 * arrow keys nudge it, right-click puts it back in the top right corner. No blur, so the game
 * stays visible; made-up players are shown when there is nothing real to show.
 */
public final class HudPositionScreen extends Screen {
	private static final int DEAD_ZONE = 25;
	private static final int HOVER = 0xFFFFFF55;
	private static final int DRAG = 0xFF55FF55;

	private final Screen parent;
	private boolean dragging;
	private double grabX;
	private double grabY;

	public HudPositionScreen(Screen parent) {
		super(Component.literal("Move HUD"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		StatsHud.editing = true;
		int half = 100;
		addRenderableWidget(Button.builder(Component.literal("Reset"), button -> reset())
				.bounds(width / 2 - half - 2, height - 28, half, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 + 2, height - 28, half, 20).build());
	}

	// No blur: the player should see the HUD against the real game
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x30000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		StatsHud hud = StatsHud.instance();
		if (hud != null) {
			hud.extractPreview(graphics);
			int[] b = hud.bounds();
			if (b != null && (dragging || inside(b, mouseX, mouseY))) {
				outline(graphics, b, dragging ? DRAG : HOVER);
			}
		}
		graphics.centeredText(font, "Drag to move · Scroll to resize · Arrows to nudge · Right-click to reset",
				width / 2, 10, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		int[] b = bounds();
		if (b == null || !inside(b, event.x(), event.y())) return false;
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			reset();
			return true;
		}
		dragging = true;
		grabX = event.x() - b[0];
		grabY = event.y() - b[1];
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (!dragging) return super.mouseDragged(event, dragX, dragY);
		int[] b = bounds();
		if (b == null) return true;
		double x = Math.max(0, Math.min(event.x() - grabX, width - b[2]));
		double y = Math.max(0, Math.min(event.y() - grabY, height - b[3]));
		place(x, y, b[2], b[3]);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			ModConfig.get().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int[] b = bounds();
		if (b == null || !inside(b, mouseX, mouseY)) return false;
		ModConfig config = ModConfig.get();
		float next = Math.round((config.hudScale + (float) scrollY * 0.05f) * 20) / 20f;
		config.hudScale = Math.max(0.5f, Math.min(1.5f, next));
		config.save();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int dx = 0, dy = 0;
		switch (event.key()) {
			case GLFW.GLFW_KEY_LEFT -> dx = -1;
			case GLFW.GLFW_KEY_RIGHT -> dx = 1;
			case GLFW.GLFW_KEY_UP -> dy = -1;
			case GLFW.GLFW_KEY_DOWN -> dy = 1;
			default -> {
				return super.keyPressed(event);
			}
		}
		ModConfig config = ModConfig.get();
		config.hudOffsetX += dx;
		config.hudOffsetY += dy;
		config.save();
		return true;
	}

	@Override
	public void onClose() {
		StatsHud.editing = false;
		ModConfig.get().save();
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		StatsHud.editing = false;
	}

	// Pins the HUD to the nearest screen point and stores the offset from it
	private void place(double x, double y, int w, int h) {
		ModConfig config = ModConfig.get();
		config.hudAnchorX = anchor(x + w / 2.0, width);
		config.hudAnchorY = anchor(y + h / 2.0, height);
		config.hudOffsetX = (int) Math.round(x - config.hudAnchorX * width + config.hudAnchorX * w);
		config.hudOffsetY = (int) Math.round(y - config.hudAnchorY * height + config.hudAnchorY * h);
	}

	private static float anchor(double center, int size) {
		if (center < size / 2.0 - DEAD_ZONE) return 0f;
		if (center > size / 2.0 + DEAD_ZONE) return 1f;
		return 0.5f;
	}

	private void reset() {
		ModConfig config = ModConfig.get();
		ModConfig defaults = new ModConfig();
		config.hudAnchorX = defaults.hudAnchorX;
		config.hudAnchorY = defaults.hudAnchorY;
		config.hudOffsetX = defaults.hudOffsetX;
		config.hudOffsetY = defaults.hudOffsetY;
		config.hudScale = defaults.hudScale;
		config.save();
	}

	private static int[] bounds() {
		StatsHud hud = StatsHud.instance();
		return hud == null ? null : hud.bounds();
	}

	private static boolean inside(int[] b, double x, double y) {
		return x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3];
	}

	private static void outline(GuiGraphicsExtractor graphics, int[] b, int color) {
		int x = b[0] - 1, y = b[1] - 1, w = b[2] + 2, h = b[3] + 2;
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y + 1, x + 1, y + h - 1, color);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}
}
