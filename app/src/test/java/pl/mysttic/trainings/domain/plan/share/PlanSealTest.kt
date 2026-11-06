package pl.mysttic.trainings.domain.plan.share

import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.mysttic.trainings.domain.engine.TestFixtures
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Podpis i hasło pliku planu (D-58): autor i treść nie zmieniają się po podpisaniu, hasło zamyka treść. */
class PlanSealTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /** Klucz programowy w miejsce Android Keystore: ten sam algorytm i ta sama krzywa. */
    private class SoftwareSigner : PlanSigner {
        private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        override val publicKey: ByteArray = pair.public.encoded
        override fun sign(data: ByteArray): ByteArray = Signature.getInstance(PlanSeal.ALGORITHM).run {
            initSign(pair.private)
            update(data)
            sign()
        }
    }

    private val marta = SoftwareSigner()
    private val ola = SoftwareSigner()

    private fun file(author: String = "Marta", signer: String = "Marta") = PlanFile(
        format = PlanFile.FORMAT,
        formatVersion = PlanFile.VERSION,
        appVersion = "1.2.0",
        seedVersion = 11,
        exportedAt = 99,
        signer = signer,
        plan = SharedPlan(
            uid = "abc-123",
            name = "Siła w lesie",
            author = author,
            days = listOf(SharedDay(1, "w1"), SharedDay(2, null)),
            workouts = listOf(SharedWorkout("w1", "Góra", blocks = listOf(SharedBlock("SETS", items = listOf(SharedItem("pushups", "REPS", 10)))))),
        ),
    )

    private fun envelope(text: String) = json.decodeFromString(PlanEnvelope.serializer(), text)

    private fun text(envelope: PlanEnvelope) = json.encodeToString(PlanEnvelope.serializer(), envelope)

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun payloadOf(text: String) = Base64.getDecoder().decode(envelope(text).signed!!.payload).decodeToString()

    private fun failure(block: () -> Unit): Throwable? = runCatching(block).exceptionOrNull()

    @Test
    fun `podpisany plik wraca w calosci z odciskiem klucza autora`() {
        val text = PlanSeal.seal(file(), json, marta)
        val opened = PlanSeal.open(text, json)

        assertEquals(file(), opened.file)
        assertTrue(opened.seal.signed)
        assertFalse(opened.seal.passwordProtected)
        assertEquals(PlanSeal.fingerprint(marta.publicKey), opened.seal.fingerprint)
        // Koperta ma numer 2, więc aplikacja sprzed D-58 powie „zaktualizuj aplikację”, a nie „to nie plan”.
        assertTrue(text.contains("\"formatVersion\": 2"))
        assertTrue(text.contains("\"format\": \"mysttic-trainings-plan\""))
        // Treść jedzie w Base64 pod podpisem: autora nie da się podmienić w notatniku.
        assertFalse(text.contains("Marta"))
        // Po walidacji plan podaje autora i podpisującego.
        val imported = PlanFileCodec.validate(opened.file, TestFixtures.exercises)
        assertEquals("Marta", imported.author)
        assertEquals("Marta", imported.signer)
    }

    @Test
    fun `zmiana autora albo tresci po podpisaniu blokuje odczyt`() {
        val text = PlanSeal.seal(file(), json, marta)
        val original = envelope(text)
        val payload = payloadOf(text)
        listOf(
            payload.replace("\"author\": \"Marta\"", "\"author\": \"Ola\""),
            payload.replace("\"signer\": \"Marta\"", "\"signer\": \"Ola\""),
            payload.replace("\"value\": 10", "\"value\": 100"),
        ).forEach { forged ->
            assertNotEquals("podmiana musi coś zmienić", payload, forged)
            val tampered = original.copy(signed = original.signed!!.copy(payload = b64(forged.toByteArray())))
            val error = failure { PlanSeal.open(text(tampered), json) }
            assertTrue("$error", error is PlanFileException && error.message!!.contains("po podpisaniu"))
        }
    }

    @Test
    fun `cudzy klucz pod cudzym podpisem nie przechodzi, a wlasny podpis to inny odcisk`() {
        val text = PlanSeal.seal(file(), json, marta)
        val original = envelope(text)

        // Sama podmiana klucza na klucz Oli: podpis Marty do niego nie pasuje.
        val swapped = original.copy(signed = original.signed!!.copy(publicKey = b64(ola.publicKey)))
        assertTrue(failure { PlanSeal.open(text(swapped), json) } is PlanFileException)

        // Ola podpisuje zmienioną treść swoim kluczem: plik przejdzie, ale z jej odciskiem, nie Marty.
        val forged = payloadOf(text).replace("\"author\": \"Marta\"", "\"author\": \"Ola\"").toByteArray()
        val resigned = original.copy(
            signed = original.signed!!.copy(payload = b64(forged), publicKey = b64(ola.publicKey), signature = b64(ola.sign(forged))),
        )
        val opened = PlanSeal.open(text(resigned), json)
        assertEquals(PlanSeal.fingerprint(ola.publicKey), opened.seal.fingerprint)
        assertNotEquals(PlanSeal.fingerprint(marta.publicKey), opened.seal.fingerprint)
    }

    @Test
    fun `haslo zamyka podpisana tresc`() {
        val text = PlanSeal.seal(file(), json, marta, password = "lesna-polana")
        assertFalse("podpis i treść są pod szyfrem", text.contains("payload") || text.contains("signature"))

        val none = failure { PlanSeal.open(text, json) }
        assertTrue(none is PlanPasswordException && !none.wrong)
        val wrong = failure { PlanSeal.open(text, json, password = "lesna-polanA") }
        assertTrue(wrong is PlanPasswordException && wrong.wrong)

        val opened = PlanSeal.open(text, json, password = "lesna-polana")
        assertEquals(file(), opened.file)
        assertTrue(opened.seal.signed && opened.seal.passwordProtected)
        assertEquals(PlanSeal.fingerprint(marta.publicKey), opened.seal.fingerprint)

        // Polskie znaki w haśle idą w UTF-8, tak samo przy zamykaniu i otwieraniu.
        val polish = PlanSeal.seal(file(), json, marta, password = "Żółw-ęą!")
        assertEquals(file(), PlanSeal.open(polish, json, password = "Żółw-ęą!").file)
        assertTrue(failure { PlanSeal.open(polish, json, password = "Zolw-ea!") } is PlanPasswordException)
        // Za krótkie hasło nie zamknie pliku.
        assertTrue(failure { PlanSeal.seal(file(), json, marta, password = "12345") } is IllegalArgumentException)
    }

    @Test
    fun `klucz z hasla to PBKDF2-HMAC-SHA256 jak w JDK`() {
        val salt = ByteArray(16) { it.toByte() }
        val jdk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("haslo-123".toCharArray(), salt, 1000, 256)).encoded
        assertArrayEquals(jdk, PlanSeal.pbkdf2("haslo-123", salt, 1000))
    }

    @Test
    fun `plik bez koperty wchodzi jako niepodpisany`() {
        val opened = PlanSeal.open(PlanFileCodec.encode(file(), json), json)
        assertEquals(file(), opened.file)
        assertFalse(opened.seal.signed)
        assertNull(opened.seal.fingerprint)
    }

    @Test
    fun `wzorcowe pliki formatu 1 i 2 dalej sie wczytuja`() {
        // Zamrożona koperta (umowa na lata): jeśli ten test padnie, wysłane już pliki przestaną się otwierać.
        fun resource(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes().decodeToString()

        val signed = PlanSeal.open(resource("plan-podpisany.plan.json"), json)
        assertEquals("Plan podpisany wzorcowy", signed.file.plan.name)
        assertEquals("Autor", signed.file.signer)
        assertTrue(signed.seal.signed)
        assertEquals(FIXTURE_KEY, signed.seal.fingerprint)
        assertEquals(12.5, PlanFileCodec.validate(signed.file, TestFixtures.exercises).workouts.single().blocks.single().items.first().weightKg)

        val locked = PlanSeal.open(resource("plan-z-haslem.plan.json"), json, password = "lesna-polana")
        assertEquals("Plan z hasłem wzorcowy", locked.file.plan.name)
        assertTrue(locked.seal.signed && locked.seal.passwordProtected)
        assertEquals(FIXTURE_KEY, locked.seal.fingerprint)

        val legacy = PlanSeal.open(resource("plan-wzorcowy.plan.json"), json)
        assertEquals("Plan wzorcowy", legacy.file.plan.name)
        assertFalse(legacy.seal.signed)
    }

    @Test
    fun `zepsuta albo obca koperta daje komunikat, nie wywrotke`() {
        fun message(text: String) = failure { PlanSeal.open(text, json) }.let { e ->
            assertTrue("$e", e is PlanFileException)
            e!!.message.orEmpty()
        }
        val head = """"format":"mysttic-trainings-plan","formatVersion":2"""
        assertTrue(message("""{$head,"signed":{"algorithm":"SHA256withECDSA","publicKey":"%%%","signature":"x","payload":"y"}}""").contains("uszkodzony"))
        assertTrue(message("""{$head,"signed":{"algorithm":"SHA256withECDSA","publicKey":"AAAA","signature":"AAAA","payload":"AAAA"}}""").contains("po podpisaniu"))
        assertTrue(message("""{$head,"signed":{"algorithm":"SHA512withRSA","publicKey":"AAAA","signature":"AAAA","payload":"AAAA"}}""").contains("Zaktualizuj"))
        assertTrue(message("""{"format":"mysttic-trainings-plan","formatVersion":3,"signed":{}}""").contains("nowszej"))
        assertTrue(message("""{"format":"inny","formatVersion":2,"signed":{}}""").contains("tej aplikacji"))
        val locked = """{$head,"encrypted":{"cipher":"AES-256-GCM","kdf":"PBKDF2WithHmacSHA256","iterations":%d,"salt":"AAAAAAAAAAAAAAAAAAAAAA==","iv":"AAAAAAAAAAAAAAAA","data":"AAAA"}}"""
        assertTrue(message(locked.format(5)).contains("uszkodzony"))
        assertTrue(message(locked.format(10_000_000)).contains("uszkodzony"))
        assertTrue(message(locked.replace("AES-256-GCM", "ChaCha20").format(210_000)).contains("Zaktualizuj"))
    }

    @Test
    fun `odcisk to piec grup po cztery znaki`() {
        val fingerprint = PlanSeal.fingerprint(marta.publicKey)
        assertTrue(fingerprint, Regex("[0-9A-F]{4}( [0-9A-F]{4}){4}").matches(fingerprint))
        assertEquals(fingerprint, PlanSeal.fingerprint(marta.publicKey.copyOf()))
        assertNotEquals(fingerprint, PlanSeal.fingerprint(ola.publicKey))
    }

    private companion object {
        /** Odcisk klucza, którym podpisano pliki wzorcowe formatu 2. */
        const val FIXTURE_KEY = "3616 6E0E 2B17 DD34 19FE"
    }
}
