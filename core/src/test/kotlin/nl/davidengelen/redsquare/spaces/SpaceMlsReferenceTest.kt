package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.mls.TreeKEM.LeafNode
import org.bouncycastle.mls.TreeKEM.LifeTime
import org.bouncycastle.mls.codec.*
import org.bouncycastle.mls.crypto.MlsCipherSuite
import org.bouncycastle.mls.protocol.Group
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** An independent maintained implementation checks actual RFC9420 bytes. */
class SpaceMlsReferenceTest {
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
    private val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    private val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))

    @Test fun independentBouncyCastleMemberReadsTheNativeWelcomeExporterAndHistoryMessage() {
        val native = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val reference = ReferenceMember(finiteLifetime = true)
        val admission = native.admit(reference.binding())
        val welcome = MLSInputStream.decode(Base64.getDecoder().decode(admission.welcome), MLSMessage::class.java) as MLSMessage
        val independent = Group(reference.init.encoded, reference.leafPair, reference.privateSigner,
            reference.keyPackage, welcome.welcome, null, emptyMap(), emptyMap())
        assertEquals(1L, independent.epoch)
        val record = admission.session.publish(owner, "message", "d".repeat(64), "e".repeat(128),
            "Independent reference reads this", 1_700_000_000_000)
        val body = SaitoEnvelope.decode(record.envelope()).body
        val ciphertext = Base64.getDecoder().decode(body.get("ciphertext").asString)
        val key = independent.keySchedule.MLSExporter("SaitoSpacesArchiveV1", scope.groupId.toByteArray(Charsets.UTF_8), 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, ciphertext.copyOfRange(0, 12)))
            updateAAD("SaitoSpacesRecord/v1:${scope.groupId}:${owner.publicKey}:${"d".repeat(64)}:${"e".repeat(128)}:1:message".toByteArray(Charsets.UTF_8))
        }
        assertEquals("Independent reference reads this", cipher.doFinal(ciphertext.copyOfRange(12, ciphertext.size)).toString(Charsets.UTF_8))
        val grant = admission.session.history(owner, reference.binding().credential, "f".repeat(64), "1".repeat(128), 1_700_000_000_001)
        val wire = SaitoEnvelope.decode(grant.transaction.envelope()).body.get("wire").asString
        val message = MLSInputStream.decode(Base64.getDecoder().decode(wire), MLSMessage::class.java) as MLSMessage
        val payload = JsonParser.parseString(independent.unprotect(message)[1].toString(Charsets.UTF_8)).asJsonObject
        assertEquals("history-keys", payload.get("kind").asString)
        assertEquals(reference.binding().credential, payload.get("target").asString)
        assertEquals(setOf("0", "1"), payload.getAsJsonObject("keys").keySet())
        key.fill(0)
    }

    @Test fun independentMemberOpensAnArchiveKeyGrantAndUsesItToReadPreAdmissionContent() {
        val native = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val old = native.publish(owner, "message", "6".repeat(64), "7".repeat(128),
            "This text predates the reference member", 1_700_000_000_000)
        val reference = ReferenceMember(finiteLifetime = true)
        val admission = native.admit(reference.binding())
        val welcome = MLSInputStream.decode(Base64.getDecoder().decode(admission.welcome), MLSMessage::class.java) as MLSMessage
        val independent = Group(reference.init.encoded, reference.leafPair, reference.privateSigner,
            reference.keyPackage, welcome.welcome, null, emptyMap(), emptyMap())
        val grant = admission.session.archiveHistory(owner, reference.binding().credential, "8".repeat(64), "9".repeat(128), 1_700_000_000_001)
        val body = SaitoEnvelope.decode(grant.transaction.envelope()).body
        assertEquals("history-keys", body.get("content_kind").asString)
        val wrapped = Base64.getDecoder().decode(body.get("ciphertext").asString)
        val currentKey = independent.keySchedule.MLSExporter("SaitoSpacesArchiveV1", scope.groupId.toByteArray(Charsets.UTF_8), 32)
        val opened = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(currentKey, "AES"), GCMParameterSpec(128, wrapped.copyOfRange(0, 12)))
            updateAAD(("SaitoSpacesRecord/v1:${scope.groupId}:${owner.publicKey}:${"8".repeat(64)}:" +
                "${"9".repeat(128)}:1:history-keys:${reference.binding().credential}").toByteArray(Charsets.UTF_8))
            doFinal(wrapped.copyOfRange(12, wrapped.size))
        }
        val payload = JsonParser.parseString(opened.toString(Charsets.UTF_8)).asJsonObject
        assertEquals(reference.binding().credential, payload.get("target").asString)
        assertEquals(setOf("0"), payload.getAsJsonObject("keys").keySet()) // Never wrap the wrapping key itself.
        val pastKey = Base64.getDecoder().decode(payload.getAsJsonObject("keys").get("0").asString)
        val encryptedOld = Base64.getDecoder().decode(SaitoEnvelope.decode(old.envelope()).body.get("ciphertext").asString)
        val plaintext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(pastKey, "AES"), GCMParameterSpec(128, encryptedOld.copyOfRange(0, 12)))
            updateAAD("SaitoSpacesRecord/v1:${scope.groupId}:${owner.publicKey}:${"6".repeat(64)}:${"7".repeat(128)}:0:message".toByteArray(Charsets.UTF_8))
            doFinal(encryptedOld.copyOfRange(12, encryptedOld.size)).toString(Charsets.UTF_8)
        }
        assertEquals("This text predates the reference member", plaintext)
        opened.fill(0); currentKey.fill(0); pastKey.fill(0)
    }

    @Test fun anUnboundedForeignKeyPackageIsNotAdmittedEvenWithAValidWalletBinding() {
        val native = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val reference = ReferenceMember(finiteLifetime = false)
        assertThrows(IllegalStateException::class.java) { native.admit(reference.binding()) }
    }

    private inner class ReferenceMember(finiteLifetime: Boolean) {
        private val suite = MlsCipherSuite.BCMLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val init = X25519PrivateKeyParameters(SecureRandom())
        private val leaf = X25519PrivateKeyParameters(SecureRandom())
        val leafPair = AsymmetricCipherKeyPair(leaf.generatePublicKey(), leaf)
        private val signer = suite.generateSignatureKeyPair()
        val privateSigner: ByteArray = suite.serializeSignaturePrivateKey(signer.private)
        private val publicSigner = suite.serializeSignaturePublicKey(signer.public)
        private val credential = "SaitoSpaces/v1:${member.publicKey}:${"2".repeat(64)}"
        private val lifetime = if (!finiteLifetime) LifeTime() else {
            val now = System.currentTimeMillis() / 1000
            MLSInputStream.decode(ByteBuffer.allocate(16).putLong(now - 60).putLong(now + 86_400).array(), LifeTime::class.java) as LifeTime
        }
        private val node = LeafNode(suite, leaf.generatePublicKey().encoded, publicSigner,
            Credential.forBasic(credential.toByteArray(Charsets.UTF_8)), Capabilities(), lifetime, emptyList(), privateSigner)
        val keyPackage = KeyPackage(suite, init.generatePublicKey().encoded, node, emptyList(), privateSigner)
        fun binding(): SpaceCrypto.BoundPackage {
            val unsigned = SpaceCrypto.BoundPackage(scope, member.publicKey, "2".repeat(64),
                Base64.getEncoder().encodeToString(MLSOutputStream.encode(keyPackage)), Base64.getEncoder().encodeToString(publicSigner), "")
            return unsigned.copy(walletSignature = member.sign(unsigned.signedBytes()))
        }
    }
}
