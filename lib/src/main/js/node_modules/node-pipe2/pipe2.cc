// Copyright 2015 Bobby Powers. All rights reserved.
// Use of this source code is governed by the MIT
// license that can be found in the LICENSE file.

#include <unistd.h>
#include <nan.h>

using v8::FunctionTemplate;

NAN_METHOD(pipe2) {
	int pipefd[2];

	if (!info.Length()) {
		Nan::ThrowError("pipe2 requires a callback as an arg");
		return;
	}

	if (!info[0]->IsFunction()) {
		Nan::ThrowError("pipe2 requires a callback as its first arg");
		return;
	}

	v8::Local<v8::Function> cb = info[0].As<v8::Function>();

	// TODO: cleanup (close) on error
	int err = pipe(pipefd);
	if (err) {
		v8::Local<v8::Value> jsErr = Nan::New(err);
		Nan::MakeCallback(Nan::GetCurrentContext()->Global(), cb, 1, &jsErr);
		return;
	}

	v8::Local<v8::Value> results[3];
	results[0] = Nan::Undefined();
	results[1] = Nan::New(pipefd[0]);
	results[2] = Nan::New(pipefd[1]);

	Nan::MakeCallback(Nan::GetCurrentContext()->Global(), cb, 3, results);
}

NAN_MODULE_INIT(Init) {
	Nan::Set(target, Nan::New("pipe2").ToLocalChecked(),
		 Nan::GetFunction(Nan::New<FunctionTemplate>(pipe2)).ToLocalChecked());
}

NODE_MODULE(pipe2, Init)
