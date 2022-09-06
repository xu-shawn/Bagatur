/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class bagaturchess_egtb_syzygy_SyzygyJNIBridge */

#ifndef _Included_bagaturchess_egtb_syzygy_SyzygyJNIBridge
#define _Included_bagaturchess_egtb_syzygy_SyzygyJNIBridge
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     bagaturchess_egtb_syzygy_SyzygyJNIBridge
 * Method:    init
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_bagaturchess_egtb_syzygy_SyzygyJNIBridge_init
  (JNIEnv *, jclass, jstring);

/*
 * Class:     bagaturchess_egtb_syzygy_SyzygyJNIBridge
 * Method:    getTBLargest
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_bagaturchess_egtb_syzygy_SyzygyJNIBridge_getTBLargest
  (JNIEnv *, jclass);

/*
 * Class:     bagaturchess_egtb_syzygy_SyzygyJNIBridge
 * Method:    probeDTM
 * Signature: (JJJJJJJJIIZ)I
 */
JNIEXPORT jint JNICALL Java_bagaturchess_egtb_syzygy_SyzygyJNIBridge_probeDTM
(JNIEnv*, jclass, jlong, jlong, jlong, jlong, jlong, jlong, jlong, jlong, jint, jint, jboolean);

/*
 * Class:     bagaturchess_egtb_syzygy_SyzygyJNIBridge
 * Method:    probeWDL
 * Signature: (JJJJJJJJIIZ)I
 */
JNIEXPORT jint JNICALL Java_bagaturchess_egtb_syzygy_SyzygyJNIBridge_probeWDL
  (JNIEnv *, jclass, jlong, jlong, jlong, jlong, jlong, jlong, jlong, jlong, jint, jint, jboolean);

  /*
 * Class:     bagaturchess_egtb_syzygy_SyzygyJNIBridge
 * Method:    probeDTZ
 * Signature: (JJJJJJJJIIZ)I
 */
JNIEXPORT jint JNICALL Java_bagaturchess_egtb_syzygy_SyzygyJNIBridge_probeDTZ
  (JNIEnv *, jclass, jlong, jlong, jlong, jlong, jlong, jlong, jlong, jlong, jint, jint, jboolean);

#ifdef __cplusplus
}
#endif
#endif
