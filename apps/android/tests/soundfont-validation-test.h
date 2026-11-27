/* Included by soundfont-test.c: small fixtures, never a multi-GiB sample allocation. */
static unsigned char* test_table(unsigned char* data, size_t size, const char* name) {
    for (size_t p = 12; p + 12 <= size; p += 8 + sf_u32(data + p + 4)) {
        if (memcmp(data + p, "LIST", 4) || memcmp(data + p + 8, "pdta", 4)) continue;
        size_t end = p + 8 + sf_u32(data + p + 4);
        for (size_t c = p + 12; c + 8 <= end; c += 8 + sf_u32(data + c + 4))
            if (!memcmp(data + c, name, 4)) return data + c + 8;
    }
    assert(0); return NULL;
}
static void test_u16(unsigned char* p, unsigned value) { p[0] = value; p[1] = value >> 8; }
static void test_u32(unsigned char* p, uint32_t value) {
    test_u16(p, value); test_u16(p + 2, value >> 16);
}
static void validation_compatibility_checks(const unsigned char* original, size_t size) {
    unsigned char* data = malloc(size);
    assert(data);
    const struct { unsigned op, value; int valid; } cases[] = {
        {4, 65535, 1}, {12, 32768, 1}, {45, 65535, 1}, {50, 65535, 1},
        {52, 100, 1}, {52, 150, 1}, {52, (unsigned short)-150, 1},
        {52, 1200, 1}, {52, (unsigned short)-1200, 1},
        {52, 1201, 0}, {52, (unsigned short)-1201, 0},
        {58, 65535, 1}, {58, 0, 1}, {58, 127, 1}, {58, 128, 0}, {58, 65534, 0},
        {43, (96 << 8) | 98, 1}, {43, (60 << 8) | 60, 1},
        {43, (128 << 8), 0}, {43, 128, 0}, {44, (96 << 8) | 98, 0}, {61, 0, 0}
    };
    for (size_t i = 0; i < sizeof cases / sizeof cases[0]; ++i) {
        memcpy(data, original, size);
        unsigned char* gen = test_table(data, size, "igen");
        /* The tone fixture's first generator is not its sampleID. */
        assert(sf_u16(gen) != 53);
        test_u16(gen, cases[i].op); test_u16(gen + 2, cases[i].value);
        assert(!!sf_validate(data, size) == cases[i].valid);
        if (cases[i].op == 4 || cases[i].op == 12 || cases[i].op == 45 || cases[i].op == 50) {
            struct tsf_region region; union tsf_hydra_genamount amount;
            tsf_region_clear(&region, TSF_FALSE);
            amount.shortAmount = (short)cases[i].value;
            tsf_region_operator(&region, cases[i].op, &amount, NULL);
            unsigned expected = (unsigned)((int)amount.shortAmount * 32768);
            assert((cases[i].op == 4 ? region.offset : cases[i].op == 12 ? region.end :
                    cases[i].op == 45 ? region.loop_start : region.loop_end) == expected);
        }
    }
    /* Terminal records are sentinels, not usable instrument/sample references. */
    for (int sample = 0; sample < 2; ++sample) {
        memcpy(data, original, size);
        unsigned char* gen = test_table(data, size, sample ? "igen" : "pgen");
        unsigned char* target = test_table(data, size, sample ? "shdr" : "inst");
        test_u16(gen, sample ? 53 : 41);
        test_u16(gen + 2, sf_u32(target - 4) / (sample ? 46 : 22) - 1);
        assert(!sf_validate(data, size));
    }
    free(data);
    /* Grow only metadata; preserve the original terminal record and all references. */
    const char* names[] = {"phdr", "igen"};
    const size_t strides[] = {38, 4}, counts[] = {1329, 63056}, caps[] = {2049, 65536};
    for (int t = 0; t < 2; ++t) for (int over = 0; over < 2; ++over) {
        size_t count = over ? caps[t] + 1 : counts[t];
        unsigned char* table = test_table((unsigned char*)original, size, names[t]);
        size_t oldbytes = sf_u32(table - 4), extra = count * strides[t] - oldbytes;
        size_t insert = (size_t)(table - original) + oldbytes - strides[t];
        data = malloc(size + extra); assert(data);
        memcpy(data, original, insert);
        for (size_t off = 0; off < extra; off += strides[t])
            memcpy(data + insert + off, original + insert, strides[t]);
        memcpy(data + insert + extra, original + insert, size - insert);
        test_u32(data + 4, (uint32_t)(size + extra - 8));
        test_u32(data + (table - original) - 4, (uint32_t)(oldbytes + extra));
        for (size_t p = 12; p + 12 < size; p += 8 + sf_u32(data + p + 4))
            if (!memcmp(data + p, "LIST", 4) && !memcmp(data + p + 8, "pdta", 4)) {
                test_u32(data + p + 4, sf_u32(data + p + 4) + (uint32_t)extra); break;
            }
        assert(!!sf_validate(data, size + extra) == !over);
        free(data);
    }
    puts("SoundFont: HiDef metadata limits, signed offsets, tuning, ranges and terminal-reference regressions passed");
}
