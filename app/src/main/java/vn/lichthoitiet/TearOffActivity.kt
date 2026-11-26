package vn.lichthoitiet

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lịch bloc xé tay: kéo tờ trên cùng để xé (tờ xoay quanh góc còn dính gáy, rách dần từ góc đang kéo),
 * thả đủ xa hoặc vuốt nhanh thì tờ lịch rời gáy và bay theo mô phỏng vật lý (vận tốc ném, trọng lực,
 * lực cản không khí, xoay và rung giấy 3D), kèm tiếng giấy và rung nhẹ.
 */
class TearOffActivity : ComponentActivity() {
    private lateinit var sound: PaperSound

    override fun onCreate(savedInstanceState: Bundle?) {
        // Nền gỗ tối: chữ và biểu tượng thanh hệ thống luôn màu sáng
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        sound = PaperSound(this)
        val start = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        setContent { TearOffScreen(start, sound) { finish() } }
    }

    override fun onDestroy() {
        sound.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DATE = "date"
    }
}

/** Tiếng giấy: sột soạt khi kéo, tiếng xé khi rời gáy. Im khi máy để im lặng / rung hoặc khi người dùng tắt tiếng. */
class PaperSound(ctx: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ).build()
    private val rustleId = pool.load(ctx, R.raw.paper_rustle, 1)
    private val tearId = pool.load(ctx, R.raw.paper_tear, 1)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    var muted = Prefs.get(ctx).getBoolean("tearMuted", false)
        private set
    private val sp = Prefs.get(ctx)

    fun toggleMute() {
        muted = !muted
        sp.edit().putBoolean("tearMuted", muted).apply()
    }

    private fun allowed() = !muted && (audio == null || audio.ringerMode == AudioManager.RINGER_MODE_NORMAL)

    fun rustle(volume: Float = 0.55f) {
        if (allowed()) pool.play(rustleId, volume, volume, 1, 0, 0.9f + Random.nextFloat() * 0.25f)
    }

    fun tear() {
        if (allowed()) pool.play(tearId, 1f, 1f, 2, 0, 0.92f + Random.nextFloat() * 0.16f)
    }

    fun release() = pool.release()
}

private fun View.tick() = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
private fun View.thud() = performHapticFeedback(
    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
)

/** Lệnh từ các nút của màn lịch xé. */
private enum class TearCmd { TEAR, PREV, TODAY }

/** Dữ liệu một tờ lịch. */
private class BlocPage(val date: LocalDate) {
    val lunar = LunarCalendar.convert(date)
    val special = SuggestionEngine.specialDayName(lunar)
    val red = date.dayOfWeek == DayOfWeek.SUNDAY || special != null
    val weekday = WidgetRenderer.dayOfWeek(date)
    val quote = Quotes.forDate(date)
    val tiet = LunarCalendar.tietKhiStartsToday(lunar.jd)
    val lunarTitle = SkyScene.lunarTitle(lunar)
    val canChi = "Ngày ${LunarCalendar.canChiDay(lunar.jd)} · Tháng ${LunarCalendar.canChiMonth(lunar)}"
    val year = "Năm ${LunarCalendar.canChiYear(lunar.year)}"
    val hoangDao = LunarCalendar.isHoangDao(lunar)
    val gio = LunarCalendar.gioHoangDaoList(lunar.jd).joinToString(", ") { it.first }
    private val alm = Almanac.of(lunar)
    val truc = "Trực ${alm.truc}" + (alm.nen.takeIf { it.isNotEmpty() }?.let { ": nên ${it.take(3).joinToString(", ").lowercase(java.util.Locale("vi"))}" } ?: "")
}

/** Một tờ đã xé đang bay: trạng thái vật lý, tâm xoay ở giữa tờ. */
private class Flyer(val date: LocalDate, x: Float, y: Float, rot: Float, var vx: Float, var vy: Float, var spin: Float) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var rot by mutableFloatStateOf(rot)
    var rotX by mutableFloatStateOf(0f)
    var alpha by mutableFloatStateOf(1f)
    val id = Random.nextLong()
}

