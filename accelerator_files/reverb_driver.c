#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>

// ================================================================
// RoCC Communication Macros
// ================================================================

// Define a macro for a RoCC custom instruction.
// This uses the same opcode (custom0) as your ReverbRoCC accelerator.
// 'funct' is 0 since we are not using multiple functions.
#define ROCC_INSTRUCTION(opcode, rd, rs1, rs2, funct)             \
  asm volatile (                                                  \
    ".insn r 0x0B, " #funct ", " #opcode ", %0, %1, %2"           \
    : "=r" (rd)                                                   \
    : "r" (rs1), "r" (rs2))

// The opcode you used in Chisel (custom0 = 0)
#define REVERB_OPCODE 0

// ================================================================
// Function: Send one PCM sample to accelerator and get processed one
// ================================================================
static inline int16_t reverb_process_sample(int16_t sample)
{
    int16_t processed;
    // Send sample in rs1, no second operand needed
    ROCC_INSTRUCTION(REVERB_OPCODE, processed, sample, 0, 0);
    return processed;
}

// ================================================================
// Main Program
// ================================================================
int main(int argc, char *argv[])
{
    if (argc < 3) {
        printf("Usage: %s input.pcm output.pcm\n", argv[0]);
        return 1;
    }

    const char *input_file = argv[1];
    const char *output_file = argv[2];

    FILE *fin = fopen(input_file, "rb");
    if (!fin) {
        perror("Error opening input file");
        return 1;
    }

    FILE *fout = fopen(output_file, "wb");
    if (!fout) {
        perror("Error opening output file");
        fclose(fin);
        return 1;
    }

    printf("Running ReverbRoCC on PCM data...\n");

    int16_t input_sample;
    int16_t output_sample;
    size_t samples_processed = 0;

    while (fread(&input_sample, sizeof(int16_t), 1, fin) == 1) {
        // Send to RoCC, get processed result
        output_sample = reverb_process_sample(input_sample);
        fwrite(&output_sample, sizeof(int16_t), 1, fout);
        samples_processed++;
    }

    printf("Done. Processed %zu samples.\n", samples_processed);

    fclose(fin);
    fclose(fout);

    return 0;
}
