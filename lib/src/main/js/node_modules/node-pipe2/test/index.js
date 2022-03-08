// Copyright 2015 Bobby Powers. All rights reserved.
// Use of this source code is governed by the MIT
// license that can be found in the LICENSE file.

var pipe2 = require('../').pipe2;
var assert = require('assert');


describe('pipe2 extension', function() {
    it('should call pipe2', function(done) {
        pipe2(function(err, rfd, wfd) {
            assert.equal(err, undefined);
            assert.ok(rfd >= 0);
            assert.ok(wfd >= 0);
            assert.ok(wfd === rfd + 1);
            done();
        });
    });
});
