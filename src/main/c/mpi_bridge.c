#include <jni.h>
#include <mpi.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void throw_java_exception(JNIEnv *env, const char *message) {
    jclass exception_class = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (exception_class != NULL) {
        (*env)->ThrowNew(env, exception_class, message);
    }
}

static void throw_mpi_exception(JNIEnv *env, int error_code, const char *operation) {
    char error_text[MPI_MAX_ERROR_STRING];
    int error_length = 0;
    MPI_Error_string(error_code, error_text, &error_length);

    char message[MPI_MAX_ERROR_STRING + 128];
    snprintf(message, sizeof(message), "%s failed: %.*s", operation, error_length, error_text);
    throw_java_exception(env, message);
}

JNIEXPORT jint JNICALL Java_research_mpi_MpiNativeBridge_mpiInitThread(
        JNIEnv *env, jclass cls, jstring requested_level) {
    const char *required = (*env)->GetStringUTFChars(env, requested_level, NULL);
    if (required == NULL) {
        return -1;
    }

    int requested = MPI_THREAD_SINGLE;
    if (strcmp(required, "MPI_THREAD_FUNNELED") == 0) {
        requested = MPI_THREAD_FUNNELED;
    } else if (strcmp(required, "MPI_THREAD_SERIALIZED") == 0) {
        requested = MPI_THREAD_SERIALIZED;
    } else if (strcmp(required, "MPI_THREAD_MULTIPLE") == 0) {
        requested = MPI_THREAD_MULTIPLE;
    } else if (strcmp(required, "MPI_THREAD_SINGLE") != 0) {
        (*env)->ReleaseStringUTFChars(env, requested_level, required);
        throw_java_exception(env, "Unsupported requested MPI thread support level");
        return -1;
    }

    int provided = MPI_THREAD_SINGLE;
    int result = MPI_Init_thread(NULL, NULL, requested, &provided);
    (*env)->ReleaseStringUTFChars(env, requested_level, required);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Init_thread");
        return -1;
    }

    result = MPI_Comm_set_errhandler(MPI_COMM_WORLD, MPI_ERRORS_RETURN);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Comm_set_errhandler");
        return -1;
    }
    return provided;
}

JNIEXPORT jint JNICALL Java_research_mpi_MpiNativeBridge_mpiRank(JNIEnv *env, jclass cls) {
    int rank = -1;
    int result = MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Comm_rank");
        return -1;
    }
    return rank;
}

JNIEXPORT jint JNICALL Java_research_mpi_MpiNativeBridge_mpiSize(JNIEnv *env, jclass cls) {
    int size = 0;
    int result = MPI_Comm_size(MPI_COMM_WORLD, &size);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Comm_size");
        return 0;
    }
    return size;
}

JNIEXPORT jint JNICALL Java_research_mpi_MpiNativeBridge_mpiQueryThread(JNIEnv *env, jclass cls) {
    int provided = MPI_THREAD_SINGLE;
    int result = MPI_Query_thread(&provided);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Query_thread");
        return -1;
    }
    return provided;
}

JNIEXPORT void JNICALL Java_research_mpi_MpiNativeBridge_mpiBarrier(JNIEnv *env, jclass cls) {
    int result = MPI_Barrier(MPI_COMM_WORLD);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Barrier");
    }
}

JNIEXPORT jlong JNICALL Java_research_mpi_MpiNativeBridge_mpiMaxLong(
        JNIEnv *env, jclass cls, jlong value) {
    jlong maximum = 0;
    int result = MPI_Allreduce(
            &value, &maximum, 1, MPI_LONG_LONG_INT, MPI_MAX, MPI_COMM_WORLD);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Allreduce(MPI_MAX)");
        return 0;
    }
    return maximum;
}

JNIEXPORT jlong JNICALL Java_research_mpi_MpiNativeBridge_mpiSumLong(
        JNIEnv *env, jclass cls, jlong value) {
    jlong sum = 0;
    int result = MPI_Allreduce(
            &value, &sum, 1, MPI_LONG_LONG_INT, MPI_SUM, MPI_COMM_WORLD);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Allreduce(MPI_SUM)");
        return 0;
    }
    return sum;
}

