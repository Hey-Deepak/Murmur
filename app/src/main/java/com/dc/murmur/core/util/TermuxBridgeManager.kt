package com.dc.murmur.core.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast

class TermuxBridgeManager(private val context: Context) {

    companion object {
        private const val TAG = "TermuxBridgeManager"
        private const val TERMUX_PACKAGE = "com.termux"

        // F-Droid Termux: RunCommandService (requires com.termux.permission.RUN_COMMAND)
        private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"

        // Google Play / older Termux: TermuxService (requires signature-level TERMUX_INTERNAL)
        private const val TERMUX_SERVICE = "com.termux.app.TermuxService"
        private const val ACTION_EXECUTE = "com.termux.service_execute"
        private const val EXTRA_COMMAND = "com.termux.execute.FILE_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.execute.arguments"
        private const val EXTRA_BACKGROUND = "com.termux.execute.background"

        private const val BRIDGE_SCRIPT = "/data/data/com.termux/files/home/murmur/start-bridge.sh"
        private const val STOP_SCRIPT = "/data/data/com.termux/files/home/murmur/stop-bridge.sh"
    }

    enum class Result {
        COMMAND_SENT,
        TERMUX_OPENED,
        FAILED
    }

    fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun hasRunCommandService(): Boolean {
        return try {
            context.packageManager.getServiceInfo(
                ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE), 0
            )
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun startBridge(port: Int): Result {
        Log.w(TAG, "Requesting bridge start on port $port")

        // Strategy 1: F-Droid Termux RunCommandService
        if (hasRunCommandService()) {
            Log.w(TAG, "RunCommandService found, using RUN_COMMAND intent")
            val intent = Intent(ACTION_RUN_COMMAND).apply {
                component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
                putExtra(RUN_COMMAND_PATH, BRIDGE_SCRIPT)
                putExtra(RUN_COMMAND_ARGUMENTS, arrayOf(port.toString()))
                putExtra(RUN_COMMAND_BACKGROUND, true)
            }
            if (trySendService(intent)) return Result.COMMAND_SENT
        }

        // Strategy 2: TermuxService direct (requires TERMUX_INTERNAL — unlikely to work)
        Log.w(TAG, "Trying TermuxService with service_execute action")
        val directIntent = Intent(ACTION_EXECUTE).apply {
            component = ComponentName(TERMUX_PACKAGE, TERMUX_SERVICE)
            putExtra(EXTRA_COMMAND, BRIDGE_SCRIPT)
            putExtra(EXTRA_ARGUMENTS, arrayOf(port.toString()))
            putExtra(EXTRA_BACKGROUND, true)
        }
        if (trySendService(directIntent)) return Result.COMMAND_SENT

        // Strategy 3: Open Termux Activity (user must start manually)
        Log.w(TAG, "Falling back to opening Termux Activity")
        return openTermuxWithToast(
            "Run in Termux: ~/murmur/start-bridge.sh $port"
        )
    }

    fun stopBridge(): Result {
        Log.w(TAG, "Requesting bridge stop")

        if (hasRunCommandService()) {
            val intent = Intent(ACTION_RUN_COMMAND).apply {
                component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
                putExtra(RUN_COMMAND_PATH, STOP_SCRIPT)
                putExtra(RUN_COMMAND_ARGUMENTS, emptyArray<String>())
                putExtra(RUN_COMMAND_BACKGROUND, true)
            }
            if (trySendService(intent)) return Result.COMMAND_SENT
        }

        val directIntent = Intent(ACTION_EXECUTE).apply {
            component = ComponentName(TERMUX_PACKAGE, TERMUX_SERVICE)
            putExtra(EXTRA_COMMAND, STOP_SCRIPT)
            putExtra(EXTRA_ARGUMENTS, emptyArray<String>())
            putExtra(EXTRA_BACKGROUND, true)
        }
        if (trySendService(directIntent)) return Result.COMMAND_SENT

        return openTermuxWithToast("Run in Termux: ~/murmur/stop-bridge.sh")
    }

    fun installScripts(): Result {
        Log.w(TAG, "Installing bridge scripts to Termux")
        val script = buildString {
            appendLine("mkdir -p \$HOME/.termux")
            appendLine("if ! grep -q 'allow-external-apps' \$HOME/.termux/termux.properties 2>/dev/null; then")
            appendLine("    echo 'allow-external-apps = true' >> \$HOME/.termux/termux.properties")
            appendLine("fi")
            appendLine("mkdir -p \$HOME/murmur")

            // Write start-bridge.sh
            appendLine("cat > \$HOME/murmur/start-bridge.sh << 'STARTEOF'")
            appendLine("#!/data/data/com.termux/files/usr/bin/bash")
            appendLine("set -euo pipefail")
            appendLine("PORT=\"\${1:-8735}\"")
            appendLine("WORK_DIR=\"\$HOME/murmur\"")
            appendLine("JAR=\"\$WORK_DIR/claude-bridge-all.jar\"")
            appendLine("PID_FILE=\"\$WORK_DIR/bridge.pid\"")
            appendLine("LOG_FILE=\"\$WORK_DIR/bridge.log\"")
            appendLine("mkdir -p \"\$WORK_DIR\"")
            appendLine("if [ ! -f \"\$JAR\" ]; then")
            appendLine("    echo \"ERROR: JAR not found at \$JAR\" >&2")
            appendLine("    exit 1")
            appendLine("fi")
            appendLine("if [ -f \"\$PID_FILE\" ]; then")
            appendLine("    OLD_PID=\$(cat \"\$PID_FILE\")")
            appendLine("    if kill -0 \"\$OLD_PID\" 2>/dev/null; then")
            appendLine("        echo \"Bridge already running (PID \$OLD_PID)\"")
            appendLine("        exit 0")
            appendLine("    else")
            appendLine("        rm -f \"\$PID_FILE\"")
            appendLine("    fi")
            appendLine("fi")
            appendLine("termux-wake-lock 2>/dev/null || true")
            appendLine("nohup java -jar \"\$JAR\" \"\$PORT\" > \"\$LOG_FILE\" 2>&1 &")
            appendLine("BRIDGE_PID=\$!")
            appendLine("echo \"\$BRIDGE_PID\" > \"\$PID_FILE\"")
            appendLine("sleep 2")
            appendLine("if kill -0 \"\$BRIDGE_PID\" 2>/dev/null; then")
            appendLine("    echo \"Bridge started (PID \$BRIDGE_PID, port \$PORT)\"")
            appendLine("    exit 0")
            appendLine("else")
            appendLine("    echo \"ERROR: Bridge died after start\" >&2")
            appendLine("    rm -f \"\$PID_FILE\"")
            appendLine("    exit 1")
            appendLine("fi")
            appendLine("STARTEOF")

            // Write stop-bridge.sh
            appendLine("cat > \$HOME/murmur/stop-bridge.sh << 'STOPEOF'")
            appendLine("#!/data/data/com.termux/files/usr/bin/bash")
            appendLine("set -euo pipefail")
            appendLine("WORK_DIR=\"\$HOME/murmur\"")
            appendLine("PID_FILE=\"\$WORK_DIR/bridge.pid\"")
            appendLine("if [ ! -f \"\$PID_FILE\" ]; then")
            appendLine("    echo \"Bridge is not running\"")
            appendLine("    exit 0")
            appendLine("fi")
            appendLine("PID=\$(cat \"\$PID_FILE\")")
            appendLine("if ! kill -0 \"\$PID\" 2>/dev/null; then")
            appendLine("    rm -f \"\$PID_FILE\"")
            appendLine("    exit 0")
            appendLine("fi")
            appendLine("kill \"\$PID\" 2>/dev/null || true")
            appendLine("for i in \$(seq 1 10); do")
            appendLine("    if ! kill -0 \"\$PID\" 2>/dev/null; then")
            appendLine("        rm -f \"\$PID_FILE\"")
            appendLine("        echo \"Bridge stopped\"")
            appendLine("        exit 0")
            appendLine("    fi")
            appendLine("    sleep 0.5")
            appendLine("done")
            appendLine("kill -9 \"\$PID\" 2>/dev/null || true")
            appendLine("sleep 0.5")
            appendLine("rm -f \"\$PID_FILE\"")
            appendLine("echo \"Bridge killed\"")
            appendLine("exit 0")
            appendLine("STOPEOF")

            appendLine("chmod +x \$HOME/murmur/start-bridge.sh")
            appendLine("chmod +x \$HOME/murmur/stop-bridge.sh")
            appendLine("echo \"Scripts installed to \$HOME/murmur/\"")
        }

        if (hasRunCommandService()) {
            val intent = Intent(ACTION_RUN_COMMAND).apply {
                component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
                putExtra(RUN_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(RUN_COMMAND_ARGUMENTS, arrayOf("-c", script))
                putExtra(RUN_COMMAND_BACKGROUND, true)
            }
            if (trySendService(intent)) return Result.COMMAND_SENT
        }

        val directIntent = Intent(ACTION_EXECUTE).apply {
            component = ComponentName(TERMUX_PACKAGE, TERMUX_SERVICE)
            putExtra(EXTRA_COMMAND, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", script))
            putExtra(EXTRA_BACKGROUND, true)
        }
        if (trySendService(directIntent)) return Result.COMMAND_SENT

        return openTermuxWithToast(
            "Run: bash /sdcard/Download/setup-bridge.sh"
        )
    }

    private fun trySendService(intent: Intent): Boolean {
        try {
            context.startForegroundService(intent)
            Log.w(TAG, "Sent via startForegroundService")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "startForegroundService failed: ${e.message}")
        }
        try {
            context.startService(intent)
            Log.w(TAG, "Sent via startService")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "startService also failed: ${e.message}")
        }
        return false
    }

    private fun openTermuxWithToast(message: String): Result {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                return Result.TERMUX_OPENED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not open Termux: ${e.message}")
        }
        return Result.FAILED
    }
}
