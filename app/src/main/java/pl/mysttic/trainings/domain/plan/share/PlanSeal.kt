package pl.mysttic.trainings.domain.plan.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Klucz, którym telefon podpisuje plany (D-58). Na telefonie żyje w Android Keystore, testy dają klucz programowy. */
interface PlanSigner {
    /** Klucz publiczny EC P-256 w X.509 (SubjectPublicKeyInfo): jedzie w pliku, żeby odbiorca mógł sprawdzić podpis. */
    val publicKey: ByteArray

    /** Podpis [data] algorytmem [PlanSeal.ALGORITHM]. */
    fun sign(data: ByteArray): ByteArray
}

/**
 * Koperta pliku planu od formatu 2 (D-58): podpisana treść albo podpisana treść zamknięta hasłem. Te same znaczniki
 * `format` i `formatVersion` co w zwykłym pliku, więc aplikacja sprzed D-58 mówi „zaktualizuj aplikację”, a nie
 * „to nie jest plik planu”. Nazwy pól są umową na lata jak w `PlanFile`.
 */
@Serializable
data class PlanEnvelope(
    @SerialName("format") val format: String,
    @SerialName("formatVersion") val formatVersion: Int,
    @SerialName("signed") val signed: SignedPlan? = null,
    @SerialName("encrypted") val encrypted: EncryptedPlan? = null,
)

@Serializable
data class SignedPlan(
    @SerialName("algorithm") val algorithm: String,
    @SerialName("publicKey") val publicKey: String,
    @SerialName("signature") val signature: String,
    /** Plik planu (`PlanFile`) jako JSON w UTF-8, w Base64: podpis obejmuje dokładnie te bajty. */
    @SerialName("payload") val payload: String,
)

@Serializable
data class EncryptedPlan(
    @SerialName("cipher") val cipher: String,
    @SerialName("kdf") val kdf: String,
    @SerialName("iterations") val iterations: Int,
    @SerialName("salt") val salt: String,
    @SerialName("iv") val iv: String,
    /** [SignedPlan] jako JSON, zaszyfrowany AES-256-GCM kluczem z hasła; ostatnie 16 bajtów to znacznik GCM. */
    @SerialName("data") val data: String,
)

/** Pochodzenie pliku po otwarciu. */
data class PlanSealInfo(
    /** Podpis zgadza się z treścią; false = plik bez podpisu (sprzed D-58), więc autora nie da się potwierdzić. */
    val signed: Boolean,
    /** Odcisk klucza, który podpisał plik ([PlanSeal.fingerprint]); null bez podpisu. */
    val fingerprint: String?,
    val passwordProtected: Boolean,
)

data class OpenedPlanFile(val file: PlanFile, val seal: PlanSealInfo)

/** Plik zamknięty hasłem: hasła nie podano ([wrong] = false) albo nie pasuje. */
class PlanPasswordException(val wrong: Boolean) :
    IllegalArgumentException(if (wrong) "Hasło nie pasuje." else "Plan jest chroniony hasłem.")

/**
 * Podpis i hasło pliku planu (D-58). Czysty Kotlin na `java.security` i `javax.crypto`, bez nowych bibliotek.
 *
 * Podpis ECDSA P-256 obejmuje całą treść, także autora i podpisującego: zmiana czegokolwiek po podpisaniu zatrzymuje
 * import. Klucz publiczny jedzie w pliku, więc podpis dowodzi tylko tego, że plik podpisał właściciel tego klucza;
 * kto chce sprawdzić autora, porównuje odcisk klucza z tym, który autor pokazuje w „O mnie”. Hasło zamyka podpisaną
 * treść szyfrem AES-256-GCM z kluczem z PBKDF2-HMAC-SHA256 (hasło w UTF-8): bez hasła nie odczyta jej nikt,
 * także ta aplikacja.
 */
object PlanSeal {
    const val ALGORITHM = "SHA256withECDSA"
    const val CIPHER = "AES-256-GCM"
    const val KDF = "PBKDF2WithHmacSHA256"
    const val ITERATIONS = 210_000
    const val MIN_PASSWORD = 6

    /** Widełki liczby iteracji w obcym pliku: za mało to słaby klucz, za dużo to telefon mielący minutami. */
    private val ACCEPTED_ITERATIONS = 10_000..2_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val FINGERPRINT_BYTES = 10
    private const val TAMPERED =
        "Podpis nie zgadza się z treścią: ktoś zmienił plik po podpisaniu, na przykład autora albo ćwiczenia. " +
            "Poproś autora o nowy plik."
    private const val BROKEN = "Plik planu jest uszkodzony."

