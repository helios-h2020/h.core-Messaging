//
// Created by Teddy Grenman on 17.03.2020.
//

#ifndef HEO_NODEJS_MOBILE_STDIO_REDIRECT_H
#define HEO_NODEJS_MOBILE_STDIO_REDIRECT_H

class HeoStdioRedirect {
    const char *logTag;

    int  _redirectOutput(FILE *file, int LOG_LEVEL, std::string logTag);
public:
    HeoStdioRedirect(const char *tag = "NODE-HELIOS") : logTag(tag) {};
    void run();
};

#endif //HEO_NODEJS_MOBILE_STDIO_REDIRECT_H
