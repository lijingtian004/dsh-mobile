package com.deepseek.dshmobile.service

import android.content.Context
import android.util.Log
import com.deepseek.dshmobile.util.EngineSettings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class DshEngineManager(private val context: Context) {

    companion object {
        private const val TAG = "DshEngineManager"
        private const val PORT = 3080
        private const val HOST = "127.0.0.1"

        @Volatile
        private var appContext: Context? = null

        val instance: DshEngineManager by lazy {
            DshEngineManager(appContext!!)
        }

        fun init(context: Context) {
            appContext = context.applicationContext
        }

        /** 引擎工作目录 */
        fun engineDir(context: Context): File = File(context.filesDir, "dsh_engine")

        /** dsh home 目录（存放 settings.yaml 等配置） */
        fun dshHome(context: Context): File = File(engineDir(context), "home")

        var isRunning: Boolean = false
            private set

        var isPreparing: Boolean = false
            private set

        /** 引擎当前状态描述（用于通知栏与设置页展示） */
        val status = MutableStateFlow("引擎未启动")
    }

    private var process: Process? = null
    private val lock = Any()

    /**
     * 初始化并启动本地 dsh 引擎（单飞，避免并发重复解压）
     */
    suspend fun initialize(context: Context): Boolean {
        synchronized(lock) {
            if (isRunning || isPreparing) return isRunning
            isPreparing = true
        }
        try {
            return doInitialize(context)
        } finally {
            isPreparing = false
        }
    }

    private suspend fun doInitialize(context: Context): Boolean {
        val appDir = engineDir(context)
        val nodeBin = File(appDir, "node")
        val bundleJs = File(appDir, "bundle/node_modules/@deepseek-ai/dsh/lib/bin.js")

        // 首次运行：从 assets 解压引擎资源
        if (!nodeBin.exists() || !bundleJs.exists()) {
            Log.i(TAG, "Engine binaries not found, extracting from assets...")
            status.value = "首次启动：正在解压引擎资源（约 300MB，需要几分钟）..."
            if (!extractFromAssets(appDir)) {
                Log.e(TAG, "Failed to extract engine binaries")
                status.value = "引擎资源解压失败"
                return false
            }
        }
        // 解压完成后再次确认
        if (!nodeBin.exists() || !bundleJs.exists()) {
            Log.e(TAG, "Engine binaries still missing after extraction")
            status.value = "引擎资源不完整"
            return false
        }

        // 写入自定义 provider 配置（若用户填写）
        prepareDshHome(context)

        return try {
            val home = dshHome(context)
            home.mkdirs()
            File(home, "tmp").mkdirs()

            val pb = ProcessBuilder(
                nodeBin.absolutePath,
                bundleJs.absolutePath,
                "web", "--no-open"
            )
            pb.redirectErrorStream(true)
            pb.directory(appDir)
            val env = pb.environment()
            env["HOME"] = home.absolutePath
            env["TMPDIR"] = File(home, "tmp").absolutePath
            env["DSH_HOME"] = home.absolutePath
            env["LD_LIBRARY_PATH"] =
                File(appDir, "lib").absolutePath + ":" + (env["LD_LIBRARY_PATH"] ?: "")
            // Termux 编译的 OpenSSL 会尝试读取其前缀下的 openssl.cnf（无权限），禁用之
            env["OPENSSL_CONF"] = "/dev/null"

            // 注入用户自定义 API Key（配合 settings.yaml 的 apiKeyEnv）
            val cfg = EngineSettings.load(context)
            if (cfg.apiKey.isNotBlank()) {
                env["CUSTOM_API_KEY"] = cfg.apiKey
            }

            status.value = "正在启动引擎进程..."
            process = pb.start()
            Log.i(TAG, "Engine process started")

            status.value = "正在等待引擎就绪..."
            val ok = waitForReady()
            if (!ok) {
                status.value = "引擎启动超时，请查看日志"
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start engine", e)
            status.value = "启动失败：${e.message}"
            false
        }
    }

    /**
     * 根据应用内设置生成 $DSH_HOME/settings.yaml（自定义 OpenAI 兼容 provider）
     */
    private suspend fun prepareDshHome(context: Context) {
        val cfg = EngineSettings.load(context)
        if (cfg.baseUrl.isBlank() || cfg.modelId.isBlank()) return
        val home = dshHome(context)
        home.mkdirs()
        val safeUrl = cfg.baseUrl.replace("\"", "")
        val safeModel = cfg.modelId.replace("\"", "")
        val yaml = buildString {
            appendLine("llm-pi-ai:")
            appendLine("  providers:")
            appendLine("    custom-gateway:")
            appendLine("      apiKeyEnv: CUSTOM_API_KEY")
            appendLine("      api: openai-completions")
            appendLine("      baseURL: \"$safeUrl\"")
            appendLine("      models:")
            appendLine("        - id: $safeModel")
        }
        try {
            File(home, "settings.yaml").writeText(yaml)
            Log.i(TAG, "Custom provider written to settings.yaml")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write settings.yaml", e)
        }
    }

    /**
     * 从 assets 递归解压引擎文件（支持子目录），带断点标记
     */
    private fun extractFromAssets(targetDir: File): Boolean {
        return try {
            val marker = File(targetDir, ".extracted")
            if (marker.exists()) {
                Log.i(TAG, "Engine already extracted, skipping")
                return true
            }
            copyAssetDirectory("engine", targetDir)
            marker.writeText("done")
            Log.i(TAG, "Engine extracted successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract engine", e)
            false
        }
    }

    private fun copyAssetDirectory(assetPath: String, targetDir: File) {
        targetDir.mkdirs()
        val children = context.assets.list(assetPath) ?: return
        for (name in children) {
            val childAssetPath = "$assetPath/$name"
            val childFile = File(targetDir, name)
            val grandChildren = context.assets.list(childAssetPath)
            if (grandChildren != null && grandChildren.isNotEmpty()) {
                copyAssetDirectory(childAssetPath, childFile)
            } else {
                // 已存在且大小一致则跳过，加快二次解压
                if (childFile.exists() && childFile.length() > 0) continue
                context.assets.open(childAssetPath).use { input ->
                    childFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                childFile.setExecutable(true, false)
            }
        }
    }

    /**
     * 等待 dsh web 服务就绪（不抛异常；首次解压可能耗时数分钟）
     */
    private suspend fun waitForReady(): Boolean {
        val timeoutMs = 300_000L
        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val conn = URL("http://$HOST:$PORT").openConnection() as HttpURLConnection
                conn.connectTimeout = 800
                if (conn.responseCode in 200..399) {
                    Log.i(TAG, "Engine service is ready")
                    status.value = "引擎已运行 (127.0.0.1:3080)"
                    isRunning = true
                    return true
                }
            } catch (_: Exception) {
                // 服务尚未就绪，继续等待
            }
            kotlinx.coroutines.delay(1000)
        }

        Log.e(TAG, "Engine startup timeout after ${timeoutMs}ms")
        return false
    }

    /**
     * 停止引擎服务
     */
    fun stop() {
        process?.let {
            it.destroy()
            Log.i(TAG, "Engine process destroyed")
        }
        process = null
        isRunning = false
        status.value = "引擎未启动"
    }

    /**
     * 检查引擎是否正在运行
     */
    fun ping(): Boolean {
        return try {
            val conn = URL("http://$HOST:$PORT").openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.responseCode in 200..399
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 发送消息到引擎
     */
    suspend fun sendMessage(content: String, sessionId: String? = null): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("http://$HOST:$PORT/api/chat")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000

                val escapedContent = content.replace("\\", "\\\\").replace("\"", "\\\"")
                val body = """{"content":"$escapedContent","sessionId":"$sessionId"}"""
                conn.outputStream.use { it.write(body.toByteArray()) }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    throw RuntimeException("HTTP $responseCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                throw e
            }
        }
    }
}