private val INK = Color(0xFF1C2A52)
private val RED = Color(0xFFC0272D)
private val PAPER = Color(0xFFFBF7EE)
private val PAPER_EDGE = Color(0xFFE9E1CF)
private val LUNAR = Color(0xFF7D6B4F)
private val SERIF = FontFamily.Serif

@Composable
private fun TearOffScreen(start: LocalDate, sound: PaperSound, onClose: () -> Unit) {
    var currentDay by rememberSaveable { mutableStateOf(start.toEpochDay()) }
    val current = LocalDate.ofEpochDay(currentDay)
    var muted by remember { mutableStateOf(sound.muted) }
    var torn by rememberSaveable { mutableStateOf(0) }
    // Hàng đợi lệnh của các nút (Xé tờ, Hôm qua, Hôm nay) xử lý lần lượt: bấm nhanh không mất lượt, không lẫn thứ tự
    val commands = remember { Channel<TearCmd>(capacity = 32) }
    val flyers = remember { mutableStateListOf<Flyer>() }
    val view = LocalView.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A211B), Color(0xFF3B2E25), Color(0xFF1C1612)))),
    ) {
        // Vân gỗ mặt bàn
        Canvas(Modifier.fillMaxSize()) {
            val rnd = Random(3)
            repeat(26) {
                val y0 = rnd.nextFloat() * size.height
                val path = Path().apply {
                    moveTo(0f, y0)
                    var x = 0f
                    while (x < size.width) { x += 40f; lineTo(x, y0 + sin(x / 90f + it) * 6f) }
                }
                drawPath(path, Color(0x14FFFFFF), style = Stroke(width = 1f + rnd.nextFloat() * 2f))
            }
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconChip(TearIcon.CLOSE, "Đóng", onClose)
                Spacer(Modifier.width(10.dp))
                BasicText(
                    "Lịch xé",
                    Modifier.weight(1f),
                    style = TextStyle(color = Color(0xFFF3E6CF), fontSize = 18.sp, fontFamily = SERIF, fontWeight = FontWeight.SemiBold),
                )
                IconChip(if (muted) TearIcon.MUTE else TearIcon.SOUND, if (muted) "Bật tiếng" else "Tắt tiếng") { sound.toggleMute(); muted = sound.muted }
                Spacer(Modifier.width(8.dp))
                Chip("Hôm nay", "Về hôm nay") { commands.trySend(TearCmd.TODAY) }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().zIndex(1f), contentAlignment = Alignment.Center) {
                // Tờ + gáy (46dp) + khoảng thở vừa trong chiều cao còn lại; tối thiểu 80dp để chữ (co theo tờ) không âm
                val w = minOf(maxWidth * 0.86f, 380.dp, (maxHeight - 56.dp) / 1.42f).coerceAtLeast(80.dp)
                val h = w * 1.42f
                Bloc(
                    current = current, w = w, h = h, sound = sound, view = view, flyers = flyers, commands = commands,
                    onTorn = { torn++; currentDay += 1 },
                    onPrev = { currentDay -= 1; sound.rustle(0.35f); view.tick() },
                    onToday = {
                        val today = LocalDate.now().toEpochDay()
                        if (currentDay != today) { currentDay = today; sound.rustle(0.4f) }
                    },
                )
            }
            BasicText(
                if (torn == 0) "Kéo tờ lịch xuống hoặc sang ngang để xé" else "Ngày ${current.dayOfMonth}/${current.monthValue}/${current.year}",
                Modifier.fillMaxWidth().padding(top = 4.dp),
                style = TextStyle(color = Color(0xB3F3E6CF), fontSize = 13.sp, textAlign = TextAlign.Center),
            )
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Chip("‹ Hôm qua", "Lùi một ngày") { commands.trySend(TearCmd.PREV) }
                Chip("Xé tờ ›", "Xé tờ hôm nay") {
                    commands.trySend(TearCmd.TEAR)
                }
            }
        }
    }
}


