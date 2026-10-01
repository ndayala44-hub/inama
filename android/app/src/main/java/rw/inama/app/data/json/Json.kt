package rw.inama.app.data.json

/**
 * Small, dependency-free JSON codec used for the bundled knowledge base, Inama API responses and
 * on-phone storage.
 *
 * Why not kotlinx.serialization? The MVP keeps the build minimal (no compiler plugin) and keeps
 * every JSON → domain mapping unit-testable on a plain JVM. All mapping goes through
 * the data.mapper package, so swapping in kotlinx.serialization or Moshi later touches only that package.
 */
sealed interface JsonValue

data class JsonString(val value: String) : JsonValue
data class JsonNumber(val value: Double) : JsonValue
data class JsonBool(val value: Boolean) : JsonValue
data object JsonNull : JsonValue

data class JsonArray(val items: List<JsonValue>) : JsonValue {
    val size: Int get() = items.size
    fun objects(): List<JsonObject> = items.filterIsInstance<JsonObject>()
    fun strings(): List<String> = items.mapNotNull { (it as? JsonString)?.value }
}

data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue {
    operator fun get(key: String): JsonValue? = fields[key]
    fun has(key: String): Boolean = fields[key] != null && fields[key] != JsonNull

    fun stringOrNull(key: String): String? = (fields[key] as? JsonString)?.value
    fun string(key: String, default: String = ""): String = stringOrNull(key) ?: default
    fun doubleOrNull(key: String): Double? = (fields[key] as? JsonNumber)?.value
    fun double(key: String, default: Double = 0.0): Double = doubleOrNull(key) ?: default
    fun int(key: String, default: Int = 0): Int = doubleOrNull(key)?.toInt() ?: default
    fun intOrNull(key: String): Int? = doubleOrNull(key)?.toInt()
    fun long(key: String, default: Long = 0L): Long = doubleOrNull(key)?.toLong() ?: default
    fun longOrNull(key: String): Long? = doubleOrNull(key)?.toLong()
    fun bool(key: String, default: Boolean = false): Boolean = (fields[key] as? JsonBool)?.value ?: default
    fun obj(key: String): JsonObject? = fields[key] as? JsonObject
    fun arr(key: String): JsonArray = (fields[key] as? JsonArray) ?: JsonArray(emptyList())
    fun strings(key: String): List<String> = arr(key).strings()
    fun objects(key: String): List<JsonObject> = arr(key).objects()

    /** Object of number values, e.g. {"holes":0.95}. */
    fun doubleMap(key: String): Map<String, Double> =
        obj(key)?.fields?.mapNotNull { (k, v) -> (v as? JsonNumber)?.let { k to it.value } }?.toMap() ?: emptyMap()
}

class JsonParseException(message: String) : Exception(message)

object Json {

    fun parse(text: String): JsonValue {
        val p = Parser(text)
        val value = p.readValue()
        p.skipWhitespace()
        if (!p.atEnd()) throw JsonParseException("Unexpected trailing content at ${p.pos}")
        return value
    }

    fun parseObject(text: String): JsonObject =
        parse(text) as? JsonObject ?: throw JsonParseException("Expected a JSON object")

    fun stringify(value: JsonValue): String = StringBuilder().also { write(it, value) }.toString()

    // ---- building values from Kotlin -------------------------------------------------------

    /** Converts Kotlin values (String, Number, Boolean, null, List, Map, JsonValue) to JSON. */
    fun of(value: Any?): JsonValue = when (value) {
        null -> JsonNull
        is JsonValue -> value
        is String -> JsonString(value)
        is Boolean -> JsonBool(value)
        is Int -> JsonNumber(value.toDouble())
        is Long -> JsonNumber(value.toDouble())
        is Float -> JsonNumber(value.toDouble())
        is Double -> JsonNumber(value)
        is Number -> JsonNumber(value.toDouble())
        is Enum<*> -> JsonString(value.name)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to of(v) })
        is Iterable<*> -> JsonArray(value.map { of(it) })
        is Array<*> -> JsonArray(value.map { of(it) })
        else -> throw IllegalArgumentException("Cannot convert ${value::class.simpleName} to JSON")
    }

    fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) -> k to of(v) })

    // ---- writer ---------------------------------------------------------------------------

    private fun write(sb: StringBuilder, value: JsonValue) {
        when (value) {
            is JsonNull -> sb.append("null")
            is JsonBool -> sb.append(if (value.value) "true" else "false")
            is JsonNumber -> {
                val d = value.value
                if (d.isNaN() || d.isInfinite()) sb.append("null")
                else if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) sb.append(d.toLong())
                else sb.append(d)
            }
            is JsonString -> writeString(sb, value.value)
            is JsonArray -> {
                sb.append('[')
                value.items.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    write(sb, v)
                }
                sb.append(']')
            }
            is JsonObject -> {
                sb.append('{')
                var first = true
                for ((k, v) in value.fields) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k)
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append(String.format("\\u%04x", ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
    }

    // ---- parser ---------------------------------------------------------------------------

    private class Parser(private val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\n' || s[pos] == '\r' || s[pos] == '\t')) pos++
        }

        fun readValue(): JsonValue {
            skipWhitespace()
            if (atEnd()) throw JsonParseException("Unexpected end of input")
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonString(readString())
                't' -> literal("true", JsonBool(true))
                'f' -> literal("false", JsonBool(false))
                'n' -> literal("null", JsonNull)
                else -> if (c == '-' || c in '0'..'9') readNumber() else throw JsonParseException("Unexpected '$c' at $pos")
            }
        }

        private fun literal(word: String, value: JsonValue): JsonValue {
            if (!s.startsWith(word, pos)) throw JsonParseException("Expected $word at $pos")
            pos += word.length
            return value
        }

        private fun readObject(): JsonObject {
            pos++ // {
            val map = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (pos < s.length && s[pos] == '}') {
                pos++
                return JsonObject(map)
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || s[pos] != '"') throw JsonParseException("Expected key at $pos")
                val key = readString()
                skipWhitespace()
                if (atEnd() || s[pos] != ':') throw JsonParseException("Expected ':' at $pos")
                pos++
                map[key] = readValue()
                skipWhitespace()
                if (atEnd()) throw JsonParseException("Unterminated object")
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return JsonObject(map)
                    }
                    else -> throw JsonParseException("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun readArray(): JsonArray {
            pos++ // [
            val list = ArrayList<JsonValue>()
            skipWhitespace()
            if (pos < s.length && s[pos] == ']') {
                pos++
                return JsonArray(list)
            }
            while (true) {
                list += readValue()
                skipWhitespace()
                if (atEnd()) throw JsonParseException("Unterminated array")
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return JsonArray(list)
                    }
                    else -> throw JsonParseException("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun readString(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonParseException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonParseException("Bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonParseException("Bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw JsonParseException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): JsonNumber {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E' || s[pos] == '+' || s[pos] == '-')) pos++
            val text = s.substring(start, pos)
            return JsonNumber(text.toDoubleOrNull() ?: throw JsonParseException("Bad number '$text' at $start"))
        }
    }
}
