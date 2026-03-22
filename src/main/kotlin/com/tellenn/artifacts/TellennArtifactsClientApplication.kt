package com.tellenn.artifacts

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@Suppress("unused")
@EnableScheduling
@SpringBootApplication
class TellennArtifactsClientApplication


object AppConfig {
	var maxLevel: Int = 0
}

fun main(args: Array<String>) {
	runApplication<TellennArtifactsClientApplication>(*args)
}