@Composable
private fun Chip(text: String, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        BasicText(text, style = TextStyle(color = Color(0xFFF3E6CF), fontSize = 15.sp, fontWeight = FontWeight.Medium))
    }
}

/** Icon nét cùng kiểu bộ Lucide của giao diện chính (khung 24×24, nét 2): đóng, loa, loa tắt. */
private enum class TearIcon { CLOSE, SOUND, MUTE }

@Composable
private fun IconChip(icon: TearIcon, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Canvas(Modifier.size(20.dp)) { lucideIcon(icon, Color(0xFFF3E6CF)) }
    }
}

private fun DrawScope.lucideIcon(icon: TearIcon, color: Color) {
    val k = size.minDimension / 24f
    val stroke = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1 * k, y1 * k), Offset(x2 * k, y2 * k), strokeWidth = 2f * k, cap = StrokeCap.Round)
    if (icon == TearIcon.CLOSE) { line(18f, 6f, 6f, 18f); line(6f, 6f, 18f, 18f); return }
    val body = Path().apply {
        moveTo(11f * k, 5f * k); lineTo(6.2f * k, 8.5f * k); lineTo(3f * k, 8.5f * k); lineTo(3f * k, 15.5f * k)
        lineTo(6.2f * k, 15.5f * k); lineTo(11f * k, 19f * k); close()
    }
    drawPath(body, color, style = stroke)
    if (icon == TearIcon.SOUND) {
        // Hai sóng âm: cung tròn tâm (12,12) bán kính 5 và tâm (13,12) bán kính 9
        drawArc(color, -36.87f, 73.74f, false, topLeft = Offset(7f * k, 7f * k), size = Size(10f * k, 10f * k), style = stroke)
        drawArc(color, -45f, 90f, false, topLeft = Offset(4f * k, 3f * k), size = Size(18f * k, 18f * k), style = stroke)
    } else {
        line(22f, 9f, 16f, 15f); line(16f, 9f, 22f, 15f)
    }
}

/** Đổi góc xoay quanh góc trên (trái nếu kéo sang phải, phải nếu kéo sang trái) thành độ dời của tâm tờ. */
private fun pivotToCenter(w: Float, h: Float, rightPivot: Boolean, deg: Float): Offset {
    val t = Math.toRadians(deg.toDouble())
    val px = if (rightPivot) w else 0f
    val rx = w / 2 - px
    val ry = h / 2
    val cx = px + (rx * cos(t) - ry * sin(t)).toFloat()
    val cy = (rx * sin(t) + ry * cos(t)).toFloat()
    return Offset(cx - w / 2, cy - h / 2)
}

