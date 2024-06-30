#include <jni.h>
#include <immintrin.h>
#include "bagaturchess_nnue_v2_JNIUtils.h"

#define HIDDEN_SIZE 1024
#define QA 255

static __m256i qa_vec;
static __m256i zero_vec;
static int initialized = 0;

JNIEXPORT jint JNICALL Java_bagaturchess_nnue_1v2_JNIUtils_evaluateVectorized
  (JNIEnv *env, jclass clazz, jshortArray L2Weights, jshortArray UsValues, jshortArray ThemValues, jintArray evalVec) {
	  
    jshort *l2_weights = (*env)->GetShortArrayElements(env, L2Weights, NULL);
    jshort *us_values = (*env)->GetShortArrayElements(env, UsValues, NULL);
    jshort *them_values = (*env)->GetShortArrayElements(env, ThemValues, NULL);
    jint *eval_vec_array = (*env)->GetIntArrayElements(env, evalVec, NULL);

    if (!initialized) {
        qa_vec = _mm256_set1_epi16(QA);
        zero_vec = _mm256_setzero_si256();
        initialized = 1;
    }

    __m256i eval_vec = _mm256_loadu_si256((__m256i*)eval_vec_array); // Load eval_vec_array into eval_vec

    for (int i = 0; i < HIDDEN_SIZE; i += 16) {
        // Load values
        __m256i us_val = _mm256_loadu_si256((__m256i*) &us_values[i]);
        __m256i them_val = _mm256_loadu_si256((__m256i*) &them_values[i]);

        // Clamp values to [0, QA]
        __m256i us_clamped = _mm256_max_epi16(zero_vec, _mm256_min_epi16(us_val, qa_vec));
        __m256i them_clamped = _mm256_max_epi16(zero_vec, _mm256_min_epi16(them_val, qa_vec));

        // Load weights
        __m256i l2_weights_us = _mm256_loadu_si256((__m256i*) &l2_weights[i]);
        __m256i l2_weights_them = _mm256_loadu_si256((__m256i*) &l2_weights[i + HIDDEN_SIZE]);

        // Split into 128-bit parts for conversion
        __m128i us_clamped_lo = _mm256_extracti128_si256(us_clamped, 0);
        __m128i us_clamped_hi = _mm256_extracti128_si256(us_clamped, 1);
        __m128i them_clamped_lo = _mm256_extracti128_si256(them_clamped, 0);
        __m128i them_clamped_hi = _mm256_extracti128_si256(them_clamped, 1);

        __m128i l2_weights_us_lo = _mm256_extracti128_si256(l2_weights_us, 0);
        __m128i l2_weights_us_hi = _mm256_extracti128_si256(l2_weights_us, 1);
        __m128i l2_weights_them_lo = _mm256_extracti128_si256(l2_weights_them, 0);
        __m128i l2_weights_them_hi = _mm256_extracti128_si256(l2_weights_them, 1);

        // Convert to 32-bit integers to prevent overflow
        __m256i us_clamped_lo_32 = _mm256_cvtepi16_epi32(us_clamped_lo);
        __m256i us_clamped_hi_32 = _mm256_cvtepi16_epi32(us_clamped_hi);
        __m256i them_clamped_lo_32 = _mm256_cvtepi16_epi32(them_clamped_lo);
        __m256i them_clamped_hi_32 = _mm256_cvtepi16_epi32(them_clamped_hi);

        __m256i l2_weights_us_lo_32 = _mm256_cvtepi16_epi32(l2_weights_us_lo);
        __m256i l2_weights_us_hi_32 = _mm256_cvtepi16_epi32(l2_weights_us_hi);
        __m256i l2_weights_them_lo_32 = _mm256_cvtepi16_epi32(l2_weights_them_lo);
        __m256i l2_weights_them_hi_32 = _mm256_cvtepi16_epi32(l2_weights_them_hi);

        // Square the values
        __m256i us_sq_lo = _mm256_mullo_epi32(us_clamped_lo_32, us_clamped_lo_32);
        __m256i us_sq_hi = _mm256_mullo_epi32(us_clamped_hi_32, us_clamped_hi_32);
        __m256i them_sq_lo = _mm256_mullo_epi32(them_clamped_lo_32, them_clamped_lo_32);
        __m256i them_sq_hi = _mm256_mullo_epi32(them_clamped_hi_32, them_clamped_hi_32);

        // Multiply squared values with weights
        __m256i us_result_lo = _mm256_mullo_epi32(us_sq_lo, l2_weights_us_lo_32);
        __m256i us_result_hi = _mm256_mullo_epi32(us_sq_hi, l2_weights_us_hi_32);
        __m256i them_result_lo = _mm256_mullo_epi32(them_sq_lo, l2_weights_them_lo_32);
        __m256i them_result_hi = _mm256_mullo_epi32(them_sq_hi, l2_weights_them_hi_32);

        // Accumulate results
        eval_vec = _mm256_add_epi32(eval_vec, us_result_lo);
        eval_vec = _mm256_add_epi32(eval_vec, us_result_hi);
        eval_vec = _mm256_add_epi32(eval_vec, them_result_lo);
        eval_vec = _mm256_add_epi32(eval_vec, them_result_hi);
    }

    // Store the result back to eval_vec_array
    _mm256_storeu_si256((__m256i*)eval_vec_array, eval_vec);

    (*env)->ReleaseShortArrayElements(env, L2Weights, l2_weights, 0);
    (*env)->ReleaseShortArrayElements(env, UsValues, us_values, 0);
    (*env)->ReleaseShortArrayElements(env, ThemValues, them_values, 0);
    (*env)->ReleaseIntArrayElements(env, evalVec, eval_vec_array, 0);

    // Return the sum of all elements in eval_vec_array
    int eval = 0;
    for (int i = 0; i < 8; i++) {
        eval += eval_vec_array[i];
    }

    return eval;
}
