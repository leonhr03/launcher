package com.cleanlauncher.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.ui.BlurredBackdrop
import com.cleanlauncher.ui.frosted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.roundToInt

/** Eigener iOS-Code (nur gesalzener Hash wird gespeichert). */
object Passcode {
    private fun hash(code: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + ":" + code).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun isSet(prefs: Prefs) = prefs.lockCodeHash.isNotEmpty()

    fun set(prefs: Prefs, code: String) {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = bytes.joinToString("") { "%02x".format(it) }
        prefs.lockCodeSalt = salt
        prefs.lockCodeHash = hash(code, salt)
        prefs.lockCodeLength = code.length
    }

    fun clear(prefs: Prefs) {
        prefs.lockCodeHash = ""
        prefs.lockCodeSalt = ""
    }

    fun check(prefs: Prefs, code: String) = isSet(prefs) && hash(code, prefs.lockCodeSalt) == prefs.lockCodeHash
}

/**
 * iOS-Codeeingabe: Punkte oben, runde Glas-Tasten, Fehler = Schütteln.
 * [onSuccess] wird nach richtigem Code aufgerufen, [onCancel] bei „Abbrechen“.
 */
@Composable
fun PasscodeScreen(
    prefs: Prefs,
    title: String = "Code eingeben",
    length: Int = prefs.lockCodeLength.coerceIn(4, 8),
    resetTick: Int = 0,
    onCode: (String) -> Boolean,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
) {
    var entered by remember(resetTick) { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    var blockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(blockedUntil) {
        while (System.currentTimeMillis() < blockedUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val blocked = now < blockedUntil

    fun press(d: String) {
        if (blocked || entered.length >= length) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        entered += d
        if (entered.length == length) {
            val code = entered
            scope.launch {
                delay(120)
                if (onCode(code)) {
                    onSuccess()
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    wrong++
                    if (wrong >= 5) {
                        blockedUntil = System.currentTimeMillis() + 30_000L * (wrong - 4)
                    }
                    for (x in listOf(30f, -30f, 20f, -20f, 10f, 0f)) shake.animateTo(x, tween(45))
                    entered = ""
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        BlurredBackdrop(0.45f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(90.dp))
            Text(
                if (blocked) "Bitte warten (${((blockedUntil - now) / 1000) + 1} s)" else title,
                color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                repeat(length) { i ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .then(
                                if (i < entered.length) Modifier.background(Color.White)
                                else Modifier.border(1.5.dp, Color.White, CircleShape),
                            ),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val rows = listOf(
                listOf("1" to "", "2" to "A B C", "3" to "D E F"),
                listOf("4" to "G H I", "5" to "J K L", "6" to "M N O"),
                listOf("7" to "P Q R S", "8" to "T U V", "9" to "W X Y Z"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                rows.forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        r.forEach { (d, letters) -> Key(prefs, d, letters) { press(d) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    Spacer(Modifier.size(78.dp))
                    Key(prefs, "0", "") { press("0") }
                    Spacer(Modifier.size(78.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 44.dp)) {
                Text(
                    "Abbrechen",
                    color = Color.White, fontSize = 17.sp,
                    modifier = Modifier.clickable { onCancel() }.padding(8.dp),
                )
                Spacer(Modifier.weight(1f))
                if (entered.isNotEmpty()) {
                    Text(
                        "Löschen",
                        color = Color.White, fontSize = 17.sp,
                        modifier = Modifier.clickable { entered = entered.dropLast(1) }.padding(8.dp),
                    )
                } else {
                    Spacer(Modifier.width(80.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Key(prefs: Prefs, digit: String, letters: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(78.dp)
            .frosted(CircleShape, Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.2f), prefs.glass)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Normal)
            if (letters.isNotEmpty()) {
                Text(letters, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            }
        }
    }
}
