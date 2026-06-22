package com.dating.modules.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthFlowIntegrationTests {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `oauth login issues tokens, refresh rotates, and logout revokes session`() {
        val unique = UUID.randomUUID().toString()

        val login = restTemplate.postForEntity(
            "/auth/oauth/google",
            mapOf(
                "providerUserId" to "google-$unique",
                "providerEmail" to "flow-$unique@example.com",
                "deviceId" to "device-$unique",
            ),
            Map::class.java,
        )

        assertEquals(HttpStatus.OK, login.statusCode)
        val loginData = map(data(login.body))
        val loginTokens = map(loginData["tokens"])
        val user = map(loginData["user"])
        val firstAccessToken = string(loginTokens["accessToken"])
        val firstRefreshToken = string(loginTokens["refreshToken"])
        assertNotNull(firstAccessToken)
        assertNotNull(firstRefreshToken)
        assertEquals("PENDING_VERIFICATION", user["status"])

        val verify = restTemplate.postForEntity(
            "/auth/identity/verify",
            authorized(
                firstAccessToken,
                mapOf(
                    "ciHash" to "ci-$unique",
                    "diHash" to "di-$unique",
                    "verifiedBirthDate" to LocalDate.of(1995, 1, 1).toString(),
                    "verifiedGender" to "MALE",
                    "carrier" to "TEST",
                ),
            ),
            Map::class.java,
        )
        assertEquals(HttpStatus.OK, verify.statusCode)
        assertEquals("PENDING_AGREEMENTS", map(data(verify.body))["status"])

        val agreements = restTemplate.postForEntity(
            "/auth/agreements",
            authorized(
                firstAccessToken,
                mapOf(
                    "agreements" to listOf(
                        mapOf("agreementType" to "TERMS_OF_SERVICE", "version" to "1.0", "agreed" to true),
                        mapOf("agreementType" to "PRIVACY_POLICY", "version" to "1.0", "agreed" to true),
                        mapOf("agreementType" to "LOCATION_BASED", "version" to "1.0", "agreed" to true),
                    ),
                ),
            ),
            Map::class.java,
        )
        assertEquals(HttpStatus.OK, agreements.statusCode)
        assertEquals("ACTIVE", map(data(agreements.body))["status"])

        val sessions = restTemplate.exchange(
            "/auth/sessions",
            HttpMethod.GET,
            authorized(firstAccessToken, null),
            Map::class.java,
        )
        assertEquals(HttpStatus.OK, sessions.statusCode)
        val sessionItems = data(sessions.body) as List<*>
        assertEquals(1, sessionItems.size)
        assertEquals("device-$unique", map(sessionItems[0])["deviceId"])

        val refresh = restTemplate.postForEntity(
            "/auth/token/refresh",
            mapOf("refreshToken" to firstRefreshToken),
            Map::class.java,
        )
        assertEquals(HttpStatus.OK, refresh.statusCode)
        val refreshedTokens = map(map(data(refresh.body))["tokens"])
        val secondAccessToken = string(refreshedTokens["accessToken"])
        val secondRefreshToken = string(refreshedTokens["refreshToken"])
        assertNotEquals(firstAccessToken, secondAccessToken)
        assertNotEquals(firstRefreshToken, secondRefreshToken)

        val reusedRefresh = restTemplate.postForEntity(
            "/auth/token/refresh",
            mapOf("refreshToken" to firstRefreshToken),
            Map::class.java,
        )
        assertEquals(HttpStatus.UNAUTHORIZED, reusedRefresh.statusCode)

        val afterReuse = restTemplate.exchange(
            "/auth/sessions",
            HttpMethod.GET,
            authorized(secondAccessToken, null),
            Map::class.java,
        )
        assertEquals(HttpStatus.UNAUTHORIZED, afterReuse.statusCode)

        val relogin = restTemplate.postForEntity(
            "/auth/oauth/google",
            mapOf(
                "providerUserId" to "google-$unique",
                "providerEmail" to "flow-$unique@example.com",
                "deviceId" to "device-$unique",
            ),
            Map::class.java,
        )
        assertEquals(HttpStatus.OK, relogin.statusCode)
        val reloginTokens = map(map(data(relogin.body))["tokens"])
        val thirdAccessToken = string(reloginTokens["accessToken"])

        val logout = restTemplate.postForEntity(
            "/auth/logout",
            authorized(thirdAccessToken, emptyMap<String, Any>()),
            Map::class.java,
        )
        assertEquals(HttpStatus.OK, logout.statusCode)

        val afterLogout = restTemplate.exchange(
            "/auth/sessions",
            HttpMethod.GET,
            authorized(thirdAccessToken, null),
            Map::class.java,
        )
        assertEquals(HttpStatus.UNAUTHORIZED, afterLogout.statusCode)
    }

    private fun authorized(accessToken: String, body: Any?): HttpEntity<Any> {
        val headers = HttpHeaders()
        headers.setBearerAuth(accessToken)
        return HttpEntity(body ?: emptyMap<String, Any>(), headers)
    }

    private fun data(body: Map<*, *>?): Any =
        requireNotNull(body?.get("data")) { "Missing response data: $body" }

    private fun map(value: Any?): Map<*, *> =
        value as? Map<*, *> ?: error("Expected map but got $value")

    private fun number(value: Any?): Number =
        value as? Number ?: error("Expected number but got $value")

    private fun string(value: Any?): String =
        value as? String ?: error("Expected string but got $value")
}
