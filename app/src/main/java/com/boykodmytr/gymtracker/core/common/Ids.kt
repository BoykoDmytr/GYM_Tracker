package com.boykodmytr.gymtracker.core.common

import java.util.UUID

/**
 * Client-generated UUIDs instead of auto-increment ids: records created on different devices never
 * collide, which keeps the door open for cloud sync and backup merge without rewriting keys.
 */
fun newId(): String = UUID.randomUUID().toString()
