// Verilated -*- C++ -*-
// DESCRIPTION: main() calling loop, created with Verilator --main

#include "Vtop.h"
#include "verilated.h"
#include "verilated_fst_c.h"
#include <difftest.h>
#include <getopt.h>
#include <iostream>

static struct option long_options[] = {
    {"ref", required_argument, nullptr, 'r'},
    {"image", required_argument, nullptr, 'i'},
    {"help", no_argument, nullptr, 'h'},
    {"diff", no_argument, nullptr, 'd'},
    {nullptr, 0, nullptr, 0}};

void print_usage(const char *prog_name) {
  std::cerr << "Usage: " << prog_name << " [options]\n"
            << "Options:\n"
            << "  -r, --ref <file>     Reference file\n"
            << "  -i, --image <file>   Image file\n"
            << "  -d, --diff           Enable difftest\n"
            << "  -h, --help           Show this help message\n";
}

int main(int argc, char **argv, char **) {

  // Setup context, defaults, and parse command line
  Verilated::debug(0);
  const std::unique_ptr<VerilatedContext> contextp{new VerilatedContext};
  contextp->traceEverOn(true);
  contextp->commandArgs(argc, argv);

  // Construct the Verilated model, from Vtop.h generated from Verilating
  const std::unique_ptr<Vtop> topp{new Vtop{contextp.get(), ""}};

  const char *ref_file = nullptr;
  const char *img_file = nullptr;
  bool diff_enable = false;

  int opt;
  int option_idx = 0;
  while ((opt = getopt_long(argc, argv, "r:i:hd", long_options, &option_idx)) !=
         -1) {
    switch (opt) {
    case 'r':
      ref_file = optarg;
      break;
    case 'i':
      img_file = optarg;
      break;
    case 'h':
      print_usage(argv[0]);
      return 0;
    case 'd':
      diff_enable = true;
      break;
    case '?':
      print_usage(argv[0]);
      return 1;
    default:
      std::cerr << "Unknown error while parsing options\n";
      return 1;
    }
  }

  if (diff_enable) {
    difftest_init(topp.get(), ref_file, img_file);
  }

  // Simulate until $finish
  while (!contextp->gotFinish()) {
    // Evaluate model
    topp->eval();
    // Advance time
    if (!topp->eventsPending())
      break;
    contextp->time(topp->nextTimeSlot());
  }

  if (!contextp->gotFinish()) {
    VL_DEBUG_IF(VL_PRINTF("+ Exiting without $finish; no events left\n"););
  }

  // Execute 'final' processes
  topp->final();

  // Print statistical summary report
  contextp->statsPrintSummary();

  return 0;
}
