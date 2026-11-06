package pl.mysttic.trainings.data.share

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import pl.mysttic.trainings.domain.plan.share.PlanSeal
import pl.mysttic.trainings.domain.plan.share.PlanSigner
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Klucz podpisu planów (D-58) w Android Keystore: powstaje przy pierwszym użyciu i nigdy nie wychodzi z telefonu,
 * więc nie ma go też w kopii zapasowej. Nowy telefon albo ponowna instalacja to nowy klucz i nowy odcisk; pliki
 * podpisane starym dalej się sprawdzają, bo każdy niesie swój klucz publiczny.
 */
@Singleton
class PlanSigningKey @Inject constructor() : PlanSigner {

    override val publicKey: ByteArray get() = entry().certificate.publicKey.encoded

    override fun sign(data: ByteArray): ByteArray = Signature.getInstance(PlanSeal.ALGORITHM).run {
        initSign(entry().privateKey)
        update(data)
        sign()
    }

    /** Odcisk klucza tego telefonu do „O mnie” i ekranu wysyłki; pierwsze wywołanie tworzy klucz (poza wątkiem UI). */
    fun fingerprint(): String = PlanSeal.fingerprint(publicKey)

    @Synchronized
    private fun entry(): KeyStore.PrivateKeyEntry {
        (store().getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry)?.let { return it }
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply {
            initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
        }.generateKeyPair()
        return store().getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
    }

    private fun store(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "plan-signing"
    }
}
