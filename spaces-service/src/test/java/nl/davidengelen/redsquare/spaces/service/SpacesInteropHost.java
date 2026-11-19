package nl.davidengelen.redsquare.spaces.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.davidengelen.redsquare.saito.SaitoEnvelope;
import nl.davidengelen.redsquare.saito.SaitoTransaction;
import nl.davidengelen.redsquare.saito.SaitoWallet;
import nl.davidengelen.redsquare.spaces.SpaceCrypto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

/** Explicit isolated rehearsal helper. Never reads a node or user identity. */
public final class SpacesInteropHost {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("An owned fixture directory is required");
        Path root = Path.of(args[0]);
        if (!Files.isDirectory(root) || Files.exists(root.resolve("ready.json"))) throw new IllegalArgumentException("Fixture must be new");
        // Public unsafe scalars, never fund these identities.
        var owner = SaitoWallet.Companion.fromPrivateKey("0".repeat(63) + "1");
        var service = SaitoWallet.Companion.fromPrivateKey("0".repeat(63) + "2");
        var clock = Clock.systemUTC();
        var scope = new SpaceCrypto.Scope(service.getPublicKey(), "a".repeat(64), "b".repeat(64), "0".repeat(64));
        var creator = SpaceCrypto.Companion.device(owner.getPublicKey(), "d".repeat(64)).create(scope);
        var metadata = creator.publish(owner, "metadata", "e".repeat(64), "0".repeat(128), "Owned interoperability community", clock.millis());
        var body = new JsonObject();
        body.addProperty("module", "Spaces"); body.addProperty("request", "event"); body.addProperty("version", 1);
        body.addProperty("kind", "create"); body.addProperty("space_id", scope.getSpaceId());
        body.addProperty("operation_id", "c".repeat(64)); body.addProperty("channel_id", scope.getChannelId());
        body.addProperty("key_generation", scope.getGeneration()); body.addProperty("epoch", 0); body.addProperty("visibility", "private");
        body.addProperty("metadata", Base64.getEncoder().encodeToString(metadata.envelope().getBytes(StandardCharsets.UTF_8)));
        var devices = new JsonObject();
        creator.getBindings().forEach(devices::addProperty); body.add("devices", devices);
        var original = SaitoTransaction.Companion.create(owner, body, clock.millis(), List.of(service.getPublicKey()));
        Files.writeString(root.resolve("original.txt"), original.encoded());
        Files.writeString(root.resolve("original-envelope.json"), original.envelope());
        try (var server = new SpacesServiceServer(root.resolve("state"), service, clock, 0)) {
            server.start();
            var ready = new JsonObject();
            ready.addProperty("port", server.getPort()); ready.addProperty("owner", owner.getPublicKey());
            ready.addProperty("service", service.getPublicKey()); ready.addProperty("original", original.getId());
            Files.writeString(root.resolve("ready.json"), ready.toString());
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
            while (!Files.exists(root.resolve("stop"))) {
                if (System.nanoTime() >= deadline) throw new IllegalStateException("Owned fixture lifetime exceeded");
                Thread.sleep(50);
            }
        }
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("state/spaces.sqlite"));
             var sql = db.createStatement(); var rows = sql.executeQuery("SELECT original,receipt FROM controls")) {
            if (!rows.next()) throw new IllegalStateException("No original was durably accepted");
            var storedOriginal = rows.getString(1);
            var storedReceipt = rows.getString(2);
            if (rows.next()) throw new IllegalStateException("Duplicate logical creation");
            if (!storedOriginal.equals(original.envelope())) throw new IllegalStateException("Original bytes changed in Node transport");
            var received = SaitoEnvelope.INSTANCE.decode(Files.readString(root.resolve("node-receipt.json")));
            var persisted = SaitoEnvelope.INSTANCE.decode(storedReceipt);
            if (!received.getId().equals(persisted.getId()) || !received.getAuthor().equals(service.getPublicKey()) ||
                !received.getBody().get("event").getAsString().equals(original.envelope())) throw new IllegalStateException("Returned receipt is not the actual persisted effect");
            var result = new JsonObject();
            result.addProperty("acceptedControls", 1); result.addProperty("originalId", original.getId());
            result.addProperty("receiptId", received.getId()); result.addProperty("service", received.getAuthor());
            result.addProperty("originalBytesPreserved", true); result.addProperty("actualReceiptVerified", true);
            Files.writeString(root.resolve("result.json"), result.toString());
        }
    }
}
