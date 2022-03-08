'use strict';

const stream = require('stream');
const split = require('split');
const { EventEmitter } = require('events');


class InOutStreamController extends EventEmitter {
    constructor(options) {
        super();

        let input = this._input = options.input;
        let output = this._output = options.output;
        let outputStream = new stream.Transform({
            objectMode: true,
            transform: function (chunk, encoding, cb) {
                let buf = Buffer.from(JSON.stringify(chunk) + '\n');
                this.push(buf);
                cb();
            },
        });

        this.inputStream = input.pipe(split(JSON.parse));
        this.outputStream = outputStream;
        outputStream.pipe(output);

        this.inputStream.on('data', this._handleData.bind(this));
        this.streams = new Map();
        this._streamId = 1;
        this._eventId = 1;
        this._callId = 1;
        this.events = new EventEmitter();
        this.calls = new EventEmitter();

        this.outputStream.on('error', (err) => {
            console.error("Error in output stream:", err);
        });
        this.inputStream.on('error', (err) => {
            console.error("Error in input stream:", err);
        });
    }

    destroy(error) {
        this._input.destroy(error);
        this._input.end();
        this._output.destroy(error);
        this._output.end();
    }

    _handleData(obj) {
        if (obj.type === 'event') {
            this.events.emit(obj.name, obj.data, obj.id, this);

            return;
        }

        if (obj.type === 'call') {
            let outputStream = this.outputStream;
            let cb = (reply, err = null) => {
                if (err === undefined) {
                    err = null;
                }

                let returnType = (err === null || err === undefined) ? 'return' : 'return-error'

                try {
                    outputStream.write({
                        type: returnType,
                        id: obj.id,
                        data: reply,
                        error: err,
                    });
                } catch (e) {
                    console.error("Error writing to output stream", e);
                }
            };

            this.calls.emit(obj.name, obj.data, cb, this);

            return;
        }

        if (obj.type === 'stream') {
            let stream = this.streams.get(obj.id);
            if (!stream) {
                return;
            }

            if (obj.data !== undefined && obj.data !== null) {
                let pushed = stream.push(Buffer.from(obj.data));

                if (!pushed) {
                    // cannot push? close?
                }
            }

            if (obj.close) {
                // close of stream
                stream.push(null);
            }
        }
    }

    createStream() {
        let streamId = String(this._streamId++);
        let outputStream = this.outputStream;

        let dstream = new stream.Duplex({
            write(chunk, encoding, cb) {
                let data = chunk;
                if (encoding !== 'buffer') {
                    data = Buffer.from(chunk);
                }
                outputStream.write({
                    type: 'stream',
                    id: streamId,
                    data: Array.from(data),
                });

                cb();
            },
            read(size) {},
        });

        dstream.id = streamId;

        dstream.on('end', () => {
            this.streams.delete(dstream);
        });

        this.streams.set(streamId, dstream);

        return dstream;
    }

    event(event, data, id = "") {
        let buf;

        if (Buffer.isBuffer(data)) {
            buf = data;
        } else if (typeof data === 'string') {
            buf = Buffer.from(data);
        } else {
            buf = Buffer.from(JSON.stringify(data));
        }

        this.outputStream.write({
            type: 'event',
            name: event,
            id: String(id),
            cid: String(this._eventId++),
            data: Array.from(buf),
        });
    }

    call(name, args) {
        // FIXME: handle returns or errors, currently completely ignored
        this.outputStream.write({
            type: 'call',
            name: name,
            id: String(this._callId++),
            data: Array.from(args),
        });
    }
}

module.exports = InOutStreamController;
