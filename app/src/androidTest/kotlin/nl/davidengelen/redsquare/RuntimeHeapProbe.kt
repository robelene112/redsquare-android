package nl.davidengelen.redsquare

import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.os.Bundle
import android.os.Build
import android.os.Process
import org.json.JSONObject

/** Provisioning observation only: no JUnit cases, feature actions or heap changes. */
class RuntimeHeapProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            check(targetContext.packageName == "nl.davidengelen.redsquare.dev")
            check(Build.VERSION.SDK_INT == 29 && Build.HARDWARE in listOf("ranchu", "goldfish"))
            val manager = targetContext.getSystemService(ActivityManager::class.java)
            result.putString("heapProbe", JSONObject().apply {
                put("package", targetContext.packageName)
                put("sdk", Build.VERSION.SDK_INT)
                put("hardware", Build.HARDWARE)
                put("pid", Process.myPid())
                put("parentPid", android.system.Os.getppid())
                put("is64Bit", Process.is64Bit())
                put("applicationFlags", targetContext.applicationInfo.flags)
                put("actualBytes", Runtime.getRuntime().maxMemory())
                put("declaredBytes", manager.memoryClass.toLong() * 1024 * 1024)
            }.toString())
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("probeError", failure.toString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
