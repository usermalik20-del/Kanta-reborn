#include <stdlib.h>
#include <stdint.h>
#include <stddef.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include "soundfont.h"
#include "sf2-validation.h"

typedef union allocation_header { size_t size; max_align_t alignment; } allocation_header;
static _Thread_local size_t allocated;
static const size_t budget = 128u * 1024u * 1024u;

static void* bounded_malloc(size_t bytes) {
    if (bytes > budget - allocated || bytes > SIZE_MAX - sizeof(allocation_header)) return NULL;
    allocation_header* p = malloc(sizeof(*p) + bytes);
    if (!p) return NULL;
    p->size = bytes;
    allocated += bytes;
    return p + 1;
}
static void bounded_free(void* value) {
    if (!value) return;
    allocation_header* p = (allocation_header*)value - 1;
    allocated -= p->size;
    free(p);
}
static void* bounded_realloc(void* value, size_t bytes) {
    if (!value) return bounded_malloc(bytes);
    allocation_header* p = (allocation_header*)value - 1;
    size_t old = p->size;
    if (bytes > budget - (allocated - old) || bytes > SIZE_MAX - sizeof(*p)) return NULL;
    p = realloc(p, sizeof(*p) + bytes);
    if (!p) return NULL;
    p->size = bytes;
    allocated = allocated - old + bytes;
    return p + 1;
}
#define TSF_MALLOC bounded_malloc
#define TSF_REALLOC bounded_realloc
#define TSF_FREE bounded_free
#define TSF_IMPLEMENTATION
#include "vendor/tsf.h"

struct kanta_soundfont { tsf* synth; };

kanta_soundfont* kanta_sf_open(const char* path) {
    struct stat info;
    if (!path || stat(path, &info) || !S_ISREG(info.st_mode) || info.st_size < 12) return NULL;

    // Validate the cross-table references before the third-party loader dereferences them. The
    // file is mapped for this rather than read, so a large bank costs address space instead of
    // a heap copy that has to fit in RAM, and the validator only faults in the directory and
    // the sample headers. This also returns the sample count the region check below needs; the
    // pinned loader keeps no such count of its own.
    size_t size = (size_t)info.st_size;
    int descriptor = open(path, O_RDONLY | O_CLOEXEC);
    if (descriptor < 0) return NULL;
    void* mapped = mmap(NULL, size, PROT_READ, MAP_PRIVATE, descriptor, 0);
    close(descriptor);
    if (mapped == MAP_FAILED) return NULL;
    size_t sample_count = sf_validate((const unsigned char*)mapped, size);
    munmap(mapped, size);
    if (!sample_count) return NULL;

    // The sample data is streamed into the loader instead of being read into a second copy of
    // the whole file.
    FILE* file = fopen(path, "rb");
    if (!file) return NULL;
    struct tsf_stream stream = {
        .data = file,
        .read = (int(*)(void*, void*, unsigned int))&tsf_stream_stdio_read,
        .skip = (int(*)(void*, unsigned int))&tsf_stream_stdio_skip
    };
    tsf* synth = tsf_load(&stream);
    fclose(file);

    if (!synth || tsf_get_presetcount(synth) == 0) { if (synth) tsf_close(synth); return NULL; }

    // Second line of defence: every decoded region must stay inside the sample data the
    // validator sized above.
    for (int p = 0; p < synth->presetNum; ++p) {
        for (int r = 0; r < synth->presets[p].regionNum; ++r) {
            struct tsf_region* region = &synth->presets[p].regions[r];
            if (region->offset >= region->end || region->end >= sample_count ||
                (region->loop_mode && (region->loop_start >= region->loop_end ||
                 region->loop_end >= region->end))) {
                tsf_close(synth);
                return NULL;
            }
        }
    }

    tsf_set_output(synth, TSF_STEREO_INTERLEAVED, 44100, -9.0f);
    if (!tsf_set_max_voices(synth, 128)) { tsf_close(synth); return NULL; }
    for (int channel = 0; channel < 16; ++channel) {
        if (!tsf_channel_set_presetindex(synth, channel, 0)) { tsf_close(synth); return NULL; }
    }
    tsf_channel_set_presetnumber(synth, 9, 0, 1);

    kanta_soundfont* result = malloc(sizeof(*result));
    if (!result) { tsf_close(synth); return NULL; }
    result->synth = synth;
    return result;
}
void kanta_sf_close(kanta_soundfont* bank) {
    if (bank) { tsf_close(bank->synth); free(bank); }
}
void kanta_sf_midi(kanta_soundfont* bank, int status, int a, int b) {
    if (!bank || status < 0x80 || status > 0xef || a < 0 || a > 127 || b < 0 || b > 127) return;
    int channel = status & 15;
    switch (status & 0xf0) {
        case 0x80: tsf_channel_note_off(bank->synth, channel, a); break;
        case 0x90:
            if (b) tsf_channel_note_on(bank->synth, channel, a, b / 127.0f);
            else tsf_channel_note_off(bank->synth, channel, a);
            break;
        case 0xb0: tsf_channel_midi_control(bank->synth, channel, a, b); break;
        case 0xc0: tsf_channel_set_presetnumber(bank->synth, channel, a, channel == 9); break;
        case 0xe0: tsf_channel_set_pitchwheel(bank->synth, channel, (b << 7) | a); break;
        default: break;
    }
}
void kanta_sf_tune(kanta_soundfont* bank, float semitones) {
    if (!bank || !isfinite(semitones)) return;
    if (semitones < -12) semitones = -12;
    if (semitones > 12) semitones = 12;
    for (int channel = 0; channel < 16; ++channel)
        tsf_channel_set_tuning(bank->synth, channel, channel == 9 ? 0 : semitones);
}
void kanta_sf_render(kanta_soundfont* bank, float* samples, int frames) {
    if (!bank || !samples || frames < 0 || frames > 4096) return;
    tsf_render_float(bank->synth, samples, frames, 0);
    for (int i = 0; i < frames * 2; ++i) {
        float value = samples[i];
        samples[i] = !isfinite(value) ? 0 : value > 1 ? 1 : value < -1 ? -1 : value;
    }
}
