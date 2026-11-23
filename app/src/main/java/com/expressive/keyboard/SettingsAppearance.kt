@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class
)

package com.expressive.keyboard

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface as AndroidTypeface
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// НАСТРОЙКИ: ТЕМА, ШРИФТ, ЦВЕТА И ФОН, КЛАВИШИ
// ============================================================================

/** Режим темы: светлая, тёмная, AMOLED или как в системе — вверху раздела «Темы». */
@Composable
internal fun ThemeModeItem(settings: KeyboardSettings) {
    val context = LocalContext.current
    ChoiceItem(
        Icons.Rounded.DarkMode, "Sáng hoặc tối", "AMOLED — màu đen tuyệt đối cho màn hình OLED",
        listOf("SYSTEM" to "Tự động", "LIGHT" to "Sáng", "DARK" to "Tối", "AMOLED" to "AMOLED"),
        settings.themeMode
    ) { mode -> KeyboardPrefs.edit(context) { putString("theme_mode", mode) } }
}

/** Шрифт клавиш и форма кнопок-«печенек» — часть раздела «Клавиши и шрифт». */
@Composable
internal fun FontSections(settings: KeyboardSettings) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val name = withContext(Dispatchers.IO) { importCustomFont(context, uri) }
            if (name != null) {
                KeyboardPrefs.saveCustomFont(context, name)
                Toast.makeText(context, tr("Шрифт «{0}» применён", name), Toast.LENGTH_SHORT).show()
            } else Toast.makeText(context, tr("Нужен корректный файл .ttf или .otf"), Toast.LENGTH_LONG).show()
        }
    }
    SettingsSection("Phông chữ") {
        if (settings.skinPlugin.isNotEmpty()) {
            GroupItem {
                Text(
                    tr("У темы может быть свой шрифт — выбрать, какой важнее, можно в «Темы» → «Шрифт в этой теме»."),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
        ChoiceItem(
            Icons.Rounded.TextFields, "Kiểu chữ",
            if (settings.fontStyle == "CUSTOM") tr("Свой шрифт: {0}", settings.customFontName) else "Округлый — как на макете",
            listOf("ROUNDED" to "Bo tròn", "SYSTEM" to "Mặc định hệ thống", "SERIF" to "Serif", "MONOSPACE" to "Mono", "PIXEL" to "Pixel"),
            settings.fontStyle
        ) { f -> KeyboardPrefs.edit(context) { putString("font_style", f) } }
        SliderItem(
            icon = Icons.Rounded.FormatBold, title = "Độ đậm chữ",
            value = settings.fontWeight.toFloat(), range = 300f..900f, steps = 5,
            format = {
                when (it.roundToInt() / 100 * 100) {
                    300 -> tr("Mảnh"); 400 -> tr("Thường"); 500 -> tr("Trung bình"); 600 -> tr("Bán đậm"); 700 -> tr("Đậm"); 800 -> tr("Rất đậm")
                    else -> tr("Đen")
                }
            },
            onCommit = { v -> KeyboardPrefs.edit(context) { putInt("font_weight", (v / 100f).roundToInt() * 100) } }
        )
        SliderItem(
            icon = Icons.Rounded.FormatSize, title = "Cỡ chữ",
            value = settings.keyLabelScale, range = 0.7f..1.4f,
            format = { "${(it * 100).roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("key_label_scale", v) } }
        )
        ActionItem(
            icon = Icons.Rounded.FontDownload,
            title = "Tải phông chữ tùy chỉnh",
            subtitle = "Tệp .ttf hoặc .otf",
            onClick = { fontLauncher.launch("*/*") },
            trailing = if (settings.customFontName.isNotEmpty()) {
                {
                    IconButton(onClick = { KeyboardPrefs.resetCustomFont(context) }) {
                        Icon(Icons.Rounded.DeleteOutline, tr("Xóa phông chữ"), tint = cs.error)
                    }
                }
            } else null
        )
    }
    // волнистая форма «печенек»: кнопки меню и микрофона, подсказка над клавишей (если у темы нет своих)
    SettingsSection("Hình dạng phím") {
        GroupItem { Column(Modifier.padding(vertical = 16.dp)) { ShapePicker(settings) } }
        // у готовых форм (сердце, ромб, стрелка…) и круга волн нет
        val tunable = ExpressiveShapePreset.fromId(settings.shapePreset).tunable
        if (tunable) SliderItem(
            icon = Icons.Rounded.AutoAwesome, title = "Số lượng gợn sóng",
            value = settings.waveCount.toFloat(), range = 4f..18f, steps = 13,
            format = { "${it.roundToInt()}" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putInt("wave_count", v.roundToInt()) } }
        )
        if (tunable) SliderItem(
            icon = Icons.Rounded.Tune, title = "Độ sâu gợn sóng",
            value = settings.waveAmplitudePercent, range = 0f..14f,
            format = { "${it.roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("wave_amplitude_percent", v) } }
        )
    }
}

// ----------------------------------------------------------------------- цвета и фон

private data class ColorTarget(val key: String, val title: String)

@Composable
internal fun ColorsPage(settings: KeyboardSettings) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<ColorTarget?>(null) }
    val auto = remember(cs) { buildKeyPalette(cs, KeyboardSettings()) }
    var importing by remember { mutableStateOf(false) }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val ok = withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                    val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                    when {
                        type?.startsWith("video/") != true && importBackground(context, uri) -> {
                            KeyboardPrefs.setBackgroundImage(context, video = false)
                            true
                        }
                        type?.startsWith("image/") != true && importBackgroundVideo(context, uri) -> {
                            KeyboardPrefs.setBackgroundImage(context, video = true)
                            true
                        }
                        else -> false
                    }
                }
                if (!ok) Toast.makeText(context, tr("Не удалось открыть фото или видео"), Toast.LENGTH_LONG).show()
            } finally {
                importing = false
            }
        }
    }
    val monet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    PreviewSection(settings)
    // акцент — главный цвет клавиатуры: вверху и в одном месте (раньше он терялся среди цветов клавиш)
    val themeColors = PluginRepository.skinPlugin(settings)?.skin?.forMode(keyboardIsDark(settings))?.colors
    val themeAccent = themeColors?.get("accent")?.let { Color(it) }
    val themeAccentText = themeColors?.get("accentText")?.let { Color(it) }
    SettingsSection("Màu nhấn") {
        ColorRow("Акцент: Enter, Shift, нажатие, подсветка", settings.colorAccent, themeAccent ?: auto.accent) { picker = ColorTarget("color_accent", "Màu nhấn") }
        ColorRow("Текст на акценте", settings.colorAccentText, themeAccentText ?: auto.onAccent) { picker = ColorTarget("color_accent_text", "Текст на акценте") }
        GroupItem {
            Text(
                tr(
                    if (settings.skinPlugin.isNotEmpty()) "Акцент перекрашивает и выбранную тему: Enter, подсветку, свечение. Отключить для темы — «Темы» → «Красить в мой акцентный цвет». «Авто» — цвет самой темы."
                    else "«Авто» — цвет из палитры Material You ниже."
                ),
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
    }
    SettingsSection("Hình dạng") {
        GroupItem {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ShapeAmountPicker(settings)
                Text(
                    tr("«Печеньки», цветы и клевер из Material 3 Expressive. «Только акценты» оставляет их у меню, микрофона и главной карточки, «Без форм» убирает совсем."),
                    Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
    }
    SettingsSection("Bảng màu") {
        ChoiceItem(
            Icons.Rounded.Palette, "Bảng màu Material You",
            if (settings.colorSource == "SEED") "Вся тема строится из выбранного цвета"
            else if (monet) "Цвета берутся из обоев (Monet)" else "Monet доступен с Android 12 — используется базовая палитра",
            listOf("DYNAMIC" to "Từ hình nền", "SEED" to "Màu tùy chỉnh"),
            settings.colorSource
        ) { v -> KeyboardPrefs.edit(context) { putString("color_source", v) } }
        AnimatedVisibility(
            visible = settings.colorSource == "SEED",
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            GroupItem {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SWATCHES.chunked(6).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { c ->
                                ColorDot(Color(c), settings.seedColor == c) { KeyboardPrefs.edit(context) { putInt("seed_color", c) } }
                            }
                        }
                    }
                    FilledTonalButton(onClick = { picker = ColorTarget("seed_color", "Màu chính") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Colorize, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Chọn màu bất kỳ"))
                    }
                }
            }
        }
    }
    SettingsSection("Màu phím") {
        if (settings.skinPlugin.isNotEmpty()) {
            GroupItem {
                Text(
                    tr("У темы свои цвета клавиш — здесь они меняются только там, где тема их не задаёт."),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
        ColorRow("Nền bàn phím", settings.colorBackground, auto.background) { picker = ColorTarget("color_background", "Nền bàn phím") }
        ColorRow("Phím chữ", settings.colorKey, auto.letter) { picker = ColorTarget("color_key", "Phím chữ") }
        ColorRow("Chữ trên phím", settings.colorKeyText, auto.onLetter) { picker = ColorTarget("color_key_text", "Chữ trên phím") }
        ColorRow("Phím chức năng", settings.colorFunc, auto.func) { picker = ColorTarget("color_func", "Phím chức năng") }
        ColorRow("Chữ trên phím chức năng", settings.colorFuncText, auto.onFunc) { picker = ColorTarget("color_func_text", "Chữ trên phím chức năng") }
        ToggleItem(
            Icons.Rounded.Layers, "Nền phím",
            if (settings.keyBackground) "Плашки под буквами — как в теме"
            else "Không nền phím, chỉ hiển thị chữ trên nền; nền phím cũng không hiện khi làm mờ",
            settings.keyBackground
        ) { on -> KeyboardPrefs.edit(context) { putBoolean("key_background", on) } }
        SliderItem(
            icon = Icons.Rounded.Opacity, title = "Độ mờ phím",
            value = settings.keyOpacity, range = 0.2f..1f,
            format = { "${(it * 100).roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("key_opacity", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.Opacity, title = "Độ trong suốt nền",
            value = settings.bgTransparency, range = 0f..1f,
            format = { if (it < 0.01f) tr("Không") else "${(it * 100).roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("bg_transparency", v) } }
        )
        ActionItem(Icons.Rounded.RestartAlt, "Khôi phục màu chủ đề", "Сбросить акцент, ручные цвета и прозрачность", onClick = { KeyboardPrefs.resetColors(context) })
    }
    // система может запретить размытие окон: экономия заряда, «Уменьшить прозрачность», слабая видеокарта
    val blurAllowed = rememberWindowBlurSupported()
    SettingsSection("Làm mờ") {
        ChoiceItem(
            Icons.Rounded.BlurOn, "Làm mờ nền phía dưới bàn phím",
            when {
                Build.VERSION.SDK_INT < 31 -> "Нужен Android 12 или новее"
                settings.windowBlur != "OFF" && !blurAllowed -> "Сейчас система не разрешает размытие окон: включена экономия заряда, «Уменьшить прозрачность/размытие» в спец. возможностях, или телефон его не поддерживает"
                settings.windowBlur == "ON" -> "Приложение под клавиатурой видно сквозь матовый фон. Расходует заряд: экран перерисовывается видеокартой"
                settings.windowBlur == "OFF" -> "Обычный фон — экономнее всего"
                else -> "Если тема его просит (например, «Жидкое стекло»)"
            },
            listOf("THEME" to "Как в теме", "ON" to "Bật", "OFF" to "Tắt"), settings.windowBlur
        ) { v -> KeyboardPrefs.edit(context) { putString("window_blur", v) } }
        if (settings.windowBlur != "OFF") {
            SliderItem(
                Icons.Rounded.Opacity, "Mức làm mờ", settings.windowBlurDp, 4f..100f, { "${it.roundToInt()} dp" },
                onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("window_blur_dp", v) } }
            )
        }
    }
    SettingsSection("Nền bàn phím") {
        ActionItem(
            icon = Icons.Rounded.Wallpaper,
            title = if (settings.hasBackgroundImage) "Đổi nền" else "Chọn nền",
            subtitle = if (importing) "Đang tải…" else "Ảnh hoặc video từ thư viện",
            onClick = { if (!importing) bgLauncher.launch(arrayOf("image/*", "video/*")) },
            trailing = if (settings.hasBackgroundImage) {
                {
                    IconButton(enabled = !importing, onClick = { KeyboardPrefs.clearBackgroundImage(context) }) {
                        Icon(Icons.Rounded.DeleteOutline, tr("Xóa nền"), tint = cs.error)
                    }
                }
            } else null
        )
        AnimatedVisibility(settings.hasBackgroundImage) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val frame = settings.photoFrame
                val video = settings.hasBackgroundVideo
                val image = if (video) null else rememberBackgroundBitmap(settings.bgImageVersion)
                if (video || image != null) {
                    val palette = rememberKeyPalette(settings)
                    Box(Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(20.dp)).background(palette.background)) {
                        val alpha = 1f - settings.bgTransparency
                        if (video) KeyboardVideo(settings, Modifier.matchParentSize(), alpha)
                        else if (image != null) KeyboardPhoto(image, settings, Modifier.matchParentSize(), alpha)
                        Box(Modifier.matchParentSize().background(palette.background.copy(alpha = settings.bgDim)))
                    }
                }
                ChoiceItem(Icons.Rounded.Crop, "Cách hiển thị nền", null,
                    listOf("COVER" to "Lấp đầy", "FIT" to "Vừa khung", "STRETCH" to "Kéo giãn"), frame.mode.name
                ) { mode -> KeyboardPrefs.edit(context) { putString("photo_fit", mode) } }
                val sliders = listOf(
                    Triple("Tỷ lệ nền", "photo_zoom", frame.zoom to (0.5f..3f)),
                    Triple("Chiều rộng nền", "photo_width", frame.width to (0.5f..2f)),
                    Triple("Chiều cao nền", "photo_height", frame.height to (0.5f..2f)),
                    Triple("Dịch nền theo chiều ngang", "photo_x", frame.x to (-1f..1f)),
                    Triple("Dịch nền theo chiều dọc", "photo_y", frame.y to (-1f..1f))
                )
                for ((title, pref, control) in sliders) {
                    SliderItem(
                        icon = Icons.Rounded.Tune, title = title, value = control.first, range = control.second,
                        format = { "${(it * 100).roundToInt()}%" },
                        onCommit = { v -> KeyboardPrefs.edit(context) { putFloat(pref, v) } }
                    )
                }
                ActionItem(Icons.Rounded.RestartAlt, "Đặt lại vị trí nền", null, onClick = {
                    KeyboardPrefs.edit(context) {
                        remove("photo_fit")
                        sliders.forEach { remove(it.second) }
                    }
                })
                SliderItem(
                    icon = Icons.Rounded.Brightness6, title = "Làm tối",
                    value = settings.bgDim, range = 0f..0.9f,
                    format = { "${(it * 100).roundToInt()}%" },
                    onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("bg_dim", v) } }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SliderItem(
                        icon = Icons.Rounded.BlurOn, title = "Làm mờ",
                        value = settings.bgBlurDp, range = 0f..24f,
                        format = { if (it < 0.5f) tr("Không") else "${it.roundToInt()} dp" },
                        onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("bg_blur_dp", v) } }
                    )
                }
            }
        }
    }

    picker?.let { t ->
        val current = when (t.key) {
            "seed_color" -> settings.seedColor
            "color_background" -> settings.colorBackground
            "color_key" -> settings.colorKey
            "color_key_text" -> settings.colorKeyText
            "color_func" -> settings.colorFunc
            "color_func_text" -> settings.colorFuncText
            "color_accent" -> settings.colorAccent
            else -> settings.colorAccentText
        }
        ColorPickerDialog(t.title, current, allowAuto = t.key != "seed_color", onDismiss = { picker = null }) { c ->
            KeyboardPrefs.edit(context) {
                putInt(t.key, c)
                if (t.key == "seed_color") putString("color_source", "SEED")
            }
            picker = null
        }
    }
}

