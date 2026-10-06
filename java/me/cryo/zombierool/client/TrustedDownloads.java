package me.cryo.zombierool.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Downloads a map, its mods and its gunpacks into the running game folder.
 * Only Modrinth, CurseForge and github.com/Cryo60 are accepted.
 */
public final class TrustedDownloads {
    private static final String USER_AGENT = "ZombieRool (https://github.com/Cryo60/ZombieRool)";
    private static final String GITHUB_OWNER = "cryo60";
    private static final long MAX_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_ZIP_ENTRY = 256L * 1024L * 1024L;
    private static final int MAX_ZIP_ENTRIES = 8000;
    private static final int MAX_REDIRECTS = 5;

    private static final Pattern MODRINTH = Pattern.compile(
            "https://modrinth\\.com/(?:mod|plugin|datapack|modpack)/([^/?#]+)(?:/version/([^/?#]+))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CURSEFORGE = Pattern.compile(
            "https://(?:www\\.)?curseforge\\.com/minecraft/([^/]+)/([^/?#]+)(?:/files/(\\d+))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_REPO = Pattern.compile(
            "https://github\\.com/([^/]+)/([^/?#]+)/?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_RELEASE = Pattern.compile(
            "https://github\\.com/([^/]+)/([^/]+)/releases/download/[^/]+/[^?#]+",
            Pattern.CASE_INSENSITIVE);

    private TrustedDownloads() {}

    public static boolean canFetch(String raw) {
        try {
            classify(raw);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static void install(Minecraft mc, String worldName, String mapUrl, List<String> mods, List<String> gunpacks,
                               Consumer<Component> status, Consumer<Boolean> done) {
        Thread worker = new Thread(() -> {
            boolean ok = false;
            try {
                Path game = mc.gameDirectory.toPath();
                Path scratch = game.resolve("zr-download");
                Files.createDirectories(scratch);
                post(mc, status, Component.translatable("gui.zombierool.downloader.status.working", worldName));
                FileDownload map = fetch(mapUrl, scratch, false);
                extractMap(map.file, game.resolve("saves"), sanitizeWorldName(worldName));
                Files.deleteIfExists(map.file);

                if (mods != null) {
                    Path modsDir = game.resolve("mods");
                    Files.createDirectories(modsDir);
                    int index = 0;
                    for (String modUrl : mods) {
                        index++;
                        post(mc, status, Component.translatable("gui.zombierool.downloader.status.working",
                                "mod " + index + "/" + mods.size()));
                        FileDownload jar = fetch(modUrl, modsDir, true);
                        if (!jar.file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                            Files.deleteIfExists(jar.file);
                            throw new IOException("A mod link did not resolve to a jar");
                        }
                    }
                }
                if (gunpacks != null && !gunpacks.isEmpty()) {
                    Path tacz = game.resolve("tacz");
                    Files.createDirectories(tacz);
                    int index = 0;
                    for (String packUrl : gunpacks) {
                        index++;
                        post(mc, status, Component.translatable("gui.zombierool.downloader.status.working",
                                "gunpack " + index + "/" + gunpacks.size()));
                        fetch(packUrl, tacz, false);
                    }
                }
                boolean extras = (mods != null && !mods.isEmpty()) || (gunpacks != null && !gunpacks.isEmpty());
                post(mc, status, Component.translatable(extras
                        ? "gui.zombierool.downloader.status.restart"
                        : "gui.zombierool.downloader.status.done"));
                ok = true;
            } catch (Exception e) {
                String reason = e.getMessage() == null ? "unknown" : e.getMessage();
                post(mc, status, Component.translatable("gui.zombierool.downloader.status.failed", reason));
            }
            boolean finished = ok;
            mc.execute(() -> done.accept(finished));
        }, "zr-map-download");
        worker.setDaemon(true);
        worker.start();
    }

    private static void post(Minecraft mc, Consumer<Component> status, Component message) {
        mc.execute(() -> status.accept(message));
    }

    private enum Kind { DIRECT, MODRINTH, CURSEFORGE, GITHUB_RELEASE_PAGE }

    private static Kind classify(String raw) throws IOException {
        raw = raw.trim();
        URI uri = parseHttps(raw);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (MODRINTH.matcher(raw).find()) return Kind.MODRINTH;
        if (CURSEFORGE.matcher(raw).find()) return Kind.CURSEFORGE;
        if (GITHUB_REPO.matcher(raw).find()) {
            requireOwner(path);
            return Kind.GITHUB_RELEASE_PAGE;
        }
        if (GITHUB_RELEASE.matcher(raw).find()) {
            requireOwner(path);
            return Kind.DIRECT;
        }
        if ("cdn.modrinth.com".equals(host)) return Kind.DIRECT;
        if ("raw.githubusercontent.com".equals(host) || "api.github.com".equals(host)) {
            requireOwner(path);
            return Kind.DIRECT;
        }
        if (("mediafilez.forgecdn.net".equals(host) || "edge.forgecdn.net".equals(host))
                && path.startsWith("/files/")) {
            return Kind.DIRECT;
        }
        throw new IOException("Link is not Modrinth, CurseForge or github.com/Cryo60");
    }

    private static boolean isGithub(String raw) {
        String host = URI.create(raw.trim()).getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("github.com") || host.equals("api.github.com") || host.equals("raw.githubusercontent.com");
    }

    private static void requireOwner(String path) throws IOException {
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.equalsIgnoreCase("repos")) continue;
            if (!GITHUB_OWNER.equals(part.toLowerCase(Locale.ROOT))) {
                throw new IOException("Only github.com/Cryo60 is allowed");
            }
            return;
        }
        throw new IOException("Only github.com/Cryo60 is allowed");
    }

