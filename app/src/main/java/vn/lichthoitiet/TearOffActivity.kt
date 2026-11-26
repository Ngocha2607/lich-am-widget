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
private val CRACK = Color(0xFF2D1C0C)
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

/** Độ dời tâm tờ w×h khi xoay [deg] độ quanh điểm (px, py) của tờ. */
private fun pivotShift(w: Float, h: Float, px: Float, py: Float, deg: Float): Offset {
    val t = Math.toRadians(deg.toDouble())
    val rx = w / 2 - px
    val ry = h / 2 - py
    val cx = px + (rx * cos(t) - ry * sin(t)).toFloat()
    val cy = py + (rx * sin(t) + ry * cos(t)).toFloat()
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

    // Vết xé: rách dần dọc đường xé từ mép phía tay kéo sang mép kia. Đang rách thì tờ chỉ hé quanh đầu vết nứt
    // (khe mở dần, phần chưa rách vẫn dính dưới gáy); rách gần hết tờ mới xoay, dịch theo tay.
    val uPx = density.density
    val tPx = TEAR_T * uPx
    fun tearOf(p: Float) = (p / 0.9f).coerceIn(0f, 1f)
    fun frontOf(o: Offset, tear: Float) = if (o.x >= 0f) wPx * (1f - tear) else wPx * tear
    fun freeOf(tear: Float) = ((tear - 0.72f) / 0.28f).coerceIn(0f, 1f)
    fun bodyAngle(o: Offset, tear: Float, free: Float): Float {
        val crack = (1f + 3f * tear) * minOf(1f, tear * 8f) * (if (o.x >= 0f) 1f else -1f)
        return crack + (angle(o) - crack) * free
    }
    fun liftOf(o: Offset, p: Float, free: Float) = Offset(o.x * 0.18f * free, (maxOf(o.y, 0f) * 0.22f + p * 6f * uPx) * free)

    fun release(o: Offset, vx: Float, vy: Float) {
        val fling = hypot(vx, vy) > 1500f && vy > -300f
        if (progress(o) > 0.5f || fling) {
            // Tờ bay đi bắt đầu đúng chỗ đang hiện: xoay quanh đầu vết nứt rồi dịch theo tay
            val p = progress(o)
            val tear = tearOf(p)
            val free = freeOf(tear)
            val front = frontOf(o, tear)
            val deg = bodyAngle(o, tear, free)
            val shift = pivotShift(wPx, hPx, front, tPx, deg)
            val lift = liftOf(o, p, free)
            val kick = if (fling) 1f else 0f
            flyers += Flyer(
                date = current,
                x = lift.x + shift.x, y = lift.y + shift.y, rot = deg,
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
                        drag.animateTo(Offset(wPx * 0.42f, hPx * 0.22f), tween(480))   // đủ chậm để thấy vết nứt chạy
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
        val tear = tearOf(p)
        val free = freeOf(tear)
        val front = frontOf(o, tear)
        val deg = bodyAngle(o, tear, free)
        val lift = liftOf(o, p, free)
        val rightPull = o.x >= 0f
        val topLine = remember(current, wPx) { tearLineFor(current, wPx, uPx) }
        val topShape = remember(topLine) { bodyShape(topLine) }
        val topShadow = remember(tPx) { bodyShadow(tPx + TEAR_A * uPx) }
        PaperPage(
            topPage, w, h,
            Modifier
                .offset(y = bindH - 2.dp)
                .graphicsLayer {
                    // Xoay quanh đầu vết nứt; phần chưa rách bị nâng lên thì khuất dưới dải giấy và gáy
                    transformOrigin = TransformOrigin(front / wPx, tPx / hPx)
                    rotationZ = deg + enter.value * -5f
                    translationX = lift.x
                    translationY = lift.y - enter.value * hPx * 0.35f
                    // Giấy bị kéo bong ra phía trước (trục nghiêng đi qua đường xé nên chỗ còn dính không xê dịch)
                    rotationX = -tear * 8f * (1f - free) - p * 10f * free
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
                            // Mỗi nấc vết nứt chạy thêm một đoạn: rung nhẹ, vài nấc kèm tiếng giấy
                            val step = (tearOf(progress(next)) * 10).toInt()
                            if (step > ticks) {
                                ticks = step
                                view.tick()
                                if (step == 3 || step == 7) sound.rustle(0.4f)
                            }
                        },
                    )
                },
            elevation = (2f + p * 14f).dp,
            shape = topShape,
            shadowShape = topShadow,
            overlay = { tornRim(topLine, if (rightPull) front else 0f, if (rightPull) Float.MAX_VALUE else front, uPx) },
        )

        // Đầu tờ còn dính dưới gáy: vết nứt chạy dần, lòng khe tối, mép rách trắng xơ giấy
        Canvas(
            Modifier
                .offset(y = bindH - 2.dp)
                .size(w, (TEAR_T + 40f).dp)
                .graphicsLayer { alpha = 1f - enter.value },
        ) {
            tearStrip(topLine, tear, rightPull, front, deg, lift, free, tPx, uPx, current.toEpochDay())
        }

        // Gáy lịch đỏ và mép giấy các tờ đã xé còn dính lại
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
    val dens = LocalDensity.current
    val wPx = with(dens) { w.toPx() }
    val line = remember(f.date, wPx) { tearLineFor(f.date, wPx, dens.density) }
    val shape = remember(line) { bodyShape(line) }
    val shadow = remember(wPx) { bodyShadow((TEAR_T + TEAR_A) * dens.density) }
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
        shape = shape,
        shadowShape = shadow,
        overlay = { tornRim(line, 0f, Float.MAX_VALUE, dens.density) },
    )
}

