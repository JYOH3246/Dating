package com.dating.platform.security

import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class RefreshTokenHasher(
    private val properties: AuthTokenProperties,
) {
    fun hash(rawRefreshToken: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val key = SecretKeySpec(properties.refreshTokenPepper.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(key)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(rawRefreshToken.toByteArray(Charsets.UTF_8)))
    }
}
