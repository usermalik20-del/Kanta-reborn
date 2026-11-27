#pragma once
typedef struct kanta_soundfont kanta_soundfont;
kanta_soundfont* kanta_sf_open(const char* path);
/* Reason for the most recent kanta_sf_open failure; 0 after a successful open.
 * 1 unreadable, 2 address space, 3 bad structure, 4 not enough memory. */
int kanta_sf_last_open_reason(void);
void kanta_sf_close(kanta_soundfont* bank);
void kanta_sf_midi(kanta_soundfont* bank, int status, int a, int b);
void kanta_sf_tune(kanta_soundfont* bank, float semitones);
void kanta_sf_render(kanta_soundfont* bank, float* samples, int frames);
