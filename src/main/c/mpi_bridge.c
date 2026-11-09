#include <jni.h>
#include <mpi.h>
#include <stdio.h>
#include <string.h>

JNIEXPORT void JNICALL Java_research_mpi_MpiNativeBridge_mpiInitThread(JNIEnv *env, jclass cls, jstring requestedLevel) {
    const char *required = (*env)->GetStringUTFChars(env, requestedLevel, NULL);
    if (required == NULL) {
        return;
    }

    int provided = 0;
    int requested = MPI_THREAD_SINGLE;

    if (strcmp(required, "MPI_THREAD_FUNNELED") == 0) {
        requested = MPI_THREAD_FUNNELED;
    } else if (strcmp(required, "MPI_THREAD_SERIALIZED") == 0) {
        requested = MPI_THREAD_SERIALIZED;
    } else if (strcmp(required, "MPI_THREAD_MULTIPLE") == 0) {
        requested = MPI_THREAD_MULTIPLE;
    }

    MPI_Init_thread(NULL, NULL, requested, &provided);
    (*env)->ReleaseStringUTFChars(env, requestedLevel, required);
}
