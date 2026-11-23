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
// НАСТРОЙКИ: НАБОР ТЕКСТА, ЖЕСТЫ, БУФЕР ОБМЕНА, ОТКЛИК
// ============================================================================

@Composable
internal fun TypingPage(settings: KeyboardSettings, part: SettingsPage) {
    val context = LocalContext.current
    var giphyDialog by remember { mutableStateOf(false) }
    var klipyDialog by remember { mutableStateOf(false) }
    var keyDialog by remember { mutableStateOf<String?>(null) }
    var modelDialog by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val aiScope = rememberCoroutineScope()
    // поддерживает ли телефон исправление на устройстве (Gemini Nano) — для подписи
    var nanoStatus by remember { mutableStateOf<Int?>(-1) }
    LaunchedEffect(part) {
        if (part == SettingsPage.TYPING) nanoStatus = AiFix.nanoStatus(context, "en")
    }
    if (part == SettingsPage.GESTURES) SettingsSection("Tốc độ") {
        ToggleItem(Icons.Rounded.Bolt, "Nhập tức thì", "Ký tự xuất hiện ngay khi chạm, không cần nhấc ngón tay", settings.instantInput) {
            KeyboardPrefs.edit(context) { putBoolean("instant_input", it) }
        }
    }
    if (part == SettingsPage.GESTURES) SettingsSection("Gõ không nhấc ngón tay") {
        ToggleItem(Icons.Rounded.Gesture, "Gõ vuốt", "Vuốt từ chữ này sang chữ khác để nhập từ; các lựa chọn khác hiện trong gợi ý", settings.glideTyping) {
            KeyboardPrefs.edit(context) { putBoolean("glide_typing", it) }
        }
        AnimatedVisibility(settings.glideTyping) {
            ToggleItem(Icons.Rounded.Timeline, "Theo dấu ngón tay", null, settings.glideTrail) {
                KeyboardPrefs.edit(context) { putBoolean("glide_trail", it) }
            }
        }
    }
    if (part == SettingsPage.GESTURES) SettingsSection("Thao tác vuốt trên phím") {
        ToggleItem(Icons.Rounded.North, "Vuốt lên trên phím", "Nhập ký tự phụ mà không cần giữ phím", settings.swipeUpHint) {
            KeyboardPrefs.edit(context) { putBoolean("swipe_up_hint", it) }
        }
        SliderItem(
            icon = Icons.Rounded.TouchApp, title = "Độ nhạy vuốt trên phím",
            value = settings.flickSensitivity, range = 0f..1f,
            format = { "${(it * 100).roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("flick_sensitivity", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.Timer, title = "Thời gian nhấn giữ",
            value = settings.longPressMs, range = 180f..700f,
            format = { "${it.roundToInt()} " + tr("ms") },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("long_press_ms", v) } }
        )
        ToggleItem(Icons.AutoMirrored.Rounded.Backspace, "Vuốt trái trên ⌫", "Xóa cả từ", settings.backspaceSwipe) {
            KeyboardPrefs.edit(context) { putBoolean("backspace_swipe", it) }
        }
    }
    if (part == SettingsPage.GESTURES) SettingsSection("Phím cách") {
        ChoiceItem(
            Icons.Rounded.Swipe, "Vuốt trên phím cách",
            when (settings.spaceSwipe) {
                "CURSOR" -> "Vuốt trên phím cách để di chuyển con trỏ"
                "LANGUAGE" -> "Vuốt để đổi ngôn ngữ"
                "BOTH" -> "Vuốt nhanh để đổi ngôn ngữ; nhấn giữ và vuốt để di chuyển con trỏ"
                else -> "Thao tác đã tắt"
            },
            listOf("CURSOR" to "Con trỏ", "LANGUAGE" to "Ngôn ngữ", "BOTH" to "Cả hai", "OFF" to "Tắt"), settings.spaceSwipe
        ) { v -> KeyboardPrefs.edit(context) { putString("space_swipe", v) } }
        val spaceActions = listOf(
            "PICKER" to "Chọn bàn phím", "LANGUAGE" to "Đổi ngôn ngữ", "AI_FIX" to "Cải thiện văn bản", "VOICE" to "Nhập bằng giọng nói", "EMOJI" to "Emoji",
            "CLIPBOARD" to "Bảng nhớ tạm", "TOOLBAR" to "Thanh công cụ", "SETTINGS" to "Cài đặt", "OFF" to "Không có"
        )
        ChoiceItem(
            Icons.Rounded.SpaceBar, "Nhấn giữ phím cách",
            spaceActions.firstOrNull { it.first == settings.spaceLongAction }?.second ?: "Chọn bàn phím",
            spaceActions, settings.spaceLongAction
        ) { v ->
            KeyboardPrefs.edit(context) {
                putString("space_long_action", v)
                putBoolean("space_long_press", v != "OFF")
            }
        }
    }
    if (part == SettingsPage.GESTURES) SettingsSection("Enter") {
        ToggleItem(Icons.Rounded.Send, "Gửi bằng cách nhấn giữ Enter",
            "Nếu ứng dụng hỗ trợ thao tác gửi của IME", settings.enterHoldSend
        ) { KeyboardPrefs.edit(context) { putBoolean("enter_hold_send", it) } }
        if (settings.enterHoldSend) SliderItem(
            icon = Icons.Rounded.Timer, title = "Thời gian giữ Enter",
            value = settings.enterHoldMs, range = 300f..1500f,
            format = { "${it.roundToInt()} " + tr("ms") },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("enter_hold_ms", v) } }
        )
    }
    if (part == SettingsPage.TYPING) SettingsSection("Máy tính") {
        ToggleItem(Icons.Rounded.Calculate, "Hiển thị kết quả sau dấu «=»",
            "Ví dụ, 199+123= hiển thị 322. Chạm kết quả để chèn.", settings.inlineCalculator
        ) { KeyboardPrefs.edit(context) { putBoolean("inline_calculator", it) } }
    }
    if (part == SettingsPage.GESTURES) SettingsSection("Tiện ích") {
        ToggleItem(Icons.Rounded.DataArray, "Tự đóng ngoặc", "Tự đóng các ngoặc ( [ { « và đặt con trỏ bên trong", settings.autoPair) {
            KeyboardPrefs.edit(context) { putBoolean("auto_pair", it) }
        }
        ToggleItem(Icons.Rounded.SwipeDown, "Vuốt xuống để đóng", "Vuốt xuống trên hàng gợi ý để ẩn bàn phím", settings.swipeDownHide) {
            KeyboardPrefs.edit(context) { putBoolean("swipe_down_hide", it) }
        }
    }
    if (part == SettingsPage.TYPING) SettingsSection("Исправление текста ИИ") {
        InfoCard(
            Icons.Rounded.AutoFixHigh,
            "Кнопка ИИ на панели инструментов открывает панель ИИ: исправить, деловой или дружелюбный тон, короче, эмоциональнее, эмодзи, " +
                "подробнее, дописать и своё указание. Работает с выделенным текстом, а если ничего не выделено — со всем текстом в поле. " +
                "Исправлять умеют все сервисы, остальное — только ИИ по ключу. Облачные сервисы получают обрабатываемый текст."
        )
        val providers = AiProvider.entries.filter { it != AiProvider.CUSTOM }.map { it.id to it.title } +
            settings.aiCustom.map { "CUSTOM:${it.id}" to it.title }
        val nanoText = when (nanoStatus) {
            -1 -> "проверяю…"
            FEATURE_AVAILABLE -> "готово"
            FEATURE_DOWNLOADABLE, FEATURE_DOWNLOADING -> "скачается при первом исправлении"
            else -> "этот телефон не поддерживает"
        }
        ChoiceItem(
            Icons.Rounded.AutoFixHigh, "Кто исправляет",
            when (AiProvider.from(settings.aiProvider)) {
                AiProvider.AUTO -> tr("Сначала на телефоне, потом сервис с ключом, потом LanguageTool")
                AiProvider.NANO -> tr("Gemini Nano на телефоне: без интернета, английский и ещё 6 языков (без русского) — {0}", tr(nanoText))
                AiProvider.GEMINI -> tr("Google Gemini — бесплатный ключ в Google AI Studio")
                AiProvider.CLAUDE -> tr("Claude от Anthropic — платный ключ")
                AiProvider.OPENAI -> tr("OpenAI — платный ключ")
                AiProvider.GROQ -> tr("Groq — быстрый, есть бесплатный ключ")
                AiProvider.OPENROUTER -> tr("OpenRouter — одна учётка для сотен моделей, есть бесплатные")
                AiProvider.LANGUAGETOOL -> tr("Бесплатная проверка без ключа (не ИИ)")
                AiProvider.CUSTOM -> AiKeys.of(settings).selectedCustom?.let { tr("Свой сервис: {0}", it.base().substringAfter("://")) }
                    ?: tr("Свой сервис удалён — выберите другой")
            },
            providers, settings.aiProvider
        ) { v -> KeyboardPrefs.edit(context) { putString("ai_provider", v) } }
        CustomAiItems(settings)
        ActionItem(
            Icons.Rounded.Key, "Ключ Gemini",
            if (settings.geminiKey.isBlank()) "Бесплатно: aistudio.google.com → Get API key" else "Задан",
            onClick = { keyDialog = "gemini_key" }
        )
        ActionItem(
            Icons.Rounded.Key, "Ключ Claude API",
            if (settings.anthropicKey.isBlank()) "console.anthropic.com" else "Задан",
            onClick = { keyDialog = "anthropic_key" }
        )
        ActionItem(
            Icons.Rounded.Key, "Ключ OpenAI",
            if (settings.openaiKey.isBlank()) "platform.openai.com" else "Задан",
            onClick = { keyDialog = "openai_key" }
        )
        ActionItem(
            Icons.Rounded.Key, "Ключ Groq",
            if (settings.groqKey.isBlank()) "Бесплатно: console.groq.com" else "Задан",
            onClick = { keyDialog = "groq_key" }
        )
        ActionItem(
            Icons.Rounded.Key, "Ключ OpenRouter",
            if (settings.openrouterKey.isBlank()) "openrouter.ai/keys — есть бесплатные модели" else "Задан",
            onClick = { keyDialog = "openrouter_key" }
        )
        ActionItem(
            Icons.Rounded.Tune, "Модель Gemini", settings.geminiModel.ifBlank { tr("По умолчанию: {0}", AiFix.GEMINI_MODEL) },
            onClick = { modelDialog = "gemini_model" }
        )
        ActionItem(
            Icons.Rounded.Tune, "Модель Claude", settings.claudeModel.ifBlank { tr("По умолчанию: {0}", AiFix.CLAUDE_MODEL) },
            onClick = { modelDialog = "anthropic_model" }
        )
        ActionItem(
            Icons.Rounded.Tune, "Модель OpenAI", settings.openaiModel.ifBlank { tr("По умолчанию: {0}", AiFix.OPENAI_MODEL) },
            onClick = { modelDialog = "openai_model" }
        )
        ActionItem(
            Icons.Rounded.Tune, "Модель Groq", settings.groqModel.ifBlank { tr("По умолчанию: {0}", AiFix.GROQ_MODEL) },
            onClick = { modelDialog = "groq_model" }
        )
        ActionItem(
            Icons.Rounded.Tune, "Модель OpenRouter", settings.openrouterModel.ifBlank { tr("По умолчанию: {0}", AiFix.OPENROUTER_MODEL) },
            onClick = { modelDialog = "openrouter_model" }
        )
        ActionItem(
            Icons.Rounded.NetworkCheck, "Проверить подключение",
            if (testing) tr("Проверяю…") else tr("Отправит тестовую фразу выбранному сервису и покажет ответ или причину ошибки"),
            onClick = {
                if (testing) return@ActionItem
                testing = true
                val keys = AiKeys.of(settings)
                // выбранный сервис; при «Авто» и «На телефоне» — первый, у которого есть ключ
                val p = AiFix.llmOrder(keys).firstOrNull()
                if (p == null) {
                    testing = false
                    Toast.makeText(context, tr("Сначала вставьте ключ"), Toast.LENGTH_LONG).show()
                } else aiScope.launch {
                    val r = AiFix.test(context, keys, p)
                    testing = false
                    Toast.makeText(
                        context,
                        r.fold({ "${p.title}: $it" }, { "${tr("Ошибка: {0}", it.message ?: "")} · ${p.title}" }),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
        ToggleItem(
            Icons.Rounded.AutoFixHigh, "Чистая диктовка (ИИ)",
            "Как Rambler в Pixel: из голосового ввода убираются «э-э», повторы и оговорки, расставляются знаки. Нужен ключ ИИ",
            settings.aiVoiceCleanup
        ) { KeyboardPrefs.edit(context) { putBoolean("ai_voice_cleanup", it) } }
    }
    keyDialog?.let { k ->
        val (title, current, hint) = when (k) {
            "gemini_key" -> Triple("Ключ Gemini", settings.geminiKey, "Ключ с aistudio.google.com")
            "openai_key" -> Triple("Ключ OpenAI", settings.openaiKey, "Ключ с platform.openai.com")
            "groq_key" -> Triple("Ключ Groq", settings.groqKey, "Ключ с console.groq.com")
            "openrouter_key" -> Triple("Ключ OpenRouter", settings.openrouterKey, "Ключ с openrouter.ai/keys")
            else -> Triple("Ключ Claude API", settings.anthropicKey, "Ключ с console.anthropic.com")
        }
        TextInputDialog(title, current, hint, onDismiss = { keyDialog = null }) { v ->
            KeyboardPrefs.edit(context) { putString(k, SecretBox.encrypt(v.trim())) }
            keyDialog = null
        }
    }
    modelDialog?.let { k ->
        val (title, current) = when (k) {
            "gemini_model" -> "Модель Gemini" to settings.geminiModel
            "openai_model" -> "Модель OpenAI" to settings.openaiModel
            "groq_model" -> "Модель Groq" to settings.groqModel
            "openrouter_model" -> "Модель OpenRouter" to settings.openrouterModel
            else -> "Модель Claude" to settings.claudeModel
        }
        TextInputDialog(title, current, "Пусто — модель по умолчанию", onDismiss = { modelDialog = null }) { v ->
            KeyboardPrefs.edit(context) { putString(k, v.trim()) }
            modelDialog = null
        }
    }
    if (part == SettingsPage.TYPING) SettingsSection("Sửa lỗi") {
        ToggleItem(
            Icons.Rounded.Spellcheck, "Tự sửa",
            "Исправляет опечатки по пробелу с учётом того, куда попал палец, и частые ошибки («извени», «знаеш»). ⌫ сразу после — отмена",
            settings.autoCorrect
        ) {
            KeyboardPrefs.edit(context) { putBoolean("auto_correct_enabled", it) }
        }
        ToggleItem(
            Icons.Rounded.TouchApp, "Динамические зоны клавиш",
            "Как на iPhone: касание у края клавиши засчитывается за букву, которая лучше подходит к слову. Середина клавиши — всегда она сама",
            settings.dynamicZones
        ) { KeyboardPrefs.edit(context) { putBoolean("dynamic_zones", it) } }
        AnimatedVisibility(settings.autoCorrect) {
            ToggleItem(
                Icons.Rounded.Apps, "Исправлять во всех приложениях",
                "Некоторые приложения просят клавиатуру не исправлять текст. Пароли, почта и адреса не исправляются никогда",
                settings.correctEverywhere
            ) { KeyboardPrefs.edit(context) { putBoolean("correct_everywhere", it) } }
        }
        AnimatedVisibility(settings.autoCorrect) {
            ChoiceItem(
                Icons.Rounded.Tune, "Насколько смело исправлять",
                if (settings.autoCorrectStrength == "NORMAL") "Исправляет и неоднозначные опечатки — как Gboard"
                else "Только явные опечатки: соседняя клавиша, пропущенная или лишняя буква",
                listOf("SAFE" to "Осторожно", "NORMAL" to "Активно"), settings.autoCorrectStrength
            ) { v -> KeyboardPrefs.edit(context) { putString("auto_correct_strength", v) } }
        }
        val spellName = remember { SystemSpell.currentName(context) }
        ToggleItem(
            Icons.Rounded.Spellcheck, "Системная проверка правописания",
            (if (spellName != null) tr("Сейчас: {0}.", spellName) + " " else "") +
                tr("Исправления и варианты берутся из проверки телефона — обычно это словарь Gboard. Выбрать её: Настройки → Система → Язык и ввод → Проверка правописания"),
            settings.systemSpellcheck
        ) {
            KeyboardPrefs.edit(context) { putBoolean("system_spellcheck", it) }
        }
        // красное подчёркивание и меню «Добавить в словарь / Удалить» рисует само приложение (Chrome, Telegram) по
        // системной проверке; Chrome на время меню прячет клавиатуру — в поле почты было не попасть. Клавиатура это
        // меню убрать не может, но может отвести туда, где проверку выключают
        ActionItem(
            Icons.Rounded.FormatStrikethrough, "Красное подчёркивание в приложениях",
            "Если при нажатии на слово всплывает «Добавить в словарь / Удалить» и клавиатура закрывается — выключите здесь проверку правописания телефона",
            onClick = { SystemSpell.openSettings(context) }
        )
        ToggleItem(Icons.Rounded.MoreHoriz, "Знаки после пробела", "«слово ,» → «слово, » — пробел переезжает за запятую или точку", settings.smartPunctuation) {
            KeyboardPrefs.edit(context) { putBoolean("smart_punctuation", it) }
        }
        ToggleItem(Icons.Rounded.SpaceBar, "Пробел после запятой", "После «,» «!» «?» «;» «:» за словом пробел ставится сам — следующий пробел не удвоится", settings.autoSpacePunct) {
            KeyboardPrefs.edit(context) { putBoolean("auto_space_punct", it) }
        }
        ToggleItem(Icons.Rounded.KeyboardCapslock, "Заглавная в начале предложения", null, settings.autoCapitalize) {
            KeyboardPrefs.edit(context) { putBoolean("auto_capitalize", it) }
        }
        ToggleItem(Icons.Rounded.MoreHoriz, "Точка двойным пробелом", null, settings.doubleSpacePeriod) {
            KeyboardPrefs.edit(context) { putBoolean("double_space_period", it) }
        }
        ToggleItem(Icons.Rounded.HorizontalRule, "Два дефиса — длинное тире", "«--» сразу превращается в «—»", settings.dashMerge) {
            KeyboardPrefs.edit(context) { putBoolean("dash_merge", it) }
        }
    }
    if (part == SettingsPage.TYPING) SettingsSection("Quyền riêng tư") {
        ToggleItem(Icons.Rounded.VisibilityOff, "Ẩn danh", "Не учить новые слова и не запоминать буфер. Кнопка есть и на панели инструментов", settings.incognito) {
            KeyboardPrefs.edit(context) { putBoolean("incognito", it) }
        }
    }
    if (part == SettingsPage.CLIPBOARD) SettingsSection("Bảng nhớ tạm") {
        ToggleItem(
            Icons.Rounded.ContentPaste, "Lịch sử bảng nhớ tạm",
            "Chỉ lưu trên điện thoại, không mất khi đóng bàn phím. Không lưu mật khẩu", settings.clipboardHistory
        ) {
            KeyboardPrefs.edit(context) { putBoolean("clipboard_history", it) }
        }
        AnimatedVisibility(settings.clipboardHistory) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ChoiceItem(
                    Icons.Rounded.Schedule, "Thời gian lưu", "Các mục ghim luôn được lưu",
                    listOf("1H" to "Một giờ", "1D" to "Một ngày", "7D" to "Một tuần", "30D" to "Một tháng", "FOREVER" to "Luôn luôn"), settings.clipboardKeep
                ) { v -> KeyboardPrefs.edit(context) { putString("clipboard_keep", v) } }
                val unlimited = settings.clipboardSize == Int.MAX_VALUE
                ToggleItem(
                    Icons.Rounded.AllInclusive, "Không giới hạn số mục", "Mục cũ không bị xóa cho đến khi bạn tự xóa", unlimited
                ) { KeyboardPrefs.edit(context) { putInt("clipboard_size", if (it) 0 else 50) } }
                if (!unlimited) SliderItem(
                    icon = Icons.Rounded.History, title = "Số mục lưu",
                    value = settings.clipboardSize.toFloat(), range = 5f..1000f,
                    format = { "${it.roundToInt()}" },
                    onCommit = { v -> KeyboardPrefs.edit(context) { putInt("clipboard_size", v.roundToInt()) } }
                )
                ScreenshotClipItem(settings)
            }
        }
    }
    if (part == SettingsPage.CLIPBOARD) SettingsSection("GIF") {
        val builtIn = BuildConfig.KLIPY_API_KEY.isNotBlank() || BuildConfig.GIPHY_API_KEY.isNotBlank()
        if (builtIn) InfoCard(Icons.Rounded.GifBox, "Поиск GIF уже работает — свой ключ не нужен. Можно указать свой, если хотите.")
        ActionItem(
            Icons.Rounded.GifBox, "Ключ KLIPY",
            if (settings.klipyKey.isBlank()) "Бесплатно на partner.klipy.com — поиск любых GIF" else "Задан",
            onClick = { klipyDialog = true }
        )
        ActionItem(
            Icons.Rounded.GifBox, "Ключ GIPHY API",
            if (settings.giphyKey.isBlank()) "Необязательно — запасной источник" else "Задан",
            onClick = { giphyDialog = true }
        )
    }
    if (klipyDialog) {
        TextInputDialog("Ключ KLIPY", settings.klipyKey, "Ключ с partner.klipy.com", onDismiss = { klipyDialog = false }) { v ->
            KeyboardPrefs.edit(context) { putString("klipy_key", SecretBox.encrypt(v.trim())) }
            klipyDialog = false
        }
    }
    if (giphyDialog) {
        TextInputDialog("Ключ GIPHY API", settings.giphyKey, "Ключ с developers.giphy.com", onDismiss = { giphyDialog = false }) { v ->
            KeyboardPrefs.edit(context) { putString("giphy_key", SecretBox.encrypt(v.trim())) }
            giphyDialog = false
        }
    }
}

/** «Скриншоты в буфер»: переключатель и запрос доступа к фото, без которого новые скриншоты не видны. */
@Composable
private fun ScreenshotClipItem(settings: KeyboardSettings) {
    val context = LocalContext.current
    val focused = LocalWindowInfo.current.isWindowFocused
    var granted by remember { mutableStateOf(ClipStore.hasPhotoAccess(context)) }
    LaunchedEffect(focused) { if (focused) granted = ClipStore.hasPhotoAccess(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = ClipStore.hasPhotoAccess(context)
        if (granted) KeyboardPrefs.edit(context) { putBoolean("clip_screenshots", true) }
    }
    ToggleItem(
        Icons.Rounded.Screenshot, "Скриншоты в буфер",
        if (!granted) "Нужен доступ к фото — нажмите, чтобы разрешить" else "Новый скриншот сразу появляется в буфере и предлагается вставить",
        settings.clipScreenshots && granted
    ) { on ->
        if (on && !granted) launcher.launch(ClipStore.photoPermissions())
        else KeyboardPrefs.edit(context) { putBoolean("clip_screenshots", on) }
    }
}

// ----------------------------------------------------------------------- вибрация

@Composable
internal fun FeedbackPage(settings: KeyboardSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // свой звук: копируем файл к себе — доступ к выбранному файлу потом пропадает
    val soundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val out = java.io.File(context.filesDir, HapticsEngine.SOUND_FILE)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        require(bytes.size in 1..1_048_576)
                        out.writeBytes(bytes)
                    } ?: error("no input")
                    true
                }.getOrDefault(false)
            }
            if (ok) KeyboardPrefs.edit(context) {
                putLong("custom_sound_version", System.currentTimeMillis())
                putString("sound_style", KeySoundStyle.CUSTOM.id)
            }
            else Toast.makeText(context, tr("Не удалось открыть звук (нужен файл до 1 МБ)"), Toast.LENGTH_LONG).show()
        }
    }
    SettingsSection("Вибрация") {
        ToggleItem(Icons.Rounded.Vibration, "Вибрация при нажатии", null, settings.hapticsEnabled) {
            KeyboardPrefs.edit(context) { putBoolean("haptics_enabled", it) }
            if (it) HapticsEngine.preview(context, HapticMode.fromId(settings.hapticMode), settings.vibrationMs, settings.vibrationAmplitude)
        }
        AnimatedVisibility(
            visible = settings.hapticsEnabled,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ChoiceItem(
                    Icons.Rounded.TouchApp, "Характер отклика", null,
                    HapticMode.entries.map { it.id to it.title }, settings.hapticMode
                ) { id ->
                    KeyboardPrefs.edit(context) { putString("haptic_mode", id) }
                    HapticsEngine.preview(context, HapticMode.fromId(id), settings.vibrationMs, settings.vibrationAmplitude)
                }
                AnimatedVisibility(visible = settings.hapticMode == HapticMode.CUSTOM.id) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SliderItem(
                            icon = Icons.Rounded.Timer, title = "Длительность",
                            value = settings.vibrationMs, range = 1f..80f,
                            format = { "${it.roundToInt()} " + tr("ms") },
                            onDrag = { v -> HapticsEngine.preview(context, HapticMode.CUSTOM, v, settings.vibrationAmplitude) },
                            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("vibration_ms", v) } }
                        )
                        SliderItem(
                            icon = Icons.Rounded.Bolt, title = "Сила",
                            value = settings.vibrationAmplitude, range = 1f..255f,
                            format = { "${(it / 2.55f).roundToInt()}%" },
                            onDrag = { v -> HapticsEngine.preview(context, HapticMode.CUSTOM, settings.vibrationMs, v) },
                            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("vibration_amplitude", v) } }
                        )
                    }
                }
                ToggleItem(Icons.Rounded.Keyboard, "Сильнее на Shift, Enter, пробеле", null, settings.strongSpecialKeys) {
                    KeyboardPrefs.edit(context) { putBoolean("strong_special_keys", it) }
                }
                ToggleItem(Icons.Rounded.SwapVert, "Отклик при отпускании", null, settings.vibrateOnRelease) {
                    KeyboardPrefs.edit(context) { putBoolean("vibrate_on_release", it) }
                }
            }
        }
    }
    SettingsSection("Звук") {
        ToggleItem(Icons.Rounded.MusicNote, "Звук нажатия", null, settings.soundEnabled) {
            KeyboardPrefs.edit(context) { putBoolean("sound_enabled", it) }
            if (it) HapticsEngine.previewSound(context, settings)
        }
        AnimatedVisibility(settings.soundEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val styles = KeySoundStyle.entries.filter { it != KeySoundStyle.CUSTOM || settings.customSoundVersion != 0L }
                ChoiceItem(
                    Icons.Rounded.GraphicEq, "Звук клавиш", "Нажмите, чтобы послушать",
                    styles.map { it.id to it.title }, settings.soundStyle
                ) { id ->
                    KeyboardPrefs.edit(context) { putString("sound_style", id) }
                    HapticsEngine.previewSound(context, settings.copy(soundStyle = id))
                }
                SliderItem(
                    icon = Icons.Rounded.VolumeUp, title = "Громкость",
                    value = settings.soundVolume, range = 0.05f..1f,
                    format = { "${(it * 100).roundToInt()}%" },
                    onCommit = { v ->
                        KeyboardPrefs.edit(context) { putFloat("sound_volume", v) }
                        HapticsEngine.previewSound(context, settings.copy(soundVolume = v))
                    }
                )
                ActionItem(
                    Icons.Rounded.AudioFile,
                    if (settings.customSoundVersion != 0L) "Сменить свой звук" else "Свой звук нажатия",
                    "Короткий аудиофайл (до 1 МБ): mp3, ogg, wav",
                    onClick = { soundLauncher.launch("audio/*") }
                )
                if (settings.customSoundVersion != 0L) {
                    ActionItem(Icons.Rounded.DeleteOutline, "Удалить свой звук", null, onClick = {
                        java.io.File(context.filesDir, HapticsEngine.SOUND_FILE).delete()
                        KeyboardPrefs.edit(context) {
                            putLong("custom_sound_version", 0L)
                            if (settings.soundStyle == KeySoundStyle.CUSTOM.id) putString("sound_style", KeySoundStyle.SYSTEM.id)
                        }
                    })
                }
            }
        }
    }
}

// ----------------------------------------------------------------------- плагины