    private static FileDownload fetch(String raw, Path destDir, boolean preferJar) throws IOException {
        Kind kind = classify(raw);
        return switch (kind) {
            case MODRINTH -> fetchModrinth(raw, destDir, preferJar);
            case CURSEFORGE -> fetchCurseForge(raw, destDir, preferJar);
            case GITHUB_RELEASE_PAGE -> fetchGithubRelease(raw, destDir, preferJar);
            case DIRECT -> download(raw, destDir, safeName(raw), isGithub(raw));
        };
    }

    private static FileDownload fetchModrinth(String raw, Path destDir, boolean preferJar) throws IOException {
        Matcher matcher = MODRINTH.matcher(raw);
        if (!matcher.find()) throw new IOException("Modrinth link was rejected");
        String slug = matcher.group(1);
        String version = matcher.group(2);
        String api = version != null
                ? "https://api.modrinth.com/v2/version/" + urlEncode(version)
                : "https://api.modrinth.com/v2/project/" + urlEncode(slug) + "/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22forge%22%5D";
        JsonElement parsed = readJson(api, false);
        JsonObject chosen = parsed.isJsonArray() ? firstObject(parsed.getAsJsonArray(), slug) : parsed.getAsJsonObject();
        JsonArray files = chosen.getAsJsonArray("files");
        if (files == null || files.isEmpty()) throw new IOException("Modrinth version has no file");
        JsonObject file = pickFile(files, preferJar);
        String url = file.get("url").getAsString();
        if (!url.toLowerCase(Locale.ROOT).startsWith("https://cdn.modrinth.com/")) {
            throw new IOException("Modrinth file is not on cdn.modrinth.com");
        }
        return download(url, destDir, safeName(file.get("filename").getAsString()), false);
    }

