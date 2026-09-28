package com.boykodmytr.gymtracker.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import java.io.File
import java.io.FileOutputStream

fun ComposeTestRule.waitForText(text: String, substring: Boolean = false, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitForNoText(text: String, timeoutMillis: Long = 5_000) {
    waitUntilDoesNotExist(hasText(text), timeoutMillis)
}

/**
 * Saves the main window as PNG into build/screenshots. Robolectric does not render dialog or
 * bottom-sheet windows into screenshots, so those are verified through semantics only.
 */
fun SemanticsNodeInteractionsProvider.screenshot(name: String) {
    val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
    val bitmap = onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
    FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/** Matches [text] only inside a dialog, e.g. a confirm button that repeats a toolbar label. */
fun inDialog(text: String): SemanticsMatcher = hasText(text) and hasAnyAncestor(isDialog())
