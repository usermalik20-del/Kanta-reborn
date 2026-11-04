#include <assert.h>
#include <stdio.h>
#include <math.h>
#include <string.h>
#include <unistd.h>
#include "../app/src/main/cpp/soundfont.c"

static void allocation_checks(void) {
    assert(allocated == 0);
    assert(budget == (size_t)1536u * 1024u * 1024u);
    // The full piano's decoded samples fit, with headroom for loader metadata.
    assert(budget > (size_t)1163416536u + 64u * 1024u * 1024u);
    assert(!bounded_malloc(budget + 1));
    assert(!bounded_malloc(SIZE_MAX));
    assert(allocated == 0);

    void* value = bounded_realloc(NULL, 64);
    assert(value && allocated == 64);
    memset(value, 0x5a, 64);
    // Simulate other live allocations near the ceiling without allocating GiBs.
    const size_t other = budget - 80;
    allocated = other + 64;
    assert(!bounded_malloc(17));
    assert(!bounded_realloc(value, 81));
    assert(!bounded_realloc(value, SIZE_MAX));
    assert(allocated == other + 64 && ((unsigned char*)value)[0] == 0x5a);
    value = bounded_realloc(value, 80);
    assert(value && allocated == budget);
    assert(((unsigned char*)value)[0] == 0x5a);
    assert(!bounded_malloc(1));
    assert(!bounded_realloc(value, 81));
    assert(allocated == budget);
    value = bounded_realloc(value, 32);
    assert(value && allocated == other + 32);
    bounded_free(value);
    assert(allocated == other);
    value = bounded_malloc(80);
    assert(value && allocated == budget);
    assert(!bounded_malloc(1));
    bounded_free(value);
    assert(allocated == other);
    allocated = 0; // Retire the simulated allocations, not real heap pointers.
    bounded_free(NULL);
    assert(allocated == 0);
    puts("SoundFont: 1536 MiB cap, exact boundary, realloc/overflow accounting passed");
}

