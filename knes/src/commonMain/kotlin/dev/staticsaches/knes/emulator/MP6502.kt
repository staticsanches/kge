package dev.staticsaches.knes.emulator

import dev.staticsaches.knes.emulator.MP6502.StatusFlag.B
import dev.staticsaches.knes.emulator.MP6502.StatusFlag.C
import dev.staticsaches.knes.emulator.MP6502.StatusFlag.D
import dev.staticsaches.knes.emulator.MP6502.StatusFlag.I
import dev.staticsaches.knes.emulator.MP6502.StatusFlag.N
import dev.staticsaches.knes.emulator.MP6502.StatusFlag.U
import dev.staticsaches.knes.emulator.MP6502.StatusFlag.V
import dev.staticsaches.knes.emulator.MP6502.StatusFlag.Z
import dev.staticsaches.knes.utils.UInt16
import dev.staticsaches.knes.utils.UInt16.Companion.x0000u
import dev.staticsaches.knes.utils.UInt16.Companion.x0001u
import dev.staticsaches.knes.utils.UInt16.Companion.x0080u
import dev.staticsaches.knes.utils.UInt16.Companion.x00FFu
import dev.staticsaches.knes.utils.UInt16.Companion.x0100u
import dev.staticsaches.knes.utils.UInt16.Companion.xFF00u
import dev.staticsaches.knes.utils.UInt16.Companion.xFFEEu
import dev.staticsaches.knes.utils.UInt16.Companion.xFFFAu
import dev.staticsaches.knes.utils.UInt16.Companion.xFFFCu
import dev.staticsaches.knes.utils.UInt16.Companion.xFFFEu
import dev.staticsaches.knes.utils.UInt16.Companion.xFFFFu
import dev.staticsaches.knes.utils.UInt8
import dev.staticsaches.knes.utils.UInt8.Companion.x00u
import dev.staticsaches.knes.utils.UInt8.Companion.x01u
import dev.staticsaches.knes.utils.UInt8.Companion.x80u
import dev.staticsaches.knes.utils.UInt8.Companion.xFDu
import dev.staticsaches.knes.utils.UInt8.Companion.xFFu

/**
 * Represents the 6502 microprocessor.
 */