    /** Plik planu do wysyłki: treść podpisana kluczem telefonu, a z [password] zamknięta hasłem. */
    fun seal(file: PlanFile, json: Json, signer: PlanSigner, password: String? = null, random: SecureRandom = SecureRandom()): String {
        val payload = PlanFileCodec.encode(file, json).toByteArray(Charsets.UTF_8)
        val signed = SignedPlan(
            algorithm = ALGORITHM,
            publicKey = b64(signer.publicKey),
            signature = b64(signer.sign(payload)),
            payload = b64(payload),
        )
        val envelope = if (password == null) {
            PlanEnvelope(PlanFile.FORMAT, PlanFile.VERSION, signed = signed)
        } else {
            require(password.length >= MIN_PASSWORD) { "Hasło musi mieć co najmniej $MIN_PASSWORD znaków." }
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            val plain = json.encodeToString(SignedPlan.serializer(), signed).toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(password, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
            PlanEnvelope(
                PlanFile.FORMAT,
                PlanFile.VERSION,
                encrypted = EncryptedPlan(CIPHER, KDF, ITERATIONS, b64(salt), b64(iv), b64(cipher.doFinal(plain))),
            )
        }
        return Json(json) { prettyPrint = true; encodeDefaults = false }.encodeToString(PlanEnvelope.serializer(), envelope)
    }

    /**
     * Odczyt pliku każdej wersji: zwykły plik planu (bez podpisu, sprzed D-58), podpisany albo zamknięty hasłem.
     * Rzuca [PlanPasswordException], gdy trzeba hasła albo nie pasuje, i [PlanFileException], gdy podpis nie zgadza się
     * z treścią albo plik jest zepsuty.
     */
    fun open(text: String, json: Json, password: String? = null): OpenedPlanFile {
        if (text.length > PlanFile.MAX_BYTES) throw PlanFileException("Plik jest za duży jak na plan treningowy.")
        val root = PlanFileCodec.parseRoot(text, json)
        if (!root.containsKey("signed") && !root.containsKey("encrypted")) {
            // Plik sprzed D-58 albo bez koperty: wchodzi jak dawniej, ale autora nie da się potwierdzić.
            return OpenedPlanFile(PlanFileCodec.decode(text, json), PlanSealInfo(signed = false, fingerprint = null, passwordProtected = false))
        }
        PlanFileCodec.checkHeader(root)
        val envelope = runCatching { json.decodeFromJsonElement(PlanEnvelope.serializer(), root) }.getOrElse { throw PlanFileException(BROKEN) }
        val encrypted = envelope.encrypted
        val signed = if (encrypted != null) decrypt(encrypted, json, password) else envelope.signed ?: throw PlanFileException(BROKEN)
        val payload = verify(signed)
        val file = PlanFileCodec.decode(payload.toString(Charsets.UTF_8), json)
        return OpenedPlanFile(file, PlanSealInfo(signed = true, fingerprint = fingerprint(bytes(signed.publicKey)), passwordProtected = encrypted != null))
    }

    /** Odcisk klucza publicznego do porównania na oko: 80 bitów SHA-256 w pięciu grupach po cztery znaki. */
    fun fingerprint(publicKey: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(publicKey).take(FINGERPRINT_BYTES)
            .joinToString("") { "%02X".format(it) }
            .chunked(4)
            .joinToString(" ")

    private fun verify(signed: SignedPlan): ByteArray {
        if (signed.algorithm != ALGORITHM) throw PlanFileException("Plik podpisano sposobem, którego ta wersja nie zna. Zaktualizuj aplikację.")
        val payload = bytes(signed.payload)
        if (payload.size > PlanFile.MAX_BYTES) throw PlanFileException("Plik jest za duży jak na plan treningowy.")
        val ok = runCatching {
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes(signed.publicKey)))
            Signature.getInstance(ALGORITHM).run {
                initVerify(key)
                update(payload)
                verify(bytes(signed.signature))
            }
        }.getOrDefault(false)
        if (!ok) throw PlanFileException(TAMPERED)
        return payload
    }

    private fun decrypt(encrypted: EncryptedPlan, json: Json, password: String?): SignedPlan {
        if (encrypted.cipher != CIPHER || encrypted.kdf != KDF) {
            throw PlanFileException("Plik zamknięto sposobem, którego ta wersja nie zna. Zaktualizuj aplikację.")
        }
        if (encrypted.iterations !in ACCEPTED_ITERATIONS) throw PlanFileException(BROKEN)
        val salt = bytes(encrypted.salt)
        val iv = bytes(encrypted.iv)
        val data = bytes(encrypted.data)
        if (salt.size !in 8..64 || iv.size != IV_BYTES || data.size > PlanFile.MAX_BYTES) throw PlanFileException(BROKEN)
        if (password.isNullOrEmpty()) throw PlanPasswordException(wrong = false)
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, aesKey(password, salt, encrypted.iterations), GCMParameterSpec(TAG_BITS, iv))
                doFinal(data)
            }
        } catch (e: GeneralSecurityException) {
            // Znacznik GCM nie pasuje: złe hasło albo zmieniony szyfrogram. Nie da się ich odróżnić i nie trzeba.
            throw PlanPasswordException(wrong = true)
        }
        return runCatching { json.decodeFromString(SignedPlan.serializer(), plain.toString(Charsets.UTF_8)) }
            .getOrElse { throw PlanFileException(BROKEN) }
    }

    /**
     * Klucz AES-256 z hasła: PBKDF2-HMAC-SHA256 z jednym blokiem wyniku (32 bajty), hasło w UTF-8. Liczone ręcznie
     * na `Mac`, bo dostawcy na Androidzie i w JDK różnie zamieniają znaki spoza ASCII na bajty.
     */
    internal fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        val u = mac.doFinal()
        val out = u.copyOf()
        repeat(iterations - 1) {
            mac.update(u)
            mac.doFinal(u, 0)
            for (i in out.indices) out[i] = (out[i].toInt() xor u[i].toInt()).toByte()
        }
        return out
    }

    private fun aesKey(password: String, salt: ByteArray, iterations: Int) = SecretKeySpec(pbkdf2(password, salt, iterations), "AES")

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun bytes(text: String): ByteArray = runCatching { Base64.getDecoder().decode(text) }.getOrElse { throw PlanFileException(BROKEN) }
}