@Composable
private fun Bloc(
    current: LocalDate,
    w: Dp,
    h: Dp,
    sound: PaperSound,
    view: View,
    flyers: MutableList<Flyer>,
    commands: Channel<TearCmd>,
    onTorn: () -> Unit,
    onPrev: () -> Unit,
    onToday: () -> Unit,
) {
    val density = LocalDensity.current
    val wPx = with(density) { w.toPx() }
    val hPx = with(density) { h.toPx() }
    val bindH = 46.dp
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val enter = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var lastDate by remember { mutableStateOf(current) }
    var ticks by remember { mutableStateOf(0) }

    // Lùi ngày (hoặc về hôm nay): tờ mới rơi nhẹ xuống từ trên gáy
    LaunchedEffect(current) {
        val from = lastDate
        lastDate = current
        try {
            // Đổi ngày giữa chừng (Hôm qua, Hôm nay khi tờ đang được kéo / tự xé): tờ mới luôn nằm phẳng, không kẹt nửa rách
            ticks = 0
            drag.snapTo(Offset.Zero)
            if (current.isBefore(from) || (current != from && flyers.none { it.date == from })) {
                enter.snapTo(1f)
                enter.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
            }
        } finally {
            // Hiệu ứng bị ngắt (đổi ngày liên tiếp, kéo tay chen vào): đưa tờ về vị trí cuối, không để treo nửa chừng
            withContext(NonCancellable) {
                if (enter.value != 0f) enter.snapTo(0f)
            }
        }
    }

    fun progress(o: Offset) = (hypot(o.x, maxOf(o.y, 0f) * 0.8f) / (wPx * 0.55f)).coerceIn(0f, 1f)
    fun angle(o: Offset) = (o.x / wPx * 30f + maxOf(o.y, 0f) / hPx * 6f * if (o.x >= 0) 1 else -1).coerceIn(-42f, 42f)

    fun release(o: Offset, vx: Float, vy: Float) {
        val fling = hypot(vx, vy) > 1500f && vy > -300f
        if (progress(o) > 0.5f || fling) {
            val deg = angle(o)
            val rightPivot = o.x < 0
            val shift = pivotToCenter(wPx, hPx, rightPivot, deg)
            val tx = o.x * 0.18f
            val ty = maxOf(o.y, 0f) * 0.22f
            val kick = if (fling) 1f else 0f
            flyers += Flyer(
                date = current,
                x = tx + shift.x, y = ty + shift.y, rot = deg,
                vx = if (fling) vx * 0.9f else (if (o.x >= 0) 900f else -900f),
                vy = if (fling) maxOf(vy, 300f) else 1100f,
                spin = (if (o.x >= 0) 1 else -1) * (120f + 160f * kick) + vx * 0.04f,
            )
            sound.tear()
            view.thud()
            scope.launch { drag.snapTo(Offset.Zero) }
            ticks = 0
            onTorn()
        } else {
            scope.launch { drag.animateTo(Offset.Zero, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
            ticks = 0
        }
    }

    // Nút "Xé tờ": kéo tự động một đoạn rồi thả; bị ngắt (đổi ngày giữa chừng) thì bỏ lượt đó
    val releaseNow = rememberUpdatedState<(Offset, Float, Float) -> Unit> { o, vx, vy -> release(o, vx, vy) }
    val prevNow = rememberUpdatedState(onPrev)
    val todayNow = rememberUpdatedState(onToday)
    LaunchedEffect(Unit) {
        for (cmd in commands) {
            // Chờ vài khung hình để việc đặt lại tờ sau lệnh trước (đổi ngày, snapTo) xong hẳn
            repeat(3) { withFrameNanos { } }
            when (cmd) {
                TearCmd.PREV -> prevNow.value()
                TearCmd.TODAY -> todayNow.value()
                TearCmd.TEAR -> {
                    sound.rustle()
                    try {
                        drag.animateTo(Offset(wPx * 0.42f, hPx * 0.22f), tween(240))
                    } catch (e: CancellationException) {
                        if (!isActive) throw e
                        continue   // người dùng kéo tay chen vào: bỏ lượt tự xé này
                    }
                    releaseNow.value(drag.value, 1400f, 900f)
                }
            }
        }
    }

    Box(Modifier.size(w, h + bindH)) {
        // Độ dày xấp lịch phía dưới
        Box(
            Modifier
                .offset(y = bindH + 6.dp)
                .size(w, h)
                .shadow(10.dp, RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(PAPER_EDGE, RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                .drawBehind {
                    for (i in 1..5) {
                        val y = size.height - i * 1.5f * density.density
                        drawLine(Color(0x22000000), Offset(4f, y), Offset(size.width - 4f, y), 1f)
                    }
                },
        )
        // Tờ ngày kế tiếp nằm dưới
        val nextPage = remember(current) { BlocPage(current.plusDays(1)) }
        val topPage = remember(current) { BlocPage(current) }
        PaperPage(nextPage, w, h, Modifier.offset(y = bindH - 2.dp), elevation = 0.dp)

        // Tờ trên cùng, kéo để xé
        val o = drag.value
        val p = progress(o)
        val deg = angle(o)
        PaperPage(
            topPage, w, h,
            Modifier
                .offset(y = bindH - 2.dp)
                .graphicsLayer {
                    transformOrigin = if (o.x >= 0) TransformOrigin(0f, 0f) else TransformOrigin(1f, 0f)
                    rotationZ = deg + enter.value * -5f
                    translationX = o.x * 0.18f
                    translationY = maxOf(o.y, 0f) * 0.22f + p * 6f * density.density - enter.value * hPx * 0.35f
                    rotationX = -p * 10f
                    cameraDistance = 14f * density.density
                    alpha = 1f - enter.value * 0.85f
                }
                .pointerInput(current) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            sound.rustle()
                            view.tick()
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            release(drag.value, v.x, v.y)
                        },
                        onDragCancel = { release(drag.value, 0f, 0f) },
                        onDrag = { change, amount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val next = drag.value + amount
                            scope.launch { drag.snapTo(next) }
                            val step = (progress(next) * 5).toInt()
                            if (step > ticks) {
                                ticks = step
                                view.tick()
                                if (step == 2) sound.rustle(0.45f)
                            }
                        },
                    )
                },
            elevation = (2f + p * 14f).dp,
        )

        // Gáy lịch đỏ và mép giấy còn dính lại sau khi xé
        Binding(current, w, bindH)

        // Các tờ đang bay (vẽ trên cùng, không bị cắt)
        flyers.forEach { f -> key(f.id) { FlyingPage(f, w, h, bindH, hPx, flyers) } }
    }
}

@Composable
private fun FlyingPage(f: Flyer, w: Dp, h: Dp, bindH: Dp, hPx: Float, flyers: MutableList<Flyer>) {
    LaunchedEffect(f.id) {
        var last = 0L
        var t = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            t += dt
            // Trọng lực, lực cản không khí (giấy nhẹ nên cản nhiều theo phương ngang), xoay chậm dần
            f.vy += 2300f * dt
            f.vx *= exp(-1.4f * dt)
            f.vy *= exp(-0.35f * dt)
            f.spin *= exp(-0.8f * dt)
            f.x += f.vx * dt
            f.y += f.vy * dt
            f.rot += f.spin * dt
            f.rotX = sin(t * 9f) * 32f * exp(-0.6f * t)
            if (t > 0.7f) f.alpha = (1f - (t - 0.7f) / 0.6f).coerceIn(0f, 1f)
            if (f.alpha <= 0f || f.y > hPx * 2.2f) break
        }
        flyers.remove(f)
    }
    val page = remember(f.date) { BlocPage(f.date) }
    PaperPage(
        page, w, h,
        Modifier
            .offset(y = bindH - 2.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin.Center
                translationX = f.x
                translationY = f.y
                rotationZ = f.rot
                rotationX = f.rotX
                cameraDistance = 12f * density
                alpha = f.alpha
            },
        elevation = 18.dp,
        torn = true,
    )
}