JNIEXPORT void JNICALL Java_research_mpi_MpiNativeBridge_mpiSendRecvBytes(
        JNIEnv *env, jclass cls, jbyteArray send_array, jbyteArray receive_array,
        jint destination, jint source, jint tag) {
    jsize send_count = (*env)->GetArrayLength(env, send_array);
    jsize receive_count = (*env)->GetArrayLength(env, receive_array);
    if (send_count != receive_count) {
        throw_java_exception(env, "MPI_Sendrecv buffers must have equal lengths");
        return;
    }

    jbyte *send_buffer = (*env)->GetByteArrayElements(env, send_array, NULL);
    if (send_buffer == NULL) {
        return;
    }
    jbyte *receive_buffer = (*env)->GetByteArrayElements(env, receive_array, NULL);
    if (receive_buffer == NULL) {
        (*env)->ReleaseByteArrayElements(env, send_array, send_buffer, JNI_ABORT);
        return;
    }

    int result = MPI_Sendrecv(
            send_buffer, (int) send_count, MPI_BYTE, destination, tag,
            receive_buffer, (int) receive_count, MPI_BYTE, source, tag,
            MPI_COMM_WORLD, MPI_STATUS_IGNORE);
    (*env)->ReleaseByteArrayElements(env, send_array, send_buffer, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, receive_array, receive_buffer, 0);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Sendrecv");
    }
}

JNIEXPORT void JNICALL Java_research_mpi_MpiNativeBridge_mpiAllreduceSumLongArray(
        JNIEnv *env, jclass cls, jlongArray values_array) {
    jsize count = (*env)->GetArrayLength(env, values_array);
    if (count <= 0) {
        throw_java_exception(env, "MPI_Allreduce requires a non-empty long array");
        return;
    }

    jlong *values = (jlong *) malloc((size_t) count * sizeof(jlong));
    if (values == NULL) {
        throw_java_exception(env, "Could not allocate MPI_Allreduce buffer");
        return;
    }
    (*env)->GetLongArrayRegion(env, values_array, 0, count, values);
    if ((*env)->ExceptionCheck(env)) {
        free(values);
        return;
    }

    int result = MPI_Allreduce(
            MPI_IN_PLACE, values, (int) count, MPI_LONG_LONG_INT, MPI_SUM, MPI_COMM_WORLD);
    if (result == MPI_SUCCESS) {
        (*env)->SetLongArrayRegion(env, values_array, 0, count, values);
    }
    free(values);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Allreduce");
    }
}

JNIEXPORT jlongArray JNICALL Java_research_mpi_MpiNativeBridge_mpiExchangeHalos(
        JNIEnv *env, jclass cls, jlong left_send, jlong right_send, jint left_rank, jint right_rank) {
    jlong left_receive = 0;
    jlong right_receive = 0;
    int result = MPI_Sendrecv(
            &right_send, 1, MPI_LONG_LONG_INT, right_rank, 0,
            &left_receive, 1, MPI_LONG_LONG_INT, left_rank, 0,
            MPI_COMM_WORLD, MPI_STATUS_IGNORE);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Sendrecv(right halo)");
        return NULL;
    }

    result = MPI_Sendrecv(
            &left_send, 1, MPI_LONG_LONG_INT, left_rank, 1,
            &right_receive, 1, MPI_LONG_LONG_INT, right_rank, 1,
            MPI_COMM_WORLD, MPI_STATUS_IGNORE);
    if (result != MPI_SUCCESS) {
        throw_mpi_exception(env, result, "MPI_Sendrecv(left halo)");
        return NULL;
    }

    jlong values[2] = {left_receive, right_receive};
    jlongArray result_array = (*env)->NewLongArray(env, 2);
    if (result_array != NULL) {
        (*env)->SetLongArrayRegion(env, result_array, 0, 2, values);
    }
    return result_array;
}

JNIEXPORT void JNICALL Java_research_mpi_MpiNativeBridge_mpiFinalize(JNIEnv *env, jclass cls) {
    int initialized = 0;
    int finalized = 0;
    MPI_Initialized(&initialized);
    MPI_Finalized(&finalized);
    if (initialized && !finalized) {
        int result = MPI_Finalize();
        if (result != MPI_SUCCESS) {
            throw_mpi_exception(env, result, "MPI_Finalize");
        }
    }
}
