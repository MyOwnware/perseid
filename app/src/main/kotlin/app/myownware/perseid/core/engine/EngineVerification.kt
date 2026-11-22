package app.myownware.perseid.core.engine

import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipFile

internal fun verifiedEngineChecksum(
    checksums: ByteArray,
    detached: ByteArray,
    publicKey: ByteArray,
): String {
    if (!validSignature(checksums, detached, publicKey)) throw EngineUpdateFailure(EngineProblem.Verification)
    return checksums
        .decodeToString()
        .lineSequence()
        .mapNotNull { ENGINE_CHECKSUM.matchEntire(it)?.groupValues?.get(1) }
        .singleOrNull()
        ?.lowercase()
        ?: throw EngineUpdateFailure(EngineProblem.Verification)
}

internal fun verifyEngineArchive(
    archive: File,
    expected: String,
    version: String,
) {
    val sha256 = MessageDigest.getInstance("SHA-256")
    archive.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var count = input.read(buffer)
        while (count != -1) {
            sha256.update(buffer, 0, count)
            count = input.read(buffer)
        }
    }
    if (sha256.digest().toHexString() != expected || !archiveHasVersion(archive, version)) {
        throw EngineUpdateFailure(EngineProblem.Verification)
    }
}

private fun archiveHasVersion(
    archive: File,
    version: String,
): Boolean =
    try {
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry("yt_dlp/version.py") ?: return false
            zip.getInputStream(entry).bufferedReader().use { reader ->
                reader.lineSequence().take(VERSION_HEADER_LINES).any { it == "__version__ = '$version'" }
            }
        }
    } catch (_: ZipException) {
        false
    }

private fun validSignature(
    data: ByteArray,
    detached: ByteArray,
    publicKey: ByteArray,
): Boolean =
    try {
        val calculator = JcaKeyFingerprintCalculator()
        val keys = PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(publicKey.inputStream()), calculator)
        val packets = PGPObjectFactory(PGPUtil.getDecoderStream(detached.inputStream()), calculator)
        val signatures = packets.nextObject() as? PGPSignatureList
        val signature = signatures?.takeIf { it.size() == 1 }?.get(0)
        signature != null && packets.nextObject() == null && verifyPinned(data, signature, keys)
    } catch (_: IOException) {
        false
    } catch (_: PGPException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

private fun verifyPinned(
    data: ByteArray,
    signature: PGPSignature,
    keys: PGPPublicKeyRingCollection,
): Boolean {
    val key = keys.getPublicKey(signature.keyID)
    if (key == null || key.fingerprint.toHexString() != SIGNING_FINGERPRINT ||
        signature.signatureType != PGPSignature.BINARY_DOCUMENT
    ) {
        return false
    }
    signature.init(JcaPGPContentVerifierBuilderProvider(), key)
    signature.update(data)
    return signature.verify()
}

private val ENGINE_CHECKSUM = Regex("([0-9a-fA-F]{64}) [ *]yt-dlp")
private const val SIGNING_FINGERPRINT = "ac0cbbe6848d6a873464af4e57cf65933b5a7581"

private const val VERSION_HEADER_LINES = 10