/** Mép trên răng cưa của tờ lịch (chỗ xé khỏi gáy). */
private fun tornTop(seed: Int, amp: Float): Shape = GenericShape { size, _ ->
    val rnd = Random(seed)
    moveTo(0f, amp)
    var x = 0f
    while (x < size.width) {
        x = minOf(size.width, x + 6f + rnd.nextFloat() * 10f)
        lineTo(x, amp * (0.2f + rnd.nextFloat() * 1.6f))
    }
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun PaperPage(page: BlocPage, w: Dp, h: Dp, modifier: Modifier, elevation: Dp, torn: Boolean = false) {
    val density = LocalDensity.current
    val shape = if (torn) tornTop(page.date.toEpochDay().toInt(), 3f * density.density) else RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
    val ink = if (page.red) RED else INK
    val dayFont = with(density) { (w * 0.40f).toSp() }
    // Cỡ chữ, khoảng cách theo bề rộng tờ (tờ nhỏ khi xoay ngang hoặc cửa sổ nhỏ)
    val k = (w / 330.dp).coerceIn(0.5f, 1.15f)
    // Cỡ chữ quy từ dp: tờ lịch giữ đúng tỉ lệ như bản in kể cả khi máy đặt cỡ chữ lớn (chữ vẫn co theo cỡ tờ)
    fun ts(v: Float): TextUnit = with(density) { (v * k).dp.toSp() }
    Box(
        modifier
            .size(w, h)
            .shadow(elevation, shape)
            .clip(shape)
            .background(PAPER)
            .drawBehind { paperFibers(page.date.toEpochDay().toInt()) },
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp * k, vertical = 14.dp * k),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                "THÁNG ${page.date.monthValue} · ${page.date.year}",
                style = TextStyle(color = LUNAR, fontSize = ts(12f), letterSpacing = ts(2f), fontWeight = FontWeight.Medium),
            )
            BasicText(
                page.weekday.uppercase(java.util.Locale("vi")),
                Modifier.padding(top = 6.dp * k),
                style = TextStyle(color = ink, fontSize = ts(20f), fontFamily = SERIF, fontWeight = FontWeight.Bold, letterSpacing = ts(3f)),
            )
            BasicText(
                page.date.dayOfMonth.toString(),
                style = TextStyle(color = ink, fontSize = dayFont, fontFamily = SERIF, fontWeight = FontWeight.Bold, lineHeight = dayFont),
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33000000)))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp * k), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(34.dp * k)) {
                    drawMoon(center, size.minDimension / 2 - 2f, SkyScene.moonIllumination(page.lunar.day), SkyScene.moonWaxing(page.lunar.day),
                        Color(0xFFE2B53A), Color(0x22000000))
                }
                Spacer(Modifier.width(10.dp * k))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        page.lunarTitle + " âm lịch",
                        style = TextStyle(color = if (page.special != null) RED else INK, fontSize = ts(16f), fontFamily = SERIF, fontWeight = FontWeight.SemiBold),
                    )
                    BasicText(page.canChi, style = TextStyle(color = LUNAR, fontSize = ts(12.5f)))
                    BasicText(page.year + (page.tiet?.let { " · Tiết ${it.replaceFirstChar { c -> c.uppercase() }}" } ?: ""), style = TextStyle(color = LUNAR, fontSize = ts(12.5f)))
                }
            }
            page.special?.let {
                BasicText(
                    "✦ $it ✦",
                    Modifier.padding(top = 8.dp * k),
                    style = TextStyle(color = RED, fontSize = ts(14f), fontFamily = SERIF, fontWeight = FontWeight.Bold),
                )
            }
            // Như lịch bloc treo tường: hoàng / hắc đạo, giờ hoàng đạo, trực ngày
            Column(Modifier.fillMaxWidth().padding(top = 10.dp * k)) {
                BasicText(
                    if (page.hoangDao) "Ngày hoàng đạo" else "Ngày hắc đạo",
                    style = TextStyle(color = if (page.hoangDao) Color(0xFFB8860B) else LUNAR, fontSize = ts(12.5f), fontWeight = FontWeight.Bold, letterSpacing = ts(1f)),
                )
                BasicText(
                    "Giờ hoàng đạo: ${page.gio}",
                    Modifier.padding(top = 2.dp * k),
                    style = TextStyle(color = INK, fontSize = ts(12.5f), lineHeight = ts(17f)),
                )
                BasicText(
                    page.truc,
                    Modifier.padding(top = 2.dp * k),
                    style = TextStyle(color = INK, fontSize = ts(12.5f), lineHeight = ts(17f)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            BasicText(
                page.quote.text,
                Modifier.fillMaxWidth(),
                style = TextStyle(color = INK, fontSize = ts(14f), fontFamily = SERIF, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, lineHeight = ts(20f)),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                "— ${page.quote.kind} —",
                Modifier.padding(top = 4.dp * k),
                style = TextStyle(color = LUNAR, fontSize = ts(11f)),
            )
        }
    }
}

