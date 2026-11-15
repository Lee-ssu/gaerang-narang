package kr.gaerang

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.SequenceInputStream

/** Keeps the current local page visible while the next page renders with vehicle data. */
internal class ZipFrontend(private val context: Context, private val action: (String, String) -> Unit) {
    private class Page(val web: WebView) {
        var url: String? = null
        var generation = 0L
        var loaded = false
        var sent: String? = null
        var prepareSent = false
        var revealRequested = false
        var interactive = false
    }

    val view = FrameLayout(context)
    private val pages = mutableListOf<Page>()
    private var shown: Page? = null
    private var staged: Page? = null
    private var outgoing: Page? = null
    private var state = "{}"
    private var paper = Color.rgb(246, 241, 234)
    private var generation = 0L

    init {
        view.setBackgroundColor(paper)
    }

    fun present(screen: String, portrait: Boolean) {
        val folder = if (portrait) "portrait" else "landscape"
        val next = "https://$ASSET_HOST/zip-ui/screens/$folder/$screen.html"
        if (staged?.url?.substringBefore('?') == next) return
        staged?.let(::retire)
        staged = null
        if (shown?.url?.substringBefore('?') == next) return
        finishTransition()

        val page = pages.firstOrNull { it !== shown } ?: createPage().also(pages::add)
        val request = ++generation
        val cached = page.loaded && page.url?.substringBefore('?') == next
        if (!cached) retire(page)
        page.generation = request
        page.prepareSent = false
        page.revealRequested = false
        staged = page
        page.web.alpha = if (shown == null) 0f else 1f
        // The new WebView must be attached and visible to produce a displayable frame.
        // Place it underneath the opaque current page while it is preparing that frame.
        view.addView(page.web, 0, FrameLayout.LayoutParams(-1, -1))
        page.web.onResume()
        if (!cached) {
            page.url = "$next?render=$request"
            page.web.loadUrl(requireNotNull(page.url))
        }
    }

    fun update(values: JSONObject) {
        val color = if (values.optBoolean("darkMode")) Color.rgb(21, 18, 15) else Color.rgb(246, 241, 234)
        if (color != paper) {
            paper = color
            view.setBackgroundColor(paper)
        }
        state = values.toString()
        (staged ?: shown)?.let(::sync)
    }

    fun hide() {
        pages.forEach(::retire)
        shown = null
        staged = null
        outgoing = null
    }

    fun close() {
        hide()
        pages.forEach { it.web.destroy() }
        pages.clear()
    }

    fun pauseCareVideo() {
        shown?.web?.evaluateJavascript("window.pauseGaerangCareVideo?.()", null)
    }

    private fun createPage(): Page {
        val web = WebView(context)
        val page = Page(web)
        web.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            useWideViewPort = true
            loadWithOverviewMode = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse =
                loadAsset(request.url)

            override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                if (!page.loaded || page.url != web.url || !request.isForMainFrame || target.scheme != "https" ||
                    target.host != ACTION_HOST || target.path != "/command") return true
                val command = target.getQueryParameter("action") ?: return true
                val value = target.getQueryParameter("value").orEmpty()
                if (command == "frontendReady") {
                    if (staged === page && value == page.generation.toString()) awaitFrame(page)
                } else if (shown === page && page.interactive && (staged == null || command in SECTION_ACTIONS)) {
                    action(command, value)
                }
                return true
            }

            override fun onPageFinished(web: WebView, loadedUrl: String) {
                if (staged !== page || loadedUrl != page.url || page.loaded) return
                page.loaded = true
                sync(page)
            }
        }
        return page
    }

    private fun loadAsset(url: Uri): WebResourceResponse {
        val path = url.path.orEmpty().removePrefix("/").takeIf {
            url.scheme == "https" && url.host == ASSET_HOST &&
                it.startsWith("zip-ui/") && ASSET_PATH.matches(it) &&
                it.split('/').none { segment -> segment == "." || segment == ".." }
        }
        if (path != null) {
            val type = when (path.substringAfterLast('.')) {
                "html" -> "text/html"
                "css" -> "text/css"
                "js" -> "application/javascript"
                "otf" -> "font/otf"
                "ttf" -> "font/ttf"
                "jpg" -> "image/jpeg"
                "mp4" -> if (path == GUARDIAN_VIDEO) "video/mp4" else null
                else -> null
            }
            if (type != null) runCatching {
                val content = if (path == "zip-ui/runtime/host.js") {
                    SequenceInputStream(context.assets.open("zip-ui/runtime/cabin.js"),
                        SequenceInputStream(context.assets.open("zip-ui/runtime/theme.js"), context.assets.open(path)))
                } else context.assets.open(path)
                        val encoding = if (type == "text/html" || type == "text/css" || type == "application/javascript")
                            "UTF-8" else null
                        return WebResourceResponse(type, encoding, content)
            }
        }
        return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(),
            ByteArrayInputStream(byteArrayOf()))
    }

    private fun sync(page: Page) {
        val prepareReveal = staged === page && !page.prepareSent
        if (!page.loaded || (!prepareReveal && page.sent == state)) return
        page.web.setBackgroundColor(paper)
        val encoded = JSONObject.quote(state)
        page.sent = state
        val prepare = if (prepareReveal) ";window.prepareGaerangReveal('${page.generation}')" else ""
        if (prepareReveal) page.prepareSent = true
        page.web.evaluateJavascript("window.renderGaerangState($encoded)$prepare", null)
    }

    private fun awaitFrame(page: Page) {
        if (page.revealRequested) return
        page.revealRequested = true
        page.web.postVisualStateCallback(page.generation, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (staged === page && page.generation == requestId) reveal(page)
            }
        })
    }

    private fun reveal(page: Page) {
        val previous = shown
        val animate = previous != null && view.isShown && ValueAnimator.areAnimatorsEnabled()
        previous?.let { setInteractive(it, false) }
        page.web.alpha = if (animate) 0f else 1f
        page.web.bringToFront()
        shown = page
        staged = null
        outgoing = previous
        if (animate) {
            page.web.animate().alpha(1f).setDuration(280)
                .setInterpolator(DecelerateInterpolator()).withEndAction { finishTransition() }.start()
        } else {
            finishTransition()
        }
    }

    private fun finishTransition() {
        shown?.let {
            it.web.animate().cancel()
            it.web.alpha = 1f
            setInteractive(it, true)
        }
        // Keep one prepared page available for quick back-and-forth between sections.
        outgoing?.let(::detach)
        outgoing = null
    }

    private fun setInteractive(page: Page, enabled: Boolean) {
        if (page.interactive == enabled) return
        page.interactive = enabled
        page.web.importantForAccessibility = if (enabled) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        page.web.evaluateJavascript("window.setGaerangInteractive($enabled)", null)
    }

    private fun retire(page: Page) {
        if (page.url == null) return
        detach(page)
        page.url = null
        page.loaded = false
        page.sent = null
        page.web.stopLoading()
        page.web.loadUrl("about:blank")
    }

    private fun detach(page: Page) {
        setInteractive(page, false)
        page.web.animate().cancel()
        view.removeView(page.web)
        page.web.onPause()
    }

    companion object {
        private const val ASSET_HOST = "assets.gaerang.invalid"
        private const val ACTION_HOST = "action.gaerang.invalid"
        private const val GUARDIAN_VIDEO = "zip-ui/media/guardian_voice.mp4"
        private val ASSET_PATH = Regex("[a-zA-Z0-9_./-]+(?<![.])")
        private val SECTION_ACTIONS = setOf("home", "places", "care", "profile")
    }
}
