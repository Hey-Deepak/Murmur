package com.dc.murmur.core.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

class TermuxBridgeManager(private val context: Context) {

    companion object {
        private const val TAG = "TermuxBridgeManager"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val EXTRA_COMMAND = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    }

    fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun startBridge(port: Int) {
        Log.d(TAG, "Requesting bridge start on port $port")
        val intent = buildRunCommandIntent(
            command = "/data/data/com.termux/files/home/murmur/start-bridge.sh",
            arguments = arrayOf(port.toString())
        )
        context.startService(intent)
    }

    fun stopBridge() {
        Log.d(TAG, "Requesting bridge stop")
        val intent = buildRunCommandIntent(
            command = "/data/data/com.termux/files/home/murmur/stop-bridge.sh",
            arguments = emptyArray()
        )
        context.startService(intent)
    }

    fun installScripts() {
        Log.d(TAG, "Installing bridge scripts to Termux")
        val script = buildString {
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
            appendLine("nohup java -jar \"\$JAR\" -port=\"\$PORT\" > \"\$LOG_FILE\" 2>&1 &")
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

            // Make executable
            appendLine("chmod +x \$HOME/murmur/start-bridge.sh")
            appendLine("chmod +x \$HOME/murmur/stop-bridge.sh")
            appendLine("echo \"Scripts installed to \$HOME/murmur/\"")
        }

        val intent = buildRunCommandIntent(
            command = "/data/data/com.termux/files/usr/bin/bash",
            arguments = arrayOf("-c", script)
        )
        context.startService(intent)
    }

    private fun buildRunCommandIntent(command: String, arguments: Array<String>): Intent {
        return Intent(ACTION_RUN_COMMAND).apply {
            component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND, command)
            putExtra(EXTRA_ARGUMENTS, arguments)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_SESSION_ACTION, 0) // SESSSION_ACTION_NOTHING
        }
    }
}
