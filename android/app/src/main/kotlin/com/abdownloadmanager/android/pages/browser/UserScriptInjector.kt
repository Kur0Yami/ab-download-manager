package com.abdownloadmanager.android.pages.browser

import android.webkit.WebView
import java.io.File

/**
 * Userscript sederhana (gaya Tampermonkey).
 *
 * Taruh file *.user.js (atau *.js) di folder:
 *   <penyimpanan app>/Android/data/<package>/files/userscripts/
 * Header yang didukung: // @match dan // @include (boleh pakai wildcard *).
 * Script dijalankan setelah halaman selesai dimuat, dan hanya jika URL cocok.
 */
object UserScriptInjector {

    private const val GM_SHIM = """
if (typeof GM_addStyle === 'undefined') {
  window.GM_addStyle = function (css) {
    var s = document.createElement('style');
    s.textContent = css;
    (document.head || document.documentElement).appendChild(s);
    return s;
  };
}
"""

    fun scriptsDir(view: WebView): File {
        val base = view.context.getExternalFilesDir(null) ?: view.context.filesDir
        return File(base, "userscripts").apply { mkdirs() }
    }

    fun injectFor(view: WebView, url: String?) {
        if (url == null) return
        val files = scriptsDir(view).listFiles { f ->
            f.isFile && (f.name.endsWith(".user.js") || f.name.endsWith(".js"))
        } ?: return

        for (file in files.sortedBy { it.name }) {
            val code = runCatching { file.readText() }.getOrNull() ?: continue
            val patterns = parsePatterns(code)
            if (patterns.isEmpty()) continue
            if (patterns.none { matches(it, url) }) continue
            view.evaluateJavascript("(function(){$GM_SHIM\n$code\n})();", null)
        }
    }

    private fun parsePatterns(code: String): List<String> {
        val regex = Regex("""^\s*//\s*@(?:match|include)\s+(\S+)""", RegexOption.MULTILINE)
        return regex.findAll(code).map { it.groupValues[1] }.toList()
    }

    private fun matches(pattern: String, url: String): Boolean {
        val sb = StringBuilder()
        for (ch in pattern) {
            if (ch == '*') sb.append(".*") else sb.append(Regex.escape(ch.toString()))
        }
        return runCatching { Regex(sb.toString()).matches(url) }.getOrDefault(false)
    }
}
