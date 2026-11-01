package gg.grounds.sidebar;

import java.io.IOException;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.option.PriorityOption;
import org.geysermc.geyser.session.GeyserSession;

/** Stage PoC adapter compiled against the exact pinned Geyser runtime. */
public final class SidebarHeadExtension implements Extension {
    private volatile HeadPacks packs;

    @Subscribe
    public void initialized(GeyserPostInitializeEvent event) {
        try {
            packs = new HeadPacks(dataFolder().resolve("sessions"));
            logger().info("Personal sidebar portraits ready.");
        } catch (IOException e) {
            logger().warning("Personal sidebar portraits unavailable; using the crown fallback.");
        }
    }

    @Subscribe
    public void resourcePacks(SessionLoadResourcePacksEvent event) {
        if (packs == null || !(event.connection() instanceof GeyserSession session)) return;
        var skin = session.getClientData();
        if (skin == null) return;
        try {
            byte[] pixels = skin.getSkinData();
            int width = skin.getSkinImageWidth(), height = skin.getSkinImageHeight();
            int[] canvas;
            double[] uv;
            if (skin.isPersonaSkin()) {
                // Geyser has verified this client JWT. Read its face-only pixels
                // before Java login finishes and Geyser clears the JWT field.
                var face = AnimatedFace.read(skin.getOriginalString(), skin.getGeometryName(), skin.getGeometryData());
                if (face.isEmpty()) {
                    logger().warning("Persona face animation unavailable; using the crown fallback.");
                    return;
                }
                var frame = face.get();
                pixels = frame.pixels(); width = frame.width(); height = frame.height();
                canvas = frame.canvas(); uv = frame.uv();
            } else {
                canvas = SkinCanvas.resolve(skin.getGeometryName(), skin.getGeometryData(), width, height);
                uv = SkinCanvas.rectangles(skin.getGeometryName(), skin.getGeometryData());
            }
            boolean registered = packs.offer(event.connection(), pixels, width, height,
                canvas[0], canvas[1], uv,
                path -> event.register(ResourcePack.create(PackCodec.path(path)), PriorityOption.HIGHEST));
            if (registered) logger().info("Personal sidebar portrait registered (" + (skin.isPersonaSkin() ? "persona face frame, " : "classic skin, ") + "texture " + width + "x" + height
                + ", UV canvas " + canvas[0] + "x" + canvas[1] + ", face " + uv[0] + "," + uv[1] + ").");
            else logger().debug("Unsupported sidebar portrait dimensions; using the crown fallback.");
        } catch (IOException | RuntimeException e) {
            // An optional portrait must never prevent the authenticated login.
            logger().warning("Could not prepare personal sidebar portrait; using the crown fallback (" + e.getClass().getSimpleName() + ").");
        }
    }

    @Subscribe
    public void disconnected(SessionDisconnectEvent event) {
        if (packs == null) return;
        try { packs.remove(event.connection()); }
        catch (IOException e) { logger().warning("Could not remove temporary sidebar portrait."); }
    }

    @Subscribe
    public void shutdown(GeyserShutdownEvent event) {
        if (packs == null) return;
        try { packs.close(); }
        catch (IOException e) { logger().warning("Could not remove temporary sidebar portraits."); }
    }
}
