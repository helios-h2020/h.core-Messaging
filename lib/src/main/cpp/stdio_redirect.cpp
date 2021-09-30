#include <thread>
#include <unistd.h>
#include <stdio.h>
#include <android/log.h>

#include "stdio_redirect.h"

int HeoStdioRedirect::_redirectOutput(FILE *file, int LOG_LEVEL, std::string logTag) {
    int npipe[2] = {-1, -1};
    int fd = fileno(file);

    setvbuf(file, 0, _IONBF, 0);

    if (pipe(npipe) == -1) {
        return -1;
    }

    if (dup2(npipe[1], fd) == -1) {
        // close the pipes
        close(npipe[0]);
        close(npipe[1]);
        return -1;
    }

    char buf[2048];
    ssize_t redirect_size;
    while ((redirect_size = read(npipe[0], buf, sizeof buf - 1)) > 0) {
        if (buf[redirect_size - 1] == '\n')
            --redirect_size;
        buf[redirect_size] = 0;

        __android_log_write(LOG_LEVEL, logTag.c_str(), buf);
    }

    return 0;
}

void HeoStdioRedirect::run() {
    std::thread t1(&HeoStdioRedirect::_redirectOutput, this, stdout, ANDROID_LOG_INFO, logTag);
    std::thread t2(&HeoStdioRedirect::_redirectOutput, this, stderr, ANDROID_LOG_ERROR, logTag);

    t1.detach();
    t2.detach();
}
