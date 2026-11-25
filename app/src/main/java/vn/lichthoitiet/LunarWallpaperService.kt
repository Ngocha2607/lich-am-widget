package vn.lichthoitiet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hình nền động lịch âm: trời đổi màu theo giờ mọc, lặn thật của thành phố đang chọn; trăng đúng tuần trăng
 * (rằm tròn, đầu tháng lưỡi liềm); hiệu ứng theo tiết khí (Đông chí sương mù, xuân hoa đào bay, thu lá rơi…),
 * nếu đang mưa hay có sương mù thật thì theo thời tiết. Có thể hiện ngày âm trên hình nền.
 */
class LunarWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = SkyEngine()

    private inner class SkyEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val painter = SkyPainter()
        private var visible = false
        private var width = 0
        private var height = 0
        private var state: SkyState? = null
        private var stateAt = 0L
        private var registered = false
        private var powerSave = false
        private val frame = Runnable { drawFrame() }
        private val ticker = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) readPowerSave()
                stateAt = 0L
                drawFrame()
            }
        }

        private fun readPowerSave() {
            powerSave = getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                if (!registered) {
                    val f = IntentFilter().apply {
                        addAction(Intent.ACTION_TIME_TICK)
                        addAction(Intent.ACTION_TIME_CHANGED)
                        addAction(Intent.ACTION_TIMEZONE_CHANGED)
                        addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                    }
                    ContextCompat.registerReceiver(this@LunarWallpaperService, ticker, f, ContextCompat.RECEIVER_NOT_EXPORTED)
                    registered = true
                }
                readPowerSave()
                stateAt = 0L
                drawFrame()
            } else {
                handler.removeCallbacks(frame)
                unregister()
            }
        }

        private fun unregister() {
            if (registered) {
                try { unregisterReceiver(ticker) } catch (e: Exception) { }
                registered = false
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            painter.resize(w.toFloat(), h.toFloat(), resources.displayMetrics.density)
            // Bề mặt dựng lại khi đang hiện (ví dụ màn xem trước): chạy lại vòng vẽ và đồng hồ phút
            if (isVisible && !visible) onVisibilityChanged(true) else drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            unregister()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            unregister()
            super.onDestroy()
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            painter.parallax = xOffset
            if (visible) drawFrame()
        }

        private fun buildState(): SkyState {
            val prefs = Prefs(this@LunarWallpaperService)
            val w = WeatherRepo.load(this@LunarWallpaperService)
            val code = if (w != null && !w.isStale(3 * 60 * 60 * 1000L)) w.codeNow else null
            val now = LocalDateTime.now()
            val tz = ZoneId.systemDefault().rules.getOffset(now).totalSeconds / 3600.0
            return SkyScene.build(now, prefs.lat, prefs.lon, code, tz)
        }

        private fun drawFrame() {
            handler.removeCallbacks(frame)
            if (width == 0 || height == 0) return
            val now = System.currentTimeMillis()
            if (state == null || now - stateAt > 60_000L) {
                state = try { buildState() } catch (e: Exception) { state }
                stateAt = now
            }
            val s = state ?: return
            val showText = Prefs.get(this@LunarWallpaperService).getBoolean("wpText", true)
            val holder = surfaceHolder
            val canvas = try { holder.lockHardwareCanvas() } catch (e: Exception) { null }
                ?: try { holder.lockCanvas() } catch (e: Exception) { null }
                ?: return
            try {
                painter.draw(canvas, s, now, showText)
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { }
            }
            if (visible) {
                // Hạt chuyển động (hoa, lá, mưa, đom đóm) 25 hình/giây; sương trôi, sao lấp lánh 10 hình/giây;
                // còn lại (ban ngày tĩnh) 30 giây một lần cho mặt trời, màu trời nhích theo giờ
                val delay = if (powerSave) 30_000L else when (painter.motion(s)) {
                    SkyPainter.Motion.PARTICLES -> 40L
                    SkyPainter.Motion.SLOW -> 100L
                    SkyPainter.Motion.STILL -> 30_000L
                }
                handler.postDelayed(frame, delay)
            }
        }
    }
}

