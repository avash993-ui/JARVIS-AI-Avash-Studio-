package com.jarvis.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.jarvis.assistant.vm.Phase
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private class Orbit(
    val rho: Float, val tx: Float, val tz: Float, val ph: Float, val sp: Float, val w: Float,
    val hue: Float, val l: Float, val segA: FloatArray, val segLen: FloatArray,
)

private class Frag(val x: Float, val y: Float, val z: Float, val w: Float, val h: Float, val p: Float, val v: Boolean)

/**
 * Gold particle-sphere hologram. Geometry and animation follow the approved HTML design
 * (jarvis_final_design_fa_en.html): big = 680-unit design, small = 200-unit design (Siri-style).
 */
private class HoloModel(val big: Boolean) {
    val design = if (big) 680f else 200f
    val rd = if (big) 250f else 72f
    private val gr0 = if (big) 90f else 32f
    private val ring0 = if (big) 16f else 6f
    private val s0 = if (big) 5f else 2.5f
    private val wm = if (big) 2f else 1.5f
    private val ws = if (big) 1f else 0.55f
    private val rnd = Random(7)
    private fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    val orbits = ArrayList<Orbit>()
    val frags = ArrayList<Frag>()
    var rot = 0f; var spd = .12f; var scl = 1f; var bri = .55f; var pm = 1f; var lvl = 0f
    val tmp = FloatArray(3)
    val path = Path()

    init {
        val nO = if (big) 34 else 26
        val nF = if (big) 240 else 120
        for (i in 0 until nO) {
            val n = rnd.nextInt(3, 6)
            orbits.add(Orbit(
                rd * (.3f + .7f * Math.pow(rnd.nextDouble(), .7).toFloat()), r(0f, 3.14f), r(0f, 3.14f), r(0f, 6.28f), r(-.7f, .7f),
                r(.8f, wm), r(34f, 47f), r(52f, 70f),
                FloatArray(n) { r(0f, 6.28f) }, FloatArray(n) { r(.2f, 1.3f) }))
        }
        orbits[0] = Orbit(rd * .62f, .9f, .4f, 0f, .25f, wm * 1.6f, 40f, 62f, floatArrayOf(0f), floatArrayOf(4.2f))
        orbits[1] = Orbit(rd * .42f, 1.3f, 2.1f, 1f, -.5f, wm * 1.3f, 44f, 68f, floatArrayOf(0f), floatArrayOf(3.2f))
        for (i in 0 until nF) {
            val u = r(-1f, 1f); val th = r(0f, 6.28f); val s = Math.sqrt((1 - u * u).toDouble()).toFloat(); val rr = rd * r(.3f, 1f)
            frags.add(Frag(s * cos(th) * rr, u * rr, s * sin(th) * rr, r(2f, 9f) * ws, r(1f, 2.4f), r(0f, 6.28f), rnd.nextBoolean()))
        }
    }

    fun step(t: Float, phase: Phase) {
        val tg = when (phase) {
            Phase.Idle -> floatArrayOf(.12f, 1f, .55f, 1f)
            Phase.Listening -> floatArrayOf(.22f, .9f, .75f, 1.2f)
            Phase.Thinking -> floatArrayOf(.95f, .94f, .85f, 4f)
            Phase.Speaking -> floatArrayOf(.3f, 1f, 1f, 1.4f)
        }
        spd += (tg[0] - spd) * .05f; scl += (tg[1] - scl) * .05f; bri += (tg[2] - bri) * .05f; pm += (tg[3] - pm) * .05f
        val tl = when (phase) {
            Phase.Speaking -> min(1f, .55f * abs(sin(t * 5.3f) * sin(t * 2.1f)) + .4f * abs(sin(t * 9.7f))) + .1f
            Phase.Listening -> .35f + .3f * sin(t * 4.5f)
            else -> .03f * sin(t * 2f)
        }
        lvl += (tl - lvl) * .25f
        rot += spd * .016f
    }

