# Project Configuration
PRJ       := cl3
PRJ_DIR   := $(CURDIR)
BUILD_DIR := ./build/$(VCC)
VSRC_DIR  := ./vsrc
WAVE_DIR  := ./wave
CPUTOP    := SimTop
DUMP_WAVE := 1

$(shell mkdir -p $(WAVE_DIR))

# Tools
MILL      := $(or $(shell which mill), ./mill) # Use global mill if available, otherwise use local ./mill
MKDIR     := mkdir -p
RM        := rm -rf
MAKE      ?= make
VCC       ?= verilator
WAVE      ?= gtkwave

# Phony Targets
.PHONY: all verilog help reformat checkformat clean run

# Generate Verilog
verilog:
	@echo "Generating Verilog files..."
	$(MKDIR) $(VSRC_DIR)
	$(MILL) -i $(PRJ).runMain Elaborate --target-dir $(VSRC_DIR)
	
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
	$(RM) $(BUILD_DIR)
	$(RM) $(VSRC_DIR)


RTLSRC_CPU  		:= $(VSRC_DIR)/$(CPUTOP).sv

.PHONY: $(RTLSRC_CPU)

-include ./soc/soc.mk

VTOP := top
COMPILE_OUT := $(BUILD_DIR)/compile.log
BIN := $(BUILD_DIR)/$(VTOP)

# TODO: use systemverilog top 
ifeq ($(VCC), verilator)
	VF := $(addprefix +incdir+, $(RTLSRC_INCDIR)) \
	--Wno-lint --Wno-UNOPTFLAT --Wno-BLKANDNBLK --Wno-COMBDLY --Wno-MODDUP \
	./cl3/src/cc/verilator/main.cpp \
	./cl3/src/cc/verilator/difftest.cpp \
	--timescale 1ns/1ps \
	--autoflush \
	--trace --trace-fst \
	--build -j 0 --exe --timing --cc \
	--Mdir $(BUILD_DIR) \
	--top-module $(VTOP) -o $(VTOP)
else ifeq ($(VCC), vcs)
	VF := $(addprefix +incdir+, $(RTLSRC_INCDIR)) \
	+vc -full64 -sverilog +v2k -timescale=1ns/1ps \
	-LDFLAGS -Wl,--no-as-needed \
	+lint=TFIPC-L \
	-lca -kdb \
	-CC "$(if $(VCS_HOME), -I$(VCS_HOME)//include,)" \
	-debug_access -l $(COMPILE_OUT) \
	-Mdir=$(BUILD_DIR) \
	-top $(VTOP) -o $(BUILD_DIR)/$(VTOP)
else 
	$(error unsupport VCC)
endif


$(BIN): $(RTLSRC_CPU) $(RTLSRC_PERIP) $(RTLSRC_INTERCON) $(RTLSRC_TOP)
	$(VCC) $(RTLSRC_CPU) $(RTLSRC_PERIP) $(RTLSRC_INTERCON) $(RTLSRC_TOP) $(VF)

bin: $(BIN)

REF ?= ./utils/riscv32-spike-so
TEST_CASE ?= dummy
TEST_NAME ?= dummy
WAVE_TYPE ?= fst

ifneq ($(DUMP_WAVE),)
RUN_ARGS += +$(WAVE_TYPE)
endif
RUN_ARGS += --diff
RUN_ARGS += +firmware=./test/$(TEST_CASE)/build/$(TEST_NAME)-cl3.hex
RUN_ARGS += --image=./test/$(TEST_CASE)/build/$(TEST_NAME)-cl3.bin
RUN_ARGS += --ref=$(REF)

# Test Targets (run, gdb, latest)
run: $(BIN)
	$(BIN) $(RUN_ARGS)

wave:
	$(WAVE) $(WAVE_DIR)/$(VTOP).$(WAVE_TYPE)

.PHONY: $(BIN) wave