    private static FileDownload fetchCurseForge(String raw, Path destDir, boolean preferJar) throws IOException {
        Matcher matcher = CURSEFORGE.matcher(raw);
        if (!matcher.find()) throw new IOException("CurseForge link was rejected");
        String category = matcher.group(1);
        String slug = matcher.group(2);
        String fileId = matcher.group(3);
        String api = "https://api.cfwidget.com/minecraft/" + urlEncode(category) + "/" + urlEncode(slug);
        JsonObject project = readJson(api, false).getAsJsonObject();
        if (!project.has("files")) throw new IOException("CurseForge project was not found");
        JsonObject chosen = null;
        JsonObject fallback = null;
        for (JsonElement el : project.getAsJsonArray("files")) {
            JsonObject file = el.getAsJsonObject();
            String lower = file.get("name").getAsString().toLowerCase(Locale.ROOT);
            boolean kind = preferJar ? lower.endsWith(".jar") : (lower.endsWith(".jar") || lower.endsWith(".zip"));
            if (!kind) continue;
            if (fileId != null && file.get("id").getAsLong() == Long.parseLong(fileId)) {
                chosen = file;
                break;
            }
            if (fallback == null) fallback = file;
            if (fileId != null) continue;
            boolean versionOk = false;
            if (file.has("versions") && file.get("versions").isJsonArray()) {
                for (JsonElement version : file.getAsJsonArray("versions")) {
                    if ("1.20.1".equals(version.getAsString())) versionOk = true;
                }
            }
            if (versionOk && chosen == null) chosen = file;
        }
        if (chosen == null) chosen = fallback;
        if (chosen == null) throw new IOException("CurseForge project has no downloadable file");
        long id = chosen.get("id").getAsLong();
        String original = chosen.get("name").getAsString();
        String filename = safeName(original);
        String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8).replace("+", "%20");
        String cdn = "https://mediafilez.forgecdn.net/files/" + (id / 1000) + "/" + (id % 1000) + "/" + encoded;
        try {
            return download(cdn, destDir, filename, false);
        } catch (IOException first) {
            String edge = "https://edge.forgecdn.net/files/" + (id / 1000) + "/" + (id % 1000) + "/" + encoded;
            try {
                return download(edge, destDir, filename, false);
            } catch (IOException second) {
                throw new IOException("CurseForge refused the file");
            }
        }
    }

    private static FileDownload fetchGithubRelease(String raw, Path destDir, boolean preferJar) throws IOException {
        Matcher matcher = GITHUB_REPO.matcher(raw);
        if (!matcher.find()) throw new IOException("GitHub link was rejected");
        String owner = matcher.group(1);
        String name = matcher.group(2).replace(".git", "");
        if (!GITHUB_OWNER.equals(owner.toLowerCase(Locale.ROOT))) {
            throw new IOException("Only github.com/Cryo60 is allowed");
        }
        String api = "https://api.github.com/repos/" + urlEncode(owner) + "/" + urlEncode(name) + "/releases/latest";
        JsonObject json = readJson(api, true).getAsJsonObject();
        if (!json.has("assets")) throw new IOException("GitHub release is empty");
        for (JsonElement el : json.getAsJsonArray("assets")) {
            JsonObject asset = el.getAsJsonObject();
            String filename = asset.get("name").getAsString();
            String lower = filename.toLowerCase(Locale.ROOT);
            boolean kind = preferJar ? lower.endsWith(".jar") : (lower.endsWith(".jar") || lower.endsWith(".zip"));
            if (!kind) continue;
            String url = asset.get("browser_download_url").getAsString();
            if (!GITHUB_RELEASE.matcher(url).find() || !url.toLowerCase(Locale.ROOT).contains("/" + GITHUB_OWNER + "/")) {
                throw new IOException("GitHub asset is not on github.com/Cryo60");
            }
            return download(url, destDir, safeName(filename), true);
        }
        throw new IOException("GitHub release has no zip or jar");
    }

    private static FileDownload download(String raw, Path destDir, String filename, boolean githubChain) throws IOException {
        Files.createDirectories(destDir);
        Path out = destDir.resolve(filename).normalize();
        if (!out.startsWith(destDir.normalize())) throw new IOException("File name was rejected");
        URL current = parseHttps(raw).toURL();
        boolean github = githubChain;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpURLConnection conn = open(current, github);
            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null) throw new IOException("Redirect had no target");
                URL next = new URL(current, location);
                assertRedirect(next, github);
                current = next;
                continue;
            }
            if (code == 401 || code == 403) {
                conn.disconnect();
                throw new IOException("Host refused the file (" + code + ")");
            }
            if (code < 200 || code >= 300) {
                conn.disconnect();
                throw new IOException("Download failed (" + code + ")");
            }
            long advertised = conn.getContentLengthLong();
            if (advertised > MAX_BYTES) {
                conn.disconnect();
                throw new IOException("File is too large");
            }
            Path tmp = out.resolveSibling(out.getFileName().toString() + ".part");
            try (InputStream in = conn.getInputStream(); OutputStream file = Files.newOutputStream(tmp)) {
                copyLimited(in, file, MAX_BYTES);
            } finally {
                conn.disconnect();
            }
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            return new FileDownload(out);
        }
        throw new IOException("Too many redirects");
    }

    private static void assertRedirect(URL next, boolean githubChain) throws IOException {
        parseHttps(next.toString());
        String host = next.getHost().toLowerCase(Locale.ROOT);
        if ("cdn.modrinth.com".equals(host) || "mediafilez.forgecdn.net".equals(host) || "edge.forgecdn.net".equals(host)) {
            return;
        }
        if (githubChain && (host.equals("objects.githubusercontent.com")
                || host.equals("release-assets.githubusercontent.com")
                || host.equals("github-releases.githubusercontent.com")
                || host.equals("codeload.github.com")
                || host.equals("github.com")
                || host.equals("release-assets.githubusercontent.com"))) {
            return;
        }
        if ("github.com".equals(host) || "raw.githubusercontent.com".equals(host) || "api.github.com".equals(host)) {
            requireOwner(next.getPath() == null ? "" : next.getPath());
            return;
        }
        throw new IOException("Redirect left the allowed hosts");
    }

    private static JsonElement readJson(String api, boolean github) throws IOException {
        URL url = parseHttps(api).toURL();
        HttpURLConnection conn = open(url, github);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new IOException("Lookup failed (" + code + ")");
        }
        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            JsonElement parsed = new Gson().fromJson(reader, JsonElement.class);
            if (parsed == null) throw new IOException("Empty response");
            return parsed;
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(URL url, boolean github) throws IOException {
        parseHttps(url.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", github ? "application/vnd.github+json" : "application/json, */*");
        return conn;
    }

    private static URI parseHttps(String raw) throws IOException {
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid link");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("HTTPS required");
        if (uri.getUserInfo() != null) throw new IOException("Credentials in the link were rejected");
        if (uri.getHost() == null) throw new IOException("Invalid link");
        if (uri.getPort() != -1 && uri.getPort() != 443) throw new IOException("Only port 443 is allowed");
        return uri;
    }

    private static void extractMap(Path zip, Path saves, String worldName) throws IOException {
        Path dest = saves.resolve(worldName).normalize();
        if (!dest.startsWith(saves.normalize())) throw new IOException("Map name was rejected");
        if (Files.exists(dest)) {
            deleteTree(dest);
        }
        Files.createDirectories(dest);
        String prefix;
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            if (zf.size() > MAX_ZIP_ENTRIES) throw new IOException("Zip has too many files");
            prefix = detectPrefix(zf);
            Enumeration<? extends ZipEntry> entries = zf.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                count++;
                if (count > MAX_ZIP_ENTRIES) throw new IOException("Zip has too many files");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..")) throw new IOException("Zip path was rejected");
                if (prefix != null) {
                    if (!name.startsWith(prefix)) continue;
                    name = name.substring(prefix.length());
                }
                if (name.isEmpty()) continue;
                Path out = dest.resolve(name).normalize();
                if (!out.startsWith(dest)) throw new IOException("Zip path was rejected");
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                if (entry.getSize() > MAX_ZIP_ENTRY) throw new IOException("A file inside the zip is too large");
                Path parent = out.getParent();
                if (parent != null) Files.createDirectories(parent);
                try (InputStream in = zf.getInputStream(entry); OutputStream file = Files.newOutputStream(out)) {
                    copyLimited(in, file, MAX_ZIP_ENTRY);
                }
            }
        }
        if (!Files.exists(dest.resolve("level.dat"))) {
            deleteTree(dest);
            throw new IOException("The zip is not a Minecraft world");
        }
    }

    private static String detectPrefix(ZipFile zf) {
        if (zf.getEntry("level.dat") != null) return null;
        String root = null;
        Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName().replace('\\', '/');
            int slash = name.indexOf('/');
            if (slash <= 0) return null;
            String first = name.substring(0, slash);
            if (root == null) root = first;
            else if (!root.equals(first)) return null;
        }
        if (root != null && zf.getEntry(root + "/level.dat") != null) return root + "/";
        return null;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static void copyLimited(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > max) throw new IOException("File is too large");
            out.write(buf, 0, n);
        }
    }

    private static JsonObject firstObject(JsonArray arr, String slug) throws IOException {
        if (arr.isEmpty()) throw new IOException("No Forge 1.20.1 file for " + slug);
        return arr.get(0).getAsJsonObject();
    }

    private static JsonObject pickFile(JsonArray files, boolean preferJar) {
        JsonObject fallback = files.get(0).getAsJsonObject();
        JsonObject primary = null;
        for (JsonElement el : files) {
            JsonObject file = el.getAsJsonObject();
            if (file.has("primary") && file.get("primary").getAsBoolean()) primary = file;
            String name = file.get("filename").getAsString().toLowerCase(Locale.ROOT);
            if (preferJar && name.endsWith(".jar")) {
                if (primary != null && primary.get("filename").getAsString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    return primary;
                }
                return file;
            }
        }
        return primary != null ? primary : fallback;
    }

    private static String safeName(String raw) throws IOException {
        String name = raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int query = name.indexOf('?');
        if (query >= 0) name = name.substring(0, query);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank() || name.equals(".") || name.equals("..") || name.length() > 80) {
            throw new IOException("File name was rejected");
        }
        return name;
    }

    private static String sanitizeWorldName(String raw) throws IOException {
        String name = raw.trim();
        if (name.isEmpty() || name.length() > 64) throw new IOException("Map name was rejected");
        if (name.contains("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new IOException("Map name was rejected");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || ":*?\"<>|".indexOf(c) >= 0) throw new IOException("Map name was rejected");
        }
        return name;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record FileDownload(Path file) {}
}
