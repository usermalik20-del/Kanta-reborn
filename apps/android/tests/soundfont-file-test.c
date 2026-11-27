/* Optional local regression for user-supplied banks. Read-only; no PCM allocation.
   cc -D_POSIX_C_SOURCE=200809L -std=c11 -Wall -Wextra -Werror 
      apps/android/tests/soundfont-file-test.c -lm -o /tmp/kanta-sf-file-test
   /tmp/kanta-sf-file-test /path/to/bank.sf2
   Maps the bank for production validation, then seeks over samples and exercises
   the real hydra readers, preset expansion and post-load region safety checks. */
#include <assert.h>
#include <inttypes.h>
#include "../app/src/main/cpp/soundfont.c"

int main(int argc, char** argv) {
    assert(argc == 2 || argc == 3);
    size_t expected_unsafe = argc == 3 ? (size_t)strtoul(argv[2], NULL, 10) : 0;
    int fd = open(argv[1], O_RDONLY | O_CLOEXEC);
    struct stat info;
    assert(fd >= 0 && !fstat(fd, &info) && info.st_size >= 12);
    assert((uintmax_t)info.st_size <= SIZE_MAX);
    size_t size = (size_t)info.st_size;
    const unsigned char* data = mmap(NULL, size, PROT_READ, MAP_PRIVATE, fd, 0);
    assert(data != MAP_FAILED);
    close(fd);
    size_t frames = sf_validate(data, size);
    printf("file_bytes=%zu validated_frames=%zu\n", size, frames);
    assert(frames > 1); /* This bounded probe is PCM-only, not an SF3 decode test. */
    assert(!sf_validate(data, size - 1));
    FILE* file = fopen(argv[1], "rb"); assert(file);
    struct tsf_stream stream = {file, tsf_stream_stdio_read, tsf_stream_stdio_skip};
    struct tsf_hydra hydra = {0};
    size_t metadata_bytes = 0;
    /* Simulate other allocations so even a future accidental sample allocation
       cannot exceed 64 MiB. No backing storage exists for this accounting baseline. */
    size_t baseline = current_budget() - 64u * 1024u * 1024u;
    allocated = baseline;
    for (size_t p = 12; p < size; p += 8 + sf_u32(data + p + 4)) {
        if (memcmp(data + p, "LIST", 4) || memcmp(data + p + 8, "pdta", 4)) continue;
        /* Exercise the pinned stream skip across the signed-2-GiB boundary. */
        rewind(file);
        assert(p + 12 <= UINT32_MAX && stream.skip(file, (unsigned)(p + 12)));
        assert((uintmax_t)ftello(file) == p + 12);
        size_t end = p + 8 + sf_u32(data + p + 4);
        for (size_t c = p + 12; c < end; c += 8 + sf_u32(data + c + 4)) {
            unsigned char header[8];
            assert(stream.read(file, header, 8) == 8);
            unsigned bytes = sf_u32(header + 4);
            metadata_bytes += bytes;
#define READ_TABLE(name, stride) \
            if (!memcmp(header, #name, 4)) { \
                hydra.name##Num = (int)(bytes / stride); \
                hydra.name##s = bounded_malloc((size_t)hydra.name##Num * sizeof(*hydra.name##s)); \
                assert(hydra.name##s); \
                for (int i = 0; i < hydra.name##Num; ++i) \
                    assert(tsf_hydra_read_##name(hydra.name##s + i, &stream)); \
            } else
            READ_TABLE(phdr, 38) READ_TABLE(pbag, 4) READ_TABLE(pmod, 10)
            READ_TABLE(pgen, 4) READ_TABLE(inst, 22) READ_TABLE(ibag, 4)
            READ_TABLE(imod, 10) READ_TABLE(igen, 4) READ_TABLE(shdr, 46)
            { assert(stream.skip(file, bytes)); }
#undef READ_TABLE
        }
    }
    fclose(file);
    munmap((void*)data, size);
    assert(tsf_hydra_sanitize(&hydra));
    tsf synth = {0};
    assert(tsf_load_presets(&synth, &hydra, (unsigned)frames));
    size_t regions = 0, unsafe = 0, disabledLoops = 0, loopBeforeOffset = 0,
           loopPastEnd = 0, endAtTail = 0;
    for (int p = 0; p < synth.presetNum; ++p) {
        for (int r = 0; r < synth.presets[p].regionNum; ++r) {
            struct tsf_region* region = &synth.presets[p].regions[r];
            ++regions;
            if (region->end >= (unsigned)(frames - 1)) ++endAtTail;
            /* The loader disables a loop when start >= end, so such regions
               render once and are memory-safe. A loop the loader WOULD enable
               must stay inside the region; the loader clamps loop_end <= end. */
            int loopEnabled = region->loop_mode && region->loop_start < region->loop_end;
            if (region->loop_mode && !loopEnabled) ++disabledLoops;
            if (loopEnabled) {
                if (region->loop_start < region->offset) ++loopBeforeOffset;
                if (region->loop_end > region->end) ++loopPastEnd;
            }
            if (region->offset >= region->end || (uint64_t)region->end + 2 > frames ||
                (loopEnabled && region->loop_end > region->end)) {
                ++unsafe;
                if (unsafe <= 20) fprintf(stderr, "unsafe preset=%d region=%d offset=%u end=%u loop=%u..%u mode=%d\n",
                    p, r, region->offset, region->end, region->loop_start, region->loop_end, region->loop_mode);
            }
        }
    }
    printf("metadata_bytes=%zu presets=%d regions=%zu unsafe_regions=%zu disabled_loops=%zu "
           "loop_before_offset=%zu loop_past_end=%zu end_at_tail=%zu "
           "tracked_metadata_bytes=%zu pcm_allocation_bytes=%" PRIu64 "\n",
           metadata_bytes, synth.presetNum, regions, unsafe, disabledLoops, loopBeforeOffset,
           loopPastEnd, endAtTail, allocated - baseline, ((uint64_t)frames + 1) * sizeof(float));
    for (int p = 0; p < synth.presetNum; ++p) bounded_free(synth.presets[p].regions);
    bounded_free(synth.presets);
    bounded_free(hydra.phdrs); bounded_free(hydra.pbags); bounded_free(hydra.pmods);
    bounded_free(hydra.pgens); bounded_free(hydra.insts); bounded_free(hydra.ibags);
    bounded_free(hydra.imods); bounded_free(hydra.igens); bounded_free(hydra.shdrs);
    assert(allocated == baseline); allocated = 0;
    assert(regions && unsafe == expected_unsafe);
    puts("PASS: bounded validation, large seek, preset expansion and expected region result; PCM decode/render not attempted");
    return 0;
}