// ----------------------------------------------------------------------- клавиши

@Composable
internal fun KeyboardSizePage(settings: KeyboardSettings) {
    val context = LocalContext.current
    SettingsSection("Kích thước") {
        SliderItem(
            icon = Icons.Rounded.Height, title = "Chiều cao phím",
            // на планшете (от 600 dp) клавиши можно сделать заметно выше
            value = settings.keyHeightDp, range = 32f..(if (LocalConfiguration.current.smallestScreenWidthDp >= 600) 96f else 68f),
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("key_height_dp", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.Straighten, title = "Chiều rộng bàn phím",
            value = settings.keyboardWidth, range = 0.5f..1f,
            format = { "${(it * 100).roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("keyboard_width", if (v > 0.98f) 1f else v) } }
        )
        LandscapeScaleItem(settings)
        SliderItem(
            icon = Icons.Rounded.EmojiEmotions, title = "Kích thước emoji",
            value = settings.emojiSizeDp, range = 24f..56f,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("emoji_size_dp", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.RoundedCorner, title = "Bo góc phím",
            value = settings.cornerRadiusDp, range = 6f..30f,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("corner_radius_dp", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.SpaceBar, title = "Khoảng cách giữa các phím",
            value = settings.keySpacingDp, range = 2f..8f,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("key_spacing_dp", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.Menu, title = "Khoảng cách giữa các hàng",
            value = settings.rowSpacingDp, range = 2f..12f,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("row_spacing_dp", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.Straighten, title = "Lề hai bên",
            value = settings.sidePaddingDp, range = 0f..32f,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("side_padding_dp", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.VerticalAlignBottom, title = "Lề dưới",
            value = settings.bottomOffsetDp, range = BOTTOM_OFFSET_RANGE,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("bottom_offset_dp", v) } }
        )
    }
    SettingsSection("Chế độ một tay") {
        ChoiceItem(
            Icons.Rounded.SwapHoriz, "Vị trí", "Также включается кнопкой ⇄ на панели инструментов",
            listOf("OFF" to "Tắt", "LEFT" to "Trái", "RIGHT" to "Phải"), settings.oneHanded
        ) { v -> KeyboardPrefs.edit(context) { putString("one_handed", v) } }
        SliderItem(
            icon = Icons.Rounded.Straighten, title = "Chiều rộng bàn phím",
            value = settings.oneHandedWidth, range = 0.55f..0.9f,
            format = { "${(it * 100).roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("one_handed_w", v) } }
        )
    }
    SettingsSection("Bàn phím nổi") {
        ToggleItem(
            Icons.Rounded.PictureInPictureAlt, "Bàn phím nổi",
            "Небольшое окно поверх приложения: перетаскивайте за полоску сверху. Также включается кнопкой на панели инструментов",
            settings.floating
        ) { KeyboardPrefs.edit(context) { putBoolean("floating_keyboard", it) } }
        AnimatedVisibility(settings.floating) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SliderItem(
                    icon = Icons.Rounded.Straighten, title = "Ширина плавающей клавиатуры",
                    value = settings.floatWidth, range = 0.5f..1f,
                    format = { "${(it * 100).roundToInt()}%" },
                    onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("float_width", v) } }
                )
                SliderItem(
                    icon = Icons.Rounded.Height, title = "Высота клавиш плавающей клавиатуры",
                    value = settings.floatKeyHeightDp, range = 28f..68f,
                    format = { "${it.roundToInt()} dp" },
                    onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("float_key_height_dp", v) } }
                )
            }
        }
    }
}

