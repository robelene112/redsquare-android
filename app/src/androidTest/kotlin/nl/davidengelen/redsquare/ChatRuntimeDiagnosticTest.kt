package nl.davidengelen.redsquare

import android.app.ActivityManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Temporary process-only reduction of the intermittent API29 photo-history heap mismatch. */
class ChatRuntimeDiagnosticTest {
    @Test fun processHeapMatchesTheDeclaredDeviceMemoryClass() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev"&&Build.VERSION.SDK_INT==29&&
            Build.HARDWARE in listOf("ranchu","goldfish"))
        observeChatProcessRuntime("fresh-instrumentation")
        val declared=context.getSystemService(ActivityManager::class.java).memoryClass.toLong()*1024*1024
        assertEquals("Process heap differs from this disposable device's declared memory class",
            declared,Runtime.getRuntime().maxMemory())
    }
}
