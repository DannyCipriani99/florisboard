/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.additions

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Small math expression evaluator used by the calculator panel and the "=" auto calculation.
 *
 * Supports + - * / × ÷ ^ ( ) % decimals (also Arabic-Indic digits and the Arabic decimal / thousands separators),
 * `√`, `π`, `e`, `ans` and the functions sqrt, cbrt, abs, sin, cos, tan, asin, acos, atan, ln, log, exp.
 * `%` after a number divides it by 100; in `a + b%` / `a - b%` it is a percentage of `a`.
 */
object CalculatorEngine {
    private const val ARABIC_ZERO = '٠'
    private const val EXTENDED_ARABIC_ZERO = '۰'

    var lastAnswer: Double = 0.0

    /** Converts Arabic-Indic digits and separators to plain ASCII. */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append('0' + (ch - ARABIC_ZERO))
                in '۰'..'۹' -> sb.append('0' + (ch - EXTENDED_ARABIC_ZERO))
                '٫' -> sb.append('.')
                '٬' -> {} // Arabic thousands separator
                '×', '✕', '⋅', '·' -> sb.append('*')
                '÷', '∕' -> sb.append('/')
                '−', '–', '—' -> sb.append('-')
                '،' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private class ParseException(message: String) : Exception(message)

    private class Parser(val src: String) {
        var pos = 0

        fun parse(): Double {
            val v = expression()
            skipSpaces()
            if (pos != src.length) throw ParseException("unexpected '${src[pos]}'")
            return v
        }

        private fun skipSpaces() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skipSpaces()
            return src.getOrNull(pos)
        }

        private fun expression(): Double {
            var left = term()
            while (true) {
                when (peek()) {
                    '+' -> {
                        pos++
                        left += termWithPercent(left)
                    }
                    '-' -> {
                        pos++
                        left -= termWithPercent(left)
                    }
                    else -> return left
                }
            }
        }

        /** In `a + b%` the percent is relative to `a`. */
        private fun termWithPercent(base: Double): Double {
            val start = pos
            val value = term()
            val text = src.substring(start, pos).trimEnd()
            return if (text.endsWith("%") && !text.contains('*') && !text.contains('/')) {
                base * (value * 100) / 100
            } else {
                value
            }
        }

