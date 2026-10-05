package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.font.roboto.RobotoMono
import dev.staticsanches.kge.text.KGEFont
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.encoding.Base64

/**
 * The pure sfnt reader: the identity the `name` table declares, the `fvar` axes
 * in record order, and the declared monospaced flag.
 */
class SfntReaderTest :
    FunSpec({
        test("the bundled payloads resolve their family and subfamily names") {
            val roman = readSfntFace(payload(Roboto.romanFont))
            roman.familyName shouldBe "Roboto"
            roman.subfamilyName shouldBe "Regular"

            val italic = readSfntFace(payload(Roboto.italicFont))
            italic.familyName shouldBe "Roboto"
            italic.subfamilyName shouldBe "Italic"

            val mono = readSfntFace(payload(RobotoMono.romanFont))
            mono.familyName shouldBe "Roboto Mono"
            mono.subfamilyName shouldBe "Regular"

            val monoItalic = readSfntFace(payload(RobotoMono.italicFont))
            monoItalic.familyName shouldBe "Roboto Mono"
            monoItalic.subfamilyName shouldBe "Italic"
        }

        test("ID 16 wins over ID 1 and ID 17 wins over ID 2") {
            val face =
                readSfntFace(
                    minimalFace(
                        windowsRecord(1, "Legacy"),
                        windowsRecord(2, "Legacy Subfamily"),
                        macRecord(16, "Typographic"),
                        macRecord(17, "Typographic Subfamily"),
                    ),
                )

            face.familyName shouldBe "Typographic"
            face.subfamilyName shouldBe "Typographic Subfamily"
        }

        test("absent typographic IDs fall back to ID 1 and ID 2") {
            val face =
                readSfntFace(minimalFace(windowsRecord(1, "Legacy"), windowsRecord(2, "Legacy Subfamily")))

            face.familyName shouldBe "Legacy"
            face.subfamilyName shouldBe "Legacy Subfamily"
        }

        test("a Windows English record wins over every other record") {
            val face =
                readSfntFace(
                    minimalFace(
                        windowsRecord(2, "Regular"),
                        macRecord(1, "Mac English"),
                        windowsRecord(1, "Windows German", language = 0x0407),
                        windowsRecord(1, "Windows English"),
                    ),
                )

            face.familyName shouldBe "Windows English"
        }

        test("a Windows record of any language wins when English is absent") {
            val face =
                readSfntFace(
                    minimalFace(
                        windowsRecord(2, "Regular"),
                        macRecord(1, "Mac English"),
                        windowsRecord(1, "Windows German", language = 0x0407),
                    ),
                )

            face.familyName shouldBe "Windows German"
        }

        test("a Mac English record wins when the payload has no Windows record") {
            val face =
                readSfntFace(
                    minimalFace(
                        windowsRecord(2, "Regular"),
                        macRecord(1, "Mac French", language = 0x000C),
                        macRecord(1, "Mac English"),
                    ),
                )

            face.familyName shouldBe "Mac English"
        }

        test("an English Unicode record wins over a non-English Mac record") {
            val face =
                readSfntFace(
                    minimalFace(
                        windowsRecord(2, "Regular"),
                        macRecord(1, "Mac French", language = 0x000C),
                        unicodeRecord(1, "Unicode English", language = ENGLISH),
                    ),
                )

            face.familyName shouldBe "Unicode English"
        }

        test("an English Unicode record ranks with an English Windows record in table order") {
            val face =
                readSfntFace(
                    minimalFace(
                        windowsRecord(2, "Regular"),
                        unicodeRecord(1, "Unicode English", language = ENGLISH),
                        windowsRecord(1, "Windows English"),
                    ),
                )

            face.familyName shouldBe "Unicode English"
        }

        test("a non-English Unicode record ranks with a Windows record in table order") {
            val face =
                readSfntFace(
                    minimalFace(
                        windowsRecord(2, "Regular"),
                        unicodeRecord(1, "Unicode French", language = 0x000C),
                        windowsRecord(1, "Windows German", language = 0x0407),
                    ),
                )

            face.familyName shouldBe "Unicode French"
        }

        test("a payload without a name table is rejected") {
            shouldThrow<IllegalArgumentException> { readSfntFace(sfnt()) }
        }

        test("a payload without a family name record is rejected") {
            shouldThrow<IllegalArgumentException> { readSfntFace(minimalFace(windowsRecord(2, "Regular"))) }
        }

        test("a payload without a subfamily name record is rejected") {
            shouldThrow<IllegalArgumentException> { readSfntFace(minimalFace(windowsRecord(1, "Roboto"))) }
        }

        test("the bundled payloads expose their fvar axes in record order") {
            val roman = readSfntFace(payload(Roboto.romanFont))
            roman.axes shouldBe
                listOf(
                    axis(KGEFont.Axis.Tag.Weight, "Weight", 6_553_600, 26_214_400, 58_982_400),
                    axis(KGEFont.Axis.Tag.Width, "Width", 4_915_200, 6_553_600, 6_553_600),
                )
            readSfntFace(payload(Roboto.italicFont)).axes shouldBe roman.axes

            val mono = readSfntFace(payload(RobotoMono.romanFont))
            mono.axes shouldBe listOf(axis(KGEFont.Axis.Tag.Weight, "Weight", 6_553_600, 26_214_400, 45_875_200))
            readSfntFace(payload(RobotoMono.italicFont)).axes shouldBe mono.axes
        }

        test("an absent fvar table yields an empty axis list") {
            readSfntFace(minimalFace(windowsRecord(1, "Roboto"), windowsRecord(2, "Regular"))).axes shouldBe
                emptyList()
        }

        test("the hidden flag and a custom tag are preserved") {
            val face =
                readSfntFace(
                    faceWith(
                        fvarTable(axisRecord("XXXX", 0, 65_536, 131_072, flags = 1, nameId = 300)),
                        windowsRecord(1, "Roboto"),
                        windowsRecord(2, "Regular"),
                        windowsRecord(300, "Custom"),
                    ),
                )

            val descriptor = face.axes.single()
            descriptor.tag.raw shouldBe "XXXX"
            descriptor shouldBe
                axis(KGEFont.Axis.Tag.ofOrNull("XXXX")!!, "Custom", 0, 65_536, 131_072, hidden = true)
        }

        test("an axis with no name record falls back to its four-character tag") {
            val face =
                readSfntFace(
                    faceWith(
                        fvarTable(axisRecord("XXXX", 0, 65_536, 131_072, nameId = 999)),
                        windowsRecord(1, "Roboto"),
                        windowsRecord(2, "Regular"),
                    ),
                )

            face.axes.single().name shouldBe "XXXX"
        }

        test("an axis tag that is not four printable ASCII characters is rejected") {
            shouldThrow<IllegalArgumentException> {
                readSfntFace(
                    faceWith(
                        fvarTable(axisRecord("wgh\u0000", 0, 65_536, 131_072)),
                        windowsRecord(1, "Roboto"),
                        windowsRecord(2, "Regular"),
                    ),
                )
            }
        }

        test("fvar records that run past the table are rejected") {
            val truncated = fvarTable(axisRecord("wght", 0, 0, 0), axisRecord("wdth", 0, 0, 0)).copyOf(36)
            shouldThrow<IllegalArgumentException> {
                readSfntFace(faceWith(truncated, windowsRecord(1, "Roboto"), windowsRecord(2, "Regular")))
            }
        }

        test("the bundled payloads declare monospaced") {
            readSfntFace(payload(Roboto.romanFont)).monospaced shouldBe false
            readSfntFace(payload(Roboto.italicFont)).monospaced shouldBe false
            readSfntFace(payload(RobotoMono.romanFont)).monospaced shouldBe true
            readSfntFace(payload(RobotoMono.italicFont)).monospaced shouldBe true
        }

        test("post.isFixedPitch alone declares monospaced") {
            readSfntFace(metricFace(post = postTable(1), hhea = hheaTable(3))).monospaced shouldBe true
        }

        test("a single hmtx metric alone declares monospaced") {
            readSfntFace(metricFace(post = postTable(0), hhea = hheaTable(1))).monospaced shouldBe true
        }

        test("a proportional post flag and metric count are not monospaced") {
            readSfntFace(metricFace(post = postTable(0), hhea = hheaTable(3))).monospaced shouldBe false
        }

        test("a payload with neither post nor hhea is not monospaced") {
            readSfntFace(minimalFace(windowsRecord(1, "Roboto"), windowsRecord(2, "Regular"))).monospaced shouldBe
                false
        }

        test("a collection payload is rejected rather than read at face zero") {
            val collection =
                collectionPayload(nameTable(windowsRecord(1, "Roboto"), windowsRecord(2, "Regular")))

            shouldThrow<IllegalArgumentException> { readSfntFace(collection) }
        }

        test("a table directory entry that leaves the payload is rejected") {
            val face = minimalFace(windowsRecord(1, "Roboto"), windowsRecord(2, "Regular"))
            val entry = directoryEntry(face, "name")

            shouldThrow<IllegalArgumentException> { readSfntFace(face.patchU32(entry + 8, face.size + 4)) }
        }

        test("a truncated table directory is rejected") {
            val truncated =
                BigEndianWriter()
                    .apply {
                        u32(0x00010000)
                        u16(2)
                        u16(0)
                        u16(0)
                        u16(0)
                        ascii("name")
                        u32(0)
                        u32(28)
                        u32(4)
                    }.toByteArray()

            shouldThrow<IllegalArgumentException> { readSfntFace(truncated) }
        }
    })

