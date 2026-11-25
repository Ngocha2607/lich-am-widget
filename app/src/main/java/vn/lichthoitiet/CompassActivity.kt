package vn.lichthoitiet

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * La bàn phong thủy AR: khung hình camera sau kèm lớp phủ chỉ hướng bàn thờ, hướng xuất hành, góc tài lộc
 * theo Bát trạch của gia chủ và Hỷ thần, Tài thần của ngày. Cúi máy nằm ngang (hoặc không có camera)
 * thì chuyển sang la bàn phẳng 24 sơn.
 */
class CompassActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent { CompassScreen { finish() } }
    }
}

/** Một hướng cần chỉ trên màn hình. */
private data class ArTarget(val label: String, val sub: String, val deg: Float, val color: Color, val good: Boolean)

/** Thông số ống kính để tính đúng góc nhìn của khung camera. */
private data class Lens(val focal: Float, val sensorLong: Float, val sensorShort: Float)

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun readLens(cam: Camera): Lens? = try {
    val info = Camera2CameraInfo.from(cam.cameraInfo)
    val size = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    if (size != null && focal != null && focal > 0f) Lens(focal, max(size.width, size.height), min(size.width, size.height)) else null
} catch (e: Exception) {
    null
}

private val GOOD = Color(0xFFFFD36B)
private val GOOD2 = Color(0xFF8BF0B0)
private val BAD = Color(0xFFFF7A7A)
private val PANEL = Color(0xB3101426)
private val TEXT = Color(0xFFF5F1E6)

private val MODES = listOf("Bàn thờ", "Xuất hành", "Tài lộc", "Tám hướng")

