package com.halomod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class HaloClient implements ClientModInitializer {
    private static final int SEGMENTS = 32;
    private static final float INNER = 0.26f;
    private static final float OUTER = 0.36f;
    private static final double MAX_DIST_SQ = 128.0 * 128.0;

    private static boolean enabled = true;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.halomod.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.halomod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Halo: " + (enabled ? "ON" : "OFF")), true);
                }
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(HaloClient::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (!enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || ctx.consumers() == null) return;

        Vec3d cam = ctx.camera().getPos();
        float td = ctx.tickDelta();
        MatrixStack ms = ctx.matrixStack();
        VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLightning());

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.squaredDistanceTo(cam) > MAX_DIST_SQ) continue;

            double x = MathHelper.lerp(td, p.prevX, p.getX());
            double y = MathHelper.lerp(td, p.prevY, p.getY());
            double z = MathHelper.lerp(td, p.prevZ, p.getZ());
            double bob = Math.sin((p.age + td) * 0.1) * 0.03;

            ms.push();
            ms.translate(x - cam.x, y + p.getHeight() + 0.3 + bob - cam.y, z - cam.z);
            Matrix4f m = ms.peek().getPositionMatrix();
            ring(vc, m, INNER, OUTER, 255, 215, 0, 230);
            ms.pop();
        }
    }

    private static void ring(VertexConsumer vc, Matrix4f m, float inner, float outer,
                             int r, int g, int b, int a) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = 2 * Math.PI * i / SEGMENTS;
            double a1 = 2 * Math.PI * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

            // inner0, outer0, outer1, inner1 (and reversed, so it is visible from below and above)
            float[][] q = {
                    {c0 * inner, s0 * inner}, {c0 * outer, s0 * outer},
                    {c1 * outer, s1 * outer}, {c1 * inner, s1 * inner}
            };
            for (int k = 0; k < 4; k++) vertex(vc, m, q[k], r, g, b, a);
            for (int k = 3; k >= 0; k--) vertex(vc, m, q[k], r, g, b, a);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float[] p, int r, int g, int b, int a) {
        vc.vertex(m, p[0], 0f, p[1]).color(r, g, b, a).next();
    }
}
