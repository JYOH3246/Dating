package com.dating.platform.security

import jakarta.annotation.PostConstruct
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.context.annotation.Configuration
import java.security.Security

@Configuration
class CryptoProviderConfig {

    @PostConstruct
    fun registerBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }
}