/** Thớ giấy dó: các nét mảnh ngẫu nhiên (cố định theo ngày) rất nhạt. */
private fun DrawScope.paperFibers(seed: Int) {
    val rnd = Random(seed)
    repeat(70) {
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * size.height
        val len = 6f + rnd.nextFloat() * 22f
        val a = rnd.nextFloat() * PI.toFloat()
        drawLine(Color(0x0F6B4E1E), Offset(x, y), Offset(x + cos(a) * len, y + sin(a) * len), 1f)
    }
}

/** Trăng theo phần được chiếu sáng [illum]: nửa đĩa phía sáng cộng (hoặc trừ) elip đường phân sáng. */
internal fun DrawScope.drawMoon(c: Offset, r: Float, illum: Float, waxing: Boolean, lit: Color, dark: Color) {
    drawCircle(dark, r, c)
    val half = Path().apply {
        addArc(Rect(c.x - r, c.y - r, c.x + r, c.y + r), if (waxing) -90f else 90f, 180f)
        close()
    }
    val e = abs(1f - 2f * illum)
    val ellipse = Path().apply { addOval(Rect(c.x - r * e, c.y - r, c.x + r * e, c.y + r)) }
    val shape = Path().apply { op(half, ellipse, if (illum < 0.5f) PathOperation.Difference else PathOperation.Union) }
    drawPath(shape, lit)
    drawCircle(Color(0x33000000), r, c, style = Stroke(1f))
}

