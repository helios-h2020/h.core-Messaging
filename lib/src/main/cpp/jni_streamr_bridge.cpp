//
// Created by Teddy Grenman on 13.03.2020.
//

#include <string>
#include <memory>
#include <cstdlib>
#include <cstdint>
#include <algorithm>
#include <vector>
#include <iterator>
#include <map>
#include <android/log.h>

#include <jni.h>
#include "node.h"

#include "node_fd_pass.h"
#include "stdio_redirect.h"


static std::atomic_bool nodeStarted(false);
static std::atomic_bool nodeRunning(false);

extern "C" {

JNIEXPORT jboolean JNICALL
Java_eu_h2020_helios_1social_core_messaging_streamr_StreamrInstance_nodeStarted(
        JNIEnv */*env*/,
        jobject /*object*/) {
    return (jboolean) (nodeStarted ? JNI_TRUE : JNI_FALSE);
}

JNIEXPORT jboolean JNICALL
Java_eu_h2020_helios_1social_core_messaging_streamr_StreamrInstance_nodeRunning(
        JNIEnv */*env*/,
        jobject /*object*/) {
    return (jboolean) (nodeRunning ? JNI_TRUE : JNI_FALSE);
}


JNIEXPORT jint JNICALL
Java_eu_h2020_helios_1social_core_messaging_streamr_StreamrInstance_startNodeWithArguments(
        JNIEnv *env, jobject /*object*/, jobjectArray arguments) {
    // node/UV wanted args in contiguous memory?? Ok
    std::vector<std::size_t> node_args_positions;
    std::vector<char> buffer;
    jsize argc = env->GetArrayLength(arguments);

    if (nodeStarted) {
        __android_log_write(ANDROID_LOG_ERROR, "NodeBridge::startNodeWithArgs",
                            "Node.js already started.");
        return 1;
    }
    nodeStarted = true;

    // redirect stdin and stdout to the android log
    auto redirect = new HeoStdioRedirect();

    redirect->run();

    const char *arg = "node";

    node_args_positions.push_back(buffer.size());
    buffer.insert<const char *>(buffer.cend(), arg, arg + std::strlen(arg));
    buffer.push_back(0);

    for (jsize i = 0; i < argc; i++) {
        jstring jstr = (jstring) env->GetObjectArrayElement(arguments, i);
        arg = env->GetStringUTFChars(jstr, 0);

        node_args_positions.push_back(buffer.size());
        buffer.insert<const char *>(buffer.cend(), arg, arg + std::strlen(arg));
        buffer.push_back(0);

        env->ReleaseStringUTFChars(jstr, arg);
    }

    std::vector<char *> args(node_args_positions.size());

    size_t i = 0;
    for (auto pos: node_args_positions) {
        args[i++] = buffer.data() + pos;
    }

    __android_log_write(ANDROID_LOG_INFO, "NodeBridge::startNodeWithArgs", "Starting Node.js");
    nodeRunning = true;
    // Simply cast the size to int, overflow is unlikely.
    int node_result = node::Start(static_cast<int>(args.size()), args.data());
    nodeRunning = false;
    __android_log_print(ANDROID_LOG_INFO, "NodeBridge::startNodeWithArgs",
                        "Done with Node.js, exit value: %d", node_result);

    return jint(node_result);
}

JNIEXPORT jboolean JNICALL
Java_eu_h2020_helios_1social_core_messaging_streamr_StreamrInstance_passFileDescriptor(
        JNIEnv *env, jobject /*obj*/, jstring jname, jobject jFileDescriptor) {
    if (jname == NULL) {
        jclass ex = env->FindClass("java/lang/NullPointerException");
        env->ThrowNew(ex, "name must not be null.");
        return JNI_FALSE;
    }

    if (jFileDescriptor == NULL) {
        jclass ex = env->FindClass("java/lang/NullPointerException");
        env->ThrowNew(ex, "FileDescriptor must not be null.");
        return JNI_FALSE;
    }

    auto cls = env->GetObjectClass(jFileDescriptor);
    auto fieldId = env->GetFieldID(cls, "descriptor", "I");
    if (fieldId == NULL) {
        return JNI_FALSE;
    }
    int fd = (int) env->GetIntField(jFileDescriptor, fieldId);

    if (fd < 0) {
        // Invalid file descriptor, -1
        jclass ex = env->FindClass("java/io/IOException");
        __android_log_print(ANDROID_LOG_INFO, "NodeBridge::passFileDescriptor",
                            "Invalid FileDescriptor: %d", fd);
        env->ThrowNew(ex, "FileDescriptor is invalid (-1).");
        return JNI_FALSE;
    }

    const char *name = env->GetStringUTFChars(jname, 0);

    // cool, now we have an fd.. pass it along
    auto url = std::string("fd://") + std::to_string(fd);
    nodeFdPass::nodePassDescriptorUrl(std::string(name), url);

    env->ReleaseStringUTFChars(jname, name);

    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_eu_h2020_helios_1social_core_messaging_streamr_StreamrInstance_00024Companion_getFileDescriptorInt(
        JNIEnv *env, jobject /*obj*/, jobject jFileDescriptor) {
    if (jFileDescriptor == NULL) {
        jclass ex = env->FindClass("java/lang/NullPointerException");
        env->ThrowNew(ex, "FileDescriptor must not be null.");
        return -1;
    }

    auto cls = env->GetObjectClass(jFileDescriptor);
    auto fieldId = env->GetFieldID(cls, "descriptor", "I");
    if (fieldId == NULL) {
        return -1;
    }

    return (jint) env->GetIntField(jFileDescriptor, fieldId);
}

JNIEXPORT jint JNICALL
Java_eu_h2020_helios_1social_core_messaging_streamr_StreamrInstance_00024Companion_setFileDescriptorInt(
        JNIEnv *env, jobject /*obj*/, jobject jFileDescriptor, jint descriptor) {
    if (jFileDescriptor == NULL) {
        jclass ex = env->FindClass("java/lang/NullPointerException");
        env->ThrowNew(ex, "FileDescriptor must not be null.");
        return -1;
    }

    auto cls = env->GetObjectClass(jFileDescriptor);
    auto fieldId = env->GetFieldID(cls, "descriptor", "I");
    if (fieldId == NULL) {
        return -1;
    }

    auto prevId = (jint) env->GetIntField(jFileDescriptor, fieldId);
    env->SetIntField(jFileDescriptor, fieldId, descriptor);

    return prevId;
}

JNIEXPORT jboolean JNICALL
Java_eu_h2020_helios_1social_core_messaging_streamr_StreamrInstance_passDescriptorUrl(
        JNIEnv *env, jobject /*obj*/, jstring jname, jstring jDescriptorUrl) {
    if (jname == NULL) {
        jclass ex = env->FindClass("java/lang/NullPointerException");
        env->ThrowNew(ex, "name must not be null.");
        return JNI_FALSE;
    }

    if (jDescriptorUrl == NULL) {
        jclass ex = env->FindClass("java/lang/NullPointerException");
        env->ThrowNew(ex, "DescriptorUrl must not be null.");
        return JNI_FALSE;
    }

    const char *name = env->GetStringUTFChars(jname, 0);
    const char *url = env->GetStringUTFChars(jDescriptorUrl, 0);

    nodeFdPass::nodePassDescriptorUrl(name, url);

    env->ReleaseStringUTFChars(jname, name);
    env->ReleaseStringUTFChars(jDescriptorUrl, url);

    return JNI_TRUE;
}

JNIEXPORT jobject JNICALL
Java_eu_h2020_helios_1social_core_messaging_streamr_StreamrInstance_getDescriptorMap(
        JNIEnv *env, jobject /*obj*/) {
    jclass mapClass = env->FindClass("java/util/HashMap");
    if (mapClass == NULL) {
        return NULL;
    }

    jmethodID init = env->GetMethodID(mapClass, "<init>", "()V");
    if (init == NULL) {
        return NULL;
    }

    jmethodID jput = env->GetMethodID(mapClass, "put",
                                      "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    if (jput == NULL) {
        return NULL;
    }

    jobject hashMap = env->NewObject(mapClass, init);
    for (auto it : nodeFdPass::getFdMap()) {
        jstring key = env->NewStringUTF(it.first.c_str());
        jstring value = env->NewStringUTF(it.second.c_str());

        env->CallObjectMethod(hashMap, jput, key, value);

        env->DeleteLocalRef(key);
        env->DeleteLocalRef(value);
    }

    return hashMap;
}

}
