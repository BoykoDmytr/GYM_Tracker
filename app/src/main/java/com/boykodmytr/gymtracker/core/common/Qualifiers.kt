package com.boykodmytr.gymtracker.core.common

import javax.inject.Qualifier

/** Coroutine scope that lives as long as the process; for work that must outlive a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
