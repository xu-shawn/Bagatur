/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class NNUEBridge_NNUEBridge */

#ifndef _Included_NNUEBridge_NNUEBridge
#define _Included_NNUEBridge_NNUEBridge
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     NNUEBridge_NNUEBridge
 * Method:    init
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_NNUEBridge_NNUEBridge_init
  (JNIEnv *, jclass, jstring, jstring);

/*
 * Class:     NNUEBridge_NNUEBridge
 * Method:    evalFen
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_NNUEBridge_NNUEBridge_evalFen
  (JNIEnv *, jclass, jstring);

/*
 * Class:     NNUEBridge_NNUEBridge
 * Method:    evalArray
 * Signature: ([III)I
 */
JNIEXPORT jint JNICALL Java_NNUEBridge_NNUEBridge_evalArray
  (JNIEnv *, jclass, jintArray, jint, jint);

/*
 * Class:     NNUEBridge_NNUEBridge
 * Method:    fasterEvalArray
 * Signature: ([I[IIII)I
 */
JNIEXPORT jint JNICALL Java_NNUEBridge_NNUEBridge_fasterEvalArray
  (JNIEnv *, jclass, jintArray, jintArray, jint, jint, jint);

#ifdef __cplusplus
}
#endif
#endif
