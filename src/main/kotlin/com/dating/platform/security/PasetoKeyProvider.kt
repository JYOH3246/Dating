package com.dating.platform.security

import com.dating.modules.identity.domain.PasetoKeyStatus
import com.dating.modules.identity.infrastructure.jpa.PasetoKeyJpaEntity
import com.dating.modules.identity.infrastructure.jpa.PasetoKeyJpaRepository
import com.dating.shared.exception.DatingException
import com.dating.shared.exception.ErrorCode
import org.paseto4j.commons.PrivateKey
import org.paseto4j.commons.PublicKey
import org.paseto4j.commons.Version
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class ActivePasetoKey(
    val keyId: String,
    val privateKey: PrivateKey,
    val publicKey: PublicKey,
)

@Component
class PasetoKeyProvider(
    private val properties: AuthTokenProperties,
    private val keyRepository: PasetoKeyJpaRepository,
) {
    @Transactional
    fun activeKey(): ActivePasetoKey {
        val persisted = keyRepository.findFirstByStatus(PasetoKeyStatus.ACTIVE)
            ?: keyRepository.save(createInitialKey())

        val privateKey = readPrivateKey(persisted.secretRef)
        val publicKey = readPublicKey(persisted.publicKey)
        return ActivePasetoKey(
            keyId = persisted.keyId,
            privateKey = PrivateKey(privateKey, Version.V4),
            publicKey = PublicKey(publicKey, Version.V4),
        )
    }

    private fun createInitialKey(): PasetoKeyJpaEntity {
        val configuredPrivate = properties.paseto.privateKeyBase64.trim()
        val configuredPublic = properties.paseto.publicKeyBase64.trim()

        if (configuredPrivate.isNotEmpty() && configuredPublic.isNotEmpty()) {
            return PasetoKeyJpaEntity(
                keyId = properties.paseto.keyId,
                publicKey = configuredPublic,
                secretRef = "inline:$configuredPrivate",
                status = PasetoKeyStatus.ACTIVE,
            )
        }

        if (!properties.paseto.allowDevGeneratedKey) {
            throw DatingException(ErrorCode.INTERNAL_ERROR, "PASETO key pair is not configured.")
        }

        val generator = KeyPairGenerator.getInstance("Ed25519")
        val keyPair = generator.generateKeyPair()
        val privateKey = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        return PasetoKeyJpaEntity(
            keyId = properties.paseto.keyId,
            publicKey = publicKey,
            secretRef = "inline:$privateKey",
            status = PasetoKeyStatus.ACTIVE,
        )
    }

    private fun readPrivateKey(secretRef: String): java.security.PrivateKey {
        if (!secretRef.startsWith(INLINE_PREFIX)) {
            throw DatingException(ErrorCode.INTERNAL_ERROR, "Only inline PASETO private keys are wired for this stage.")
        }
        val encoded = Base64.getDecoder().decode(secretRef.removePrefix(INLINE_PREFIX))
        return KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(encoded))
    }

    private fun readPublicKey(publicKeyBase64: String): java.security.PublicKey {
        val encoded = Base64.getDecoder().decode(publicKeyBase64)
        return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(encoded))
    }

    private companion object {
        const val INLINE_PREFIX = "inline:"
    }
}