private const val WINDOWS = 3
private const val MAC = 1
private const val UNICODE = 0
private const val ENGLISH = 0x0409

private fun payload(chunks: List<String>): ByteArray = Base64.decode(chunks.joinToString(""))

private class NameEntry(
    val platform: Int,
    val language: Int,
    val id: Int,
    val text: String,
)

private fun windowsRecord(
    id: Int,
    text: String,
    language: Int = ENGLISH,
): NameEntry = NameEntry(WINDOWS, language, id, text)

private fun macRecord(
    id: Int,
    text: String,
    language: Int = 0,
): NameEntry = NameEntry(MAC, language, id, text)

private fun unicodeRecord(
    id: Int,
    text: String,
    language: Int = 0,
): NameEntry = NameEntry(UNICODE, language, id, text)

/** A payload whose only table is the `name` table the records build. */
private fun minimalFace(vararg records: NameEntry): ByteArray = sfnt("name" to nameTable(*records))

/** A payload with a `name` table and the given synthetic `fvar` table. */
private fun faceWith(
    fvar: ByteArray,
    vararg records: NameEntry,
): ByteArray = sfnt("name" to nameTable(*records), "fvar" to fvar)

/** A payload with a `name` table and whichever of `post`/`hhea` is given. */
private fun metricFace(
    post: ByteArray? = null,
    hhea: ByteArray? = null,
): ByteArray {
    val tables = mutableListOf("name" to nameTable(windowsRecord(1, "Roboto"), windowsRecord(2, "Regular")))
    if (post != null) tables += "post" to post
    if (hhea != null) tables += "hhea" to hhea
    return sfnt(*tables.toTypedArray())
}

