package nl.davidengelen.redsquare.spaces

import nl.davidengelen.redsquare.saito.SaitoEnvelope

/** Durable copy confirmation. It never carries user-facing metadata or an uploader identity. */
class SpaceFileStage private constructor(
    val envelope: String,
    val id: String,
    val space: String,
    val lease: String,
    val objectId: String,
    val sha256: String,
    val bytes: Long,
    val stagedAt: Long,
) {
    fun requireObject(file: SpaceFileCipher.Sealed) {
        require(objectId == file.id && sha256 == file.sha256 && bytes == file.bytes)
    }

    override fun toString() = "Signed encrypted file copy confirmation"

    companion object {
        fun read(envelope: String, authority: String, provider: String): SpaceFileStage {
            require(envelope.length <= 8192)
            val signed = SaitoEnvelope.decode(envelope)
            require(signed.author == provider && provider != authority && signed.recipients == listOf(provider, authority))
            val body = signed.body
            require(body.keySet() == setOf("module", "version", "request", "space", "lease", "object", "sha256", "bytes"))
            require(body.storageText("module") == "SpacesStorage" && body.get("version").toString() == "1" &&
                body.storageText("request") == "staged")
            return SpaceFileStage(envelope, signed.id, body.storageText("space").also { require(it.matches(FILE_ID)) },
                body.storageText("lease").also { require(it.matches(FILE_SIGNATURE)) },
                body.storageText("object").also { require(it.matches(FILE_ID)) },
                body.storageText("sha256").also { require(it.matches(FILE_ID)) },
                body.storageNumber("bytes").also { require(it in 16..SpaceFileCipher.MAX_CIPHERTEXT_BYTES) }, signed.createdAt)
        }
    }
}
