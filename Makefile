# Tools
MILL      := $(or $(shell which mill), ./mill) # Use global mill if available, otherwise use local ./mill
MKDIR     := mkdir -p
RM        := rm -rf
MAKE      ?= make
# VCC       ?= verilator
VCC       ?= vcs
WAVE      ?= gtkwave

# Project Configuration
PRJ       := cl3
PRJ_DIR   := $(CURDIR)
BUILD_DIR := ./build/$(VCC)
VSRC_DIR  := ./vsrc
WAVE_DIR  := ./wave
CPUTOP    := CL3Top
DUMP_WAVE ?= 1

RTLSRC_CPU  		:= $(VSRC_DIR)/$(CPUTOP).sv


# Generate Verilog
verilog:
	@echo "Generating Verilog files..."
	$(MKDIR) $(VSRC_DIR)
	$(MILL) -i $(PRJ).runMain Elaborate --target-dir $(VSRC_DIR)
	sed -i '/difftest.*\.sv/d' $(VSRC_DIR)/$(CPUTOP).sv
	sed -i '/mem_helper\.sv/d' $(VSRC_DIR)/$(CPUTOP).sv
	sed -i '/firrtl_black_box_resource_files.f/, $$d' $(VSRC_DIR)/$(CPUTOP).sv
	
# Show Help for Elaborate
help:
	@echo "Displaying help for Elaborate..."
	$(MILL) -i $(PRJ).runMain Elaborate --help

# Reformat Code
reformat:
	@echo "Reformatting code..."
	$(MILL) -i __.reformat

# Check Code Format
checkformat:
	@echo "Checking code format..."
	$(MILL) -i __.checkFormat

# Clean Build Artifacts
clean:
	@echo "Cleaning build artifacts..."
	$(RM) ./build
	$(RM) $(VSRC_DIR)

.PHONY: $(RTLSRC_CPU)

-include ./soc/soc.mk

VTOP := top
COMPILE_OUT := $(BUILD_DIR)/compile.log
BIN := $(BUILD_DIR)/$(VTOP)

# TODO: use systemverilog top 
# TODO: add iverilog support
ifeq ($(VCC), verilator)
	VF := $(addprefix +incdir+, $(RTLSRC_INCDIR)) \
	--Wno-lint --Wno-UNOPTFLAT --Wno-BLKANDNBLK --Wno-COMBDLY --Wno-MODDUP \
	./cl3/src/cc/verilator/main.cpp \
	./cl3/src/cc/verilator/difftest.cpp \
	-CFLAGS -I$(abspath ./cl3/src/cc/verilator/include) -DUSE_VERILATOR \
	--timescale 1ns/1ps \
	--autoflush \
	--trace --trace-fst \
	--build -j 0 --exe --timing --cc \
	--Mdir $(BUILD_DIR) \
	--top-module $(VTOP) -o $(VTOP)
else ifeq ($(VCC), vcs)
	VF := $(addprefix +incdir+, $(RTLSRC_INCDIR)) \
	+vc -full64 -sverilog +v2k -timescale=1ns/1ps \
	+lint=TFIPC-L +notimingcheck \
	-lca -kdb 	-debug_access \
	-P ${VERDI_HOME}/share/PLI/VCS/LINUX64/novas.tab \
     ${VERDI_HOME}/share/PLI/VCS/LINUX64/pli.a \
	./cl3/src/cc/vcs/difftest.cpp \
	-CFLAGS "-fPIC -std=gnu++0x \
	-I$(abspath ./cl3/src/cc/vcs/include) \
	$(if $(VCS_HOME),-I$(VCS_HOME)/include,)"  \
	-CXXFLAGS "-fPIC -std=gnu++0x \
    -I$(abspath ./cl3/src/cc/vcs/include) \
    $(if $(VCS_HOME),-I$(VCS_HOME)/include,)" \
	-LDFLAGS -Wl,--no-as-needed \
	-l $(COMPILE_OUT) \
	-Mdir=$(BUILD_DIR) \
	-top $(VTOP) -o $(BUILD_DIR)/$(VTOP)
else 
	$(error unsupport VCC)
endif

$(RTLSRC_CPU): verilog

$(BIN): $(RTLSRC_CPU) $(RTLSRC_PERIP) $(RTLSRC_INTERCON) $(RTLSRC_TOP)
	$(shell mkdir -p $(WAVE_DIR))
	$(shell mkdir -p $(BUILD_DIR))
	$(VCC) $(RTLSRC_CPU) $(RTLSRC_PERIP) $(RTLSRC_INTERCON) $(RTLSRC_TOP) $(VF)

bin: $(BIN)

REF ?= /mnt/mydata/cl3/utils/riscv32-spike-so
WAVE_TYPE ?= fsdb

# RUN_ARGS += --diff
RUN_ARGS += +ref=$(REF)
RUN_ARGS += +image=$(IMAGE).bin
RUN_ARGS += +firmware=$(IMAGE).mem

ifneq ($(DUMP_WAVE),)
RUN_ARGS += +$(WAVE_TYPE)
endif

# Test Targets (run, gdb, latest)
run: $(BIN)
	$(BIN) $(RUN_ARGS)

wave: 
	$(WAVE) $(WAVE_DIR)/top.$(WAVE_TYPE)
	
# Phony Targets
.PHONY: all verilog help reformat checkformat clean run wave
