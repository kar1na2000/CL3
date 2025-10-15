WORK_DIR  = $(shell pwd)
DST_DIR   = $(WORK_DIR)/build/$(ARCH)

PRJ_HOME := $(abspath $(WORK_DIR)../../../)

IMAGE_REL = build/$(NAME)
IMAGE     = $(abspath $(IMAGE_REL))
ARCH      = riscv32

export IMAGE

SRCS += ../common/start.S ../common/trm.c ../common/string.c ../common/stdio.c ../common/int64.c
INC_PATH += ../common/include

OBJS      = $(addprefix $(DST_DIR)/, $(addsuffix .o, $(basename $(SRCS))))

COMMON_CFLAGS := -fno-pic -march=rv32im_zicsr -mcmodel=medany  -mstrict-align -mabi=ilp32
CROSS_COMPILE := $(if $(shell command -v riscv64-unknown-linux-gnu-gcc 2>/dev/null),riscv64-unknown-linux-gnu-,riscv64-linux-gnu-)


AS        = $(CROSS_COMPILE)gcc
CC        = $(CROSS_COMPILE)gcc
CXX       = $(CROSS_COMPILE)g++
LD        = $(CROSS_COMPILE)ld
AR        = $(CROSS_COMPILE)ar
OBJDUMP   = $(CROSS_COMPILE)objdump
OBJCOPY   = $(CROSS_COMPILE)objcopy
READELF   = $(CROSS_COMPILE)readelf
HEXDUMP   = hexdump

INC_PATH += $(WORK_DIR)/include
INCFLAGS += $(addprefix -I, $(INC_PATH))

# C/C++ FLAGS
CFLAGS   += -lm -g -O2 -MMD -Wall $(INCFLAGS) $(COMMON_CFLAGS) \
            -fno-asynchronous-unwind-tables -fno-builtin -fno-stack-protector \
            -Wno-main -U_FORTIFY_SOURCE -fvisibility=hidden
CFLAGS    += -fdata-sections -ffunction-sections
CXXFLAGS +=  $(CFLAGS) -ffreestanding -fno-rtti -fno-exceptions

# AS FLAGS
ASFLAGS  += -MMD $(INCFLAGS) $(COMMON_CFLAGS)

# LD FLAGS
LDFLAGS  += -z noexecstack
LDFLAGS += -melf32lriscv
LDFLAGS   += -T ../common/linker.ld \
						 --defsym=_pmem_start=0x80000000 --defsym=_entry_offset=0x0
LDFLAGS   += --gc-sections -e _start

default: image

$(DST_DIR)/%.o: %.c
	@mkdir -p $(dir $@) && echo + CC $<
	@$(CC) -std=gnu11 $(CFLAGS) -c -o $@ $(realpath $<)

$(DST_DIR)/%.o: %.cc
	@mkdir -p $(dir $@) && echo + CXX $<
	@$(CXX) -std=c++17 $(CXXFLAGS) -c -o $@ $(realpath $<)

$(DST_DIR)/%.o: %.cpp
	@mkdir -p $(dir $@) && echo + CXX $<
	@$(CXX) -std=c++17 $(CXXFLAGS) -c -o $@ $(realpath $<)

$(DST_DIR)/%.o: %.S
	@mkdir -p $(dir $@) && echo + AS $<
	@$(AS) $(ASFLAGS) -c -o $@ $(realpath $<)

$(IMAGE).elf: $(OBJS)
	@echo + LD "->" $(IMAGE_REL).elf
	@$(LD) $(LDFLAGS) -o $(IMAGE).elf --start-group $(OBJS) --end-group

-include $(addprefix $(DST_DIR)/, $(addsuffix .d, $(basename $(SRCS))))

# TODO:
RAM_START_ADDR = 0x80000000

image:  $(IMAGE).elf image-dep
	@$(OBJDUMP) -d $(IMAGE).elf > $(IMAGE).txt
	@echo + OBJCOPY "->" $(IMAGE_REL).bin
	@$(OBJCOPY) -S --set-section-flags .bss=alloc,contents -O binary $(IMAGE).elf $(IMAGE).bin
	@echo + OBJCOPY "->" $(IMAGE_REL).hex
	@$(OBJCOPY) -O verilog --change-addresses -$(RAM_START_ADDR) --verilog-data-width 4 $< $(IMAGE).hex
	@echo + HEXDUMP "->" $(IMAGE_REL).mem
	@$(HEXDUMP) -v -e '/4 "%08x\n"' $(IMAGE).bin > $(IMAGE).mem
image-dep: $(OBJS)
	@echo \# Creating image
.PHONY: image image-dep


clean:
	rm -rf $(WORK_DIR)/build/
.PHONY: clean

run: image
	$(MAKE) -C $(PRJ_HOME) run 

gdb: image
	$(MAKE) -C $(PRJ_HOME) gdb