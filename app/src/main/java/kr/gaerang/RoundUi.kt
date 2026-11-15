package kr.gaerang

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

internal class RoundUi(private val context: Context, private val dark: Boolean, coat: Coat) {
    private fun color(day: String, night: String) = Color.parseColor(if (dark) night else day)
    val paper = color("#F6F1EA", "#15120F")
    val surface = color("#FFFFFF", "#231E1A")
    val ink = color("#2A201A", "#F4EDE4")
    val muted = color("#6F6257", "#B8AB9D")
    val line = color("#E9DFD2", "#3A322B")
    val inverse = color("#FFFFFF", "#281B14")
    val primary = ink
    val accent = color("#8A5A3C", "#E2AC7E")
    val warning = color("#B8361F", "#FF8E74")
    val warningTint = color("#FBE3DC", "#43201A")
    val tint = when (coat) {
        Coat.BROWN -> color("#F3E3D0", "#3A2D23")
        Coat.CREAM -> color("#F6EACD", "#443826")
        Coat.GRAY -> color("#EAE4EB", "#403644")
    }
    private val compact = context.resources.configuration.screenWidthDp < 1020
    private val scale = context.resources.configuration.let {
        minOf(it.screenWidthDp / (if (compact) 640f else 1280f), it.screenHeightDp / 906f).coerceIn(.75f, 1.38f)
    }
    private val regular by lazy { context.resources.getFont(R.font.noto_kr_regular) }
    private val strong by lazy { context.resources.getFont(R.font.noto_kr_bold) }
    private val headline by lazy { context.resources.getFont(R.font.noto_kr_black) }
    private val displayNumber by lazy { context.resources.getFont(R.font.poppins_bold) }

    fun dp(n: Int) = (context.resources.displayMetrics.density * n * scale).roundToInt()
    fun shape(color: Int, radius: Int = 24) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    fun outlined(fill: Int = surface, radius: Int = 24) = shape(fill, radius).apply {
        setStroke(dp(1), line)
    }

    fun text(value: String, size: Int = 17, bold: Boolean = false, color: Int = ink) =
        TextView(context).apply {
            text = value
            textSize = size * scale
            setTextColor(color)
            typeface = if (!bold) regular else if (size >= 29) headline else strong
            includeFontPadding = false
            if (Build.VERSION.SDK_INT >= 33) {
                lineBreakWordStyle = android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
            }
        }

    fun number(value: String, size: Int, color: Int = ink) = text(value, size, true, color).apply {
        typeface = displayNumber
    }

    fun button(value: String, filled: Boolean = false, action: () -> Unit) = Button(context).apply {
        text = value
        textSize = 16f * scale
        isAllCaps = false
        typeface = strong
        minHeight = dp(60)
        minimumHeight = dp(60)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        stateListAnimator = null
        styleButton(this, filled)
        setOnClickListener { action() }
    }

