# CL3

CL3 is an in-order two-issue RISC-V processor written in Chisel.

### Sub-directories Overview

```
.
├── build.sc       # Chisel project build script
├── cl3/           # CL3 processor core
│   ├── src/
│   │   ├── cc/    # C++ sources for Verilator simulation testbench
│   │   └── scala/ # Chisel sources for the main processor design
│   └── test/
│       └── src/
├── LICENSE
├── Makefile       # Top-level Makefile for build and simulation automation
├── README.md
├── script/        # Helper scripts
├── soc/           # System on Chip (SoC) components
│   ├── soc.mk     # Makefile configuration for the SoC
│   └── top/
│       └── SimTop.sv # Top-level module for simulation (SystemVerilog)
├── sw/            # Software workloads (test programs, benchmarks)
└── utils/         # Common utilities and modules


```

### Generate Verilog

- Run `make verilog` to generate verilog. Refer to `Makefile` for more information.

### Simulation

Example:

```
# Navigate to workload subdirectory
cd sw/cpu-tests

# Compile and run the simulation.
# NOte: you should install verilator and RISC-V toolchain first
make run
```

See the `Makefile` in `sw` and `sw/cpu-tests` for more information for custom workload.

### Acknowledgement

This project has been developed by referencing the work of several other open-source projects. 

-  [Ibex](https://github.com/lowRISC/ibex)

- [Nutshell](https://github.com/OSCPU/NutShell)
- [Xiangshan](https://github.com/OpenXiangShan/XiangShan)
- [biriscv](https://github.com/ultraembedded/biriscv)
- [Zhoushan](https://github.com/OSCPU-Zhoushan/Zhoushan)