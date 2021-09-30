//
// Created by Teddy Grenman on 19.03.2020.
//

#include <mutex>
#include <map>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <errno.h>

#include "node.h"

#include "node_fd_pass.h"

namespace nodeFdPass {
    std::map<std::string, std::string> pipeMap;
    std::mutex fdMutex;

    int nodePassDescriptorUrl(const std::string &name, const std::string &pipe_name) {
        std::lock_guard<std::mutex> lk(fdMutex);

        pipeMap[name] = pipe_name;
        return 1;
    }

    int nodePassDescriptorUrl(const char *name, const char *pipe_name) {
        return nodePassDescriptorUrl(std::string(name), std::string(pipe_name));
    }

    std::map<std::string, std::string> getFdMap() {
        std::lock_guard<std::mutex> lj(fdMutex);

        return pipeMap;
    }

    void GetFdMap(const v8::FunctionCallbackInfo<v8::Value> &args) {
        auto isolate = args.GetIsolate();
        auto context = isolate->GetCurrentContext();
        v8::Local<v8::Object> obj = v8::Object::New(isolate);

        std::lock_guard<std::mutex> lk(fdMutex);
        for (auto entry : pipeMap) {
            obj->Set(
                    context,
                    v8::String::NewFromUtf8(isolate, entry.first.c_str(),
                                            v8::NewStringType::kNormal).ToLocalChecked(),
                    v8::String::NewFromUtf8(isolate, entry.second.c_str(),
                                            v8::NewStringType::kNormal).ToLocalChecked()
            ).FromJust();
        }

        args.GetReturnValue().Set(obj);
    }

    void CreatePipe(const v8::FunctionCallbackInfo<v8::Value> &args) {
        int pipefd[2];
        auto isolate = args.GetIsolate();
        auto context = isolate->GetCurrentContext();
        auto resolver = v8::Promise::Resolver::New(context).ToLocalChecked();

        args.GetReturnValue().Set(resolver->GetPromise());

        int err = pipe(pipefd);
        if (err != 0) {
            auto errmsg = v8::String::NewFromUtf8(isolate, strerror(err),
                    v8::NewStringType::kNormal).ToLocalChecked();

            resolver->Reject(context, errmsg).Check();
            return;
        }

        v8::Local<v8::Object> obj = v8::Object::New(isolate);

        obj->Set(
                context,
                v8::String::NewFromUtf8(isolate, "readable", v8::NewStringType::kNormal).ToLocalChecked(),
                v8::Integer::New(isolate, pipefd[0])
        ).FromJust();

        obj->Set(
                context,
                v8::String::NewFromUtf8(isolate, "writable", v8::NewStringType::kNormal).ToLocalChecked(),
                v8::Integer::New(isolate, pipefd[1])
        ).FromJust();

        (void)(resolver->Resolve(context, obj));
    }


    void InitializeBinding(v8::Local<v8::Object> exports,
                           v8::Local<v8::Value> module,
                           v8::Local<v8::Context> context,
                           void *priv) {
        NODE_SET_METHOD(exports, "getIoMap", GetFdMap);
        NODE_SET_METHOD(exports, "createPipe", CreatePipe);
    }

    NODE_MODULE_LINKED(node_fd_pass, InitializeBinding)
}