    fun draw(ds: DrawScope, t: Float) = with(ds) {
        val sz = size.minDimension
        val f = sz / design
        val cx = sz / 2; val cy = sz / 2
        val cr = cos(rot); val sr = sin(rot); val ct = cos(.35f); val sl = sin(.35f)
        val sc = scl * (1 + .07f * lvl)

        fun proj(x: Float, y: Float, z: Float) {
            val xx = x * cr + z * sr
            var zz = -x * sr + z * cr
            val yy = y * ct - zz * sl
            zz = y * sl + zz * ct
            tmp[0] = cx + xx * sc * f; tmp[1] = cy + yy * sc * f; tmp[2] = zz
        }

        for ((i, o) in orbits.withIndex()) {
            val rho = o.rho * (1 + .06f * lvl * sin(i * 1.7f + t * 3f))
            val cxo = cos(o.tx); val sxo = sin(o.tx); val cz = cos(o.tz); val sz2 = sin(o.tz)
            for (q in o.segA.indices) {
                val a0 = o.segA[q] + o.ph + t * o.sp * pm
                val len = o.segLen[q] * (1 + .6f * lvl)
                val n = max(6, floor(len * 9f).toInt())
                path.reset()
                var zs = 0f
                for (j in 0..n) {
                    val a = a0 + len * j / n
                    val x = rho * cos(a); val y = rho * sin(a) * cxo; val z = rho * sin(a) * sxo
                    proj(x * cz - y * sz2, x * sz2 + y * cz, z)
                    zs += tmp[2]
                    if (j == 0) path.moveTo(tmp[0], tmp[1]) else path.lineTo(tmp[0], tmp[1])
                }
                val d = .35f + .65f * ((zs / (n + 1)) + rd) / (2 * rd)
                drawPath(path, hslc(o.hue, 1f, o.l / 100f, min(1f, max(0f, d * bri))),
                    style = Stroke(width = o.w * (.8f + .5f * lvl) * f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
            }
        }
        for (fr in frags) {
            proj(fr.x, fr.y, fr.z)
            val fl = .4f + .6f * abs(sin(t * (1.5f + pm * .5f) + fr.p))
            val d = .25f + .75f * (tmp[2] + rd) / (2 * rd)
            val w = fr.w * (1 + .5f * lvl) * f
            val h = fr.h * f
            val col = hslc(42f, 1f, .64f, min(1f, max(0f, fl * d * bri * .9f)))
            drawRect(col, Offset(tmp[0], tmp[1]), if (fr.v) Size(h, w) else Size(w, h), blendMode = BlendMode.Plus)
        }
        val g2 = gr0 * (1 + .4f * lvl) * f
        drawCircle(
            Brush.radialGradient(listOf(Color(1f, .84f, .47f, min(1f, .55f * bri)), Color(1f, .59f, 0f, 0f)), Offset(cx, cy), g2),
            g2, Offset(cx, cy), blendMode = BlendMode.Plus)
        for (q in 0 until 3) {
            val b = t * (2.4f + q * .8f) * pm * (if (q % 2 == 1) -1f else 1f) + q * 2.1f
            val rr = (ring0 * (1 + q * .45f) + s0 * .4f * lvl) * f
            drawArc(hslc(46f, 1f, (72 + q * 6) / 100f, min(1f, .8f * bri + .2f)),
                Math.toDegrees(b.toDouble()).toFloat(), Math.toDegrees(2.2).toFloat(), false,
                Offset(cx - rr, cy - rr), Size(rr * 2, rr * 2),
                style = Stroke(width = (2.6f - q * .5f) * (if (big) 1f else .7f) * f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        }
        drawCircle(Color(1f, .96f, .84f, .95f), (s0 * .6f + s0 * .4f * lvl) * f, Offset(cx, cy))
    }
}

@Composable
fun HoloOrb(phase: Phase, size: Dp, modifier: Modifier = Modifier, big: Boolean = false) {
    val model = remember(big) { HoloModel(big) }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - t0) / 1_000_000_000f }
    }
    Canvas(modifier.size(size).clip(CircleShape).background(Color(0xFF03060C))) {
        val tt = t
        model.step(tt, phase)
        model.draw(this, tt)
    }
}

/** HSL -> Color (h in degrees, s/l/a in 0..1). */
private fun hslc(h: Float, s: Float, l: Float, a: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r1 + m, g1 + m, b1 + m, a)
}
