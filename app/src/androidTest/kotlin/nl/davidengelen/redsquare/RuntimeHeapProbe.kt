package nl.davidengelen.redsquare

import android.app.ActivityManager
import android.os.Bundle
import android.os.Build
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.json.JSONObject

/** Separate provisioning observation, before all acceptance classes; no feature actions or heap changes. */
class RuntimeHeapProbe {
    @Test fun observesActualTargetProcessHeap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(Build.VERSION.SDK_INT == 29 && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val manager = context.getSystemService(ActivityManager::class.java)
        instrumentation.sendStatus(0, Bundle().apply {
            putString("heapProbe", JSONObject().apply {
                put("package", context.packageName)
                put("sdk", Build.VERSION.SDK_INT)
                put("hardware", Build.HARDWARE)
                put("pid", Process.myPid())
                put("parentPid", android.system.Os.getppid())
                put("is64Bit", Process.is64Bit())
                put("applicationFlags", context.applicationInfo.flags)
                put("actualBytes", Runtime.getRuntime().maxMemory())
                put("declaredBytes", manager.memoryClass.toLong() * 1024 * 1024)
            }.toString())
        })
    }
}
