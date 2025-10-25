#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

// WAV header structure
typedef struct {
    char     riff_tag[4];
    uint32_t riff_length;
    char     wave_tag[4];
    char     fmt_tag[4];
    uint32_t fmt_length;
    uint16_t audio_format;
    uint16_t num_channels;
    uint32_t sample_rate;
    uint32_t byte_rate;
    uint16_t block_align;
    uint16_t bits_per_sample;
    char     data_tag[4];
    uint32_t data_length;
} wav_header_t;

// Write WAV header
void write_wav_header(FILE *f, int sample_rate, int channels, int bits_per_sample, int num_samples) {
    wav_header_t header;
    memcpy(header.riff_tag, "RIFF", 4);
    memcpy(header.wave_tag, "WAVE", 4);
    memcpy(header.fmt_tag,  "fmt ", 4);
    memcpy(header.data_tag, "data", 4);

    header.fmt_length = 16;
    header.audio_format = 1; // PCM
    header.num_channels = channels;
    header.sample_rate = sample_rate;
    header.bits_per_sample = bits_per_sample;
    header.byte_rate = sample_rate * channels * bits_per_sample / 8;
    header.block_align = channels * bits_per_sample / 8;
    header.data_length = num_samples * channels * bits_per_sample / 8;
    header.riff_length = header.data_length + sizeof(wav_header_t) - 8;

    fwrite(&header, sizeof(header), 1, f);
}

int main(int argc, char *argv[]) {
    if (argc < 4) {
        fprintf(stderr, "Usage: %s input.raw output.wav sample_rate\n", argv[0]);
        return 1;
    }

    const char *input_file = argv[1];
    const char *output_file = argv[2];
    int sample_rate = atoi(argv[3]);

    FILE *fin = fopen(input_file, "rb");
    if (!fin) {
        perror("Error opening input file");
        return 1;
    }

    fseek(fin, 0, SEEK_END);
    long file_size = ftell(fin);
    fseek(fin, 0, SEEK_SET);

    int num_samples = file_size / sizeof(int16_t);
    int16_t *buffer = malloc(file_size);
    if (!buffer) {
        perror("Memory allocation failed");
        fclose(fin);
        return 1;
    }

    fread(buffer, sizeof(int16_t), num_samples, fin);
    fclose(fin);

    FILE *fout = fopen(output_file, "wb");
    if (!fout) {
        perror("Error opening output WAV");
        free(buffer);
        return 1;
    }

    write_wav_header(fout, sample_rate, 1, 16, num_samples);
    fwrite(buffer, sizeof(int16_t), num_samples, fout);
    fclose(fout);

    free(buffer);
    printf("Converted %s to %s (WAV)\n", input_file, output_file);
    return 0;
}
