#include <jni.h>
#include <stdint.h>
#include "soundfont.h"

JNIEXPORT jlong JNICALL Java_ph_kanta_reborn_NativeSoundFont_open(JNIEnv* env, jclass cls, jstring path) {
    (void)cls;
    const char* value = (*env)->GetStringUTFChars(env, path, NULL);
    if (!value) return 0;
    kanta_soundfont* result = kanta_sf_open(value);
    (*env)->ReleaseStringUTFChars(env, path, value);
    return (jlong)(intptr_t)result;
}

JNIEXPORT jint JNICALL Java_ph_kanta_reborn_NativeSoundFont_openReason(JNIEnv* env, jclass cls) {
    (void)env; (void)cls;
    return (jint)kanta_sf_last_open_reason();
}
JNIEXPORT void JNICALL Java_ph_kanta_reborn_NativeSoundFont_close(JNIEnv* env, jclass cls, jlong id) {
    (void)env; (void)cls; kanta_sf_close((kanta_soundfont*)(intptr_t)id);
}
JNIEXPORT void JNICALL Java_ph_kanta_reborn_NativeSoundFont_midi(JNIEnv* env, jclass cls, jlong id, jint status, jint a, jint b) {
    (void)env; (void)cls; kanta_sf_midi((kanta_soundfont*)(intptr_t)id, status, a, b);
}
JNIEXPORT void JNICALL Java_ph_kanta_reborn_NativeSoundFont_tune(JNIEnv* env, jclass cls, jlong id, jfloat pitch) {
    (void)env; (void)cls; kanta_sf_tune((kanta_soundfont*)(intptr_t)id, pitch);
}
JNIEXPORT void JNICALL Java_ph_kanta_reborn_NativeSoundFont_render(JNIEnv* env, jclass cls, jlong id, jfloatArray array, jint offset, jint frames) {
    (void)cls;
    if (frames < 0 || frames > 4096 || offset < 0 || offset > (*env)->GetArrayLength(env, array) - frames * 2) return;
    jfloat* samples = (*env)->GetFloatArrayElements(env, array, NULL);
    if (!samples) return;
    kanta_sf_render((kanta_soundfont*)(intptr_t)id, samples + offset, frames);
    (*env)->ReleaseFloatArrayElements(env, array, samples, 0);
}