static double frequency(kanta_soundfont* bank, float tuning) {
    kanta_sf_tune(bank, tuning);
    kanta_sf_midi(bank, 0xb0, 120, 0);
    kanta_sf_midi(bank, 0x90, 69, 110);
    float block[1024];
    int crossings = 0;
    float previous = 0, peak = 0;
    for (int i = 0; i < 100; ++i) {
        kanta_sf_render(bank, block, 512);
        for (int j = 0; j < 512; ++j) {
            float value = block[j * 2];
            assert(isfinite(value) && fabsf(value) <= 1);
            if (fabsf(value) > peak) peak = fabsf(value);
            if (previous < 0 && value >= 0) crossings++;
            previous = value;
        }
    }
    assert(peak > .001f);
    return crossings * 44100.0 / (512 * 100);
}
int main(int argc, char** argv) {
    assert(argc == 2);
    allocation_checks();
    kanta_soundfont* bank = kanta_sf_open(argv[1]);
    assert(bank);
    double base = frequency(bank, 0), raised = frequency(bank, 12);
    assert(base > 430 && base < 450);
    assert(raised > 860 && raised < 900);
    kanta_sf_tune(bank, NAN);
    kanta_sf_midi(bank, -1, 128, 255);
    kanta_sf_close(bank);
    assert(allocated == 0);

    // SF3/Ogg regression: the minimal compressed bank must open, render the same
    // 440 Hz tone and reject every truncation (path is repo-root relative, as in CI).
    kanta_soundfont* sf3 = kanta_sf_open("apps/android/tests/data/tone.sf3");
    assert(sf3);
    {
        // One-time diagnostic (permanent, cheap): surface the post-decode sample
        // accounting and the region the looped SF3 note will render with.
        struct kanta_soundfont* raw = (struct kanta_soundfont*)sf3;
        struct tsf* synth = raw->synth;
        struct tsf_region* r0 = &synth->presets[0].regions[0];
        fprintf(stderr,
                "SF3 diag: sampleTotal=%u presetRegions=%d region offset=%d end=%d loop=%d..%d mode=%d voices=%d/%d fontSamples[0..4]=%g %g %g %g %g [108]=%g [26460..26463]=%g %g %g %g\n",
                synth->sampleTotal, synth->presets[0].regionNum, r0->offset, r0->end,
                r0->loop_start, r0->loop_end, r0->loop_mode, synth->voiceNum,
                synth->maxVoiceNum, synth->fontSamples[0], synth->fontSamples[1],
                synth->fontSamples[2], synth->fontSamples[3], synth->fontSamples[4],
                synth->fontSamples[108], synth->fontSamples[26460], synth->fontSamples[26461],
                synth->fontSamples[26462], synth->fontSamples[26463]);
    }
    double sf3_base = frequency(sf3, 0);
    assert(sf3_base > 430 && sf3_base < 450);
    kanta_sf_close(sf3);
    assert(allocated == 0);
    FILE* sf3_file = fopen("apps/android/tests/data/tone.sf3", "rb");
    assert(sf3_file);
    assert(fseek(sf3_file, 0, SEEK_END) == 0);
    size_t sf3_size = (size_t)ftell(sf3_file);
    rewind(sf3_file);
    unsigned char* sf3_bytes = malloc(sf3_size);
    assert(sf3_bytes && fread(sf3_bytes, 1, sf3_size, sf3_file) == sf3_size);
    fclose(sf3_file);
    assert(sf_validate(sf3_bytes, sf3_size));
    for (size_t cut = 0; cut < sf3_size; cut += 17) assert(!sf_validate(sf3_bytes, cut));
    free(sf3_bytes);

    FILE* file = fopen(argv[1], "rb");
    assert(file);
    assert(fseek(file, 0, SEEK_END) == 0);
    size_t size = (size_t)ftell(file);
    rewind(file);
    unsigned char* original = malloc(size);
    unsigned char* mutation = malloc(size);
    assert(original && mutation && fread(original, 1, size, file) == size);
    fclose(file);
    assert(sf_validate(original, size));
    for (size_t cut = 0; cut < size; cut += 17) assert(!sf_validate(original, cut));
    char path[] = "/tmp/kanta-sf-test-XXXXXX";
    int fd = mkstemp(path);
    assert(fd >= 0);
    close(fd);
    const char* bad_tables[] = {"phdr", "pbag", "inst", "ibag", "pgen", "igen"};
    const size_t field_offsets[] = {24, 0, 20, 0, 2, 2};
    for (size_t test = 0; test < 6; ++test) {
        memcpy(mutation, original, size);
        size_t pos;
        for (pos = 12; pos + 40 < size; ++pos) if (!memcmp(mutation + pos, bad_tables[test], 4)) break;
        assert(pos + 40 < size);
        size_t field = pos + 8 + field_offsets[test];
        if (test >= 4) { mutation[pos + 8] = test == 4 ? 41 : 53; mutation[pos + 9] = 0; }
        mutation[field] = mutation[field + 1] = 255;
        assert(!sf_validate(mutation, size));
    }
    unsigned seed = 29;
    for (int test = 0; test < 2000; ++test) {
        memcpy(mutation, original, size);
        for (int n = 0; n < 4; ++n) {
            seed = seed * 1664525u + 1013904223u;
            size_t offset = seed % size;
            mutation[offset] ^= (unsigned char)(1u << ((seed >> 24) & 7));
        }
        file = fopen(path, "wb");
        assert(file && fwrite(mutation, 1, size, file) == size);
        fclose(file);
        bank = kanta_sf_open(path);
        if (bank) {
            kanta_sf_midi(bank, 0x90, test % 128, 127);
            float block[1024];
            kanta_sf_render(bank, block, 512);
            kanta_sf_close(bank);
        }
        assert(allocated == 0);
    }
    unlink(path);
    free(mutation);
    free(original);
    printf("SoundFont: audible tone %.1f Hz, transposed %.1f Hz; truncation and 2000 mutation checks passed\n", base, raised);
    return 0;
}
