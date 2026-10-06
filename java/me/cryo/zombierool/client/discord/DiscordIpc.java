package me.cryo.zombierool.client.discord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class DiscordIpc implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger("ZombieRool");
    private static final int HANDSHAKE = 0;
    private static final int FRAME = 1;
    private static final int CLOSE = 2;
    private static final int PING = 3;
    private static final int PONG = 4;

    private final String applicationId;
    private final int pid = (int) ProcessHandle.current().pid();
    private final boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
    private volatile boolean alive = true;
    private Transport transport;
    private DiscordPresence.Snapshot sent;
    private long lastSend;

    DiscordIpc(String applicationId) {
        this.applicationId = applicationId;
    }

    @Override
    public void run() {
        while (alive) {
            try {
                if (transport == null && !connect()) {
                    sleep(20_000);
                    continue;
                }
                pump();
                DiscordPresence.Snapshot snapshot = DiscordPresence.current;
                long now = System.currentTimeMillis();
                boolean due = now - lastSend >= (snapshot.equals(sent) ? 15_000 : 4_000);
                if (due) {
                    String json = snapshot.activityJson(pid);
                    send(FRAME, json);
                    Frame reply = awaitReply(2_000);
                    if (reply != null && reply.body.contains("\"evt\":\"ERROR\"")) {
                        LOGGER.warn("Discord a refuse la presence : {}", reply.body);
                        sent = null;
                    } else {
                        sent = snapshot;
                        if (lastSend == 0) LOGGER.info("Rich Presence envoyee.");
                    }
                    lastSend = now;
                }
                sleep(250);
            } catch (Exception error) {
                LOGGER.debug("Discord Rich Presence interrompu : {}", error.toString());
                drop();
                sleep(15_000);
            }
        }
        drop();
    }

    void close() {
        alive = false;
        try {
            if (transport != null) send(CLOSE, "{}");
        } catch (Exception ignored) {
        }
        drop();
    }

    private boolean connect() {
        for (int index = 0; index < 10; index++) {
            try {
                transport = open(index);
                send(HANDSHAKE, "{\"v\":1,\"client_id\":\"" + applicationId + "\"}");
                long deadline = System.currentTimeMillis() + 3_000;
                while (System.currentTimeMillis() < deadline) {
                    Frame frame = transport.poll();
                    if (frame == null) {
                        sleep(40);
                        continue;
                    }
                    if (frame.opcode == PING) send(PONG, frame.body);
                    if (frame.opcode == FRAME && frame.body.contains("READY")) {
                        LOGGER.info("Rich Presence Discord connectee.");
                        sent = null;
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
            drop();
        }
        return false;
    }

    private void pump() throws IOException {
        Frame frame;
        while ((frame = transport.poll()) != null) handle(frame);
    }

    private Frame awaitReply(long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Frame frame = transport.poll();
            if (frame == null) {
                sleep(30);
                continue;
            }
            handle(frame);
            if (frame.opcode == FRAME) return frame;
        }
        return null;
    }

    private void handle(Frame frame) throws IOException {
        if (frame.opcode == PING) send(PONG, frame.body);
        if (frame.opcode == CLOSE) throw new IOException("Discord a ferme la presence");
    }

    private void send(int opcode, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(opcode);
        buffer.putInt(payload.length);
        buffer.put(payload);
        transport.write(buffer.array());
    }

    private Transport open(int index) throws IOException {
        if (windows) return new WindowsPipe("\\\\.\\pipe\\discord-ipc-" + index);
        IOException last = new IOException("socket Discord introuvable");
        for (Path path : unixCandidates(index)) {
            try {
                return new UnixPipe(path);
            } catch (IOException error) {
                last = error;
            }
        }
        throw last;
    }

    private static List<Path> unixCandidates(int index) {
        List<Path> paths = new ArrayList<>();
        String name = "discord-ipc-" + index;
        String runtime = System.getenv("XDG_RUNTIME_DIR");
        if (runtime != null && !runtime.isBlank()) {
            paths.add(Path.of(runtime, name));
            paths.add(Path.of(runtime, "app", "com.discordapp.Discord", name));
            paths.add(Path.of(runtime, "snap.discord", name));
        }
        String tmp = System.getenv("TMPDIR");
        if (tmp != null && !tmp.isBlank()) paths.add(Path.of(tmp, name));
        paths.add(Path.of("/tmp", name));
        return paths;
    }

    private void drop() {
        Transport current = transport;
        transport = null;
        sent = null;
        if (current != null) current.close();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private record Frame(int opcode, String body) {}

    private interface Transport {
        void write(byte[] bytes) throws IOException;
        Frame poll() throws IOException;
        void close();
    }

    private static final class WindowsPipe implements Transport {
        private final RandomAccessFile pipe;

        private WindowsPipe(String path) throws IOException {
            this.pipe = new RandomAccessFile(path, "rw");
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            pipe.write(bytes);
        }

        @Override
        public Frame poll() throws IOException {
            if (pipe.length() < 8) return null;
            byte[] header = read(8);
            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int opcode = buffer.getInt();
            int length = buffer.getInt();
            if (length < 0 || length > 65_536) throw new IOException("trame Discord invalide");
            return new Frame(opcode, new String(read(length), StandardCharsets.UTF_8));
        }

        private byte[] read(int length) throws IOException {
            byte[] bytes = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = pipe.read(bytes, offset, length - offset);
                if (read < 0) throw new IOException("pipe Discord fermee");
                offset += read;
            }
            return bytes;
        }

        @Override
        public void close() {
            try {
                pipe.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class UnixPipe implements Transport {
        private final SocketChannel channel;
        private final ByteBuffer incoming = ByteBuffer.allocate(65_544).order(ByteOrder.LITTLE_ENDIAN);

        private UnixPipe(Path path) throws IOException {
            channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.configureBlocking(false);
            channel.connect(UnixDomainSocketAddress.of(path));
            long deadline = System.currentTimeMillis() + 1_000;
            while (!channel.finishConnect()) {
                if (System.currentTimeMillis() > deadline) throw new IOException("connexion Discord trop longue");
                DiscordIpc.sleep(20);
            }
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                if (channel.write(buffer) == 0) DiscordIpc.sleep(10);
            }
        }

        @Override
        public Frame poll() throws IOException {
            if (channel.read(incoming) < 0) throw new IOException("pipe Discord fermee");
            incoming.flip();
            if (incoming.remaining() < 8) {
                incoming.compact();
                return null;
            }
            int opcode = incoming.getInt();
            int length = incoming.getInt();
            if (length < 0 || length > 65_536) throw new IOException("trame Discord invalide");
            if (incoming.remaining() < length) {
                incoming.position(incoming.position() - 8);
                incoming.compact();
                return null;
            }
            byte[] body = new byte[length];
            incoming.get(body);
            incoming.compact();
            return new Frame(opcode, new String(body, StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
    }
}