    fun styleButton(button: Button, filled: Boolean) {
        button.background = RippleDrawable(
            ColorStateList.valueOf(0x227D583E),
            if (filled) shape(primary, 39) else outlined(radius = 39), null
        )
        button.setTextColor(if (filled) inverse else ink)
    }

    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    fun panel(fill: Int = surface, padding: Int = 22) = column().apply {
        background = shape(fill, if (compact) 32 else 36)
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    fun image(res: Int, description: String? = null) = ImageView(context).apply {
        setImageResource(res)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = description
        importantForAccessibility = if (description == null) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun gap(parent: LinearLayout, size: Int) {
        val box = if (parent.orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(dp(size), 1)
            else LinearLayout.LayoutParams(1, dp(size))
        parent.addView(View(context), box)
    }

    fun divider(parent: LinearLayout) {
        parent.addView(View(context).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    fun info(parent: LinearLayout, title: String, detail: String? = null): TextView {
        gap(parent, 12)
        parent.addView(text(title, 15, color = muted))
        return text(detail.orEmpty(), 20, true).also(parent::addView)
    }

    fun weight(weight: Float = 1f) = LinearLayout.LayoutParams(0, -2, weight)

    fun pawMap(visited: List<Pair<Place, Coat>>, title: String = "함께 다녀온 곳",
               numbered: Boolean = false) = object : View(context) {
        val points = visited.filter { it.first.routable }
        val style = Paint(Paint.ANTI_ALIAS_FLAG)
        init {
            background = shape(tint)
            contentDescription = "$title · ${points.joinToString { it.first.name }}"
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (points.isEmpty()) return
            val lats = points.map { requireNotNull(it.first.latitude) }
            val lons = points.map { requireNotNull(it.first.longitude) }
            val bottom = lats.min()
            val left = lons.min()
            val latSpan = (lats.max() - bottom).coerceAtLeast(.01)
            val lonSpan = (lons.max() - left).coerceAtLeast(.01)
            val padding = dp(49)
            val mapWidth = (width - 2 * padding).coerceAtLeast(1)
            val mapHeight = (height - 2 * padding).coerceAtLeast(1)
            style.color = muted
            style.textSize = dp(15).toFloat()
            canvas.drawText(title, dp(21).toFloat(), dp(28).toFloat(), style)
            points.forEachIndexed { index, (place, coat) ->
                val x = if (lons.max() == left) width / 2 else padding + ((lons[index] - left) / lonSpan * mapWidth).toInt()
                val y = if (lats.max() == bottom) height / 2 else height - padding - ((lats[index] - bottom) / latSpan * mapHeight).toInt()
                val radius = dp(18)
                if (numbered) {
                    style.color = primary
                    canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), style)
                    val number = (index + 1).toString()
                    style.color = inverse
                    val baseline = y - (style.ascent() + style.descent()) / 2
                    canvas.drawText(number, x - style.measureText(number) / 2, baseline, style)
                    return@forEachIndexed
                }
                style.color = surface
                canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), style)
                context.getDrawable(coat.face)?.apply {
                    setBounds(x - radius, y - radius, x + radius, y + radius)
                    draw(canvas)
                }
                style.color = ink
                val textLeft = (x - style.measureText(place.name) / 2).coerceIn(dp(10).toFloat(),
                    maxOf(dp(10).toFloat(), width - dp(10) - style.measureText(place.name)))
                canvas.drawText(place.name, textLeft, (y + dp(38)).toFloat(), style)
            }
        }
    }
    fun seatGraphic(seat: Seat, coat: Coat) = object : View(context) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val face = context.getDrawable(coat.face)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val scale = minOf(width / 172f, height / 270f)
            canvas.save()
            canvas.translate((width - 172 * scale) / 2, (height - 270 * scale) / 2)
            canvas.scale(scale, scale)
            fun block(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Int) {
                paint.color = fill
                canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
            }
            block(19f, 4f, 153f, 266f, 53f, ink)
            block(21f, 6f, 151f, 264f, 51f, surface)
            block(38f, 57f, 134f, 87f, 15f, color("#DDECF3", "#364B55"))
            block(38f, 220f, 134f, 245f, 14f, color("#DDECF3", "#364B55"))
            block(37f, 102f, 79f, 144f, 10f, paper)
            block(93f, 102f, 135f, 144f, 10f, if (seat == Seat.FRONT_RIGHT) tint else paper)
            block(37f, 166f, 135f, 209f, 11f, paper)
            val x = if (seat == Seat.REAR_LEFT) 38 else 94
            val y = if (seat == Seat.FRONT_RIGHT) 103 else 166
            block(x.toFloat(), y.toFloat(), (x + 42).toFloat(), (y + 42).toFloat(), 10f, tint)
            face?.setBounds(x + 3, y + 3, x + 39, y + 39)
            face?.draw(canvas)
            canvas.restore()
        }
    }
}