@Composable
internal fun KeysPage(settings: KeyboardSettings, onNavigate: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    SettingsSection("Phím và nút") {
        ToggleItem(Icons.Rounded.Dialpad, "Hàng số", null, settings.showNumberRow) {
            KeyboardPrefs.edit(context) { putBoolean("show_number_row", it) }
        }
        AnimatedVisibility(settings.showNumberRow) {
            SliderItem(
                icon = Icons.Rounded.Height, title = "Chiều cao hàng số",
                value = settings.numberRowScale, range = 0.6f..1.1f,
                format = { "${(it * 100).roundToInt()}%" },
                onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("number_row_scale", v) } }
            )
        }
        SliderItem(
            icon = Icons.Rounded.KeyboardReturn, title = "Chiều rộng phím Enter",
            value = settings.enterWidth, range = 1f..1.8f,
            format = { "${(it * 100).roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("enter_width", v) } }
        )
        ToggleItem(Icons.AutoMirrored.Rounded.Label, "Gợi ý trên phím", "Маленький символ, доступный по удержанию", settings.showHints) {
            KeyboardPrefs.edit(context) { putBoolean("show_hints", it) }
        }
        ChoiceItem(
            Icons.Rounded.PanTool, "Nhấn giữ chữ",
            if (settings.holdMode == "SYMBOLS") "Символ, который стоит на том же месте на странице «?123», — как в Gboard"
            else "Варианты буквы (ё, ъ, буквы с ударением) и символы",
            listOf("VARIANTS" to "Tùy chọn", "SYMBOLS" to "Như Gboard"), settings.holdMode
        ) { v -> KeyboardPrefs.edit(context) { putString("hold_mode", v) } }
        ToggleItem(
            Icons.Rounded.ScreenRotation, "Раздельная клавиатура в альбомной",
            "Повернули телефон — клавиатура делится на две половины по краям, до всех букв легко дотянуться",
            settings.landscapeSplit
        ) { KeyboardPrefs.edit(context) { putBoolean("landscape_split", it) } }
        ChoiceItem(
            Icons.Rounded.Interests, "Вид окна удержания", "Как выглядят символы, которые всплывают при удержании клавиши",
            listOf("SHAPE" to "Hình dạng", "CIRCLE" to "Hình tròn", "BOX" to "Hình vuông", "PANEL" to "Một bảng"), settings.popupStyle
        ) { v -> KeyboardPrefs.edit(context) { putString("popup_style", v) } }
        ToggleItem(Icons.Rounded.TouchApp, "Всплывающая буква при нажатии", null, settings.keyPreview) {
            KeyboardPrefs.edit(context) { putBoolean("key_preview", it) }
        }
        ToggleItem(Icons.Rounded.BorderOuter, "Viền phím", null, settings.showKeyBorders) {
            KeyboardPrefs.edit(context) { putBoolean("show_key_borders", it) }
        }
        ToggleItem(Icons.Rounded.SelectAll, "Thanh chỉnh sửa", "Над клавиатурой: курсор, выделение, копировать и вставить", settings.editBar) {
            KeyboardPrefs.edit(context) { putBoolean("edit_bar", it) }
        }
        ChoiceItem(
            Icons.Rounded.TouchApp, "Удержание кнопки панели функций",
            "Короткое нажатие открывает и закрывает панель инструментов",
            listOf("PIN" to "Ghim thanh công cụ", "EDIT_BAR" to "Thanh chỉnh sửa"),
            settings.toolsLongPress
        ) { v ->
            KeyboardPrefs.edit(context) {
                putString("tools_long_press", v)
                // выбор «панель редактирования» освобождает кнопку от закреплённой строки инструментов
                if (v == "EDIT_BAR") putBoolean("pinned_tools", false)
            }
        }
        ToggleItem(Icons.Rounded.Language, "Nút đổi ngôn ngữ", "Без неё язык меняется свайпом по пробелу", settings.showGlobeKey) {
            KeyboardPrefs.edit(context) { putBoolean("show_globe_key", it) }
        }
        if (settings.showGlobeKey) {
            val globeActions = listOf(
                "LANGUAGES" to "Danh sách ngôn ngữ", "SYSTEM" to "Chọn bàn phím", "EMOJI" to "Emoji", "CLIPBOARD" to "Bảng nhớ tạm",
                "VOICE" to "Nhập bằng giọng nói", "SETTINGS" to "Cài đặt", "ONE_HANDED" to "Chế độ một tay", "OFF" to "Không có"
            )
            ChoiceItem(
                Icons.Rounded.TouchApp, "Nhấn giữ nút ngôn ngữ",
                globeActions.firstOrNull { it.first == settings.globeLongPress }?.second ?: "Danh sách ngôn ngữ",
                globeActions, settings.globeLongPress
            ) { v -> KeyboardPrefs.edit(context) { putString("globe_long_press", v) } }
        }
        ToggleItem(Icons.Rounded.MoreHoriz, "Dấu phẩy và dấu chấm ở trang ?123", "По бокам пробела. Удержание — другие знаки: ; : ' \" ? ! …", settings.punctuationKeys) {
            KeyboardPrefs.edit(context) { putBoolean("punctuation_keys", it) }
        }
        ToggleItem(Icons.Rounded.EmojiEmotions, "Кнопка эмодзи", null, settings.showEmojiKey) {
            KeyboardPrefs.edit(context) { putBoolean("show_emoji_key", it) }
        }
        SpaceSlotItem(
            Icons.Rounded.SpaceBar, "Клавиша слева от пробела", settings.spaceLeftKey, "space_left_key",
            "Например, запятая. Удержание «,» — ; : ' \" и другие знаки"
        )
        SpaceSlotItem(
            Icons.Rounded.SpaceBar, "Клавиша справа от пробела", settings.spaceRightKey, "space_right_key",
            "Например, точка. Удержание «.» — ? ! … и другие знаки"
        )
        ToggleItem(Icons.Outlined.Mic, "Кнопка микрофона", null, settings.showMicButton) {
            KeyboardPrefs.edit(context) { putBoolean("show_mic_button", it) }
        }
        AnimatedVisibility(settings.showMicButton) {
            ChoiceItem(
                Icons.Rounded.TouchApp, "Кнопка справа вверху", "Что делает кнопка микрофона. Удержание — голосовой ввод",
                listOf(
                    "VOICE" to "Микрофон", "EMOJI" to "Emoji", "CLIPBOARD" to "Буфер", "TRANSLATE" to "Перевод",
                    "GIF" to "GIF", "UNDO" to "Отменить", "HIDE" to "Скрыть", "LANG" to "Đổi ngôn ngữ"
                ), settings.micAction
            ) { v -> KeyboardPrefs.edit(context) { putString("mic_action", v) } }
        }
        ToggleItem(
            Icons.Rounded.WidthFull, "Подсказкам вся ширина",
            "Пока предлагаются слова, кнопки меню и микрофона прячутся", settings.suggestionsFullWidth
        ) { KeyboardPrefs.edit(context) { putBoolean("suggestions_full_width", it) } }
        ToggleItem(
            Icons.Rounded.Dialpad, "Цифровая клавиатура для чисел",
            "В полях ввода числа и номера телефона — крупные цифры 4×4, как у системной клавиатуры",
            settings.numberPad
        ) { KeyboardPrefs.edit(context) { putBoolean("number_pad", it) } }
        ToggleItem(
            Icons.Rounded.VerticalAlignTop, "Верхняя панель",
            "Подсказки, микрофон и кнопки над клавиатурой. Если выключить, клавиатура станет ниже",
            settings.showHeader
        ) { KeyboardPrefs.edit(context) { putBoolean("show_header", it) } }
        ToggleItem(Icons.Rounded.Lightbulb, "Строка подсказок", "Если выключить, наверху всегда будут инструменты", settings.showSuggestions) {
            KeyboardPrefs.edit(context) { putBoolean("show_suggestions", it) }
        }
        ActionItem(Icons.Rounded.TouchApp, "Символы при удержании", "Свои символы на любой клавише: ғ на «г», $ на «d»", onClick = {
            onNavigate(SettingsPage.LONG_PRESS)
        })
    }
    FontSections(settings)
}

/** Своя клавиша у пробела на буквенной раскладке: нет, «,», «.» или любой символ (до 4 знаков). */
@Composable
private fun SpaceSlotItem(icon: ImageVector, title: String, value: String, prefKey: String, subtitle: String) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(false) }
    val selected = when (value) {
        "", ",", "." -> value
        else -> "custom"
    }
    ChoiceItem(
        icon, title, if (selected == "custom") tr("Своя: «{0}»", value) else subtitle,
        listOf("" to "Không", "," to ",", "." to ".", "custom" to "Своя…"), selected
    ) { v ->
        if (v == "custom") dialog = true else KeyboardPrefs.edit(context) { putString(prefKey, v) }
    }
    if (dialog) {
        TextInputDialog(title, if (selected == "custom") value else "", "Символ, буква или эмодзи — до 4 знаков", onDismiss = { dialog = false }) { v ->
            KeyboardPrefs.edit(context) { putString(prefKey, v.trim().take(4)) }
            dialog = false
        }
    }
}

// ----------------------------------------------------------------------- ввод
