package com.dating

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class DatingApplication

fun main(args: Array<String>) {
    runApplication<DatingApplication>(*args)
}
