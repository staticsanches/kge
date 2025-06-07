package dev.staticsaches.knes

import dev.staticsaches.knes.emulator.MP6502.Companion.and
import dev.staticsaches.knes.emulator.MP6502.StatusFlag
import dev.staticsaches.knes.emulator.NES
import dev.staticsaches.knes.utils.UInt16
import dev.staticsaches.knes.utils.UInt16.Companion.x0000u
import dev.staticsaches.knes.utils.UInt16.Companion.x8000u
import dev.staticsaches.knes.utils.UInt16.Companion.xFFFCu
import dev.staticsaches.knes.utils.UInt16.Companion.xFFFFu
import dev.staticsanches.kge.engine.KotlinGameEngine
import dev.staticsanches.kge.engine.state.input.KeyboardKey
import dev.staticsanches.kge.engine.state.input.KeyboardKeyAction
import dev.staticsanches.kge.engine.state.input.PressAction
import dev.staticsanches.kge.image.Colors

expect class KNES : KotlinGameEngine {
    internal val nes: NES
    internal val mapAsm: MutableMap<UInt16, String>

    internal val spaceKey: KeyboardKey
    internal val rKey: KeyboardKey
    internal val iKey: KeyboardKey
    internal val nKey: KeyboardKey
    internal val xKey: KeyboardKey
}

internal fun KNES.internalOnUserCreate() {
    // Load Program (assembled at https://www.masswerk.at/6502/assembler.html)
    // *=$8000
    // LDX #10
    // STX $0000
    // LDX #3
    // STX $0001
    // LDY $0000
    // LDA #0
    // CLC
    // loop
    // ADC $0001
    // DEY
    // BNE loop
    // STA $0002
    // NOP
    // NOP
    // NOP
    fillRAM(x8000u, "A2 0A 8E 00 00 A2 03 8E 01 00 AC 00 00 A9 00 18 6D 01 00 88 D0 FA 8D 02 00 EA EA EA")

    // Set Reset Vector
    fillRAM(xFFFCu, "00 80")

    // Don't forget to set IRQ and NMI vectors if you want to play with those

    // Extract disassembly
    mapAsm.clear()
    mapAsm.putAll(nes.cpu.disassemble(x0000u, xFFFFu))

    // Reset
    nes.cpu.reset()
}

internal fun KNES.internalOnKeyEvent(
    key: KeyboardKey,
    newAction: KeyboardKeyAction,
) {
    if (newAction != PressAction) return

    if (key == spaceKey) {
        do {
            nes.cpu.clock()
        } while (!nes.cpu.complete)
    } else if (key == rKey) {
        nes.cpu.reset()
    } else if (key == iKey) {
        nes.cpu.irq()
    } else if (key == nKey) {
        nes.cpu.nmi()
    } else if (key == xKey) {
        with(nes.ram.clear()) {
            while (hasRemaining()) put(0x00)
        }
        internalOnUserCreate()
    }
}

internal fun KNES.internalOnUserUpdate(): Boolean {
    clear(Colors.DARK_BLUE)

    // Draw Ram Page 0x00
    drawRAM(2, 2, x0000u, 16, 16)
    drawRAM(2, 182, x8000u, 16, 16)
    drawCPU(448, 2)
    drawCode(448, 72, 26)

    drawString(10, 370, "SPACE = Step Instruction    R = RESET    I = IRQ    N = NMI    X = Clear")
    drawStringProp(4, 468, "fps: ${timeState.fps}")

    return true
}

private fun KNES.drawRAM(
    x: Int,
    y: Int,
    initialAddress: UInt16,
    rows: Int,
    columns: Int,
) {
    var ramX = x
    var ramY = y
    var address = initialAddress
    (0..<rows).forEach {
        var offset = "$$address:"
        (0..<columns).forEach { offset += " " + nes.cpuRead(address++, true) }
        drawString(ramX, ramY, offset)
        ramY += 10
    }
}

private fun KNES.drawCPU(
    x: Int,
    y: Int,
) {
    drawString(x, y, "STATUS:", Colors.WHITE)
    drawString(x + 64, y, "N", if (StatusFlag.N and nes.cpu.statusRegister) Colors.GREEN else Colors.RED)
    drawString(x + 80, y, "V", if (StatusFlag.V and nes.cpu.statusRegister) Colors.GREEN else Colors.RED)
    drawString(x + 96, y, "-", if (StatusFlag.U and nes.cpu.statusRegister) Colors.GREEN else Colors.RED)
    drawString(x + 112, y, "B", if (StatusFlag.B and nes.cpu.statusRegister) Colors.GREEN else Colors.RED)
    drawString(x + 128, y, "D", if (StatusFlag.D and nes.cpu.statusRegister) Colors.GREEN else Colors.RED)
    drawString(x + 144, y, "I", if (StatusFlag.I and nes.cpu.statusRegister) Colors.GREEN else Colors.RED)
    drawString(x + 160, y, "Z", if (StatusFlag.Z and nes.cpu.statusRegister) Colors.GREEN else Colors.RED)
    drawString(x + 178, y, "C", if (StatusFlag.C and nes.cpu.statusRegister) Colors.GREEN else Colors.RED)
    drawString(x, y + 10, "PC: $" + nes.cpu.programCounter)
    drawString(
        x, y + 20,
        "A: $" + nes.cpu.aRegister + "  [" +
            nes.cpu.aRegister.value
                .toInt() + "]",
    )
    drawString(
        x, y + 30,
        "X: $" + nes.cpu.xRegister + "  [" +
            nes.cpu.xRegister.value
                .toInt() + "]",
    )
    drawString(
        x, y + 40,
        "Y: $" + nes.cpu.yRegister + "  [" +
            nes.cpu.yRegister.value
                .toInt() + "]",
    )
    drawString(x, y + 50, "Stack P: $" + nes.cpu.stackPointer)
}

private fun KNES.drawCode(
    x: Int,
    y: Int,
    lines: Int,
) {
    val keys = mapAsm.keys.toList()
    val baseIndex = keys.indexOf(nes.cpu.programCounter)
    if (baseIndex == -1) return // couldn't find

    var lineY = (lines shr 1) * 10 + y
    var index = baseIndex
    drawString(x, lineY, mapAsm[keys[index]]!!, Colors.CYAN)
    while (lineY < lines * 10 + y) {
        lineY += 10
        if (++index < keys.size) {
            drawString(x, lineY, mapAsm[keys[index]]!!)
        }
    }

    lineY = (lines shr 1) * 10 + y
    index = baseIndex
    while (lineY > y) {
        lineY -= 10
        if (--index > 0) {
            drawString(x, lineY, mapAsm[keys[index]]!!)
        }
    }
}

private fun KNES.fillRAM(
    offset: UInt16,
    data: String,
    hexFormat: HexFormat = HexFormat { upperCase },
) = with(nes.ram.position(offset.toInt())) {
    data.split(" ").forEach { put(it.hexToInt(hexFormat).toByte()) }
}
