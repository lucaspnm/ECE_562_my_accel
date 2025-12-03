#include <stdint.h>
#include <stdio.h>
#include <inttypes.h>
#include "rocc.h" // Chipyard integrated file for RoCC communication

#ifndef N
#define N 1024
#endif
#ifndef ITERS
#define ITERS 1
#endif

static int16_t in_buf[N]  __attribute__((aligned(64)));
static int16_t out_buf[N] __attribute__((aligned(64)));

// Default parameters
static const int16_t COMB_GAINS[4] = { 30000, 29500, 29000, 28500 };
static const int16_t AP_GAINS[2]   = { 28000, 26000 };
static const int16_t WET_GAIN      = 24576;


// RoCC interface functions
static inline void rocc_load_params(void) {
    for (int i = 0; i < 4; i++)
        ROCC_INSTRUCTION_SS(0, COMB_GAINS[i], i, 3);

    for (int i = 0; i < 2; i++)
        ROCC_INSTRUCTION_SS(0, AP_GAINS[i], i, 4);

    ROCC_INSTRUCTION_SS(0, WET_GAIN, 0, 5);
}

static inline void rocc_reset(void) {
    ROCC_INSTRUCTION_SS(0, 0, 0, 6);
}


// Run impulse through accelerator
static void run_ir(void) {
    for (int i = 0; i < N; i++) {
        in_buf[i]  = 0;
        out_buf[i] = 0;
    }
    in_buf[0] = 32767;

    for (int n = 0; n < N; n++) {
        uint64_t y = 0;

        ROCC_INSTRUCTION_SS(0, in_buf[n], 0, 0); // write sample
        ROCC_INSTRUCTION_SS(0, 0, 0, 1);         // step
        ROCC_INSTRUCTION_D(0, y, 2);             // read

        out_buf[n] = (int16_t)(y & 0xFFFF);
    }
}


// IR Summary
static void print_ir_summary(void) {

    int first_nz = -1, last_nz = -1, nz_cnt = 0;
    int peak_idx = -1;
    int16_t peak = 0;

    for (int i = 0; i < N; i++) {
        int16_t v = out_buf[i];
        if (v != 0) {
            if (first_nz < 0) first_nz = i;
            last_nz = i;
            nz_cnt++;
            if (peak_idx < 0 || v > peak || v < -peak) {
                peak = v;
                peak_idx = i;
            }
        }
    }

    printf("\n=== IR SUMMARY ===\n");
    printf("First non-zero index : %d\n", first_nz);
    printf("Last non-zero index  : %d\n", last_nz);
    printf("Non-zero sample count: %d\n", nz_cnt);
    printf("Peak amplitude       : %d at %d\n", peak, peak_idx);
}


// MAIN FUNCTION
int main(void) {

    printf("[UART] Running Schroeder RoCC Performance Test...\n");

    rocc_load_params();
    rocc_reset();

    // ---------------- TIMING START ----------------
    uint64_t start_c, end_c, start_i, end_i;

    asm volatile("fence" ::: "memory");
    asm volatile("csrr %0, mcycle"   : "=r"(start_c));
    asm volatile("csrr %0, minstret" : "=r"(start_i));

    run_ir();

    asm volatile("csrr %0, mcycle"   : "=r"(end_c));
    asm volatile("csrr %0, minstret" : "=r"(end_i));
    asm volatile("fence" ::: "memory");
    // ---------------- TIMING END ------------------

    uint64_t cycles = end_c - start_c;
    uint64_t inst   = end_i - start_i;

    printf("Cycles: %" PRIu64 "\n", cycles);
    printf("Instructions: %" PRIu64 "\n", inst);

    // Clean correct formatting
    uint64_t cpi_m = inst ? (cycles * 1000ULL) / inst : 0;
    uint64_t cps_m = (cycles * 1000ULL) / N;
    uint64_t ips_m = (inst   * 1000ULL) / N;

    printf("CPI             : %" PRIu64 ".%03" PRIu64 "\n",
           cpi_m / 1000, cpi_m % 1000);

    printf("cycles/sample   : %" PRIu64 ".%03" PRIu64 "\n",
           cps_m / 1000, cps_m % 1000);

    printf("instr/sample    : %" PRIu64 ".%03" PRIu64 "\n",
           ips_m / 1000, ips_m % 1000);


    print_ir_summary();

    // signal input/output — if needed later, uncomment below

    /*
    printf("\n=== IR Data Processing BEGIN ===\n");
    printf("# index,value\n");
    for (int i = 0; i < N; i++)
        printf("%d,%d\n", i, out_buf[i]);
    printf("=== IR Data Processing END ===\n");
    */

    printf("Done.\n");
    return 0;
}
