#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>
#include <string.h>
#include <mpg123.h>
#include <errno.h>

#define OUTPUT_SAMPLE_RATE 44100
#define OUTPUT_CHANNELS 1
#define OUTPUT_ENCODING MPG123_ENC_SIGNED_16

// --- Comb filter ---
typedef struct {
    float *buffer;
    int size;
    int index;
    float feedback;
} CombFilter;

void comb_init(CombFilter *c, int delay, float feedback) {
    c->size = delay;
    c->index = 0;
    c->feedback = feedback;
    c->buffer = calloc(delay, sizeof(float));
}

float comb_process(CombFilter *c, float input) {
    float output = c->buffer[c->index];
    c->buffer[c->index] = input + output * c->feedback;
    c->index = (c->index + 1) % c->size;
    return output;
}

// --- All-pass filter ---
typedef struct {
    float *buffer;
    int size;
    int index;
    float feedback;
} AllpassFilter;

void allpass_init(AllpassFilter *a, int delay, float feedback) {
    a->size = delay;
    a->index = 0;
    a->feedback = feedback;
    a->buffer = calloc(delay, sizeof(float));
}

float allpass_process(AllpassFilter *a, float input) {
    float bufout = a->buffer[a->index];
    float output = -input + bufout;
    a->buffer[a->index] = input + bufout * a->feedback;
    a->index = (a->index + 1) % a->size;
    return output;
}

int main(int argc, char *argv[]) {
    if (argc < 3) {
        fprintf(stderr, "Usage: %s input.mp3 output.raw\n", argv[0]);
        return 1;
    }

    const char *input_file = argv[1];
    const char *output_file = argv[2];

    // --- Initialize mpg123 ---
    if (mpg123_init() != MPG123_OK) {
        fprintf(stderr, "Error initializing mpg123.\n");
        return 1;
    }

    int err;
    mpg123_handle *mh = mpg123_new(NULL, &err);
    if (!mh) {
        fprintf(stderr, "mpg123_new failed: %s\n", mpg123_plain_strerror(err));
        mpg123_exit();
        return 1;
    }

    if (mpg123_open(mh, input_file) != MPG123_OK) {
        fprintf(stderr, "Error opening MP3: %s\n", mpg123_strerror(mh));
        mpg123_delete(mh);
        mpg123_exit();
        return 1;
    }

    // Force mono 16-bit 44.1kHz output
    if (mpg123_format_none(mh) != MPG123_OK ||
        mpg123_format(mh, OUTPUT_SAMPLE_RATE, OUTPUT_CHANNELS, OUTPUT_ENCODING) != MPG123_OK) {
        fprintf(stderr, "Failed to set output format.\n");
        mpg123_close(mh);
        mpg123_delete(mh);
        mpg123_exit();
        return 1;
    }

    size_t buffer_size = mpg123_outblock(mh);
    unsigned char *audio_buffer = malloc(buffer_size);

    int16_t *pcm_data = malloc(10 * 1024 * 1024); // 10 MB buffer
    int total_samples = 0;
    size_t done;

    while (mpg123_read(mh, audio_buffer, buffer_size, &done) == MPG123_OK) {
        memcpy(pcm_data + total_samples, audio_buffer, done);
        total_samples += done / sizeof(int16_t);
    }

    mpg123_close(mh);
    mpg123_delete(mh);
    mpg123_exit();
    free(audio_buffer);

    printf("Decoded %d samples from %s\n", total_samples, input_file);

    // --- Initialize comb + all-pass network ---
    const int num_combs = 4;
    const int num_allpasses = 2;
    CombFilter combs[num_combs];
    AllpassFilter allpasses[num_allpasses];

    int comb_delays[] = {1116, 1188, 1277, 1356};
    float comb_feedbacks[] = {0.805f, 0.827f, 0.783f, 0.764f};
    int allpass_delays[] = {225, 556};
    float allpass_feedbacks[] = {0.7f, 0.5f};

    for (int i = 0; i < num_combs; i++) comb_init(&combs[i], comb_delays[i], comb_feedbacks[i]);
    for (int i = 0; i < num_allpasses; i++) allpass_init(&allpasses[i], allpass_delays[i], allpass_feedbacks[i]);

    int16_t *output_data = malloc(total_samples * sizeof(int16_t));

    // --- Process samples (simulating RoCC accelerator) ---
    for (int i = 0; i < total_samples; i++) {
        float input_sample = pcm_data[i] / 32768.0f;

        // Parallel comb filters
        float comb_sum = 0.0f;
        for (int j = 0; j < num_combs; j++)
            comb_sum += comb_process(&combs[j], input_sample);
        comb_sum /= num_combs;

        // Serial all-pass filters
        float ap_out = comb_sum;
        for (int j = 0; j < num_allpasses; j++)
            ap_out = allpass_process(&allpasses[j], ap_out);

        float output_sample = 0.7f * input_sample + 0.3f * ap_out;

        if (output_sample > 1.0f) output_sample = 1.0f;
        if (output_sample < -1.0f) output_sample = -1.0f;

        output_data[i] = (int16_t)(output_sample * 32767);
    }

    // --- Write raw PCM output (RoCC-style) ---
    FILE *fout = fopen(output_file, "wb");
    if (!fout) {
        perror("Error opening output file");
        return 1;
    }
    fwrite(output_data, sizeof(int16_t), total_samples, fout);
    fclose(fout);

    printf("Processed raw PCM written to %s\n", output_file);

    free(pcm_data);
    free(output_data);
    for (int i = 0; i < num_combs; i++) free(combs[i].buffer);
    for (int i = 0; i < num_allpasses; i++) free(allpasses[i].buffer);

    return 0;
}