/** Vẽ bầu trời, núi, mặt nước, trăng / mặt trời, hiệu ứng và chữ ngày âm bằng Canvas. */
class SkyPainter {
    var parallax = 0.5f
    private var w = 0f
    private var h = 0f
    private var d = 1f
    // Dither để dải màu trời, quầng trăng, sương không bị phân thành vòng
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL); textAlign = Paint.Align.CENTER }
    private var stars = FloatArray(0)
    private val hills = ArrayList<Path>()
    private val particles = ArrayList<Particle>()
    private var particlesFor: SkyEffect? = null
    private var lastFrame = 0L
    private var skyShader: Shader? = null
    private var skyKey = 0L
    private val rnd = Random(2026)

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float, var rot: Float, var spin: Float, var phase: Float, val color: Int)

    fun resize(width: Float, height: Float, density: Float) {
        w = width; h = height; d = density
        val r = Random(7)
        stars = FloatArray(110 * 4) { i ->
            when (i % 4) {
                0 -> r.nextFloat() * w
                1 -> r.nextFloat() * h * 0.6f
                2 -> (0.6f + r.nextFloat() * 1.4f) * d
                else -> r.nextFloat() * 6.28f
            }
        }
        hills.clear()
        for (layer in 0 until 3) {
            val p = Path()
            val base = h * (0.62f + 0.06f * layer)
            val amp = h * (0.09f - 0.022f * layer)
            val ph = floatArrayOf(r.nextFloat() * 6f, r.nextFloat() * 6f, r.nextFloat() * 6f)
            val x0 = -w * 0.25f
            val x1 = w * 1.25f
            p.moveTo(x0, h)
            var x = x0
            while (x <= x1) {
                val t = x / w
                val y = base - amp * (0.55f * sin(t * 4.1f + ph[0]) + 0.3f * sin(t * 9.3f + ph[1]) + 0.15f * sin(t * 21f + ph[2]) + 0.4f).toFloat()
                p.lineTo(x, y)
                x += 6f * d
            }
            p.lineTo(x1, h)
            p.close()
            hills += p
        }
        particles.clear()
        particlesFor = null
        skyShader = null
        waterShader = null
        moonCache = null
        glowCache.clear()
        boatPath.reset()
    }

    enum class Motion { PARTICLES, SLOW, STILL }

    /** Nội dung đang hiện có cần chuyển động không (để chọn nhịp vẽ, tiết kiệm pin). */
    fun motion(s: SkyState): Motion {
        val night = 1f - s.daylight
        return when (s.effect) {
            SkyEffect.PETALS, SkyEffect.LEAVES, SkyEffect.DRIZZLE, SkyEffect.RAIN -> Motion.PARTICLES
            SkyEffect.SUMMER -> if (night >= 0.3f) Motion.PARTICLES else Motion.STILL
            // Sương mù dày ban đêm che sao, dải sương tối gần như không thấy trôi: không cần vẽ liên tục
            SkyEffect.FOG -> if (night > 0.5f) Motion.STILL else Motion.SLOW
            SkyEffect.MIST -> Motion.SLOW
            SkyEffect.NONE -> if (night > 0.05f) Motion.SLOW else Motion.STILL
        }
    }

    // Đối tượng dùng lại giữa các khung hình
    private val leafPath = Path()
    private val glowCache = HashMap<Int, Triple<Long, Int, Shader>>()
    private val unitCache = HashMap<Int, Shader>()

    /** Quầng sáng tròn tại (x, y): chỉ tạo shader mới khi vị trí (làm tròn tới pixel), bán kính hoặc màu đổi. */
    private fun glow(slot: Int, x: Float, y: Float, radius: Float, color: Int): Shader {
        val pos = (Math.round(x).toLong() shl 32) or (Math.round(y).toLong() and 0xFFFFFFFFL)
        val key = color * 31 + Math.round(radius)
        glowCache[slot]?.let { (p, k, sh) -> if (p == pos && k == key) return sh }
        val sh = RadialGradient(Math.round(x).toFloat(), Math.round(y).toFloat(), radius, intArrayOf(color, color and 0xFFFFFF), null, Shader.TileMode.CLAMP)
        glowCache[slot] = Triple(pos, key, sh)
        return sh
    }

    /** Quầng sáng bán kính 1 tại gốc toạ độ (vẽ sau khi dịch, co giãn canvas), dùng lại theo màu. */
    private fun unitGlow(color: Int): Shader = unitCache.getOrPut(color) {
        if (unitCache.size > 64) unitCache.clear()
        RadialGradient(0f, 0f, 1f, intArrayOf(color, color and 0xFFFFFF), null, Shader.TileMode.CLAMP)
    }
    private val boatPath = Path()
    private var moonCache: Path? = null
    private var moonKey = FloatArray(4)
    private var waterShader: Shader? = null
    private var waterKey = 0L

    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun c(v: Int, sh: Int) = (v shr sh) and 0xFF
        fun m(sh: Int) = (c(a, sh) + (c(b, sh) - c(a, sh)) * k).toInt()
        return (m(24) shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    private fun alpha(color: Int, a: Float) = (((a.coerceIn(0f, 1f) * 255).toInt()) shl 24) or (color and 0xFFFFFF)

    fun draw(c: Canvas, s: SkyState, nowMs: Long, showText: Boolean) {
        val dt = if (lastFrame == 0L) 0.04f else ((nowMs - lastFrame) / 1000f).coerceIn(0f, 0.1f)
        lastFrame = nowMs
        val night = 1f - s.daylight
        val shift = (parallax - 0.5f) * w * 0.12f

        // Trời
        val key = (s.skyTop.toLong() shl 32) or (s.skyBottom.toLong() and 0xFFFFFFFFL)
        if (skyShader == null || key != skyKey) {
            skyShader = LinearGradient(0f, 0f, 0f, h * 0.8f, s.skyTop, s.skyBottom, Shader.TileMode.CLAMP)
            skyKey = key
        }
        paint.shader = skyShader
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Sao lấp lánh
        if (night > 0.05f && s.effect != SkyEffect.FOG && s.effect != SkyEffect.RAIN) {
            val t = nowMs / 1000f
            for (i in stars.indices step 4) {
                val tw = 0.55f + 0.45f * sin(t * 1.3f + stars[i + 3])
                paint.color = alpha(0xFFFFFF, night * tw * 0.9f)
                c.drawCircle(stars[i] + shift * 0.2f, stars[i + 1], stars[i + 2], paint)
            }
        }

        // Mặt trời
        s.sunPos?.let { p ->
            val x = w * (0.12f + 0.76f * p) + shift * 0.3f
            val y = h * (0.6f - 0.4f * sin(PI * p).toFloat())
            val low = 1f - sin(PI * p).toFloat()
            val core = mix(0xFFFFF4D6.toInt(), 0xFFFFA45C.toInt(), low)
            paint.shader = glow(1, x, y, w * 0.32f, alpha(core, 0.55f))
            c.drawCircle(x, y, w * 0.32f, paint)
            paint.shader = null
            paint.color = core
            c.drawCircle(x, y, w * 0.06f, paint)
        }

        // Trăng
        val moonXY = s.moonPos?.let { p ->
            val x = w * (0.16f + 0.68f * p) + shift * 0.3f
            val y = h * (0.52f - 0.34f * sin(PI * p).toFloat())
            val r = w * 0.075f
            val glow = night * (0.25f + 0.45f * s.moonIllum)
            paint.shader = glow(2, x, y, r * 4.5f, alpha(0xFFF3DD, glow))
            c.drawCircle(x, y, r * 4.5f, paint)
            paint.shader = null
            paint.color = alpha(mix(s.skyTop, 0xFF000000.toInt(), 0.3f), 0.55f)
            c.drawCircle(x, y, r, paint)
            paint.color = 0xFFFFF1C9.toInt()
            val mk = moonKey
            val ill = if (s.moonWaxing) s.moonIllum else -s.moonIllum
            if (moonCache == null || mk[0] != Math.round(x).toFloat() || mk[1] != Math.round(y).toFloat() || mk[2] != r || mk[3] != ill) {
                moonCache = moonPath(Math.round(x).toFloat(), Math.round(y).toFloat(), r, s.moonIllum, s.moonWaxing)
                mk[0] = Math.round(x).toFloat(); mk[1] = Math.round(y).toFloat(); mk[2] = r; mk[3] = ill
            }
            c.drawPath(moonCache!!, paint)
            x to y
        }

        // Núi ba lớp (xa nhạt, gần đậm), trôi nhẹ theo khi vuốt màn hình chính
        val dark = 0xFF0E1426.toInt()
        for ((i, p) in hills.withIndex()) {
            val tint = mix(s.skyBottom, dark, 0.35f + 0.22f * i + 0.25f * night)
            paint.color = tint
            c.save()
            c.translate(shift * (0.4f + 0.3f * i), 0f)
            c.drawPath(p, paint)
            c.restore()
        }

        // Mặt nước và bóng trăng / mặt trời
        val waterTop = h * 0.8f
        val wTop = mix(s.skyBottom, dark, 0.45f + 0.3f * night)
        val wBot = mix(s.skyTop, dark, 0.6f)
        val wKey = (wTop.toLong() shl 32) or (wBot.toLong() and 0xFFFFFFFFL)
        if (waterShader == null || wKey != waterKey) {
            waterShader = LinearGradient(0f, waterTop, 0f, h, wTop, wBot, Shader.TileMode.CLAMP)
            waterKey = wKey
        }
        paint.shader = waterShader
        c.drawRect(0f, waterTop, w, h, paint)
        paint.shader = null
        val glint = moonXY ?: s.sunPos?.let { p -> (w * (0.12f + 0.76f * p) + shift * 0.3f) to 0f }
        if (glint != null) {
            val t = nowMs / 1000f
            val strength = if (moonXY != null) night * (0.3f + 0.5f * s.moonIllum) else 0.35f
            val n = 30
            for (k in 0 until n) {
                val f = k / n.toFloat()
                val yy = waterTop + (k + 0.5f) * (h - waterTop) / n
                // Vệt sóng: rộng dần về phía gần, lệch trái phải theo sóng, sáng nhạt dần
                val half = w * (0.015f + 0.11f * f) * (0.55f + 0.45f * sin(t * 1.3f + k * 1.7f).toFloat())
                val off = w * 0.012f * sin(t * 0.9f + k * 0.8f).toFloat()
                paint.color = alpha(0xFFF0C8, strength * (1f - f * 0.85f) * (0.6f + 0.4f * cos(t * 2.1f + k).toFloat()))
                c.drawRect(glint.first + off - half, yy, glint.first + off + half, yy + 1.2f * d, paint)
            }
        }
        // Thuyền nan nhỏ
        paint.color = mix(s.skyBottom, dark, 0.85f)
        if (boatPath.isEmpty) boatPath.apply {
            moveTo(-26f * d, 0f); quadTo(0f, 9f * d, 26f * d, 0f); lineTo(20f * d, -3f * d); lineTo(-20f * d, -3f * d); close()
            moveTo(-2f * d, -3f * d); lineTo(-2f * d, -26f * d); lineTo(1f * d, -26f * d); lineTo(1f * d, -3f * d); close()
        }
        c.save()
        c.translate(w * 0.7f + shift * 0.9f, h * 0.86f)
        c.drawPath(boatPath, paint)
        c.restore()

        drawEffect(c, s, dt, nowMs)

        if (showText) {
            val y = h * 0.74f
            text.setShadowLayer(6f * d, 0f, 1.5f * d, 0x99000000.toInt())
            text.color = 0xFFFFF6E3.toInt()
            text.textSize = 30f * d
            c.drawText(s.title, w / 2, y, text)
            text.textSize = 15f * d
            text.color = 0xE6FFF6E3.toInt()
            c.drawText(s.subtitle, w / 2, y + 24f * d, text)
            text.clearShadowLayer()
        }
    }

    /** Hình trăng: nửa đĩa phía sáng cộng / trừ elip đường phân sáng. */
    private fun moonPath(x: Float, y: Float, r: Float, illum: Float, waxing: Boolean): Path {
        val half = Path().apply {
            addArc(RectF(x - r, y - r, x + r, y + r), if (waxing) -90f else 90f, 180f)
            close()
        }
        val e = abs(1f - 2f * illum)
        val ell = Path().apply { addOval(RectF(x - r * e, y - r, x + r * e, y + r), Path.Direction.CW) }
        val out = Path()
        out.op(half, ell, if (illum < 0.5f) Path.Op.DIFFERENCE else Path.Op.UNION)
        return out
    }

    private fun spawn(effect: SkyEffect) {
        particles.clear()
        particlesFor = effect
        val n = when (effect) {
            SkyEffect.PETALS -> 26
            SkyEffect.LEAVES -> 18
            SkyEffect.DRIZZLE -> 90
            SkyEffect.RAIN -> 150
            SkyEffect.SUMMER -> 24
            else -> 0
        }
        repeat(n) { particles += newParticle(effect, true) }
    }

    private fun newParticle(effect: SkyEffect, anywhere: Boolean): Particle {
        val x = rnd.nextFloat() * w
        val y = if (anywhere) rnd.nextFloat() * h else -20f * d
        return when (effect) {
            SkyEffect.PETALS -> Particle(x, y, 18f * d, (28f + rnd.nextFloat() * 30f) * d, (3f + rnd.nextFloat() * 3f) * d, rnd.nextFloat() * 360f, 40f + rnd.nextFloat() * 90f, rnd.nextFloat() * 6f,
                if (rnd.nextBoolean()) 0xFFF7B8C8.toInt() else 0xFFFFD3DD.toInt())
            SkyEffect.LEAVES -> Particle(x, y, 12f * d, (36f + rnd.nextFloat() * 34f) * d, (5f + rnd.nextFloat() * 4f) * d, rnd.nextFloat() * 360f, 60f + rnd.nextFloat() * 120f, rnd.nextFloat() * 6f,
                listOf(0xFFE0A040.toInt(), 0xFFC8662E.toInt(), 0xFFE8C25A.toInt())[rnd.nextInt(3)])
            SkyEffect.DRIZZLE -> Particle(x, y, -40f * d, (260f + rnd.nextFloat() * 120f) * d, (7f + rnd.nextFloat() * 6f) * d, 0f, 0f, 0f, 0x66E6EEFF)
            SkyEffect.RAIN -> Particle(x, y, -90f * d, (620f + rnd.nextFloat() * 260f) * d, (16f + rnd.nextFloat() * 12f) * d, 0f, 0f, 0f, 0x80DDE6F5.toInt())
            else -> Particle(x, h * (0.5f + rnd.nextFloat() * 0.4f), (rnd.nextFloat() - 0.5f) * 14f * d, (rnd.nextFloat() - 0.5f) * 10f * d, (1.6f + rnd.nextFloat() * 1.4f) * d, 0f, 0f, rnd.nextFloat() * 6f, 0xFFD8FF7A.toInt())
        }
    }

    private fun drawEffect(c: Canvas, s: SkyState, dt: Float, nowMs: Long) {
        val t = nowMs / 1000f
        when (s.effect) {
            SkyEffect.FOG, SkyEffect.MIST -> {
                val dense = s.effect == SkyEffect.FOG
                // Ban ngày sương trắng sữa; ban đêm sương xám xanh ám màu trời, mờ hơn để trời không bạc phếch
                val night = 1f - s.daylight
                val fogColor = mix(0xFFF4F6FA.toInt(), mix(s.skyBottom, 0xFF8090B0.toInt(), 0.5f), night)
                val veil = (if (dense) 0.22f else 0.08f) * (0.35f + 0.65f * s.daylight)
                paint.color = alpha(fogColor, veil)
                c.drawRect(0f, 0f, w, h, paint)
                val bands = if (dense) 4 else 2
                for (i in 0 until bands) {
                    val cy = h * (if (dense) 0.5f + 0.1f * i else 0.74f + 0.05f * i)
                    val drift = ((t * (6f + 3f * i) * d) % (w * 1.6f)) - w * 0.3f
                    val bw = w * 1.1f
                    val bh = h * (if (dense) 0.09f else 0.05f)
                    val a = (if (dense) 0.38f else 0.22f) * (0.4f + 0.6f * s.daylight)
                    paint.shader = unitGlow(alpha(fogColor, a))
                    c.save()
                    c.translate(drift + bw / 2, cy)
                    c.scale(bw / 2, bh)
                    c.drawCircle(0f, 0f, 1f, paint)
                    c.restore()
                    paint.shader = null
                }
            }
            SkyEffect.NONE -> Unit
            else -> {
                if (particlesFor != s.effect) spawn(s.effect)
                if (s.effect == SkyEffect.RAIN) {
                    paint.color = 0x2A0A1020
                    c.drawRect(0f, 0f, w, h, paint)
                }
                val night = 1f - s.daylight
                for (p in particles) {
                    when (s.effect) {
                        SkyEffect.DRIZZLE, SkyEffect.RAIN -> {
                            p.x += p.vx * dt; p.y += p.vy * dt
                            paint.color = p.color
                            paint.strokeWidth = (if (s.effect == SkyEffect.RAIN) 1.4f else 1f) * d
                            val k = p.size / p.vy
                            c.drawLine(p.x, p.y, p.x - p.vx * k, p.y - p.size, paint)
                            if (p.y > h || p.x < -20f) { val q = newParticle(s.effect, false); p.x = q.x + 40f * d; p.y = q.y }
                        }
                        SkyEffect.SUMMER -> {
                            // Đom đóm ban đêm: trôi chậm, nhấp nháy; ban ngày không vẽ
                            if (night < 0.3f) continue
                            p.x += (p.vx + sin(t + p.phase) * 6f * d) * dt
                            p.y += (p.vy + cos(t * 0.8f + p.phase) * 5f * d) * dt
                            if (p.x < 0) p.x = w; if (p.x > w) p.x = 0f
                            if (p.y < h * 0.45f || p.y > h * 0.95f) p.vy = -p.vy
                            val blink = (0.5f + 0.5f * sin(t * 2.2f + p.phase * 3f)).coerceAtLeast(0f)
                            val a = night * blink
                            paint.color = alpha(p.color, a * 0.12f)
                            c.drawCircle(p.x, p.y, p.size * 5f, paint)
                            paint.color = alpha(p.color, a * 0.25f)
                            c.drawCircle(p.x, p.y, p.size * 2.6f, paint)
                            paint.color = alpha(p.color, a)
                            c.drawCircle(p.x, p.y, p.size, paint)
                        }
                        else -> {
                            // Cánh hoa đào, lá vàng: rơi chéo, đung đưa, xoay
                            p.x += (p.vx + sin(t * 1.4f + p.phase) * 22f * d) * dt
                            p.y += p.vy * dt
                            p.rot += p.spin * dt
                            if (p.y > h + 20f * d || p.x > w + 30f * d) { val q = newParticle(s.effect, false); p.x = q.x - w * 0.2f; p.y = q.y }
                            paint.color = p.color
                            c.save()
                            c.translate(p.x, p.y)
                            c.rotate(p.rot)
                            val sq = 0.45f + 0.55f * abs(sin(p.rot * PI.toFloat() / 180f))
                            if (s.effect == SkyEffect.LEAVES) {
                                leafPath.rewind()
                                leafPath.moveTo(-p.size, 0f); leafPath.quadTo(0f, -p.size * sq, p.size, 0f); leafPath.quadTo(0f, p.size * sq, -p.size, 0f); leafPath.close()
                                c.drawPath(leafPath, paint)
                            } else {
                                c.drawOval(-p.size, -p.size * 0.6f * sq, p.size, p.size * 0.6f * sq, paint)
                            }
                            c.restore()
                        }
                    }
                }
            }
        }
    }
}
