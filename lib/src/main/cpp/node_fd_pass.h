//
// Created by Teddy Grenman on 19.03.2020.
//

#ifndef HEO_NODEJS_MOBILE_NODE_FD_PASS_H
#define HEO_NODEJS_MOBILE_NODE_FD_PASS_H

namespace nodeFdPass {
    int nodePassDescriptorUrl(const char *name, const char *pipe_name);

    int nodePassDescriptorUrl(const std::string &name, const std::string &pipe_name);

    std::map<std::string, std::string> getFdMap();
}

#endif //HEO_NODEJS_MOBILE_NODE_FD_PASS_H