@Composable
private fun Binding(current: LocalDate, w: Dp, bindH: Dp) {
    val k = (w / 330.dp).coerceIn(0.5f, 1.15f)
    Box(Modifier.size(w, bindH + 10.dp)) {
        // Mép giấy còn dính dưới gáy (răng cưa)
        Canvas(Modifier.offset(y = bindH - 3.dp).size(w, 12.dp)) {
            val rnd = Random(current.toEpochDay().toInt() + 7)
            val path = Path().apply {
                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height * 0.4f)
                var x = size.width
                while (x > 0f) { x = maxOf(0f, x - 6f - rnd.nextFloat() * 10f); lineTo(x, size.height * (0.25f + rnd.nextFloat() * 0.6f)) }
                close()
            }
            drawPath(path, PAPER_EDGE)
        }
        Box(
            Modifier
                .size(w, bindH)
                .shadow(6.dp, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFFD93A3A), Color(0xFFA81F25), Color(0xFF8E1A20))),
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 3.dp, bottomEnd = 3.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Hai đinh tán và vệt sáng phản chiếu trên gáy
                val ry = size.height / 2
                listOf(size.width * 0.12f, size.width * 0.88f).forEach { x ->
                    drawCircle(Color(0xFF5A0E12), 6f * density, Offset(x, ry))
                    drawCircle(Color(0xFFE8C77A), 4f * density, Offset(x, ry))
                    drawCircle(Color(0x88FFFFFF), 1.6f * density, Offset(x - 1.2f * density, ry - 1.2f * density))
                }
                drawRect(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color.Transparent)), size = Size(size.width, size.height * 0.45f))
            }
            BasicText(
                "LỊCH ${current.year}",
                style = TextStyle(color = Color(0xFFF6DFA0), fontSize = (16f * k).sp, fontFamily = SERIF, fontWeight = FontWeight.Bold, letterSpacing = (4f * k).sp),
            )
        }
    }
}
