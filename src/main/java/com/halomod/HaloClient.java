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
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class HaloClient implements ClientModInitializer {
    private static final int SEGMENTS = 32;
    private static final float INNER = 0.26f;
    private static final float OUTER = 0.36f;
    private static final double MAX_DIST_SQ = 128.0 * 128.0;

    private static boolean haloOn = true;
    private static boolean outlineOn = true;
    private static KeyBinding haloKey;
    private static KeyBinding outlineKey;

    @Override
    public void onInitializeClient() {
        haloKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.halomod.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.halomod"));
        outlineKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.halomod.outline", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.halomod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (haloKey.wasPressed()) {
                haloOn = !haloOn;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Halo: " + (haloOn ? "ON" : "OFF")), true);
                }
            }
            while (outlineKey.wasPressed()) {
                outlineOn = !outlineOn;
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Outline: " + (outlineOn ? "ON" : "OFF")), true);
                }
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(HaloClient::render);
    }

    private static boolean shouldDraw(PlayerEntity p, MinecraftClient mc, Vec3d cam) {
        if (p == mc.player && mc.options.getPerspective().isFirstPerson()) return false;
        if (p.isSpectator() || !p.isAlive()) return false;
        return p.squaredDistanceTo(cam) <= MAX_DIST_SQ;
    }

    private static void render(WorldRenderContext ctx) {
        if (!haloOn && !outlineOn) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || ctx.consumers() == null) return;

        Vec3d cam = ctx.camera().getPos();
        float td = ctx.tickDelta();
        MatrixStack ms = ctx.matrixStack();

        if (haloOn) {
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLightning());
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (!shouldDraw(p, mc, cam)) continue;

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

        if (outlineOn) {
            VertexConsumer lines = ctx.consumers().getBuffer(RenderLayer.getLines());
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (!shouldDraw(p, mc, cam)) continue;

                double dx = MathHelper.lerp(td, p.prevX, p.getX()) - p.getX();
                double dy = MathHelper.lerp(td, p.prevY, p.getY()) - p.getY();
                double dz = MathHelper.lerp(td, p.prevZ, p.getZ()) - p.getZ();
                Box box = p.getBoundingBox().offset(dx - cam.x, dy - cam.y, dz - cam.z);
                WorldRenderer.drawBox(ms, lines, box, 1.0f, 0.84f, 0.0f, 1.0f);
            }
        }
    }

    private static void ring(VertexConsumer vc, Matrix4f m, float inner, float outer,
                             int r, int g, int b, int a) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = 2 * Math.PI * i / SEGMENTS;
            double a1 = 2 * Math.PI * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

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