class MP6502(
    private val bus: Bus,
) {
    var aRegister: UInt8 = x00u
        private set
    var xRegister: UInt8 = x00u
        private set
    var yRegister: UInt8 = x00u
        private set
    var statusRegister: UInt8 = x00u
        private set

    var stackPointer: UInt8 = x00u
        private set
    var programCounter: UInt16 = x0000u
        private set

    // External inputs

    /**
     * Forces the 6502 into a known state. This is hard-wired inside the CPU. The
     * registers are set to 0x00, the status register is cleared except for unused
     * bit which remains at 1. An absolute address is read from location 0xFFFC
     * which contains a second address that the program counter is set to. This
     * allows the programmer to jump to a known and programmable location in the
     * memory to start executing from. Typically, the programmer would set the value
     * at location 0xFFFC at compile time.
     */
    fun reset() {
        // Get address to set program counter to
        absAddress = xFFFCu
        val lo = read(absAddress++)
        val hi = read(absAddress) shl 8

        // Set it
        programCounter = hi or lo

        // Reset internal registers
        aRegister = x00u
        xRegister = x00u
        yRegister = x00u
        stackPointer = xFDu
        statusRegister = U or x00u

        // Clear internal helper variables
        absAddress = x0000u
        relAddress = x0000u
        fetched = x00u

        // Reset takes time
        cycles = 8
    }

    /**
     * Interrupt requests are a complex operation and only happen if the
     * "disable interrupt" flag is 0. IRQs can happen at any time, but
     * you don't want them to be destructive to the operation of the running
     * program. Therefore, the current instruction is allowed to finish
     * (which I facilitate by doing the whole thing when cycles == 0) and
     * then the current program counter is stored on the stack. Then the
     * current status register is stored on the stack. When the routine
     * that services the interrupt has finished, the status register
     * and program counter can be restored to how they were before it
     * occurred. This is implemented by the "RTI" instruction. Once the IRQ
     * has happened, in a similar way to a reset, a programmable address
     * is read form hard coded location 0xFFFE, which is subsequently
     * set to the program counter.
     */
    fun irq() {
        // If interrupts are allowed
        if (!I.get()) {
            // Push the program counter to the stack. It's 16-bits don't
            // forget so that takes two pushes
            write(x0100u + stackPointer--, (programCounter shr 8) and xFFu)
            write(x0100u + stackPointer--, programCounter and xFFu)

            // Then Push the status register to the stack
            B.set(false)
            U.set(true)
            I.set(true)
            write(x0100u + stackPointer--, statusRegister)

            // Read new program counter location from fixed address
            absAddress = xFFFEu
            val lo = read(absAddress)
            val hi = read(absAddress + 1u)
            programCounter = (hi shl 8) or lo

            // IRQs take time
            cycles = 7
        }
    }

    /**
     * A Non-Maskable Interrupt cannot be ignored. It behaves in exactly the
     * same way as a regular IRQ, but reads the new program counter address
     * form location 0xFFFA.
     */
    fun nmi() {
        // Push the program counter to the stack. It's 16-bits don't
        // forget so that takes two pushes
        write(x0100u + stackPointer--, (programCounter shr 8) and xFFu)
        write(x0100u + stackPointer--, programCounter and xFFu)

        // Then Push the status register to the stack
        B.set(false)
        U.set(true)
        I.set(true)
        write(x0100u + stackPointer--, statusRegister)

        // Read new program counter location from fixed address
        absAddress = xFFFAu
        val lo = read(absAddress)
        val hi = read(absAddress + 1u)
        programCounter = (hi shl 8) or lo

        cycles = 8
    }

    /**
     * Perform one clock cycles worth of emulation
     */
    fun clock() {
        // Each instruction requires a variable number of clock cycles to execute.
        // In my emulation, I only care about the final result, and so I perform
        // the entire computation in one hit. In hardware, each clock cycle would
        // perform "microcode" style transformations of the CPUs state.
        //
        // To remain compliant with connected devices, it's important that the
        // emulation also takes "time" in order to execute instructions, so I
        // implement that delay by simply counting down the cycles required by
        // the instruction. When it reaches 0, the instruction is complete, and
        // the next one is ready to be executed.
        if (cycles == 0) {
            // Read next instruction byte. This 8-bit value is used to index
            // the translation table to get the relevant information about
            // how to implement the instruction
            opcode = read(programCounter++).toInt()

            // Always set the unused status flag bit to 1
            U.set(true)

            val instruction = lookup[opcode]

            // Get Starting number of cycles
            cycles = instruction.cycles

            // Perform fetch of intermediate data using the required addressing mode
            val additionalCycle1 = instruction.addressingMode()

            // Perform operation
            val additionalCycle2 = instruction.operate()

            // The addressingMode and opcode may have altered the number
            // of cycles this instruction requires before its completed
            if (additionalCycle1 && additionalCycle2) {
                cycles++
            }

            // Always set the unused status flag bit to 1
            U.set(true)
        }

        // Increment global clock count - This is actually unused unless logging is enabled,
        // but I've kept it in because it's a handy watch variable for debugging
        clockCount++

        // Decrement the number of cycles remaining for this instruction
        cycles--
    }

    // Helper functions

    val complete: Boolean
        get() = cycles == 0

    /**
     * This is the disassembly function. Its workings are not required for emulation.
     * It is merely a convenience function to turn the binary instruction code into
     * human-readable form. It's included as part of the emulator because it can take
     * advantage of many of the CPUs internal operations to do this.
     */
    fun disassemble(
        start: UInt16,
        stop: UInt16,
    ): Map<UInt16, String> {
        // Starting at the specified address we read an instruction
        // byte, which in turn yields information from the lookup table
        // as to how many additional bytes we need to read and what the
        // addressing mode is. I need this info to assemble human-readable
        // addressing mode is. I need this info to assemble human-readable
        // syntax, which is different depending upon the addressing mode

        // As the instruction is decoded, a std::string is assembled
        // with the readable output
        var address = start
        val mapLines = LinkedHashMap<UInt16, String>()
        while (address < stop) {
            val lineAddress = address

            // Prefix line with instruction address
            var line = "$$address: "

            // Read instruction, and get its readable name
            val opcode = bus.cpuRead(address++, true).toInt()
            val instruction = lookup[opcode]

            line += instruction.name + " "

            // Get operands from desired locations, and form the
            // instruction based upon its addressing mode. These
            // routines mimic the actual fetch routine of the
            // 6502 in order to get accurate data as part of the
            // instruction
            if (instruction.addressingMode == amIMP) {
                line += " {IMP}"
            } else if (instruction.addressingMode == amIMM) {
                val value = bus.cpuRead(address++, true)
                line += "#$$value {IMM}"
            } else if (instruction.addressingMode == amZP0) {
                val lo = bus.cpuRead(address++, true)
                line += "$$lo {ZP0}"
            } else if (instruction.addressingMode == amZPX) {
                val lo = bus.cpuRead(address++, true)
                line += "$$lo, X {ZPX}"
            } else if (instruction.addressingMode == amZPY) {
                val lo = bus.cpuRead(address++, true)
                line += "$$lo, Y {ZPY}"
            } else if (instruction.addressingMode == amIZX) {
                val lo = bus.cpuRead(address++, true)
                line += "($$lo, X) {IZX}"
            } else if (instruction.addressingMode == amIZY) {
                val lo = bus.cpuRead(address++, true)
                line += "($$lo), Y {IZY}"
            } else if (instruction.addressingMode == amABS) {
                val lo = bus.cpuRead(address++, true)
                val hi = bus.cpuRead(address++, true) shl 8
                line += "$" + (hi or lo) + " {ABS}"
            } else if (instruction.addressingMode == amABX) {
                val lo = bus.cpuRead(address++, true)
                val hi = bus.cpuRead(address++, true) shl 8
                line += "$" + (hi or lo) + ", X {ABX}"
            } else if (instruction.addressingMode == amABY) {
                val lo = bus.cpuRead(address++, true)
                val hi = bus.cpuRead(address++, true) shl 8
                line += "$" + (hi or lo) + ", Y {ABY}"
            } else if (instruction.addressingMode == amIND) {
                val lo = bus.cpuRead(address++, true)
                val hi = bus.cpuRead(address++, true) shl 8
                line += "($" + (hi or lo) + ") {IND}"
            } else if (instruction.addressingMode == amREL) {
                val value = bus.cpuRead(address++, true)
                line += "$" + value + " [$" + (address + value) + "] {REL}"
            }

            // Add the formed string to a std::map, using the instruction's
            // address as the key. This makes it convenient to look for later
            // as the instructions are variable in length, so a straight-up
            // incremental index is not sufficient.
            mapLines[lineAddress] = line
            if (lineAddress > address) break
        }

        return mapLines
    }

    // Assistive variables to facilitate emulation

    private var fetched: UInt8 = x00u // Represents the working input value to the ALU
    private var absAddress: UInt16 = x0000u // All used memory addresses end up in here
    private var relAddress: UInt16 = x0000u // Represents absolute address following a branch
    private var opcode: Int = 0x00 // Is the instruction byte
    private var cycles: Int = 0 // Counts how many cycles the instruction has remaining
    private var clockCount: Long = 0 // A global accumulation of the number of clocks

    // Addressing modes

    /**
     * Address Mode: Implied
     *
     * There is no additional data required for this instruction. The instruction
     * does something very simple like sets a status bit. However, we will
     * target the accumulator, for instructions like PHA
     */
    private val amIMP: AddressingMode = {
        fetched = aRegister
        false
    }

    /**
     * Address Mode: Immediate
     *
     * The instruction expects the next byte to be used as a value, so we'll prep
     * the read address to point to the next byte
     */
    private val amIMM: AddressingMode = {
        absAddress = programCounter++
        false
    }

    /**
     * Address Mode: Zero Page
     *
     * To save program bytes, zero page addressing allows you to absolutely address
     * a location in first 0xFF bytes of address range. Clearly this only requires
     * one byte instead of the usual two.
     */
    private val amZP0: AddressingMode = {
        absAddress = read(programCounter++).toUInt16()
        false
    }

    /**
     * Address Mode: Zero Page with X Offset
     *
     * Fundamentally the same as Zero Page addressing, but the contents of the X Register
     * is added to the supplied single byte address. This is useful for iterating through
     * ranges within the first page.
     */
    private val amZPX: AddressingMode = {
        absAddress = ((read(programCounter++) + xRegister) and xFFu).toUInt16()
        false
    }

    /**
     * Address Mode: Zero Page with Y Offset
     *
     * Same as above but uses Y Register for offset
     */
    private val amZPY: AddressingMode = {
        absAddress = ((read(programCounter++) + yRegister) and xFFu).toUInt16()
        false
    }

    /**
     * Address Mode: Relative
     *
     * This address mode is exclusive to branch instructions. The address
     * must reside within -128 to +127 of the branch instruction, i.e.
     * you cant directly branch to any address in the addressable range.
     */
    private val amREL: AddressingMode = {
        relAddress = read(programCounter++).toUInt16()
        if (relAddress and x80u != x00u) {
            relAddress = relAddress or xFF00u
        }
        false
    }

    /**
     * Address Mode: Absolute
     *
     * A full 16-bit address is loaded and used
     */
    private val amABS: AddressingMode = {
        val lo = read(programCounter++)
        val hi = read(programCounter++)

        absAddress = ((hi shl 8) or lo)

        false
    }

    /**
     * Address Mode: Absolute with X Offset
     *
     * Fundamentally the same as absolute addressing, but the contents of the X Register
     * is added to the supplied two byte address. If the resulting address changes
     * the page, an additional clock cycle is required
     */
    private val amABX: AddressingMode = {
        val lo = read(programCounter++)
        val hi = read(programCounter++) shl 8

        val newAddress = (hi or lo) + xRegister
        absAddress = newAddress

        newAddress and xFF00u != hi
    }

    /**
     * Address Mode: Absolute with Y Offset
     *
     * Fundamentally the same as absolute addressing, but the contents of the Y Register
     * is added to the supplied two byte address. If the resulting address changes
     * the page, an additional clock cycle is required
     */
    private val amABY: AddressingMode = {
        val lo = read(programCounter++)
        val hi = read(programCounter++) shl 8

        val newAddress = (hi or lo) + yRegister
        absAddress = newAddress

        newAddress and xFF00u != hi
    }

    /**
     * Address Mode: Indirect
     *
     * The supplied 16-bit address is read to get the actual 16-bit address. This
     * instruction is unusual in that it has a bug in the hardware! To emulate its
     * function accurately, we also need to emulate this bug. If the low byte of the
     * supplied address is 0xFF, then to read the high byte of the actual address
     * we need to cross a page boundary. This doesn't actually work on the chip as
     * designed, instead it wraps back around in the same page, yielding an
     * invalid actual address
     */
    private val amIND: AddressingMode = {
        val pointerLo = read(programCounter++)
        val pointerHi = read(programCounter++) shl 8

        val pointer = pointerHi or pointerLo

        val lo = read((pointer + 0u))
        val hi =
            if (pointerLo == xFFu) { // Simulate page boundary hardware bug
                read((pointer and xFF00u))
            } else {
                read((pointer + 1u))
            } shl 8

        absAddress = (hi or lo)

        false
    }

    /**
     * Address Mode: Indirect X
     *
     * The supplied 8-bit address is offset by X Register to index
     * a location in page 0x00. The actual 16-bit address is read
     * from this location
     */
    private val amIZX: AddressingMode = {
        val t = read(programCounter++).toUInt16()

        val lo = read((t + xRegister) and x00FFu)
        val hi = read((t + xRegister + 1u) and x00FFu) shl 8

        absAddress = hi or lo

        false
    }

    /**
     * Address Mode: Indirect Y
     *
     * The supplied 8-bit address indexes a location in page 0x00. From
     * here the actual 16-bit address is read, and the contents of
     * Y Register is added to it to offset it. If the offset causes a
     * change in page then an additional clock cycle is required.
     */
    private val amIZY: AddressingMode = {
        val t = read(programCounter++).toUInt16()

        val lo = read(t and x00FFu)
        val hi = read((t + 1u) and x00FFu) shl 8

        absAddress = (hi or lo)
        absAddress = (absAddress + yRegister)

        absAddress and xFF00u != hi
    }

    // Fetch helper

    /**
     * This function sources the data used by the instruction into
     * a convenient numeric variable. Some instructions don't have to
     * fetch data as the source is implied by the instruction. For example
     * "INX" increments the X register. There is no additional data
     * required. For all other addressing modes, the data resides at
     * the location held within absAddress, so it is read from there.
     * Immediate address mode exploits this slightly, as that has
     * set absAddress = programCounter + 1, so it fetches the data from the
     * next byte for example "LDA $FF" just loads the accumulator with
     * 256, i.e. no far-reaching memory fetch is required. "fetched"
     * is a variable global to the CPU, and is set by calling this
     * function. It also returns it for convenience.
     */
    private fun fetch(): UInt8 {
        if (lookup[opcode].addressingMode != amIMP) {
            fetched = read(absAddress)
        }
        return fetched
    }

    // Instructions implementations

    /**
     * Instruction: Add with Carry In
     * Function:    A = A + M + C
     * Flags Out:   C, V, N, Z
     *
     * Explanation:
     * The purpose of this function is to add a value to the accumulator and a carry bit. If
     * the result is > 255 there is an overflow setting the carry bit. Ths allows you to
     * chain together ADC instructions to add numbers larger than 8-bits. This in itself is
     * simple, however the 6502 supports the concepts of Negativity/Positivity and Signed Overflow.
     *
     * 10000100 = 128 + 4 = 132 in normal circumstances, we know this as unsigned, and it allows
     * us to represent numbers between 0 and 255 (given 8 bits). The 6502 can also interpret
     * this word as something else if we assume those 8 bits represent the range -128 to +127,
     * i.e. it has become signed.
     *
     * Since 132 > 127, it effectively wraps around, through -128, to -124. This wraparound is
     * called overflow, and this is a useful to know as it indicates that the calculation has
     * gone outside the permissible range, and therefore no longer makes numeric sense.
     *
     * Note the implementation of ADD is the same in binary, this is just about how the numbers
     * are represented, so the word 10000100 can be both -124 and 132 depending upon the
     * context the programming is using it in. We can prove this!
     *
     * 10000100 =  132  or  -124
     * +00010001 = + 17      + 17
     * ========    ===       ===     See, both are valid additions, but our interpretation of
     * 10010101 =  149  or  -107     the context changes the value, not the hardware!
     *
     * In principle under the -128 to 127 range:
     * 10000000 = -128, 11111111 = -1, 00000000 = 0, 00000000 = +1, 01111111 = +127
     * therefore negative numbers have the most significant set, positive numbers do not
     *
     * To assist us, the 6502 can set the overflow flag, if the result of the addition has
     * wrapped around. V <- ~(A^M) & A^(A+M+C) :D lol, let's work out why!
     *
     * Let's suppose we have A = 30, M = 10 and C = 0
     *       A = 30 = 00011110
     *       M = 10 = 00001010+
     *  RESULT = 40 = 00101000
     *
     * Here we have not gone out of range. The resulting significant bit has not changed.
     * So let's make a truth table to understand when overflow has occurred. Here I take
     * the MSB of each component, where R is RESULT.
     *
     * A  M  R | V | A^R | A^M |~(A^M) |
     * 0  0  0 | 0 |  0  |  0  |   1   |
     * 0  0  1 | 1 |  1  |  0  |   1   |
     * 0  1  0 | 0 |  0  |  1  |   0   |
     * 0  1  1 | 0 |  1  |  1  |   0   |  so V = ~(A^M) & (A^R)
     * 1  0  0 | 0 |  1  |  1  |   0   |
     * 1  0  1 | 0 |  0  |  1  |   0   |
     * 1  1  0 | 1 |  1  |  0  |   1   |
     * 1  1  1 | 0 |  0  |  0  |   1   |
     *
     * We can see how the above equation calculates V, based on A, M and R. V was chosen
     * based on the following hypothesis:
     *    Positive Number + Positive Number = Negative Result -> Overflow
     *    Negative Number + Negative Number = Positive Result -> Overflow
     *    Positive Number + Negative Number = Either Result -> Cannot Overflow
     *    Positive Number + Positive Number = Positive Result -> OK! No Overflow
     *    Negative Number + Negative Number = Negative Result -> OK! NO Overflow
     */
    private val ocADC: OperationCode = {
        // Grab the data that we are adding to the accumulator
        fetch()

        // Add is performed in 16-bit domain for emulation to capture any
        // carry bit, which will exist in bit 8 of the 16-bit word
        val temp = C.get16() + aRegister + fetched

        // The carry flag out exists in the high byte bit 0
        C.set(temp and xFF00u != x0000u)

        // The Zero flag is set if the result is 0
        Z.set(temp and x00FFu == x0000u)

        // The signed Overflow flag is set based on all that up there! :D
        V.set((aRegister.toUInt16() xor fetched).inv() and (temp xor aRegister) and x0080u != x0000u)

        // The negative flag is set to the most significant bit of the result
        N.set(temp and x0080u != x0000u)

        // Load the result into the accumulator (it's 8-bit don't forget!)
        aRegister = temp and xFFu

        // This instruction has the potential to require an additional clock cycle
        true
    }

    /**
     * Instruction: Subtraction with Borrow In
     * Function:    A = A - M - (1 - C)
     * Flags Out:   C, V, N, Z
     *
     * Explanation:
     * Given the explanation for ADC above, we can reorganise our data
     * to use the same computation for addition, for subtraction by multiplying
     * the data by -1, i.e. make it negative
     *
     * A = A - M - (1 - C)  ->  A = A + -1 * (M - (1 - C))  ->  A = A + (-M + 1 + C)
     *
     * To make a signed positive number negative, we can invert the bits and add 1
     * (OK, I lied, a little bit of 1 and 2s complement :P)
     *
     * 5 = 00000101
     * -5 = 11111010 + 00000001 = 11111011 (or 251 in our 0 to 255 range)
     *
     * The range is actually unimportant, because if I take the value 15, and add 251
     * to it, given we wrap around at 256, the result is 10, so it has effectively
     * subtracted 5, which was the original intention. (15 + 251) % 256 = 10
     *
     * Note that the equation above used (1-C), but this got converted to + 1 + C.
     * This means we already have the +1, so all we need to do is invert the bits
     * of M, the data(!) therefore we can simply add, exactly the same way we did
     * before.
     */
    private val ocSBC: OperationCode = {
        fetch()

        // Operating in 16-bit domain to capture carry out

        // We can invert the bottom 8 bits with bitwise xor
        val value = fetched xor x00FFu

        // Notice this is exactly the same as addition from here!
        val temp = C.get16() + aRegister + value
        C.set(temp and xFF00u != x0000u)
        Z.set(temp and x00FFu == x0000u)
        V.set((temp xor aRegister) and (temp xor value) and x0080u != x0000u)
        N.set(temp and x0080u != x0000u)
        aRegister = temp and xFFu
        true
    }

    /**
     * Instruction: Bitwise Logic AND
     * Function:    A = A & M
     * Flags Out:   N, Z
     */
    private val ocAND: OperationCode = {
        fetch()
        aRegister = aRegister and fetched
        Z.set(aRegister == x00u)
        N.set(aRegister and x80u != x00u)
        true
    }

    /**
     * Instruction: Arithmetic Shift Left
     * Function:    A = C <- (A << 1) <- 0
     * Flags Out:   N, Z, C
     */
    private val ocASL: OperationCode = {
        fetch()
        val temp = fetched shl 1
        C.set(temp and xFF00u != x0000u)
        Z.set(temp and x00FFu == x0000u)
        N.set(temp and x80u != x00u)
        if (lookup[opcode].addressingMode == amIMP) {
            aRegister = temp and xFFu
        } else {
            write(absAddress, temp and xFFu)
        }
        false
    }

    /**
     * Instruction: Branch if Carry Clear
     * Function:    if(C == 0) pc = address
     */
    private val ocBCC: OperationCode = {
        if (!C.get()) doBranch()
        false
    }

    /**
     * Instruction: Branch if Carry Set
     * Function:    if(C == 1) pc = address
     */
    private val ocBCS: OperationCode = {
        if (C.get()) doBranch()
        false
    }

    /**
     * Instruction: Branch if Equal
     * Function:    if(Z == 1) pc = address
     */
    private val ocBEQ: OperationCode = {
        if (Z.get()) doBranch()
        false
    }

    private val ocBIT: OperationCode = {
        fetch()
        val temp = aRegister and fetched
        Z.set((temp and xFFu) == x00u)
        N.set(fetched and (1u shl 7) != x00u)
        V.set(fetched and (1u shl 6) != x00u)
        false
    }

    /**
     * Instruction: Branch if Negative
     * Function:    if(N == 1) pc = address
     */
    private val ocBMI: OperationCode = {
        if (N.get()) doBranch()
        false
    }

    /**
     * Instruction: Branch if Not Equal
     * Function:    if(Z == 0) pc = address
     */
    private val ocBNE: OperationCode = {
        if (!Z.get()) doBranch()
        false
    }

    /**
     * Instruction: Branch if Positive
     * Function:    if(N == 0) pc = address
     */
    private val ocBPL: OperationCode = {
        if (!N.get()) doBranch()
        false
    }

    /**
     * Instruction: Break
     * Function:    Program Sourced Interrupt
     */
    private val ocBRK: OperationCode = {
        programCounter++

        I.set(true)
        write(x0100u + stackPointer--, (programCounter shr 8) and xFFu)
        write(x0100u + stackPointer--, programCounter and xFFu)

        B.set(true)
        write(x0100u + stackPointer--, statusRegister)
        B.set(false)

        programCounter = read(xFFEEu) or (read(xFFFFu) shl 8)
        false
    }

    /**
     * Instruction: Branch if Overflow Clear
     * Function:    if(V == 0) pc = address
     */
    val ocBVC: OperationCode = {
        if (!V.get()) doBranch()
        false
    }

    /**
     * Instruction: Branch if Overflow Set
     * Function:    if(V == 1) pc = address
     */
    val ocBVS: OperationCode = {
        if (V.get()) doBranch()
        false
    }

    private fun doBranch() {
        cycles++
        absAddress = programCounter + relAddress
        if (absAddress and xFF00u != programCounter and xFF00u) cycles++
        programCounter = absAddress
    }

    /**
     * Instruction: Clear Carry Flag
     * Function:    C = 0
     */
    val ocCLC: OperationCode = {
        C.set(false)
        false
    }

    /**
     * Instruction: Clear Decimal Flag
     * Function:    D = 0
     */
    private val ocCLD: OperationCode = {
        D.set(false)
        false
    }

    /**
     * Instruction: Disable Interrupts / Clear Interrupt Flag
     * Function:    I = 0
     */
    private val ocCLI: OperationCode = {
        I.set(false)
        false
    }

    /**
     * Instruction: Clear Overflow Flag
     * Function:    V = 0
     */
    private val ocCLV: OperationCode = {
        V.set(false)
        false
    }

    /**
     * Instruction: Compare Accumulator
     * Function:    C <- A >= M      Z <- (A - M) == 0
     * Flags Out:   N, C, Z
     */
    private val ocCMP: OperationCode = {
        fetch()
        val temp = aRegister.toUInt16() - fetched
        C.set(aRegister >= fetched)
        Z.set(temp and xFFu == x00u)
        N.set(temp and x80u != x00u)
        true
    }

    /**
     * Instruction: Compare X Register
     * Function:    C <- X >= M      Z <- (X - M) == 0
     * Flags Out:   N, C, Z
     */
    private val ocCPX: OperationCode = {
        fetch()
        val temp = xRegister.toUInt16() - fetched
        C.set(xRegister >= fetched)
        Z.set(temp and xFFu == x00u)
        N.set(temp and x80u != x00u)
        false
    }

    /**
     * Instruction: Compare Y Register
     * Function:    C <- Y >= M      Z <- (Y - M) == 0
     * Flags Out:   N, C, Z
     */
    private val ocCPY: OperationCode = {
        fetch()
        val temp = yRegister.toUInt16() - fetched
        C.set(yRegister >= fetched)
        Z.set(temp and xFFu == x00u)
        N.set(temp and x80u != x00u)
        false
    }

    /**
     * Instruction: Decrement Value at Memory Location
     * Function:    M = M - 1
     * Flags Out:   N, Z
     */
    private val ocDEC: OperationCode = {
        fetch()
        val temp = fetched.toUInt16() - 1u
        write(absAddress, temp and xFFu)
        Z.set(temp and xFFu == x00u)
        N.set(temp and x80u != x00u)
        false
    }

    /**
     * Instruction: Decrement X Register
     * Function:    X = X - 1
     * Flags Out:   N, Z
     */
    private val ocDEX: OperationCode = {
        xRegister--
        Z.set(xRegister == x00u)
        N.set(xRegister and x80u != x00u)
        false
    }

    /**
     * Instruction: Decrement Y Register
     * Function:    Y = Y - 1
     * Flags Out:   N, Z
     */
    private val ocDEY: OperationCode = {
        yRegister--
        Z.set(yRegister == x00u)
        N.set(yRegister and x80u != x00u)
        false
    }

    /**
     * Instruction: Bitwise Logic XOR
     * Function:    A = A xor M
     * Flags Out:   N, Z
     */
    private val ocEOR: OperationCode = {
        fetch()
        aRegister = aRegister xor fetched
        Z.set(aRegister == x00u)
        N.set(aRegister and x80u != x00u)
        true
    }

    /**
     * Instruction: Increment Value at Memory Location
     * Function:    M = M + 1
     * Flags Out:   N, Z
     */
    private val ocINC: OperationCode = {
        fetch()
        val temp = fetched.toUInt16() + 1u
        write(absAddress, temp and xFFu)
        Z.set((temp and xFFu) == x00u)
        N.set(temp and x80u != x00u)
        false
    }

    /**
     * Instruction: Increment X Register
     * Function:    X = X + 1
     * Flags Out:   N, Z
     */
    private val ocINX: OperationCode = {
        xRegister++
        Z.set(xRegister == x00u)
        N.set(xRegister and x80u != x00u)
        false
    }

    /**
     * Instruction: Increment Y Register
     * Function:    Y = Y + 1
     * Flags Out:   N, Z
     */
    private val ocINY: OperationCode = {
        yRegister++
        Z.set(yRegister == x00u)
        N.set(yRegister and x80u != x00u)
        false
    }

    /**
     * Instruction: Jump To Location
     * Function:    pc = address
     */
    private val ocJMP: OperationCode = {
        programCounter = absAddress
        false
    }

    /**
     * Instruction: Jump To Sub-Routine
     * Function:    Push current pc to stack, pc = address
     */
    private val ocJSR: OperationCode = {
        write(x0100u + stackPointer--, (--programCounter shr 8) and xFFu)
        write(x0100u + stackPointer--, programCounter and xFFu)

        programCounter = absAddress
        false
    }

    /**
     * Instruction: Load The Accumulator
     * Function:    A = M
     * Flags Out:   N, Z
     */
    private val ocLDA: OperationCode = {
        fetch()
        aRegister = fetched
        Z.set(aRegister == x00u)
        N.set(aRegister and x80u != x00u)
        true
    }

    /**
     * Instruction: Load The X Register
     * Function:    X = M
     * Flags Out:   N, Z
     */
    private val ocLDX: OperationCode = {
        fetch()
        xRegister = fetched
        Z.set(xRegister == x00u)
        N.set(xRegister and x80u != x00u)
        true
    }

    /**
     * Instruction: Load The Y Register
     * Function:    Y = M
     * Flags Out:   N, Z
     */
    private val ocLDY: OperationCode = {
        fetch()
        yRegister = fetched
        Z.set(yRegister == x00u)
        N.set(yRegister and x80u != x00u)
        true
    }

    private val ocLSR: OperationCode = {
        fetch()
        C.set(fetched and x01u != x00u)
        val temp = fetched.toUInt16() shr 1
        Z.set((temp and xFFu) == x00u)
        N.set(temp and x80u != x00u)
        if (lookup[opcode].addressingMode == amIMP) {
            aRegister = temp and xFFu
        } else {
            write(absAddress, temp and xFFu)
        }
        false
    }

    private val ocNOP: OperationCode = {
        // Sadly not all NOPs are equal, I've added a few here
        // based on https://wiki.nesdev.com/w/index.php/CPU_unofficial_opcodes
        // and will add more based on game compatibility, and ultimately
        // I'd like to cover all illegal opcodes too
        opcode == 0x1C || opcode == 0x3C || opcode == 0x5C || opcode == 0x7C || opcode == 0xDC || opcode == 0xFC
    }

    /**
     * Instruction: Bitwise Logic OR
     * Function:    A = A | M
     * Flags Out:   N, Z
     */
    private val ocORA: OperationCode = {
        fetch()
        aRegister = aRegister or fetched
        Z.set(aRegister == x00u)
        N.set(aRegister and x80u != x00u)
        true
    }

    /**
     * Instruction: Push Accumulator to Stack
     * Function:    A -> stack
     */
    private val ocPHA: OperationCode = {
        write(x0100u + stackPointer--, aRegister)
        false
    }

    /**
     * Instruction: Push Status Register to Stack
     * Function:    status -> stack
     * Note:        Break flag is set to 1 before push
     */
    private val ocPHP: OperationCode = {
        write(x0100u + stackPointer, statusRegister or B or U)
        B.set(false)
        U.set(false)
        stackPointer--
        false
    }

    /**
     * Instruction: Pop Accumulator off Stack
     * Function:    A <- stack
     * Flags Out:   N, Z
     */
    private val ocPLA: OperationCode = {
        stackPointer++
        aRegister = read(x0100u + stackPointer)
        Z.set(aRegister == x00u)
        N.set(aRegister and x80u != x00u)
        false
    }

    /**
     * Instruction: Pop Status Register off Stack
     * Function:    Status <- stack
     */
    private val ocPLP: OperationCode = {
        stackPointer++
        statusRegister = read(x0100u + stackPointer)
        U.set(true)
        false
    }

    private val ocROL: OperationCode = {
        fetch()
        val temp = (fetched shl 1) or C.get16()
        C.set(temp and xFF00u != x0000u)
        Z.set((temp and xFFu) == x00u)
        N.set(temp and x80u != x00u)
        if (lookup[opcode].addressingMode == amIMP) {
            aRegister = temp and xFFu
        } else {
            write(absAddress, temp and xFFu)
        }
        false
    }

    private val ocROR: OperationCode = {
        fetch()
        val temp = C.get16() or (fetched shr 1)
        C.set(fetched and x01u != x00u)
        Z.set((temp and xFFu) == x00u)
        N.set(temp and x80u != x00u)
        if (lookup[opcode].addressingMode == amIMP) {
            aRegister = temp and xFFu
        } else {
            write(absAddress, temp and xFFu)
        }
        false
    }

    private val ocRTI: OperationCode = {
        statusRegister = read(x0100u + ++stackPointer)
        statusRegister = statusRegister and B.invMask
        statusRegister = statusRegister and U.invMask

        programCounter = read(x0100u + ++stackPointer).toUInt16()
        programCounter = programCounter or (read(x0100u + ++stackPointer) shl 8)
        false
    }

    private val ocRTS: OperationCode = {
        stackPointer++
        programCounter = read(x0100u + stackPointer++).toUInt16()
        programCounter = programCounter or (read(x0100u + stackPointer++) shl 8)
        false
    }

    /**
     * Instruction: Set Carry Flag
     * Function:    C = 1
     */
    private val ocSEC: OperationCode = {
        C.set(true)
        false
    }

    /**
     * Instruction: Set Decimal Flag
     * Function:    D = 1
     */
    private val ocSED: OperationCode = {
        D.set(true)
        false
    }

    /**
     * Instruction: Set Interrupt Flag / Enable Interrupts
     * Function:    I = 1
     */
    private val ocSEI: OperationCode = {
        I.set(true)
        false
    }

    /**
     * Instruction: Store Accumulator at Address
     * Function:    M = A
     */
    private val ocSTA: OperationCode = {
        write(absAddress, aRegister)
        false
    }

    /**
     * Instruction: Store X Register at Address
     * Function:    M = X
     */
    private val ocSTX: OperationCode = {
        write(absAddress, xRegister)
        false
    }

    /**
     * Instruction: Store Y Register at Address
     * Function:    M = Y
     */
    private val ocSTY: OperationCode = {
        write(absAddress, yRegister)
        false
    }

    /**
     * Instruction: Transfer Accumulator to X Register
     * Function:    X = A
     * Flags Out:   N, Z
     */
    private val ocTAX: OperationCode = {
        xRegister = aRegister
        Z.set(xRegister == x00u)
        N.set(xRegister and x80u == x00u)
        false
    }

    /**
     * Instruction: Transfer Accumulator to Y Register
     * Function:    Y = A
     * Flags Out:   N, Z
     */
    private val ocTAY: OperationCode = {
        yRegister = aRegister
        Z.set(yRegister == x00u)
        N.set(yRegister and x80u != x00u)
        false
    }

    /**
     * Instruction: Transfer Stack Pointer to X Register
     * Function:    X = stack pointer
     * Flags Out:   N, Z
     */
    private val ocTSX: OperationCode = {
        xRegister = stackPointer
        Z.set(xRegister == x00u)
        N.set(xRegister and x80u != x00u)
        false
    }

    /**
     * Instruction: Transfer X Register to Accumulator
     * Function:    A = X
     * Flags Out:   N, Z
     */
    private val ocTXA: OperationCode = {
        aRegister = xRegister
        Z.set(aRegister == x00u)
        N.set(aRegister and x80u != x00u)
        false
    }

    /**
     * Instruction: Transfer X Register to Stack Pointer
     * Function:    stack pointer = X
     */
    private val ocTXS: OperationCode = {
        stackPointer = xRegister
        false
    }

    /**
     * Instruction: Transfer Y Register to Accumulator
     * Function:    A = Y
     * Flags Out:   N, Z
     */
    private val ocTYA: OperationCode = {
        aRegister = yRegister
        Z.set(aRegister == x00u)
        N.set(aRegister and x80u != x00u)
        false
    }

    /**
     * This function captures illegal opcodes
     */
    private val ocXXX: OperationCode = { false }

    // Bus connectivity

    private fun read(address: UInt16): UInt8 = bus.cpuRead(address, false)

    private fun write(
        address: UInt16,
        data: UInt8,
    ) = bus.cpuWrite(address, data)

    // Status manipulation

    private fun StatusFlag.get(): Boolean = this and statusRegister

    private fun StatusFlag.get16(): UInt16 = if (get()) x0001u else x0000u

    private fun StatusFlag.set(value: Boolean) {
        statusRegister = if (value) statusRegister or mask else statusRegister and invMask
    }

    // Instruction

    private fun i(
        name: String,
        operate: OperationCode,
        addressingMode: AddressingMode,
        cycles: Int,
    ): Instruction = Instruction(name = name, operate = operate, addressingMode = addressingMode, cycles = cycles)

    private class Instruction(
        val name: String,
        val operate: OperationCode,
        val addressingMode: AddressingMode,
        val cycles: Int,
    )

    // Lookup

    private val lookup =
        arrayOf(
            i("BRK", ocBRK, amIMM, 7), i("ORA", ocORA, amIZX, 6), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("???", ocNOP, amIMP, 3), i("ORA", ocORA, amZP0, 3), i("ASL", ocASL, amZP0, 5), i("???", ocXXX, amIMP, 5),
            i("PHP", ocPHP, amIMP, 3), i("ORA", ocORA, amIMM, 2), i("ASL", ocASL, amIMP, 2), i("???", ocXXX, amIMP, 2),
            i("???", ocNOP, amIMP, 4), i("ORA", ocORA, amABS, 4), i("ASL", ocASL, amABS, 6), i("???", ocXXX, amIMP, 6),
            i("BPL", ocBPL, amREL, 2), i("ORA", ocORA, amIZY, 5), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("???", ocNOP, amIMP, 4), i("ORA", ocORA, amZPX, 4), i("ASL", ocASL, amZPX, 6), i("???", ocXXX, amIMP, 6),
            i("CLC", ocCLC, amIMP, 2), i("ORA", ocORA, amABY, 4), i("???", ocNOP, amIMP, 2), i("???", ocXXX, amIMP, 7),
            i("???", ocNOP, amIMP, 4), i("ORA", ocORA, amABX, 4), i("ASL", ocASL, amABX, 7), i("???", ocXXX, amIMP, 7),
            i("JSR", ocJSR, amABS, 6), i("AND", ocAND, amIZX, 6), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("BIT", ocBIT, amZP0, 3), i("AND", ocAND, amZP0, 3), i("ROL", ocROL, amZP0, 5), i("???", ocXXX, amIMP, 5),
            i("PLP", ocPLP, amIMP, 4), i("AND", ocAND, amIMM, 2), i("ROL", ocROL, amIMP, 2), i("???", ocXXX, amIMP, 2),
            i("BIT", ocBIT, amABS, 4), i("AND", ocAND, amABS, 4), i("ROL", ocROL, amABS, 6), i("???", ocXXX, amIMP, 6),
            i("BMI", ocBMI, amREL, 2), i("AND", ocAND, amIZY, 5), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("???", ocNOP, amIMP, 4), i("AND", ocAND, amZPX, 4), i("ROL", ocROL, amZPX, 6), i("???", ocXXX, amIMP, 6),
            i("SEC", ocSEC, amIMP, 2), i("AND", ocAND, amABY, 4), i("???", ocNOP, amIMP, 2), i("???", ocXXX, amIMP, 7),
            i("???", ocNOP, amIMP, 4), i("AND", ocAND, amABX, 4), i("ROL", ocROL, amABX, 7), i("???", ocXXX, amIMP, 7),
            i("RTI", ocRTI, amIMP, 6), i("EOR", ocEOR, amIZX, 6), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("???", ocNOP, amIMP, 3), i("EOR", ocEOR, amZP0, 3), i("LSR", ocLSR, amZP0, 5), i("???", ocXXX, amIMP, 5),
            i("PHA", ocPHA, amIMP, 3), i("EOR", ocEOR, amIMM, 2), i("LSR", ocLSR, amIMP, 2), i("???", ocXXX, amIMP, 2),
            i("JMP", ocJMP, amABS, 3), i("EOR", ocEOR, amABS, 4), i("LSR", ocLSR, amABS, 6), i("???", ocXXX, amIMP, 6),
            i("BVC", ocBVC, amREL, 2), i("EOR", ocEOR, amIZY, 5), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("???", ocNOP, amIMP, 4), i("EOR", ocEOR, amZPX, 4), i("LSR", ocLSR, amZPX, 6), i("???", ocXXX, amIMP, 6),
            i("CLI", ocCLI, amIMP, 2), i("EOR", ocEOR, amABY, 4), i("???", ocNOP, amIMP, 2), i("???", ocXXX, amIMP, 7),
            i("???", ocNOP, amIMP, 4), i("EOR", ocEOR, amABX, 4), i("LSR", ocLSR, amABX, 7), i("???", ocXXX, amIMP, 7),
            i("RTS", ocRTS, amIMP, 6), i("ADC", ocADC, amIZX, 6), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("???", ocNOP, amIMP, 3), i("ADC", ocADC, amZP0, 3), i("ROR", ocROR, amZP0, 5), i("???", ocXXX, amIMP, 5),
            i("PLA", ocPLA, amIMP, 4), i("ADC", ocADC, amIMM, 2), i("ROR", ocROR, amIMP, 2), i("???", ocXXX, amIMP, 2),
            i("JMP", ocJMP, amIND, 5), i("ADC", ocADC, amABS, 4), i("ROR", ocROR, amABS, 6), i("???", ocXXX, amIMP, 6),
            i("BVS", ocBVS, amREL, 2), i("ADC", ocADC, amIZY, 5), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("???", ocNOP, amIMP, 4), i("ADC", ocADC, amZPX, 4), i("ROR", ocROR, amZPX, 6), i("???", ocXXX, amIMP, 6),
            i("SEI", ocSEI, amIMP, 2), i("ADC", ocADC, amABY, 4), i("???", ocNOP, amIMP, 2), i("???", ocXXX, amIMP, 7),
            i("???", ocNOP, amIMP, 4), i("ADC", ocADC, amABX, 4), i("ROR", ocROR, amABX, 7), i("???", ocXXX, amIMP, 7),
            i("???", ocNOP, amIMP, 2), i("STA", ocSTA, amIZX, 6), i("???", ocNOP, amIMP, 2), i("???", ocXXX, amIMP, 6),
            i("STY", ocSTY, amZP0, 3), i("STA", ocSTA, amZP0, 3), i("STX", ocSTX, amZP0, 3), i("???", ocXXX, amIMP, 3),
            i("DEY", ocDEY, amIMP, 2), i("???", ocNOP, amIMP, 2), i("TXA", ocTXA, amIMP, 2), i("???", ocXXX, amIMP, 2),
            i("STY", ocSTY, amABS, 4), i("STA", ocSTA, amABS, 4), i("STX", ocSTX, amABS, 4), i("???", ocXXX, amIMP, 4),
            i("BCC", ocBCC, amREL, 2), i("STA", ocSTA, amIZY, 6), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 6),
            i("STY", ocSTY, amZPX, 4), i("STA", ocSTA, amZPX, 4), i("STX", ocSTX, amZPY, 4), i("???", ocXXX, amIMP, 4),
            i("TYA", ocTYA, amIMP, 2), i("STA", ocSTA, amABY, 5), i("TXS", ocTXS, amIMP, 2), i("???", ocXXX, amIMP, 5),
            i("???", ocNOP, amIMP, 5), i("STA", ocSTA, amABX, 5), i("???", ocXXX, amIMP, 5), i("???", ocXXX, amIMP, 5),
            i("LDY", ocLDY, amIMM, 2), i("LDA", ocLDA, amIZX, 6), i("LDX", ocLDX, amIMM, 2), i("???", ocXXX, amIMP, 6),
            i("LDY", ocLDY, amZP0, 3), i("LDA", ocLDA, amZP0, 3), i("LDX", ocLDX, amZP0, 3), i("???", ocXXX, amIMP, 3),
            i("TAY", ocTAY, amIMP, 2), i("LDA", ocLDA, amIMM, 2), i("TAX", ocTAX, amIMP, 2), i("???", ocXXX, amIMP, 2),
            i("LDY", ocLDY, amABS, 4), i("LDA", ocLDA, amABS, 4), i("LDX", ocLDX, amABS, 4), i("???", ocXXX, amIMP, 4),
            i("BCS", ocBCS, amREL, 2), i("LDA", ocLDA, amIZY, 5), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 5),
            i("LDY", ocLDY, amZPX, 4), i("LDA", ocLDA, amZPX, 4), i("LDX", ocLDX, amZPY, 4), i("???", ocXXX, amIMP, 4),
            i("CLV", ocCLV, amIMP, 2), i("LDA", ocLDA, amABY, 4), i("TSX", ocTSX, amIMP, 2), i("???", ocXXX, amIMP, 4),
            i("LDY", ocLDY, amABX, 4), i("LDA", ocLDA, amABX, 4), i("LDX", ocLDX, amABY, 4), i("???", ocXXX, amIMP, 4),
            i("CPY", ocCPY, amIMM, 2), i("CMP", ocCMP, amIZX, 6), i("???", ocNOP, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("CPY", ocCPY, amZP0, 3), i("CMP", ocCMP, amZP0, 3), i("DEC", ocDEC, amZP0, 5), i("???", ocXXX, amIMP, 5),
            i("INY", ocINY, amIMP, 2), i("CMP", ocCMP, amIMM, 2), i("DEX", ocDEX, amIMP, 2), i("???", ocXXX, amIMP, 2),
            i("CPY", ocCPY, amABS, 4), i("CMP", ocCMP, amABS, 4), i("DEC", ocDEC, amABS, 6), i("???", ocXXX, amIMP, 6),
            i("BNE", ocBNE, amREL, 2), i("CMP", ocCMP, amIZY, 5), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("???", ocNOP, amIMP, 4), i("CMP", ocCMP, amZPX, 4), i("DEC", ocDEC, amZPX, 6), i("???", ocXXX, amIMP, 6),
            i("CLD", ocCLD, amIMP, 2), i("CMP", ocCMP, amABY, 4), i("NOP", ocNOP, amIMP, 2), i("???", ocXXX, amIMP, 7),
            i("???", ocNOP, amIMP, 4), i("CMP", ocCMP, amABX, 4), i("DEC", ocDEC, amABX, 7), i("???", ocXXX, amIMP, 7),
            i("CPX", ocCPX, amIMM, 2), i("SBC", ocSBC, amIZX, 6), i("???", ocNOP, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("CPX", ocCPX, amZP0, 3), i("SBC", ocSBC, amZP0, 3), i("INC", ocINC, amZP0, 5), i("???", ocXXX, amIMP, 5),
            i("INX", ocINX, amIMP, 2), i("SBC", ocSBC, amIMM, 2), i("NOP", ocNOP, amIMP, 2), i("???", ocSBC, amIMP, 2),
            i("CPX", ocCPX, amABS, 4), i("SBC", ocSBC, amABS, 4), i("INC", ocINC, amABS, 6), i("???", ocXXX, amIMP, 6),
            i("BEQ", ocBEQ, amREL, 2), i("SBC", ocSBC, amIZY, 5), i("???", ocXXX, amIMP, 2), i("???", ocXXX, amIMP, 8),
            i("???", ocNOP, amIMP, 4), i("SBC", ocSBC, amZPX, 4), i("INC", ocINC, amZPX, 6), i("???", ocXXX, amIMP, 6),
            i("SED", ocSED, amIMP, 2), i("SBC", ocSBC, amABY, 4), i("NOP", ocNOP, amIMP, 2), i("???", ocXXX, amIMP, 7),
            i("???", ocNOP, amIMP, 4), i("SBC", ocSBC, amABX, 4), i("INC", ocINC, amABX, 7), i("???", ocXXX, amIMP, 7),
        )

    enum class StatusFlag {
        /**
         * Carry Bit
         */
        C,

        /**
         * Zero
         */
        Z,

        /**
         * Disable Interrupts
         */
        I,

        /**
         * Decimal Mode (unused in this implementation)
         */
        D,

        /**
         * Break
         */
        B,

        /**
         * Unused
         */
        U,

        /**
         * Overflow
         */
        V,

        /**
         * Negative
         */
        N,
        ;

        val mask: UInt8 = UInt8(1u shl ordinal)
        val invMask: UInt8 = mask.inv()
    }

    companion object {
        infix fun StatusFlag.and(statusRegister: UInt8): Boolean = mask and statusRegister != x00u

        infix fun UInt8.or(flag: StatusFlag): UInt8 = this or flag.mask

        infix fun StatusFlag.or(statusRegister: UInt8): UInt8 = mask or statusRegister
    }
}

