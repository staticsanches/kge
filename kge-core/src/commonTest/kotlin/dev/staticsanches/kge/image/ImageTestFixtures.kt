package dev.staticsanches.kge.image

/*
 * Multi-format decode fixtures (S6 step 6). All are encoded offline from the
 * same 2x2 RGBA surface as tinyPngBytes (red opaque, green 50%, blue opaque,
 * yellow 50%), so the BMP round-trips against tinyPngPixels while the lossy
 * formats pin dimensions only. The bytes are embedded so every test target
 * decodes the same input without classpath resource differences.
 */

/**
 * A 2x2 baseline JPEG (quality 90) generated offline with
 * `magick source.png -quality 90 tiny.jpg`; byte-verified signature `FF D8 FF
 * E0` (JPEG/JFIF, SOI + APP0) and trailer `FF D9` (EOI). Lossy, so only the
 * dimensions are pinned in tests.
 */
internal val tinyJpegBytes: ByteArray =
    byteArrayOf(
        -1, -40, -1, -32, 0, 16, 74, 70, 73, 70, 0, 1, 1, 0, 0, 1,
        0, 1, 0, 0, -1, -37, 0, 67, 0, 3, 2, 2, 3, 2, 2, 3,
        3, 3, 3, 4, 3, 3, 4, 5, 8, 5, 5, 4, 4, 5, 10, 7,
        7, 6, 8, 12, 10, 12, 12, 11, 10, 11, 11, 13, 14, 18, 16, 13,
        14, 17, 14, 11, 11, 16, 22, 16, 17, 19, 20, 21, 21, 21, 12, 15,
        23, 24, 22, 20, 24, 18, 20, 21, 20, -1, -37, 0, 67, 1, 3, 4,
        4, 5, 4, 5, 9, 5, 5, 9, 20, 13, 11, 13, 20, 20, 20, 20,
        20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20,
        20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20,
        20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, -1, -64,
        0, 17, 8, 0, 2, 0, 2, 3, 1, 17, 0, 2, 17, 1, 3, 17,
        1, -1, -60, 0, 20, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 8, -1, -60, 0, 29, 16, 0, 2, 2, 3,
        0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 4, 2, 3,
        5, 6, 7, 0, 17, 33, -1, -60, 0, 21, 1, 1, 1, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 8, -1, -60, 0,
        30, 17, 1, 1, 0, 2, 1, 5, 1, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 1, 2, 3, 4, 5, 0, 6, 7, 17, 33, 34, -1, -38, 0,
        12, 3, 1, 0, 2, 17, 3, 17, 0, 63, 0, 87, -16, 13, 87, 8,
        -1, 0, 9, -26, -20, -77, -121, 65, -122, 110, -42, -15, -74, 91, 117, -86,
        -62, 83, -78, 69, 90, -55, -108, -119, 30, -55, 39, -23, 39, -56, 19, -54,
        25, -78, -23, 119, -25, 61, -85, -85, 76, 99, -115, -67, -103, -103, -105, -44,
        -52, -103, -84, 38, 67, -48, 0, 0, 31, 3, -31, -46, -29, -73, -72, 110,
        66, 77, -35, -35, 60, 89, 50, -28, -3, 93, -42, 56, -86, -86, -81, -75,
        85, 72, -75, 84, -86, -86, -86, -86, -5, -21, -1, -39,
    )

/**
 * A 2x2 32-bit `BI_BITFIELDS` BMP (BITMAPV5HEADER, 154 bytes) generated
 * offline with `magick source.png tiny.bmp`; byte-verified signature `42 4D`
 * ("BM"), DIB header size `124` and `biBitCount = 32` with an alpha mask. The
 * `getImageData`/STB paths read it as un-premultiplied BGRA, so it is lossless
 * and pinned against [tinyPngPixels].
 */
internal val tinyBmpBytes: ByteArray =
    byteArrayOf(
        66, 77, -102, 0, 0, 0, 0, 0, 0, 0, -118, 0, 0, 0, 124, 0,
        0, 0, 2, 0, 0, 0, 2, 0, 0, 0, 1, 0, 32, 0, 3, 0,
        0, 0, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, -1, 0, 0, -1, 0, 0, -1, 0,
        0, 0, 0, 0, 0, -1, 66, 71, 82, 115, -113, -62, -11, 40, 81, -72,
        30, 21, 30, -123, -21, 1, 51, 51, 51, 19, 102, 102, 102, 38, 102, 102,
        102, 6, -103, -103, -103, 9, 61, 10, -41, 3, 40, 92, -113, 50, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, 0, 0, -1, 0, -1,
        -1, -128, 0, 0, -1, -1, 0, -1, 0, -128,
    )

/**
 * A 2x2 GIF (GIF89a) generated offline with `magick source.png tiny.gif`;
 * byte-verified signature `47 49 46 38 39 61` ("GIF89a"). Palette-based, so a
 * source pixel at 50% alpha is quantized and only the dimensions are pinned.
 */
internal val tinyGifBytes: ByteArray =
    byteArrayOf(
        71, 73, 70, 56, 57, 97, 2, 0, 2, 0, -15, 0, 0, -1, 0, 0,
        0, -1, 0, -1, -1, 0, 0, 0, -1, 33, -7, 4, 0, 0, 0, 0,
        0, 44, 0, 0, 0, 0, 2, 0, 2, 0, 0, 2, 3, 68, 38, 5,
        0, 59,
    )

/**
 * A 2x2 lossless VP8L WEBP generated offline with
 * `cwebp -lossless -quiet source.png -o tiny.webp`; byte-verified signature
 * `52 49 46 46` ("RIFF") + `57 45 42 50` ("WEBP", `VP8L` chunk). Browser-only:
 * the JVM's STB backend has no WEBP decoder, but `createImageBitmap` does.
 */
internal val tinyWebpBytes: ByteArray =
    byteArrayOf(
        82, 73, 70, 70, 52, 0, 0, 0, 87, 69, 66, 80, 86, 80, 56, 76,
        39, 0, 0, 0, 47, 1, 64, 0, 16, 31, 48, -1, 2, -126, 34, -1,
        71, 19, 16, 20, -7, 63, -102, -128, -96, -24, -70, -27, -126, 119, -110, -128,
        -102, -74, 13, 88, -4, 38, 29, 17, -3, -113, 3, 0,
    )
