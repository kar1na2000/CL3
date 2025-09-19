package cl3

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode

object CL3InstInfo {
  val instPatterns = Seq(
    // EXEC (General-Purpose Execution Instructions)
    InstructionPattern(instType = "U", opType = "EXEC", name = "lui", opcode = BitPat("b0110111")),
    InstructionPattern(instType = "U", opType = "EXEC", name = "auipc", opcode = BitPat("b0010111")),
    InstructionPattern(
      instType = "I",
      opType = "EXEC",
      name = "addi",
      func3 = BitPat("b000"),
      opcode = BitPat("b0010011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "EXEC",
      name = "slti",
      func3 = BitPat("b010"),
      opcode = BitPat("b0010011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "EXEC",
      name = "sltiu",
      func3 = BitPat("b011"),
      opcode = BitPat("b0010011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "EXEC",
      name = "xori",
      func3 = BitPat("b100"),
      opcode = BitPat("b0010011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "EXEC",
      name = "ori",
      func3 = BitPat("b110"),
      opcode = BitPat("b0010011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "EXEC",
      name = "andi",
      func3 = BitPat("b111"),
      opcode = BitPat("b0010011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "EXEC",
      name = "slli",
      func7 = BitPat("b0000000"),
      func3 = BitPat("b001"),
      opcode = BitPat("b0010011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "EXEC",
      name = "srli",
      func7 = BitPat("b0000000"),
      func3 = BitPat("b101"),
      opcode = BitPat("b0010011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "EXEC",
      name = "srai",
      func7 = BitPat("b0100000"),
      func3 = BitPat("b101"),
      opcode = BitPat("b0010011")
    ),
    InstructionPattern(
      instType = "R",
      opType = "EXEC",
      name = "add",
      func7 = BitPat("b0000000"),
      func3 = BitPat("b000"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "R",
      opType = "EXEC",
      name = "sub",
      func7 = BitPat("b0100000"),
      func3 = BitPat("b000"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "R",
      opType = "EXEC",
      name = "sll",
      func7 = BitPat("b0000000"),
      func3 = BitPat("b001"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "R",
      opType = "EXEC",
      name = "slt",
      func7 = BitPat("b0000000"),
      func3 = BitPat("b010"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "R",
      opType = "EXEC",
      name = "sltu",
      func7 = BitPat("b0000000"),
      func3 = BitPat("b011"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "R",
      opType = "EXEC",
      name = "xor",
      func7 = BitPat("b0000000"),
      func3 = BitPat("b100"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "R",
      opType = "EXEC",
      name = "srl",
      func7 = BitPat("b0000000"),
      func3 = BitPat("b101"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "R",
      opType = "EXEC",
      name = "sra",
      func7 = BitPat("b0100000"),
      func3 = BitPat("b101"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "R",
      opType = "EXEC",
      name = "or",
      func7 = BitPat("b0000000"),
      func3 = BitPat("b110"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "R",
      opType = "EXEC",
      name = "and",
      func7 = BitPat("b0000000"),
      func3 = BitPat("b111"),
      opcode = BitPat("b0110011")
    ),

    // BRANCH (Branch and Jump Instructions)
    InstructionPattern(instType = "J", opType = "BRANCH", name = "jal", opcode = BitPat("b1101111")),
    InstructionPattern(
      instType = "I",
      opType = "BRANCH",
      name = "jalr",
      func3 = BitPat("b000"),
      opcode = BitPat("b1100111")
    ),
    InstructionPattern(
      instType = "B",
      opType = "BRANCH",
      name = "beq",
      func3 = BitPat("b000"),
      opcode = BitPat("b1100011")
    ),
    InstructionPattern(
      instType = "B",
      opType = "BRANCH",
      name = "bne",
      func3 = BitPat("b001"),
      opcode = BitPat("b1100011")
    ),
    InstructionPattern(
      instType = "B",
      opType = "BRANCH",
      name = "blt",
      func3 = BitPat("b100"),
      opcode = BitPat("b1100011")
    ),
    InstructionPattern(
      instType = "B",
      opType = "BRANCH",
      name = "bge",
      func3 = BitPat("b101"),
      opcode = BitPat("b1100011")
    ),
    InstructionPattern(
      instType = "B",
      opType = "BRANCH",
      name = "bltu",
      func3 = BitPat("b110"),
      opcode = BitPat("b1100011")
    ),
    InstructionPattern(
      instType = "B",
      opType = "BRANCH",
      name = "bgeu",
      func3 = BitPat("b111"),
      opcode = BitPat("b1100011")
    ),

    // MEM (Memory Access Instructions)
    InstructionPattern(
      instType = "I",
      opType = "MEM",
      name = "lb",
      func3 = BitPat("b000"),
      opcode = BitPat("b0000011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "MEM",
      name = "lh",
      func3 = BitPat("b001"),
      opcode = BitPat("b0000011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "MEM",
      name = "lw",
      func3 = BitPat("b010"),
      opcode = BitPat("b0000011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "MEM",
      name = "lbu",
      func3 = BitPat("b100"),
      opcode = BitPat("b0000011")
    ),
    InstructionPattern(
      instType = "I",
      opType = "MEM",
      name = "lhu",
      func3 = BitPat("b101"),
      opcode = BitPat("b0000011")
    ),
    InstructionPattern(
      instType = "S",
      opType = "MEM",
      name = "sb",
      func3 = BitPat("b000"),
      opcode = BitPat("b0100011")
    ),
    InstructionPattern(
      instType = "S",
      opType = "MEM",
      name = "sh",
      func3 = BitPat("b001"),
      opcode = BitPat("b0100011")
    ),
    InstructionPattern(
      instType = "S",
      opType = "MEM",
      name = "sw",
      func3 = BitPat("b010"),
      opcode = BitPat("b0100011")
    ),

    // MUL (Multiplication Instructions)
    InstructionPattern(
      instType = "M",
      opType = "MUL",
      name = "mul",
      func7 = BitPat("b0000001"),
      func3 = BitPat("b000"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "M",
      opType = "MUL",
      name = "mulh",
      func7 = BitPat("b0000001"),
      func3 = BitPat("b001"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "M",
      opType = "MUL",
      name = "mulhsu",
      func7 = BitPat("b0000001"),
      func3 = BitPat("b010"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "M",
      opType = "MUL",
      name = "mulhu",
      func7 = BitPat("b0000001"),
      func3 = BitPat("b011"),
      opcode = BitPat("b0110011")
    ),

    // DIV (Division and Remainder Instructions)
    InstructionPattern(
      instType = "M",
      opType = "DIV",
      name = "div",
      func7 = BitPat("b0000001"),
      func3 = BitPat("b100"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "M",
      opType = "DIV",
      name = "divu",
      func7 = BitPat("b0000001"),
      func3 = BitPat("b101"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "M",
      opType = "DIV",
      name = "rem",
      func7 = BitPat("b0000001"),
      func3 = BitPat("b110"),
      opcode = BitPat("b0110011")
    ),
    InstructionPattern(
      instType = "M",
      opType = "DIV",
      name = "remu",
      func7 = BitPat("b0000001"),
      func3 = BitPat("b111"),
      opcode = BitPat("b0110011")
    ),

    // CSR (Control and Status Register Instructions)
    InstructionPattern(
      instType = "CSR",
      opType = "CSR",
      name = "csrrw",
      func3 = BitPat("b001"),
      opcode = BitPat("b1110011")
    ),
    InstructionPattern(
      instType = "CSR",
      opType = "CSR",
      name = "csrrs",
      func3 = BitPat("b010"),
      opcode = BitPat("b1110011")
    ),
    InstructionPattern(
      instType = "CSR",
      opType = "CSR",
      name = "csrrc",
      func3 = BitPat("b011"),
      opcode = BitPat("b1110011")
    ),
    InstructionPattern(
      instType = "CSR",
      opType = "CSR",
      name = "csrrwi",
      func3 = BitPat("b101"),
      opcode = BitPat("b1110011")
    ),
    InstructionPattern(
      instType = "CSR",
      opType = "CSR",
      name = "csrrsi",
      func3 = BitPat("b110"),
      opcode = BitPat("b1110011")
    ),
    InstructionPattern(
      instType = "CSR",
      opType = "CSR",
      name = "csrrci",
      func3 = BitPat("b111"),
      opcode = BitPat("b1110011")
    ),

    // SYS (System Instructions)
    InstructionPattern(
      instType = "R",
      opType = "SYS",
      name = "fence",
      func3 = BitPat("b000"),
      opcode = BitPat("b0001111")
    ),
    InstructionPattern(
      instType = "PRIV",
      opType = "SYS",
      name = "ecall/ebreak",
      func3 = BitPat("b000"),
      opcode = BitPat("b1110011")
    )
  )

  val allFields = Seq(
    OP0Field,
    OP1Field,
    OP2Field,
    IllegalField,
    WENField,
    EXUField,
    LSUField,
    MULField,
    DIVField,
    CSRField,
    BRField
  )

}
