/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_orangelabs_rcs_core_ims_protocol_rtp_codec_video_h264_decoder_NativeH264Decoder */

#ifndef _Included_NativeH264Decoder
#define _Included_NativeH264Decoder
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Method:    InitDecoder
 */
JNIEXPORT jint JNICALL Java_com_orangelabs_rcs_core_ims_protocol_rtp_codec_video_h264_decoder_NativeH264Decoder_InitDecoder
  (JNIEnv *, jclass);

/*
 * Method:    DeinitDecoder
 */
JNIEXPORT jint JNICALL Java_com_orangelabs_rcs_core_ims_protocol_rtp_codec_video_h264_decoder_NativeH264Decoder_DeinitDecoder
  (JNIEnv *, jclass);

/*
 * Method:    DecodeAndConvert
 */
JNIEXPORT jintArray JNICALL Java_com_orangelabs_rcs_core_ims_protocol_rtp_codec_video_h264_decoder_NativeH264Decoder_DecodeAndConvert
  (JNIEnv *, jclass, jbyteArray);

/*
 * Method:    GetLastDecodeStatus
 */
JNIEXPORT jint JNICALL Java_com_orangelabs_rcs_core_ims_protocol_rtp_codec_video_h264_decoder_NativeH264Decoder_getLastDecodeStatus
  (JNIEnv *env, jclass clazz);

#ifdef __cplusplus
}
#endif
#endif