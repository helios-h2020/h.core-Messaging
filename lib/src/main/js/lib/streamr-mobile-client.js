'use strict';

const IoStreamController = require('./in-out-stream');
const { ApiController } = require('./api-controller');
const stream = require('stream');
const nodeFdPass = process._linkedBinding("node_fd_pass");

async function main(api) {
    console.log("Started main, now wait.");
}

console.error("STREAMR MOBILE CLIENT JAVASCRIPT RUNNING...");

const { URL } = require('url');
const fs = require('fs');
const net = require('net');
function openStream(url, mode = 'r') {
    url = new URL(url);

    switch (url.protocol) {
        case 'fd:':
            console.info("Open for read/write:", url.host);
            let fd = parseInt(url.host, 10);
            return new net.Socket({
                fd: fd,
                writable: mode === 'w' || mode === 'rw',
                readable: mode === 'r' || mode === 'rw',
            });

        case 'unix:':
            if (mode === 'w') {
                return fs.createWriteStream(url.pathname, {
                    flags: 'w',
                });
            }

            return fs.createReadStream(url.pathname, {
                flags: 'r',
            });

        default:
            throw new Error(`Protocol "${url.protocol}" not implemented: ${uri}`);
    }
}

let ioMap = nodeFdPass.getIoMap();
let inout = null;

if (ioMap.inout) {
    inout = openStream(ioMap.inout, 'rw');
}

let ioHandler = new IoStreamController({
    input: inout || openStream(ioMap.input, 'r'),
    output: inout || openStream(ioMap.output, 'w'),
});

let api = new ApiController(ioHandler);

main(api);