/** Độ sâu (dp) của đường xé dưới mép trên tờ và độ lởm chởm của răng xé. */
private const val TEAR_T = 12f
private const val TEAR_A = 1.8f

/** Đường xé của một tờ: các điểm (x, y) theo px, răng cưa không đều và lượn nhẹ, cố định theo ngày. */
private class TearLine(val xs: FloatArray, val ys: FloatArray)

private fun tearLineFor(date: LocalDate, w: Float, u: Float, dy: Float = 0f): TearLine {
    val rnd = Random(date.toEpochDay() * 7919L + 13L)
    val t = (TEAR_T + dy) * u
    val a = TEAR_A * u
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    var x = 0f
    var drift = 0f
    while (true) {
        drift = (drift + (rnd.nextFloat() - 0.5f) * a).coerceIn(-a, a)
        xs += x
        ys += t + drift + (rnd.nextFloat() - 0.5f) * a
        if (x >= w) break
        x = minOf(w, x + u * (2.5f + rnd.nextFloat() * 4.5f))
    }
    return TearLine(xs.toFloatArray(), ys.toFloatArray())
}

/** Phần tờ dưới đường xé (phần sẽ bay đi). */
private fun bodyShape(line: TearLine): Shape = GenericShape { size, _ ->
    moveTo(0f, line.ys[0])
    for (i in 1 until line.xs.size) lineTo(line.xs[i], line.ys[i])
    lineTo(size.width, line.ys[line.ys.size - 1])
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/** Hình đổ bóng của phần tờ dưới đường xé: chữ nhật từ chân răng xé trở xuống. */
private fun bodyShadow(top: Float): Shape = GenericShape { size, _ -> addRect(Rect(0f, top, size.width, size.height)) }

/** Dải giấy từ mép trên tờ tới đường xé (còn dính dưới gáy). */
private fun stripPath(line: TearLine, w: Float) = Path().apply {
    moveTo(0f, 0f)
    lineTo(w, 0f)
    for (i in line.xs.size - 1 downTo 0) lineTo(line.xs[i], line.ys[i])
    close()
}

/** Mép rách trắng của đoạn [from, to] trên đường xé, vẽ trên phần tờ bị xé. */
private fun DrawScope.tornRim(line: TearLine, from: Float, to: Float, u: Float) {
    for (i in 0 until line.xs.size - 1) {
        val mid = (line.xs[i] + line.xs[i + 1]) / 2f
        if (mid < from || mid > to) continue
        val a = Offset(line.xs[i], line.ys[i])
        val b = Offset(line.xs[i + 1], line.ys[i + 1])
        drawLine(Color(0x14000000), a + Offset(0f, 2f * u), b + Offset(0f, 2f * u), 1f * u)
        drawLine(Color.White, a, b, 1.6f * u, cap = StrokeCap.Round)
    }
}

/**
 * Dải đầu tờ còn dính dưới gáy. Đoạn đã rách: lòng khe nứt tối (giữa mép dải và mép trên thân tờ, tính cùng phép
 * xoay [deg] quanh đầu vết nứt [front] và độ dời [lift] như lớp thân tờ), mép rách trắng có xơ giấy;
 * trước đầu vết nứt có vệt căng mờ (giấy sắp rách).
 */
private fun DrawScope.tearStrip(
    line: TearLine, tear: Float, rightPull: Boolean, front: Float, deg: Float, lift: Offset, free: Float, t: Float, u: Float, seed: Long,
) {
    val w = size.width
    drawPath(stripPath(line, w), PAPER)
    if (tear <= 0f) return
    val n = line.xs.size
    val torn = { x: Float -> if (rightPull) x >= front else x <= front }
    if (free < 1f) {
        val th = Math.toRadians(deg.toDouble())
        val c = cos(th).toFloat()
        val sn = sin(th).toFloat()
        val idx = (0 until n).filter { torn(line.xs[it]) }
        if (idx.size > 1) {
            val gap = Path().apply {
                idx.forEachIndexed { k, i -> if (k == 0) moveTo(line.xs[i], line.ys[i]) else lineTo(line.xs[i], line.ys[i]) }
                for (i in idx.reversed()) {
                    val dx = line.xs[i] - front
                    val dy = line.ys[i] - t
                    lineTo(front + dx * c - dy * sn + lift.x, t + dx * sn + dy * c + lift.y + 0.6f * u)
                }
                close()
            }
            val k = 1f - free
            drawPath(gap, Brush.verticalGradient(listOf(CRACK.copy(alpha = 0.55f * k), CRACK.copy(alpha = 0.22f * k)), startY = t - 2f * u, endY = t + 16f * u))
        }
    }
    val reach = w * 0.1f
    val rnd = Random(seed * 31L + 5L)
    for (i in 0 until n - 1) {
        val a = Offset(line.xs[i], line.ys[i])
        val b = Offset(line.xs[i + 1], line.ys[i + 1])
        val mid = (a.x + b.x) / 2f
        val r1 = rnd.nextFloat()
        val r2 = rnd.nextFloat()
        val r3 = rnd.nextFloat()
        if (torn(mid)) {
            drawLine(Color(0x2E000000), a + Offset(0f, 1.8f * u), b + Offset(0f, 1.8f * u), 2.4f * u)
            drawLine(Color.White, a, b, 1.3f * u, cap = StrokeCap.Round)
            if (r1 < 0.55f) drawLine(Color(0xD9FFFFFF), a, a + Offset((r2 - 0.5f) * 1.6f * u, (1f + r3 * 2.2f) * u), 0.8f * u, cap = StrokeCap.Round)
        } else {
            val d = if (rightPull) front - mid else mid - front
            if (d < reach) drawLine(Color.Black.copy(alpha = 0.18f * (1f - d / reach)), a, b, 0.9f * u)
        }
    }
}

@Composable
private fun PaperPage(
    page: BlocPage, w: Dp, h: Dp, modifier: Modifier, elevation: Dp,
    shape: Shape? = null, overlay: (DrawScope.() -> Unit)? = null, shadowShape: Shape? = null,
) {
    val density = LocalDensity.current
    val clip = shape ?: RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
    val ink = if (page.red) RED else INK
    val dayFont = with(density) { (w * 0.40f).toSp() }
    // Cỡ chữ, khoảng cách theo bề rộng tờ (tờ nhỏ khi xoay ngang hoặc cửa sổ nhỏ)
    val k = (w / 330.dp).coerceIn(0.5f, 1.15f)
    // Cỡ chữ quy từ dp: tờ lịch giữ đúng tỉ lệ như bản in kể cả khi máy đặt cỡ chữ lớn (chữ vẫn co theo cỡ tờ)
    fun ts(v: Float): TextUnit = with(density) { (v * k).dp.toSp() }
    Box(
        modifier
            .size(w, h)
            // Bóng theo hình lồi riêng (hình răng cưa là đa giác lõm, Android 10 trở xuống không đổ bóng được)
            .shadow(elevation, shadowShape ?: clip, clip = false)
            .clip(clip)
            .background(PAPER)
            .drawBehind { paperFibers(page.date.toEpochDay().toInt()) },
    ) {
        Column(
            // Trên cùng chừa chỗ cho dải giấy dính gáy và đường xé
            Modifier.fillMaxSize().padding(start = 18.dp * k, end = 18.dp * k, top = TEAR_T.dp + 5.dp * k, bottom = 14.dp * k),
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
        overlay?.let { draw -> Canvas(Modifier.fillMaxSize()) { draw() } }
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
        // Dải giấy các tờ đã xé còn dính dưới gáy, đúng đường xé của từng tờ: tờ vừa xé (hôm qua) nằm dưới,
        // tờ xé trước đó đè lên trên; vì vậy xé xong, dải của tờ vừa xé ở đúng chỗ vết nứt
        Canvas(Modifier.offset(y = bindH - 2.dp).size(w, (TEAR_T + TEAR_A + 6f).dp)) {
            val u = density
            listOf(1L to 0f, 2L to -1.6f).forEach { (back, dy) ->
                val line = tearLineFor(current.minusDays(back), size.width, u, dy)
                drawPath(stripPath(line, size.width), if (back == 1L) PAPER else PAPER_EDGE)
                for (i in 0 until line.xs.size - 1) {
                    drawLine(
                        Color(0x26000000), Offset(line.xs[i], line.ys[i] + 1.2f * u), Offset(line.xs[i + 1], line.ys[i + 1] + 1.2f * u), 1.4f * u,
                    )
                }
            }
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