/**
 * The 6502 can address between 0x0000 - 0xFFFF. The high byte is often referred
 * to as the "page", and the low byte is the offset into that page. This implies
 * there are 256 pages, each containing 256 bytes.
 *
 * Several addressing modes have the potential to require an additional clock
 * cycle if they cross a page boundary. This is combined with several instructions
 * that enable this additional clock cycle. So each addressing function returns
 * a flag saying it has potential, as does each instruction. If both instruction
 * and address function return 1, then an additional clock cycle is required.
 */
private typealias AddressingMode = () -> Boolean

/**
 * There are 56 "legitimate" opcodes provided by the 6502 CPU. I
 * have not modelled "unofficial" opcodes. As each opcode is
 * defined by 1 byte, there are potentially 256 possible codes.
 * Codes are not used in a "switch case" style on a processor,
 * instead they are responsible for switching individual parts of
 * CPU circuits on and off. The opcodes listed here are official,
 * meaning that the functionality of the chip when provided with
 * these codes is as the developers intended it to be. Unofficial
 * codes will of course also influence the CPU circuitry in
 * interesting ways, and can be exploited to gain additional
 * functionality!
 *
 * These functions return false normally, but some are capable of
 * requiring more clock cycles when executed under certain
 * conditions combined with certain addressing modes. If that is
 * the case, they return true.
 */
private typealias OperationCode = () -> Boolean
