package com.ghostmago.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostmago.app.data.AppState

/**
 * Natural iOS-style calculator (LTR digits).
 * Unlock vault: type PIN then = (default 0000, changeable in settings).
 */
@Composable
fun CalculatorDecoyScreen(onUnlocked: () -> Unit) {
    // Force LTR so numbers and operators render correctly under app-wide RTL
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        CalculatorBody(onUnlocked)
    }
}

@Composable
private fun CalculatorBody(onUnlocked: () -> Unit) {
    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    var acc by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<Char?>(null) }
    var typingNew by remember { mutableStateOf(true) }
    var pinBuf by remember { mutableStateOf("") }

    fun opSymbol(o: Char): String = when (o) {
        '+' -> "+"
        '-' -> "−"
        '*' -> "×"
        '/' -> "÷"
        else -> ""
    }

    fun fmt(n: Double): String {
        if (n.isNaN() || n.isInfinite()) return "Error"
        val asLong = n.toLong()
        return if (n == asLong.toDouble() && n in -1e15..1e15) {
            asLong.toString()
        } else {
            var s = String.format(java.util.Locale.US, "%.10f", n).trimEnd('0').trimEnd('.')
            if (s.length > 12) s = String.format(java.util.Locale.US, "%.6g", n)
            s
        }
    }

    fun apply(a: Double, b: Double, o: Char): Double = when (o) {
        '+' -> a + b
        '-' -> a - b
        '*' -> a * b
        '/' -> if (b == 0.0) Double.NaN else a / b
        else -> b
    }

    fun refreshExpr(a: Double?, o: Char?) {
        expression = if (a != null && o != null) "${fmt(a)} ${opSymbol(o)}" else ""
    }

    fun onDigit(d: String) {
        pinBuf = (pinBuf + d).takeLast(16)
        if (typingNew || display == "0" || display == "Error") {
            display = d
            typingNew = false
        } else if (display.length < 14) {
            display += d
        }
    }

    fun onDot() {
        if (typingNew || display == "Error") {
            display = "0."
            typingNew = false
            return
        }
        if (!display.contains('.')) display += "."
    }

    fun onOp(next: Char) {
        try {
            val v = display.replace(',', '.').toDouble()
            if (acc != null && op != null && !typingNew) {
                val r = apply(acc!!, v, op!!)
                display = fmt(r)
                acc = r
            } else {
                acc = v
            }
            op = next
            typingNew = true
            pinBuf = ""
            refreshExpr(acc, op)
        } catch (_: Exception) {
            display = "Error"
            expression = ""
            acc = null
            op = null
            typingNew = true
            pinBuf = ""
        }
    }

    fun onEquals() {
        val pin = AppState.vaultPin.ifBlank { "0000" }
        if (pinBuf == pin || display == pin) {
            pinBuf = ""
            onUnlocked()
            return
        }
        try {
            val v = display.replace(',', '.').toDouble()
            if (acc != null && op != null) {
                val r = apply(acc!!, v, op!!)
                expression = "${fmt(acc!!)} ${opSymbol(op!!)} ${fmt(v)} ="
                display = fmt(r)
            }
            acc = null
            op = null
            typingNew = true
            pinBuf = ""
        } catch (_: Exception) {
            display = "Error"
            expression = ""
            acc = null
            op = null
            typingNew = true
            pinBuf = ""
        }
    }

    fun onClear() {
        display = "0"
        expression = ""
        acc = null
        op = null
        typingNew = true
        pinBuf = ""
    }

    fun onSign() {
        try {
            if (display != "0" && display != "Error") {
                display = fmt(-display.replace(',', '.').toDouble())
            }
        } catch (_: Exception) {
        }
        pinBuf = ""
    }

    fun onPercent() {
        try {
            display = fmt(display.replace(',', '.').toDouble() / 100.0)
            typingNew = true
        } catch (_: Exception) {
        }
        pinBuf = ""
    }

    val bgScreen = Color(0xFF000000)
    val bgNum = Color(0xFF333333)
    val bgFn = Color(0xFFA5A5A5)
    val bgOp = Color(0xFFFF9F0A)
    val white = Color(0xFFFFFFFF)
    val black = Color(0xFF000000)
    val gray = Color(0xFFA0A0A0)

    Column(
        Modifier
            .fillMaxSize()
            .background(bgScreen)
            .padding(horizontal = 14.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Display panel
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            if (expression.isNotBlank()) {
                Text(
                    text = expression,
                    color = gray,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = display,
                color = white,
                fontSize = when {
                    display.length > 11 -> 36.sp
                    display.length > 8 -> 44.sp
                    else -> 64.sp
                },
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val rows = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "=")
        )

        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { key ->
                    val isZero = key == "0"
                    val isOp = key in listOf("÷", "×", "−", "+", "=")
                    val isFn = key in listOf("C", "±", "%")
                    val bg = when {
                        isOp -> bgOp
                        isFn -> bgFn
                        else -> bgNum
                    }
                    val fg = if (isFn) black else white

                    Button(
                        onClick = {
                            when (key) {
                                "C" -> onClear()
                                "±" -> onSign()
                                "%" -> onPercent()
                                "÷" -> onOp('/')
                                "×" -> onOp('*')
                                "−" -> onOp('-')
                                "+" -> onOp('+')
                                "=" -> onEquals()
                                "." -> onDot()
                                else -> onDigit(key)
                            }
                        },
                        modifier = Modifier
                            .weight(if (isZero) 2.1f else 1f)
                            .aspectRatio(if (isZero) 2.1f else 1f),
                        shape = if (isZero) RoundedCornerShape(40.dp) else CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = bg,
                            contentColor = fg,
                            disabledContainerColor = bg,
                            disabledContentColor = fg
                        ),
                        contentPadding = PaddingValues(0.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = if (isZero) Alignment.CenterStart else Alignment.Center
                        ) {
                            Text(
                                text = key,
                                color = fg,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center,
                                modifier = if (isZero) Modifier.padding(start = 28.dp) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}