        private fun term(): Double {
            var left = power()
            while (true) {
                when (peek()) {
                    '*' -> { pos++; left *= power() }
                    '/' -> { pos++; left /= power() }
                    '(', 'π', '√' -> left *= power() // implicit multiplication: 2(3), 2π
                    else -> {
                        if (peek()?.isLetter() == true) left *= power() else return left
                    }
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            if (peek() == '^') {
                pos++
                return base.pow(powerExponent())
            }
            return base
        }

        private fun powerExponent(): Double {
            // right associative
            val base = unary()
            if (peek() == '^') {
                pos++
                return base.pow(powerExponent())
            }
            return base
        }

        private fun unary(): Double {
            return when (peek()) {
                '-' -> { pos++; -unary() }
                '+' -> { pos++; unary() }
                else -> postfix()
            }
        }

        private fun postfix(): Double {
            var v = primary()
            while (true) {
                when (peek()) {
                    '%' -> { pos++; v /= 100.0 }
                    '!' -> { pos++; v = factorial(v) }
                    else -> return v
                }
            }
        }

        private fun factorial(v: Double): Double {
            if (v < 0 || v != Math.floor(v) || v > 170) throw ParseException("factorial")
            var r = 1.0
            for (i in 2..v.toInt()) r *= i
            return r
        }

        private fun primary(): Double {
            val c = peek() ?: throw ParseException("unexpected end")
            return when {
                c == '(' -> {
                    pos++
                    val v = expression()
                    if (peek() == ')') pos++ else throw ParseException("missing )")
                    v
                }
                c == 'π' -> { pos++; PI }
                c == '√' -> { pos++; sqrt(unary()) }
                c.isDigit() || c == '.' -> number()
                c.isLetter() -> identifier()
                else -> throw ParseException("unexpected '$c'")
            }
        }

        private fun number(): Double {
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E') &&
                pos + 1 < src.length && (src[pos + 1].isDigit() || src[pos + 1] == '-' && pos + 2 < src.length && src[pos + 2].isDigit())
            ) {
                pos += 2
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            return src.substring(start, pos).toDoubleOrNull() ?: throw ParseException("number")
        }

        private fun identifier(): Double {
            val start = pos
            while (pos < src.length && src[pos].isLetter()) pos++
            val name = src.substring(start, pos).lowercase()
            return when (name) {
                "pi" -> PI
                "e" -> E
                "ans" -> lastAnswer
                else -> {
                    if (peek() != '(') throw ParseException("unknown $name")
                    pos++
                    val arg = expression()
                    if (peek() == ')') pos++ else throw ParseException("missing )")
                    when (name) {
                        "sqrt" -> sqrt(arg)
                        "cbrt" -> cbrt(arg)
                        "abs" -> abs(arg)
                        "sin" -> sin(arg)
                        "cos" -> cos(arg)
                        "tan" -> tan(arg)
                        "asin" -> asin(arg)
                        "acos" -> acos(arg)
                        "atan" -> atan(arg)
                        "ln" -> ln(arg)
                        "log" -> log10(arg)
                        "exp" -> exp(arg)
                        else -> throw ParseException("unknown $name")
                    }
                }
            }
        }
    }

    /** Evaluates [expression]; returns null if it is not a valid math expression. */
    fun evaluate(expression: String): Double? {
        val normalized = normalize(expression).trim()
        if (normalized.isEmpty()) return null
        return try {
            val v = Parser(normalized).parse()
            if (v.isNaN() || v.isInfinite()) null else v
        } catch (_: Exception) {
            null
        }
    }

    /** Formats a result without floating point noise: 0.1+0.2 -> 0.3, 5.0 -> 5. */
    fun format(value: Double, arabicDigits: Boolean = false): String {
        var text = BigDecimal(value).round(MathContext(12)).stripTrailingZeros().let {
            if (it.scale() < 0 && it.precision() - it.scale() <= 15) it.setScale(0) else it
        }.toPlainString()
        if (text == "-0") text = "0"
        if (arabicDigits) {
            text = text.map { if (it in '0'..'9') ARABIC_ZERO + (it - '0') else if (it == '.') '٫' else it }
                .joinToString("")
        }
        return text
    }

    private fun usesArabicDigits(s: String) = s.any { it in '٠'..'٩' || it in '۰'..'۹' }

    private val allowedChar = Regex("[0-9٠-٩۰-۹.٫+\\-−–*×÷/^%()!√π\\sa-zA-Z]")

    /**
     * Looks at the end of [textBefore] (the text in front of an "=") and returns the formatted result of the longest
     * trailing math expression, or null if there is none. A valid expression needs at least one operator.
     */
    fun solveTrailing(textBefore: String): String? {
        val line = textBefore.substringAfterLast('\n')
        var start = line.length
        while (start > 0 && allowedChar.matches(line[start - 1].toString())) start--
        val candidate = line.substring(start)
        // try the longest candidate first, then drop leading characters until it parses
        var from = 0
        while (from < candidate.length) {
            val piece = candidate.substring(from).trim()
            if (piece.isNotEmpty() && hasOperator(piece)) {
                evaluate(piece)?.let {
                    lastAnswer = it
                    return format(it, usesArabicDigits(piece))
                }
            }
            from++
            while (from < candidate.length && candidate[from].isWhitespace()) from++
        }
        return null
    }

    private fun hasOperator(s: String): Boolean {
        val body = s.trimStart('-', '+')
        return body.any { it in "+-*/^×÷−–%√!" } || Regex("[a-zA-Z]+\\(").containsMatchIn(body)
    }

    /**
     * Finds every `expression =` (optionally followed by an old result) in [text] and returns the text with the
     * results filled in, plus the number of solved expressions.
     */
    fun solveAll(text: String): Pair<String, Int> {
        val sb = StringBuilder(text)
        var count = 0
        var idx = sb.length - 1
        while (idx >= 0) {
            if (sb[idx] == '=') {
                val before = sb.substring(0, idx)
                val result = solveTrailing(before)
                if (result != null) {
                    // remove an old result after "=" (spaces + number)
                    var end = idx + 1
                    while (end < sb.length && sb[end] == ' ') end++
                    val oldStart = end
                    while (end < sb.length && (sb[end].isDigit() || sb[end] in ".,٫-−" || sb[end] in '٠'..'٩')) end++
                    if (end > oldStart) sb.delete(idx + 1, end) else sb.delete(idx + 1, oldStart)
                    sb.insert(idx + 1, " $result")
                    count++
                }
            }
            idx--
        }
        return sb.toString() to count
    }
}
