package nl.davidengelen.redsquare

import android.content.Intent
import android.app.Activity
import android.app.Instrumentation
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.net.InetAddress
import java.io.File
import mockwebserver3.MockWebServer
import mockwebserver3.MockResponse
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import org.hamcrest.CoreMatchers.allOf

class PreviewUpdateTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aNewerReviewBuildOffersTheReviewPage() {
        val server = MockWebServer()
        val body = """{"applicationId":"nl.davidengelen.redsquare","versionCode":2000000000,"versionName":"future-preview"}"""
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse =
                MockResponse.Builder().body(body).addHeader("Content-Type", "application/json").build()
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val intent = Intent(context, MainActivity::class.java).putExtra("feedDisabled", true).putExtra(
                "reviewManifestUrl", "http://127.0.0.1:${server.port}/latest.json")
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    ui.waitUntil(10_000) {
                        ui.onAllNodes(hasText("Preview update available")).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNodeWithText("Preview update available").assertIsDisplayed()
                    ui.onNodeWithText("View update").assertIsDisplayed()
                    ui.onNodeWithText("View update").performClick()
                    Intents.intended(allOf(hasAction(Intent.ACTION_VIEW), hasData("https://davidengelen.nl/redsquare/")))
                } finally { captureFeedEvidence("preview-update-evidence/screen.png") }
            }
        } finally {
            Intents.release()
            try {
                val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "preview-update-evidence").apply { mkdirs() }
                File(directory, "requests.txt").writeText("HTTP manifest requests: ${server.requestCount}\n")
            } catch (_: Exception) { /* Evidence must not hide the test result. */ }
            server.close()
        }
    }
}
