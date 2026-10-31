package app.quadro.monitor

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Salvaschermo dell'app: dopo alcuni minuti senza toccare lo schermo mostra un fondo nero con l'ora,
 * che cambia posizione ogni minuto (per non segnare gli schermi OLED) e abbassa la luminosità.
 * In alternativa alterna le schede create ogni 30 secondi. Un tocco o il tasto indietro lo chiudono. minutes = 0 lo disattiva.
 * Tutto il resto dell'app continua a funzionare sotto (letture, avvisi).
 */
@Composable
fun ScreensaverHost(minutes: Int, brightness: Float, rotate: Boolean, onRotate: () -> Unit, content: @Composable () -> Unit) {
    val rotateNow by rememberUpdatedState(onRotate)
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var active by remember { mutableStateOf(false) }

    LaunchedEffect(minutes) {
        lastTouch = System.currentTimeMillis()
        active = false
        while (minutes > 0) {
            delay(5_000)
            if (!active && System.currentTimeMillis() - lastTouch >= minutes * 60_000L) active = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch = System.currentTimeMillis()
                    }
                }
            },
    ) {
        content()
        if (active && minutes > 0) {
            val dismiss = { active = false; lastTouch = System.currentTimeMillis() }
            BackHandler { dismiss() }
            if (rotate) {
                // Le schede si alternano ogni 30 secondi; il primo tocco chiude e non preme nulla.
                DimScreen(brightness)
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(30_000)
                        rotateNow()
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismiss() },
                )
            } else {
                DimScreen(brightness)
                ClockScreen(onDismiss = dismiss)
            }
        }
    }
}

@Composable
private fun DimScreen(brightness: Float) {
    val view = LocalView.current
    DisposableEffect(brightness) {
        val window = view.context.findActivity()?.window
        val params = window?.attributes
        if (window != null && params != null) {
            params.screenBrightness = brightness.coerceIn(0.01f, 1f)
            window.attributes = params
        }
        onDispose {
            if (window != null) {
                val p = window.attributes
                p.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = p
            }
        }
    }
}

@Composable
private fun ClockScreen(onDismiss: () -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L + 50L)
        }
    }
    val minuteIndex = (now / 60_000L).toInt()
    // Posizione che cambia ogni minuto, sempre dentro lo schermo.
    val dx = ((minuteIndex * 37) % 61 - 30).dp
    val dy = ((minuteIndex * 53) % 161 - 80).dp
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.offset(dx, dy), horizontalAlignment = Alignment.CenterHorizontally) {
            M3Text(fmtTime(now, "HH:mm"), fontSize = 64.sp, color = Color(0xFF6B6B6B))
            M3Text(fmtTime(now, "EEEE d MMMM"), fontSize = 16.sp, color = Color(0xFF4A4A4A))
        }
    }
}
