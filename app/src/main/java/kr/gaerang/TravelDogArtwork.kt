package kr.gaerang

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.PathParser
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Native geometry derived from the supplied Choco rig. */
internal class TravelDogArtwork private constructor(private val root: Layer) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: Canvas, coat: Coat, phase: Float, moving: Boolean) {
        val angle = ((if (moving) phase else .12f) * 2 * PI).toFloat()
        root.draw(canvas, paint, coat, angle, moving)
    }

    private data class Transform(val kind: String, val x: Float, val y: Float, val z: Float) {
        fun apply(canvas: Canvas) {
            if (kind == "translate") canvas.translate(x, y) else canvas.rotate(x, y, z)
        }
    }

    private class Layer(json: JSONObject) {
        private val name = json.optString("part")
        private val shape = json.optString("d").takeIf(String::isNotEmpty)?.let(PathParser::createPathFromPathData)
        private val fill = readColor(json, "fill")
        private val stroke = readColor(json, "stroke")
        private val strokeWidth = json.optDouble("width", 0.0).toFloat()
        private val alpha = (json.optDouble("alpha", 1.0) * 255).toInt()
        private val cap = if (json.optBoolean("round")) Paint.Cap.ROUND else Paint.Cap.BUTT
        private val children = json.optJSONArray("children")?.let { items ->
            List(items.length()) { Layer(items.getJSONObject(it)) }
        }.orEmpty()
        private val transforms = json.optJSONArray("transform")?.let { items ->
            List(items.length()) { i ->
                val spec = items.getJSONArray(i)
                val values = spec.getJSONArray(1)
                Transform(spec.getString(0), values.getDouble(0).toFloat(),
                    values.optDouble(1, 0.0).toFloat(), values.optDouble(2, 0.0).toFloat())
            }
        }.orEmpty()

        fun draw(canvas: Canvas, paint: Paint, coat: Coat, angle: Float, moving: Boolean) {
            if (!moving && name == "tongue") return
            val transform = transforms.isNotEmpty() || name.isNotEmpty()
            if (transform) {
                canvas.save()
                transforms.forEach { it.apply(canvas) }
                pose(canvas, angle, moving)
            }
            shape?.let { path ->
                if (fill != null) {
                    paint.style = Paint.Style.FILL
                    paint.color = paletteColor(fill, coat)
                    paint.alpha = alpha
                    canvas.drawPath(path, paint)
                }
                if (stroke != null) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = cap
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.strokeWidth = strokeWidth
                    paint.color = paletteColor(stroke, coat)
                    paint.alpha = alpha
                    canvas.drawPath(path, paint)
                }
            }
            children.forEach { it.draw(canvas, paint, coat, angle, moving) }
            if (transform) canvas.restore()
        }

        private fun pose(canvas: Canvas, angle: Float, moving: Boolean) {
            if (!moving) return
            when (name) {
                "body" -> canvas.translate(0f, -2.6f * abs(sin(angle)))
                "tail" -> canvas.rotate(10f * sin(2f * angle), 94f, 102f)
                "head" -> canvas.rotate(2f * sin(2f * angle), 200f, 96f)
                "ear" -> canvas.rotate(12f + 8f * sin(angle - 1.2f), 210f, 58f)
                "legLH", "legRF" -> canvas.rotate(-19f * sin(angle))
                "legLF", "legRH" -> canvas.rotate(19f * sin(angle))
                "kneeLH" -> canvas.rotate(-34f * cos(angle).coerceAtLeast(0f))
                "kneeRF" -> canvas.rotate(34f * cos(angle).coerceAtLeast(0f))
                "kneeRH" -> canvas.rotate(-34f * (-cos(angle)).coerceAtLeast(0f))
                "kneeLF" -> canvas.rotate(34f * (-cos(angle)).coerceAtLeast(0f))
            }
        }
    }

    companion object {
        private var cached: TravelDogArtwork? = null
        private val cream = mapOf(
            0xC98B5B to 0xE1C3A5, 0xDFA978 to 0xF3DAB9, 0xEEC9A0 to 0xFAE9CC,
            0xA86B3E to 0xBA937B, 0x9A5F36 to 0xAB8065, 0x8F5730 to 0x93674E, 0xB57849 to 0xC29977
        )
        private val gray = mapOf(
            0xC98B5B to 0x7F7B89, 0xDFA978 to 0x9A96A3, 0xEEC9A0 to 0xB8B4BF,
            0xA86B3E to 0x6C6877, 0x9A5F36 to 0x585561, 0x8F5730 to 0x5F5C69, 0xB57849 to 0x787381
        )

        fun get(context: Context): TravelDogArtwork = cached ?: context.assets.open("travel_dog.json")
            .bufferedReader().use { TravelDogArtwork(Layer(JSONObject(it.readText()))) }.also { cached = it }

        private fun readColor(json: JSONObject, key: String): Int? =
            json.optString(key).takeIf(String::isNotEmpty)?.let(Color::parseColor)

        private fun paletteColor(color: Int, coat: Coat): Int = when (coat) {
            Coat.BROWN -> color
            Coat.CREAM -> cream[color and 0xFFFFFF]?.or(0xFF000000.toInt()) ?: color
            Coat.GRAY -> gray[color and 0xFFFFFF]?.or(0xFF000000.toInt()) ?: color
        }
    }
}
