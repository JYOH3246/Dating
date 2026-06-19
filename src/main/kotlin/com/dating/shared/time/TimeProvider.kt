package com.dating.shared.time

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

interface TimeProvider {
    fun now(): Instant
    fun today(): LocalDate
}

@Component
class SystemTimeProvider : TimeProvider {
    private val clock: Clock = Clock.systemUTC()

    override fun now(): Instant = Instant.now(clock)

    override fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneOffset.UTC))
}
