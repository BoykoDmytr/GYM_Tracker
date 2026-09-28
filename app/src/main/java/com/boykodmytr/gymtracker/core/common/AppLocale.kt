package com.boykodmytr.gymtracker.core.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import com.boykodmytr.gymtracker.ui.format.AppLocale

/**
 * The UI exists only in Ukrainian. Android picks plural forms (підхід / підходи / підходів) by the
 * *configuration* locale, so on an English phone Ukrainian strings would get English plural rules
 * ("20 підходу"). Pinning the configuration to Ukrainian keeps strings, plurals and formats consistent.
 * Remove this once the app ships other translations.
 */
@SuppressLint("AppBundleLocaleChanges") // only Ukrainian resources exist, nothing to download per language
fun Context.withAppLocale(): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(AppLocale)
    return createConfigurationContext(config)
}
