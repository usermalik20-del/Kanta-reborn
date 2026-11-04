#ifndef KANTA_SF2_VALIDATION_H
#define KANTA_SF2_VALIDATION_H
#include <stdint.h>
#include <stddef.h>
#include <string.h>

typedef struct { const unsigned char* data; size_t count; } sf_table;
static uint16_t sf_u16(const unsigned char* p) { return p[0] | ((uint16_t)p[1] << 8); }
static uint32_t sf_u32(const unsigned char* p) {
    return sf_u16(p) | ((uint32_t)sf_u16(p + 2) << 16);
}
static int sf_indices(sf_table source, size_t stride, size_t offset, size_t limit) {
    unsigned previous = 0;
    for (size_t i = 0; i < source.count; ++i) {
        unsigned value = sf_u16(source.data + i * stride + offset);
        if (value < previous || value >= limit) return 0;
        previous = value;
    }
    return 1;
}
/* Validate all cross-table references before the third-party loader dereferences them. */
static size_t sf_validate(const unsigned char* data, size_t size) {
    static const char names[9][5] = {"phdr", "pbag", "pmod", "pgen", "inst", "ibag", "imod", "igen", "shdr"};
    static const size_t strides[9] = {38, 4, 10, 4, 22, 4, 10, 4, 46};
    sf_table tables[9] = {{0}};
    size_t samples = 0, compressedBytes = 0;
    int pdta = 0, sdta = 0;
    if (size < 12 || memcmp(data, "RIFF", 4) || memcmp(data + 8, "sfbk", 4) ||
        sf_u32(data + 4) != size - 8) return 0;
    for (size_t pos = 12; pos < size;) {
        if (size - pos < 8) return 0;
        size_t bytes = sf_u32(data + pos + 4), start = pos + 8;
        /* TinySoundFont's RIFF reader does not consume odd-byte padding. */
        if ((bytes & 1) || bytes > size - start) return 0;
        size_t end = start + bytes;
        if (!memcmp(data + pos, "RIFF", 4) || !memcmp(data + pos, "pdta", 4) || !memcmp(data + pos, "sdta", 4)) return 0;
        if (!memcmp(data + pos, "LIST", 4)) {
            if (bytes < 4) return 0;
            int kind = !memcmp(data + start, "pdta", 4) ? 1 : !memcmp(data + start, "sdta", 4) ? 2 : 0;
            if ((kind == 1 && pdta++) || (kind == 2 && sdta++)) return 0;
            if (kind) for (size_t child = start + 4; child < end;) {
                if (end - child < 8) return 0;
                size_t length = sf_u32(data + child + 4), body = child + 8;
                if (!memcmp(data + child, "LIST", 4) || !memcmp(data + child, "RIFF", 4)) return 0;
                if ((length & 1) || length > end - body) return 0;
                if (kind == 1) for (int i = 0; i < 9; ++i) {
                    if (memcmp(data + child, names[i], 4)) continue;
                    if (tables[i].data || !length || length % strides[i] || length / strides[i] > 16384) return 0;
                    tables[i].data = data + body;
                    tables[i].count = length / strides[i];
                }
                if (kind == 2 && length >= 4) {
                    if (!memcmp(data + child, "smpl", 4)) {
                        if (samples) return 0;
                        samples = length / 2;
                    }
                    /* SF3 keeps compressed Ogg streams in a smpl or smpo chunk; the
                       TinySoundFont loader uses the first such chunk as its buffer. */
                    if (!compressedBytes
                        && (!memcmp(data + child, "smpl", 4) || !memcmp(data + child, "smpo", 4))) {
                        if (length < 8) return 0;
                        compressedBytes = length;
                    }
                }
                child = body + length;
            }
        }
        pos = end;
    }
    for (int i = 0; i < 9; ++i) if (!tables[i].data) return 0;
    if ((!samples && !compressedBytes) || tables[0].count < 2 || tables[0].count > 1025 ||
        tables[4].count < 2 || tables[8].count < 2) return 0;
    if (!sf_indices(tables[0], 38, 24, tables[1].count) ||
        !sf_indices(tables[4], 22, 20, tables[5].count) ||
        !sf_indices(tables[1], 4, 0, tables[3].count) ||
        !sf_indices(tables[1], 4, 2, tables[2].count) ||
        !sf_indices(tables[5], 4, 0, tables[7].count) ||
        !sf_indices(tables[5], 4, 2, tables[6].count)) return 0;
    for (int table = 3; table <= 7; table += 4) {
        for (size_t i = 0; i + 1 < tables[table].count; ++i) {
            const unsigned char* gen = tables[table].data + i * 4;
            unsigned op = sf_u16(gen), value = sf_u16(gen + 2);
            /* The pinned loader shifts signed coarse offsets; reject negative values before that operation. */
            if ((op == 4 || op == 12 || op == 45 || op == 50) && (value & 0x8000)) return 0;
            int signed_value = (int16_t)value;
            if ((op == 51 && (signed_value < -120 || signed_value > 120)) ||
                (op == 52 && (signed_value < -99 || signed_value > 99)) ||
                (op == 56 && signed_value > 1200) ||
                (op == 58 && (signed_value < -1 || signed_value > 127))) return 0;
            if (op > 60 || ((op == 43 || op == 44) && (gen[2] > gen[3] || gen[3] > 127))) return 0;
            if ((op == 41 && (table != 3 || value >= tables[4].count - 1)) ||
                (op == 53 && (table != 7 || value >= tables[8].count - 1))) return 0;
        }
    }
    int anyCompressed = 0;
    for (size_t i = 0; i + 1 < tables[8].count; ++i) {
        const unsigned char* sample = tables[8].data + i * 46;
        uint32_t start = sf_u32(sample + 20), end = sf_u32(sample + 24);
        uint32_t rate = sf_u32(sample + 36);
        unsigned type = sf_u16(sample + 44);
        unsigned base = type & 0x0F;
        if (rate < 400 || rate > 384000 ||
            (sample[40] > 127 && sample[40] != 255) ||
            (base != 1 && base != 2 && base != 4 && base != 8)) return 0;
        if (!(type & 0x30)) {
            /* Raw PCM sample: start/end and loop points are frame offsets inside the
               linear sample data. */
            if (start >= end || end >= samples || sf_u32(sample + 28) > samples ||
                sf_u32(sample + 32) > samples) return 0;
        } else {
            /* Compressed (SF3/Ogg) sample: start/end are byte offsets inside the
               container chunk; loop points are decoded frames whose lengths are only
               known after decoding, so only their ordering can be checked here. */
            anyCompressed = 1;
            if (start >= end || end > compressedBytes) return 0;
            uint32_t loopStart = sf_u32(sample + 28), loopEnd = sf_u32(sample + 32);
            if ((loopStart || loopEnd) && loopStart >= loopEnd) return 0;
        }
    }
    /* Bound repeated instrument expansion as well as the on-disk table sizes. */
    size_t work = 0, regions = 0;
    for (size_t p = 0; p + 1 < tables[0].count; ++p) {
        unsigned begin = sf_u16(tables[0].data + p * 38 + 24);
        unsigned end = sf_u16(tables[0].data + (p + 1) * 38 + 24);
        for (unsigned b = begin; b < end; ++b) {
            unsigned g0 = sf_u16(tables[1].data + b * 4), g1 = sf_u16(tables[1].data + (b + 1) * 4);
            for (unsigned g = g0; g < g1; ++g) {
                if (++work > 1000000) return 0;
                const unsigned char* gen = tables[3].data + g * 4;
                if (sf_u16(gen) != 41) continue;
                unsigned inst = sf_u16(gen + 2);
                unsigned b0 = sf_u16(tables[4].data + inst * 22 + 20), b1 = sf_u16(tables[4].data + (inst + 1) * 22 + 20);
                for (unsigned ib = b0; ib < b1; ++ib) {
                    unsigned i0 = sf_u16(tables[5].data + ib * 4), i1 = sf_u16(tables[5].data + (ib + 1) * 4);
                    for (unsigned ig = i0; ig < i1; ++ig) {
                        if (++work > 1000000) return 0;
                        if (sf_u16(tables[7].data + ig * 4) == 53 && ++regions > 8192) return 0;
                    }
                }
            }
        }
    }
    /* For compressed banks the decoded frame count is only known after Ogg
       decoding; the caller re-validates regions against sampleTotal. */
    return regions ? (anyCompressed ? 1 : samples) : 0;
}
#endif
