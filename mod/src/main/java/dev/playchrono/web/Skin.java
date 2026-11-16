package dev.playchrono.web;

import dev.playchrono.reflect.R;
import dev.playchrono.util.Json;
import dev.playchrono.util.Log;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * The player's own head for the dashboard: the skin is read from Mojang's public profile (the same servers the game
 * uses), the 8x8 face and its hat layer are cut out and saved as {@code face.png} in the data folder. Nothing else
 * is sent anywhere; offline accounts simply keep the generated avatar.
 */
public final class Skin {
    private static final String PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String TEXTURES = "textures.minecraft.net";
    private static final int SIZE = 64;

    private Skin() {}

    /** Background thread only. At most once a day; never throws. */
    public static void refresh(Path dataDir, int timeoutMs) {
        Path face = dataDir.resolve("face.png");
        try {
            if (Files.exists(face) && System.currentTimeMillis() - Files.getLastModifiedTime(face).toMillis() < 86_400_000L) return;
            Object user = R.call(R.callStatic("Minecraft.getInstance"), "Minecraft.getUser");
            Object id = R.call(user, "User.getProfileId");
            if (id == null) return;
            String uuid = id.toString().replace("-", "");
            if (!uuid.matches("[0-9a-f]{32}")) return;
            Map<String, Object> profile = Json.map(Json.tryParse(new String(get(PROFILE + uuid, timeoutMs, 16 * 1024), StandardCharsets.UTF_8)));
            String skinUrl = skinUrl(profile);
            if (skinUrl == null) return;
            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(get(skinUrl, timeoutMs, 64 * 1024)));
            if (skin == null || skin.getWidth() != 64 || (skin.getHeight() != 64 && skin.getHeight() != 32)) return;
            BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            int k = SIZE / 8;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int base = skin.getRGB(8 + x, 8 + y) | 0xFF000000;
                    int hat = skin.getRGB(40 + x, 8 + y);
                    int c = (hat >>> 24) > 0 ? hat | 0xFF000000 : base; // hat layer drawn over the face
                    for (int dy = 0; dy < k; dy++) for (int dx = 0; dx < k; dx++) out.setRGB(x * k + dx, y * k + dy, c);
                }
            }
            Path tmp = dataDir.resolve("face.png.tmp");
            ImageIO.write(out, "png", tmp.toFile());
            Files.move(tmp, face, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // offline, or Mojang unreachable: keep the previous head (or the generated avatar)
        } catch (Throwable t) {
            Log.warn("skin refresh failed", t);
        }
    }

    /** The saved head as a data: URI (the dashboard's CSP allows data: images), or null. */
    public static String dataUri(Path dataDir) {
        try {
            Path face = dataDir.resolve("face.png");
            if (!Files.isRegularFile(face) || Files.size(face) > 32 * 1024) return null;
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(face));
        } catch (IOException e) {
            return null;
        }
    }

    static String skinUrl(Map<String, Object> profile) {
        if (profile == null || !(profile.get("properties") instanceof List)) return null;
        for (Object o : (List<?>) profile.get("properties")) {
            Map<String, Object> p = Json.map(o);
            if (p == null || !"textures".equals(p.get("name"))) continue;
            String json = new String(Base64.getDecoder().decode(Json.str(p.get("value"), "")), StandardCharsets.UTF_8);
            Map<String, Object> skin = Json.map(Json.map(Json.map(Json.tryParse(json)).get("textures")).get("SKIN"));
            String url = skin == null ? null : Json.str(skin.get("url"), null);
            if (url == null) return null;
            URI u = URI.create(url);
            // only Mojang's texture server, always over HTTPS
            if (!TEXTURES.equals(u.getHost()) || u.getPath() == null || !u.getPath().matches("/texture/[0-9a-f]{20,80}")) return null;
            return "https://" + TEXTURES + u.getPath();
        }
        return null;
    }

    private static byte[] get(String url, int timeoutMs, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent", "PlayChrono");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n, total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > max) throw new IOException("too large");
                    b.write(buf, 0, n);
                }
                return b.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
