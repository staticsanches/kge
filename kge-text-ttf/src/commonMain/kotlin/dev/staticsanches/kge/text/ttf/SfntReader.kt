@file:Suppress("ktlint:standard:filename")

package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.text.KGEFont

/** A payload's identity and its variation axes, read from the sfnt tables. */
internal class SfntFace(
    val familyName: String,
    val subfamilyName: String,
    val monospaced: Boolean,
    /** Descriptors in `fvar` record order. */
    val axes: List<KGEFont.Axis>,
)

/** Reads [bytes] as one sfnt face; throws [IllegalArgumentException] when it is not one. */
internal fun readSfntFace(bytes: ByteArray): SfntFace {
    val tables = readTableDirectory(bytes)
    val names = readNames(bytes, tables["name"] ?: throw IllegalArgumentException("the payload has no name table"))
    val familyName =
        requireNotNull(names.value(16) ?: names.value(1)) {
            "the name table has no family name record (ID 1)"
        }
    val subfamilyName =
        requireNotNull(names.value(17) ?: names.value(2)) {
            "the name table has no subfamily name record (ID 2)"
        }
    return SfntFace(
        familyName = familyName,
        subfamilyName = subfamilyName,
        monospaced = bytes.isFixedPitch(tables["post"]) || bytes.numberOfHMetrics(tables["hhea"]) == 1,
        axes = readAxes(bytes, tables["fvar"], names),
    )
}

private const val WINDOWS_PLATFORM = 3
private const val MAC_PLATFORM = 1
private const val UNICODE_PLATFORM = 0
private const val WINDOWS_ENGLISH = 0x0409
private const val MAC_ENGLISH = 0
private const val HIDDEN_FLAG = 0x0001
private const val AXIS_RECORD_SIZE = 20
private const val COLLECTION_MAGIC = 0x74746366

private class Table(
    val offset: Int,
    val length: Int,
)

private fun readTableDirectory(bytes: ByteArray): Map<String, Table> {
    require(bytes.size >= 12) { "the payload is too short to be an sfnt face: ${bytes.size} bytes" }
    require(bytes.int32(0) != COLLECTION_MAGIC) {
        "the payload is a font collection (ttcf), not a single sfnt face"
    }
    val count = bytes.u16(4)
    require(12 + 16 * count <= bytes.size) {
        "the table directory is truncated: $count records do not fit in ${bytes.size} bytes"
    }
    val tables = LinkedHashMap<String, Table>(count)
    for (index in 0 until count) {
        val at = 12 + 16 * index
        val tag = bytes.ascii(at, 4)
        val offset = bytes.uint32(at + 8)
        val length = bytes.uint32(at + 12)
        require(offset + length <= bytes.size.toLong()) {
            "the $tag table runs past the payload: $offset + $length exceeds ${bytes.size} bytes"
        }
        tables[tag] = Table(offset.toInt(), length.toInt())
    }
    return tables
}

private class NameRecord(
    val platform: Int,
    val language: Int,
    val nameId: Int,
    val text: String,
) {
    /** The record's rank; a lower rank wins, ties keep table order. */
    val preference: Int
        get() {
            val preferred = platform == WINDOWS_PLATFORM || platform == UNICODE_PLATFORM
            return when {
                preferred && language == WINDOWS_ENGLISH -> 0
                preferred -> 1
                platform == MAC_PLATFORM && language == MAC_ENGLISH -> 2
                else -> 3
            }
        }
}

private class NameTable(
    private val records: List<NameRecord>,
) {
    fun value(nameId: Int): String? = records.filter { it.nameId == nameId }.minByOrNull { it.preference }?.text
}

private fun readNames(
    bytes: ByteArray,
    table: Table,
): NameTable {
    require(table.length >= 6) { "the name table is truncated: ${table.length} bytes" }
    val base = table.offset
    val count = bytes.u16(base + 2)
    val storage = bytes.u16(base + 4)
    require(6 + 12 * count <= table.length) {
        "the name table directory is truncated: $count records do not fit in ${table.length} bytes"
    }
    val records = ArrayList<NameRecord>(count)
    for (index in 0 until count) {
        val at = base + 6 + 12 * index
        val platform = bytes.u16(at)
        val language = bytes.u16(at + 4)
        val nameId = bytes.u16(at + 6)
        val length = bytes.u16(at + 8)
        val offset = bytes.u16(at + 10)
        val from = base.toLong() + storage + offset
        require(from + length <= base.toLong() + table.length) {
            "the name record $nameId runs past the name table"
        }
        records += NameRecord(platform, language, nameId, bytes.nameText(platform, from.toInt(), length))
    }
    return NameTable(records)
}

private fun ByteArray.isFixedPitch(table: Table?): Boolean {
    if (table == null || table.length < 16) return false
    return uint32(table.offset + 12) != 0L
}

private fun ByteArray.numberOfHMetrics(table: Table?): Int {
    if (table == null || table.length < 36) return 0
    return u16(table.offset + 34)
}

private fun readAxes(
    bytes: ByteArray,
    table: Table?,
    names: NameTable,
): List<KGEFont.Axis> {
    if (table == null) return emptyList()
    require(table.length >= 16) { "the fvar table is truncated: ${table.length} bytes" }
    val base = table.offset
    val arrayOffset = bytes.u16(base + 4)
    val count = bytes.u16(base + 8)
    val recordSize = bytes.u16(base + 10)
    if (count == 0) return emptyList()
    require(recordSize >= AXIS_RECORD_SIZE) {
        "the fvar axis record size $recordSize is smaller than $AXIS_RECORD_SIZE"
    }
    require(arrayOffset.toLong() + count.toLong() * recordSize <= table.length.toLong()) {
        "the fvar axis records run past the table: $count records of $recordSize bytes from $arrayOffset exceed ${table.length}"
    }
    return List(count) { index ->
        val at = base + arrayOffset + index * recordSize
        val tag = bytes.ascii(at, 4)
        KGEFont.Axis(
            tag =
                KGEFont.Axis.Tag.ofOrNull(tag)
                    ?: throw IllegalArgumentException("the fvar axis tag $tag is not four printable ASCII characters"),
            name = names.value(bytes.u16(at + 18)) ?: tag,
            min = KGEFont.Axis.Value.of(bytes.int32(at + 4)),
            default = KGEFont.Axis.Value.of(bytes.int32(at + 8)),
            max = KGEFont.Axis.Value.of(bytes.int32(at + 12)),
            hidden = bytes.u16(at + 16) and HIDDEN_FLAG != 0,
        )
    }
}

private fun ByteArray.nameText(
    platform: Int,
    offset: Int,
    length: Int,
): String {
    if (platform == WINDOWS_PLATFORM || platform == UNICODE_PLATFORM) {
        val chars = CharArray(length / 2)
        for (index in chars.indices) chars[index] = u16(offset + index * 2).toChar()
        return chars.concatToString()
    }
    val chars = CharArray(length)
    for (index in chars.indices) chars[index] = u8(offset + index).toChar()
    return chars.concatToString()
}

private fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

private fun ByteArray.u16(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)

private fun ByteArray.int32(offset: Int): Int =
    (u8(offset) shl 24) or (u8(offset + 1) shl 16) or (u8(offset + 2) shl 8) or u8(offset + 3)

private fun ByteArray.uint32(offset: Int): Long = int32(offset).toLong() and 0xFFFFFFFFL

private fun ByteArray.ascii(
    offset: Int,
    length: Int,
): String {
    val chars = CharArray(length)
    for (index in chars.indices) chars[index] = u8(offset + index).toChar()
    return chars.concatToString()
}