private fun postTable(isFixedPitch: Int): ByteArray =
    BigEndianWriter()
        .apply {
            u32(0x00020000)
            u32(0)
            u16(0)
            u16(0)
            u32(isFixedPitch)
        }.toByteArray()

private fun hheaTable(numberOfHMetrics: Int): ByteArray =
    BigEndianWriter()
        .apply {
            u32(0x00010000)
            repeat(15) { u16(0) }
            u16(numberOfHMetrics)
        }.toByteArray()

/** A `ttcf` collection header whose first 28 bytes double as a readable table directory. */
private fun collectionPayload(table: ByteArray): ByteArray =
    BigEndianWriter()
        .apply {
            ascii("ttcf")
            u16(1) // majorVersion, seen as numTables by a reader that skips the check
            u16(0) // minorVersion
            u32(1) // numFonts
            ascii("name") // offsetTable[0], seen as a directory tag
            u32(0) // checksum
            u32(28) // offset
            u32(table.size) // length
            bytes(table)
        }.toByteArray()

private fun directoryEntry(
    payload: ByteArray,
    tag: String,
): Int {
    val count = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
    for (index in 0 until count) {
        val at = 12 + 16 * index
        val candidate = CharArray(4) { (payload[at + it].toInt() and 0xFF).toChar() }.concatToString()
        if (candidate == tag) return at
    }
    error("no $tag table in the synthetic payload")
}

private fun ByteArray.patchU32(
    offset: Int,
    value: Int,
): ByteArray {
    val copy = copyOf()
    copy[offset] = (value ushr 24).toByte()
    copy[offset + 1] = (value ushr 16).toByte()
    copy[offset + 2] = (value ushr 8).toByte()
    copy[offset + 3] = value.toByte()
    return copy
}

