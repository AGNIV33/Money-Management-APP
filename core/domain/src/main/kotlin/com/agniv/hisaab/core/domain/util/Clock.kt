package com.agniv.hisaab.core.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** Injected so tests control "now" and "today". */
interface TimeProvider {
    fun now(): Instant

    fun today(): LocalDate
}

class SystemTimeProvider
    @Inject
    constructor() : TimeProvider {
        override fun now(): Instant = Instant.now()

        override fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())
    }

/** UUIDs are made on the phone so rows can be created offline (ARCHITECTURE.md §4.1). */
fun interface IdGenerator {
    fun newId(): String
}

class UuidGenerator
    @Inject
    constructor() : IdGenerator {
        override fun newId(): String = UUID.randomUUID().toString()
    }