@Composable
private fun CompassScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val sp = remember { Prefs.get(ctx) }
    val profileYear = remember { Prefs(ctx).birthYear }
    val hasCameraHw = remember { ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var year by remember { mutableIntStateOf(sp.getInt("fsYear", if (profileYear > 0) profileYear else 1990)) }
    var male by remember { mutableStateOf(sp.getBoolean("fsMale", true)) }
    var mode by remember { mutableIntStateOf(sp.getInt("fsMode", 0)) }
    var hasCam by remember { mutableStateOf(hasCameraHw && ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var camError by remember { mutableStateOf<String?>(null) }
    var lens by remember { mutableStateOf<Lens?>(null) }
    // Ma trận xoay đổi liên tục: chỉ phần vẽ và dòng hướng (LiveView) đọc nó, phần còn lại không dựng lại theo cảm biến
    val matrix = remember { mutableStateOf(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)) }
    val accuracy = remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var noSensor by remember { mutableStateOf(false) }

    fun save() = sp.edit().putInt("fsYear", year).putBoolean("fsMale", male).putInt("fsMode", mode).apply()

    var askedCam by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCam = it }
    LaunchedEffect(Unit) { if (!hasCam && hasCameraHw && !askedCam) { askedCam = true; launcher.launch(Manifest.permission.CAMERA) } }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Cảm biến: vector xoay (có con quay) hoặc gia tốc + từ trường; lọc thông thấp để la bàn không rung.
    // Chỉ chạy khi màn hình đang hiện (ON_RESUME → ON_PAUSE) để không hao pin khi ra màn hình chính.
    DisposableEffect(lifecycleOwner) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val rv = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        // Máy tính bảng có hướng tự nhiên nằm ngang: khi khóa dọc, màn hình xoay 90° so với trục cảm biến
        val rotation = ContextCompat.getDisplayOrDefault(ctx).rotation
        val listener = object : SensorEventListener {
            val raw = FloatArray(9)
            val remapped = FloatArray(9)
            val smooth = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
            val acc = FloatArray(3)
            val mag = FloatArray(3)
            var hasAcc = false
            var hasMag = false
            var first = true

            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> SensorManager.getRotationMatrixFromVector(raw, e.values)
                    Sensor.TYPE_ACCELEROMETER -> {
                        e.values.copyInto(acc, endIndex = 3); hasAcc = true
                        if (rv != null || !hasMag || !SensorManager.getRotationMatrix(raw, null, acc, mag)) return
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        e.values.copyInto(mag, endIndex = 3); hasMag = true
                        if (rv != null || !hasAcc || !SensorManager.getRotationMatrix(raw, null, acc, mag)) return
                    }
                    else -> return
                }
                val src = when (rotation) {
                    Surface.ROTATION_90 -> remapped.also { SensorManager.remapCoordinateSystem(raw, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, it) }
                    Surface.ROTATION_180 -> remapped.also { SensorManager.remapCoordinateSystem(raw, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, it) }
                    Surface.ROTATION_270 -> remapped.also { SensorManager.remapCoordinateSystem(raw, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, it) }
                    else -> raw
                }
                val k = if (first) 1f else 0.16f
                first = false
                for (i in 0 until 9) smooth[i] += k * (src[i] - smooth[i])
                matrix.value = smooth.copyOf()
            }

            override fun onAccuracyChanged(s: Sensor, a: Int) {
                if (s.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy.intValue = a
            }
        }
        var running = false
        fun start() {
            if (running || sm == null) return
            if (rv == null && (accel == null || magnet == null)) { noSensor = true; return }
            if (rv != null) sm.registerListener(listener, rv, SensorManager.SENSOR_DELAY_GAME)
            else sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            if (magnet != null) sm.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)
            running = true
        }
        fun stop() {
            if (running) sm?.unregisterListener(listener)
            running = false
        }
        if (sm == null) noSensor = true
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }

    // Quay lại từ Cài đặt máy sau khi cấp quyền camera: dùng camera ngay
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasCameraHw && !hasCam &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            ) hasCam = true
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Camera sau gắn với vòng đời màn hình
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(hasCam, lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        if (hasCam) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                if (disposed) return@addListener   // màn hình đã đóng trước khi camera sẵn sàng
                try {
                    val p = future.get()
                    provider = p
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    p.unbindAll()
                    val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                    lens = readLens(cam)
                    camError = null
                } catch (e: Exception) {
                    camError = "Không mở được camera sau"
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose {
            disposed = true
            provider?.unbindAll()
        }
    }

    // Các giá trị chỉ đổi khi đổi gia chủ hoặc chế độ: tính một lần, không tính lại theo cảm biến
    val today = remember { LocalDate.now() }
    val lunar = remember { LunarCalendar.convert(today) }
    val alm = remember { Almanac.of(lunar) }
    val cung = remember(year, male) { PhongThuy.cungPhi(year, male) }
    val stars = remember(cung) { PhongThuy.batTrach(cung) }
    val targets: List<ArTarget> = remember(mode, stars) {
        val hy = Huong.parse(alm.hyThan)
        val tai = Huong.parse(alm.taiThan)
        val sinhKhi = stars.first { it.name == "Sinh Khí" }
        when (mode) {
            0 -> stars.map { ArTarget(it.name, if (it.good) it.meaning else "lưng bàn thờ tựa được", it.huong.deg, if (it.good) GOOD else BAD, it.good) }
            1 -> listOfNotNull(
                hy?.let { ArTarget("Hỷ thần", "Gặp may, việc vui · ${it.label}", it.deg, GOOD, true) },
                tai?.let { ArTarget("Tài thần", "Cầu tài lộc · ${it.label}", it.deg, GOOD2, true) },
            )
            2 -> listOfNotNull(
                ArTarget("Sinh Khí", "Góc tài lộc của gia chủ · ${sinhKhi.huong.label}", sinhKhi.huong.deg, GOOD, true),
                tai?.let { ArTarget("Tài thần hôm nay", it.label, it.deg, GOOD2, true) },
            )
            else -> stars.map { ArTarget(it.name, it.meaning, it.huong.deg, if (it.good) GOOD else BAD, it.good) }
        }
    }
    val explain = when (mode) {
        0 -> "Bàn thờ nên quay mặt về một trong 4 hướng tốt (vàng) của gia chủ, lưng tựa về hướng xấu (đỏ). Đứng giữa nhà, giơ máy lên và xoay người để thấy các hướng."
        1 -> "Hôm nay ngày ${LunarCalendar.canChiDay(lunar.jd)}: xuất hành về hướng Hỷ thần để gặp may, Tài thần để cầu tài."
        2 -> "Hướng Sinh Khí là hướng tài lộc của gia chủ: hợp đặt bàn làm việc, két tiền, cây tài lộc."
        else -> "Tám hướng Bát trạch của gia chủ cung ${cung.name}: 4 hướng tốt (vàng), 4 hướng xấu (đỏ)."
    }

    // Đủ chỗ cho mọi nhãn của la bàn phẳng (24 sơn, 8 sao, chữ độ) để không đo lại chữ mỗi khung hình
    val measurer = rememberTextMeasurer(cacheSize = 96)
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0F1E))) {
        if (hasCam && camError == null) AndroidView({ previewView }, Modifier.fillMaxSize())

        LiveView(
            matrix = matrix, accuracy = accuracy, lens = lens, camOk = hasCam && camError == null,
            targets = targets, stars = stars, measurer = measurer, explain = explain,
            note = when {
                noSensor -> "Máy không có cảm biến la bàn."
                !hasCameraHw -> "Máy không có camera: dùng la bàn phẳng. Đặt máy nằm ngang, cạnh trên chỉ hướng cần xem."
                !hasCam -> "Chưa có quyền camera: đang dùng la bàn phẳng. Đặt máy nằm ngang, cạnh trên chỉ hướng cần xem."
                camError != null -> "$camError: đang dùng la bàn phẳng."
                else -> null
            },
        )

        // Bảng trên: đóng, chế độ, gia chủ
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("✕", "Đóng", false, onClose)
                Spacer(Modifier.width(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MODES.forEachIndexed { i, m -> Pill(m, m, mode == i) { mode = i; save() } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(PANEL).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    "Gia chủ ${if (male) "nam" else "nữ"} $year (${LunarCalendar.canChiYear(year)})\nCung ${cung.name} · ${cung.group}",
                    Modifier.weight(1f),
                    style = TextStyle(color = TEXT, fontSize = 13.sp, lineHeight = 18.sp),
                )
                Pill(if (male) "Nam" else "Nữ", "Đổi giới tính gia chủ", false) { male = !male; save() }
                Spacer(Modifier.width(6.dp))
                Pill("−", "Năm sinh trước", false) { if (year > 1920) { year--; save() } }
                Spacer(Modifier.width(6.dp))
                Pill("+", "Năm sinh sau", false) { if (year < today.year) { year++; save() } }
            }
        }
    }
}