private fun axis(
    tag: KGEFont.Axis.Tag,
    name: String,
    min: Int,
    default: Int,
    max: Int,
    hidden: Boolean = false,
): KGEFont.Axis =
    KGEFont.Axis(
        tag = tag,
        name = name,
        min = KGEFont.Axis.Value.of(min),
        default = KGEFont.Axis.Value.of(default),
        max = KGEFont.Axis.Value.of(max),
        hidden = hidden,
    )

private class AxisEntry(
    val tag: String,
    val min: Int,
    val default: Int,
    val max: Int,
    val flags: Int,
    val nameId: Int,
)

private fun axisRecord(
    tag: String,
    min: Int,
    default: Int,
    max: Int,
    flags: Int = 0,
    nameId: Int = 0,
): AxisEntry = AxisEntry(tag, min, default, max, flags, nameId)

private fun fvarTable(vararg axes: AxisEntry): ByteArray {
    val table = BigEndianWriter()
    table.u16(1) // majorVersion
    table.u16(0) // minorVersion
    table.u16(16) // axesArrayOffset
    table.u16(2) // reserved
    table.u16(axes.size) // axisCount
    table.u16(20) // axisSize
    table.u16(0) // instanceCount
    table.u16(0) // instanceSize
    for (entry in axes) {
        table.ascii(entry.tag)
        table.u32(entry.min)
        table.u32(entry.default)
        table.u32(entry.max)
        table.u16(entry.flags)
        table.u16(entry.nameId)
    }
    return table.toByteArray()
}

private fun nameTable(vararg records: NameEntry): ByteArray {
    val encoded = records.map { it to it.encoded() }
    val storage = BigEndianWriter()
    val offsets =
        encoded.map { (_, bytes) ->
            val offset = storage.size
            storage.bytes(bytes)
            offset
        }

    val table = BigEndianWriter()
    table.u16(0)
    table.u16(records.size)
    table.u16(6 + 12 * records.size)
    encoded.forEachIndexed { index, (record, bytes) ->
        table.u16(record.platform)
        table.u16(if (record.platform == MAC) 0 else 1)
        table.u16(record.language)
        table.u16(record.id)
        table.u16(bytes.size)
        table.u16(offsets[index])
    }
    table.bytes(storage.toByteArray())
    return table.toByteArray()
}

private fun NameEntry.encoded(): ByteArray {
    val writer = BigEndianWriter()
    if (platform == WINDOWS || platform == UNICODE) writer.utf16be(text) else writer.ascii(text)
    return writer.toByteArray()
}

/** The offset table and directory of a minimal big-endian sfnt payload. */
private fun sfnt(vararg tables: Pair<String, ByteArray>): ByteArray {
    val sorted = tables.sortedBy { it.first }
    var next = 12 + 16 * sorted.size
    val placed =
        sorted.map { (tag, bytes) ->
            val offset = (next + 3) and -4
            next = offset + bytes.size
            Triple(tag, offset, bytes)
        }

    val writer = BigEndianWriter()
    writer.u32(0x00010000)
    writer.u16(placed.size)
    writer.u16(0)
    writer.u16(0)
    writer.u16(0)
    for ((tag, offset, bytes) in placed) {
        writer.ascii(tag)
        writer.u32(0)
        writer.u32(offset)
        writer.u32(bytes.size)
    }
    for ((_, _, bytes) in placed) {
        writer.pad(4)
        writer.bytes(bytes)
    }
    return writer.toByteArray()
}

private class BigEndianWriter {
    private val out = ArrayList<Byte>()

    val size: Int get() = out.size

    fun u8(value: Int) {
        out.add(value.toByte())
    }

    fun u16(value: Int) {
        u8(value ushr 8)
        u8(value)
    }

    fun u32(value: Int) {
        u16(value ushr 16)
        u16(value)
    }

    fun ascii(text: String) {
        text.forEach { u8(it.code) }
    }

    fun utf16be(text: String) {
        text.forEach { u16(it.code) }
    }

    fun bytes(data: ByteArray) {
        data.forEach { out.add(it) }
    }

    fun pad(alignment: Int) {
        while (size % alignment != 0) out.add(0)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}
