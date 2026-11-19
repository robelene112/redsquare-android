# Spaces service

Source-only implementation for137. It is not installed on a Saito node.
Current executable paths cover signed creation, invitation and admission
controls, a separate join-request queue, durable original receipt replay and
loopback HTTP transport. Content paging, authenticated reads, node integration
and operational readiness/rollback are still required before activation.

The service owns ordering and public membership metadata. Clients retain all
community MLS state and archive keys. This service does not accept community
file uploads or choose a storage provider.

## Local verification and standalone runtime

Use `scripts/android-build :spaces-service:test` for the isolated owned fixtures.
The process case uses only public unsafe test identities, actually kills the
owned child process, starts a distinct process and compares the original signed
result. It proves service durability, not Android process recovery or node
compatibility. HTTP is bound exclusively to127.0.0.1.

Build the distribution with `:spaces-service:installDist` through the project's
supported Gradle environment. Startup requires these explicit environment values:

- `SPACES_STATE_DIR`: a dedicated persistent service directory.
- `SPACES_IDENTITY_FILE`: an existing owner-only JSON file with version1,
  `publicKey` and `privateKey`, matching each other. Never pass the private key
  on the command line, put it in Git, or replace a missing identity automatically.
- `SPACES_LISTEN_PORT`: an explicit local port, or0 for an isolated rehearsal.

Run `spaces-service/build/install/spaces-service/bin/spaces-service` in that
environment. Successful startup prints `SPACES_READY <actualPort> <publicKey>`.
This line reports the local listener, not an activated Saito capability.

SQLite JDBC is pinned to the official stable
[3.54.1.0 release](https://github.com/xerial/sqlite-jdbc/releases/tag/3.54.1.0).
Schema1 uses immediate transactions, WAL and full synchronous commits. Initial
schema creation is atomic. Re-entry checks the stored service identity and
rejects unknown versions or unversioned existing data instead of resetting it.
Do not copy an older database over subsequent accepted writes. Keep the entire
database/WAL family together during offline recovery. A proper operational
migration/readiness/write-preserving rollback rehearsal remains an activation
prerequisite, alongside the node adapter and its actual compatibility proof.
