# node-pipe2

[![Build Status](https://travis-ci.org/bpowers/node-pipe2.svg)](https://travis-ci.org/bpowers/node-pipe2)

This package provides access to the
[`pipe2`](http://man7.org/linux/man-pages/man2/pipe2.2.html) system
call available on OSX, Linux and other UNIX variants.

## Usage

```javascript
var pipe2 = require('node-pipe2');

pipe2(function(err, rfd, wfd) {
    if (err) {
        console.log('pipe failed, maybe out of FDs? ' + err);
        return;
    }
    // use rfd and wfd
});
```