/** Phần đổi theo cảm biến: lớp vẽ AR / la bàn phẳng và bảng hướng ở dưới. */
@Composable
private fun LiveView(
    matrix: State<FloatArray>,
    accuracy: State<Int>,
    lens: Lens?,
    camOk: Boolean,
    targets: List<ArTarget>,
    stars: List<SaoBatTrach>,
    measurer: TextMeasurer,
    explain: String,
    note: String?,
) {
    val r = matrix.value
    val pitch = ArMath.cameraPitch(r)
    // Trễ chuyển chế độ: cúi quá −55° thì sang la bàn phẳng, ngẩng lên quá −45° mới về AR (không nhấp nháy ở ngưỡng)
    val flatHold = remember { booleanArrayOf(false) }
    flatHold[0] = if (flatHold[0]) pitch < -45f else pitch < -55f
    val flat = !camOk || flatHold[0]
    val heading = if (flat) ArMath.topHeading(r) else ArMath.cameraHeading(r)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Đang hướng về ${PhongThuy.describe(heading)}" }) {
            val f = lens?.let { ArMath.displayFocal(size.width, size.height, it.focal, it.sensorLong, it.sensorShort) }
                ?: ArMath.fallbackFocal(size.height)
            if (flat) drawFlatCompass(heading, targets, stars, measurer) else drawAr(r, f, heading, targets, measurer)
        }
        // Bảng dưới: hướng đang nhìn, giải thích, cảnh báo hiệu chỉnh
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(10.dp)
                .clip(RoundedCornerShape(16.dp)).background(PANEL).padding(12.dp),
        ) {
            BasicText(
                "${PhongThuy.describe(heading)} · sơn ${PhongThuy.son(heading)}",
                style = TextStyle(color = TEXT, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            val shown = note ?: when {
                accuracy.value <= SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "La bàn cần hiệu chỉnh: xoay máy theo hình số 8 vài lần, tránh xa đồ sắt, nam châm."
                flat -> "La bàn phẳng: cạnh trên của máy chỉ hướng đang xem. Giơ máy lên để xem qua camera."
                else -> null
            }
            BasicText(explain, Modifier.padding(top = 4.dp), style = TextStyle(color = Color(0xCCF5F1E6), fontSize = 12.5.sp, lineHeight = 17.sp))
            shown?.let { BasicText(it, Modifier.padding(top = 4.dp), style = TextStyle(color = GOOD, fontSize = 12.5.sp, lineHeight = 17.sp)) }
            BasicText(
                "Theo la bàn từ của máy, chỉ để tham khảo.",
                Modifier.padding(top = 4.dp),
                style = TextStyle(color = Color(0x99F5F1E6), fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun Pill(text: String, desc: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) Color(0xFFFFD36B) else PANEL)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        BasicText(text, style = TextStyle(color = if (on) Color(0xFF1B1B1B) else TEXT, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

private fun DrawScope.label(
    m: TextMeasurer, text: String, at: Offset, color: Color, px: Float, bold: Boolean = false, center: Boolean = true, maxWidth: Float = 0f,
) {
    val style = TextStyle(color = color, fontSize = px.toSp(), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
    val layout = if (maxWidth > 0f) {
        m.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxWidth.toInt()))
    } else {
        m.measure(text, style)
    }
    val x = if (center) at.x - layout.size.width / 2f else at.x
    drawText(layout, topLeft = Offset(x, at.y - layout.size.height / 2f))
}

/** Lớp phủ AR: đường chân trời, thẻ hướng ở đúng phương vị, mũi tên ở mép cho hướng ngoài khung, thước la bàn. */
private fun DrawScope.drawAr(r: FloatArray, f: Float, heading: Float, targets: List<ArTarget>, m: TextMeasurer) {
    val w = size.width
    val h = size.height
    val d = density
    // Đường chân trời: nối hai điểm chiếu của hướng lệch ±30°
    val a = ArMath.project(r, heading - 30f, w, h, f)
    val b = ArMath.project(r, heading + 30f, w, h, f)
    if (a != null && b != null) {
        val dx = b.first - a.first
        val dy = b.second - a.second
        drawLine(Color(0x66FFFFFF), Offset(a.first - dx * 4, a.second - dy * 4), Offset(b.first + dx * 4, b.second + dy * 4), 1.5f * d)
    }
    // Tâm ngắm
    drawCircle(Color(0x99FFFFFF), 10f * d, Offset(w / 2, h / 2), style = Stroke(1.5f * d))

    // Lượt 1: thẻ của các hướng nằm trong khung (nhớ vị trí để nhãn ngoài khung không đè lên)
    val cards = mutableListOf<androidx.compose.ui.geometry.Rect>()
    val outside = mutableListOf<ArTarget>()
    for (t in targets) {
        val p = ArMath.project(r, t.deg, w, h, f)
        if (p == null || p.first !in 0f..w || p.second !in 0f..h) { outside += t; continue }
        val (x, y) = p
        val top = y - 70f * d
        drawLine(t.color, Offset(x, y), Offset(x, top + 22f * d), 2f * d)
        drawCircle(t.color, 5f * d, Offset(x, y))
        val box = Size(170f * d, 48f * d)
        drawRoundRect(Color(0xD9101426), Offset(x - box.width / 2, top - box.height / 2), box, androidx.compose.ui.geometry.CornerRadius(14f * d))
        drawRoundRect(t.color, Offset(x - box.width / 2, top - box.height / 2), box, androidx.compose.ui.geometry.CornerRadius(14f * d), style = Stroke(1.5f * d))
        label(m, t.label, Offset(x, top - 9f * d), t.color, 15f * d, bold = true, maxWidth = box.width - 16f * d)
        label(m, t.sub, Offset(x, top + 11f * d), TEXT, 11f * d, maxWidth = box.width - 16f * d)
        cards += androidx.compose.ui.geometry.Rect(x - box.width / 2 - 4f * d, top - box.height / 2 - 4f * d, x + box.width / 2 + 4f * d, y + 6f * d)
    }
    // Lượt 2: hướng ngoài khung thành mũi tên ở mép, xếp từ trên xuống, bỏ qua chỗ đã có thẻ hoặc nhãn khác
    val tapeTop = h * 0.66f - 22f * d
    val placed = mutableListOf<androidx.compose.ui.geometry.Rect>()
    for (t in outside.sortedBy { abs(PhongThuy.delta(heading, it.deg)) }) {
        val delta = PhongThuy.delta(heading, t.deg)
        val right = delta > 0
        val x = if (right) w - 26f * d else 26f * d
        val txt = "${t.label} ${abs(delta).toInt()}°"
        val layout = m.measure(txt, TextStyle(color = t.color, fontSize = (12f * d).toSp(), fontWeight = FontWeight.SemiBold))
        val lw = layout.size.width + 30f * d
        val lh = layout.size.height + 8f * d
        var y = h * 0.2f
        var rect: androidx.compose.ui.geometry.Rect
        while (true) {
            rect = if (right) androidx.compose.ui.geometry.Rect(w - lw - 8f * d, y - lh / 2, w, y + lh / 2)
            else androidx.compose.ui.geometry.Rect(0f, y - lh / 2, lw + 8f * d, y + lh / 2)
            if ((cards + placed).none { it.overlaps(rect) }) break
            y += 8f * d
            if (y + lh / 2 > tapeTop) break
        }
        if (y + lh / 2 > tapeTop) continue   // hết chỗ: bỏ nhãn này (đã có quá nhiều thẻ trên màn hình)
        placed += rect
        val tri = Path().apply {
            if (right) { moveTo(x + 10f * d, y); lineTo(x - 6f * d, y - 9f * d); lineTo(x - 6f * d, y + 9f * d) }
            else { moveTo(x - 10f * d, y); lineTo(x + 6f * d, y - 9f * d); lineTo(x + 6f * d, y + 9f * d) }
            close()
        }
        drawPath(tri, t.color)
        val tx = if (right) x - 12f * d - layout.size.width else x + 12f * d
        drawRoundRect(Color(0xB3101426), Offset(tx - 6f * d, y - layout.size.height / 2f - 3f * d),
            Size(layout.size.width + 12f * d, layout.size.height + 6f * d), androidx.compose.ui.geometry.CornerRadius(8f * d))
        drawText(layout, topLeft = Offset(tx, y - layout.size.height / 2f))
    }

    // Thước la bàn ngang ở 62% chiều cao: vạch 5°, chữ 24 sơn mỗi 15°
    val tapeY = h * 0.66f
    val pxPerDeg = f * tan(Math.toRadians(1.0)).toFloat()
    drawRect(Color(0x66101426), Offset(0f, tapeY - 18f * d), Size(w, 44f * d))
    val span = (w / 2 / pxPerDeg).toInt() + 5
    val base = Math.round(heading / 5f) * 5
    for (k in -span / 5..span / 5) {
        val deg = base + k * 5
        val x = w / 2 + PhongThuy.delta(heading, deg.toFloat()) * pxPerDeg
        if (x < 0 || x > w) continue
        val nd = Math.floorMod(deg, 360)
        val major = nd % 15 == 0
        drawLine(Color(0xCCFFFFFF), Offset(x, tapeY - 18f * d), Offset(x, tapeY - (if (major) 6f else 12f) * d), (if (major) 2f else 1f) * d)
        if (major) {
            val card = when (nd) { 0 -> "B"; 90 -> "Đ"; 180 -> "N"; 270 -> "T"; else -> null }
            label(m, card ?: PhongThuy.SON[nd / 15], Offset(x, tapeY + 8f * d), if (card != null) GOOD else TEXT, (if (card != null) 15f else 11f) * d, bold = card != null)
        }
    }
    val caret = Path().apply { moveTo(w / 2, tapeY - 4f * d); lineTo(w / 2 - 7f * d, tapeY - 18f * d); lineTo(w / 2 + 7f * d, tapeY - 18f * d); close() }
    drawPath(caret, GOOD)
}

/** La bàn phẳng: vòng 24 sơn và tám cung Bát trạch xoay theo hướng Bắc thật; vạch cố định ở đỉnh là hướng cạnh trên máy. */
private fun DrawScope.drawFlatCompass(heading: Float, targets: List<ArTarget>, stars: List<SaoBatTrach>, m: TextMeasurer) {
    val d = density
    val c = Offset(size.width / 2, size.height * 0.5f)
    val rOut = min(size.width, size.height) * 0.42f
    drawCircle(Brush.radialGradient(listOf(Color(0xFF1C2448), Color(0xFF0B0F1E)), c, rOut * 1.2f), rOut * 1.08f, c)
    rotate(-heading, c) {
        // Tám cung (mỗi cung 45°) tô theo tốt / xấu
        for (s in stars) {
            drawArc(
                if (s.good) Color(0x33FFD36B) else Color(0x26FF7A7A),
                startAngle = s.huong.deg - 90f - 22.5f, sweepAngle = 45f, useCenter = true,
                topLeft = Offset(c.x - rOut * 0.78f, c.y - rOut * 0.78f), size = Size(rOut * 1.56f, rOut * 1.56f),
            )
        }
        drawCircle(Color(0x66FFFFFF), rOut, c, style = Stroke(1.5f * d))
        drawCircle(Color(0x44FFFFFF), rOut * 0.78f, c, style = Stroke(1f * d))
        for (i in 0 until 72) {
            val a = Math.toRadians(i * 5.0 - 90)
            val major = i % 3 == 0
            val r0 = rOut * (if (major) 0.92f else 0.96f)
            drawLine(Color(0xAAFFFFFF), Offset(c.x + (r0 * cos(a)).toFloat(), c.y + (r0 * sin(a)).toFloat()),
                Offset(c.x + (rOut * cos(a)).toFloat(), c.y + (rOut * sin(a)).toFloat()), (if (major) 2f else 1f) * d)
        }
        PhongThuy.SON.forEachIndexed { i, name ->
            val a = Math.toRadians(i * 15.0 - 90)
            val rr = rOut * 0.85f
            val card = when (i) { 0 -> "B"; 6 -> "Đ"; 12 -> "N"; 18 -> "T"; else -> null }
            label(m, card ?: name, Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat()),
                if (card != null) GOOD else TEXT, (if (card != null) 16f else 11f) * d, bold = card != null)
        }
        // Tên sao Bát trạch và các hướng cần chỉ
        for (s in stars) {
            val a = Math.toRadians(s.huong.deg - 90.0)
            val rr = rOut * 0.55f
            label(m, s.name, Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat()), if (s.good) GOOD else BAD, 11f * d, bold = true)
        }
        for (t in targets) {
            val a = Math.toRadians(t.deg - 90.0)
            val p = Offset(c.x + (rOut * 1.0f * cos(a)).toFloat(), c.y + (rOut * 1.0f * sin(a)).toFloat())
            drawCircle(t.color, 7f * d, p)
        }
    }
    // Vạch chỉ hướng cố định ở đỉnh
    val tri = Path().apply {
        moveTo(c.x, c.y - rOut - 4f * d); lineTo(c.x - 10f * d, c.y - rOut - 22f * d); lineTo(c.x + 10f * d, c.y - rOut - 22f * d); close()
    }
    drawPath(tri, GOOD)
    drawCircle(GOOD, 4f * d, c)
}
