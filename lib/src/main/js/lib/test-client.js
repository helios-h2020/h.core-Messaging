'use string';

// const PeerId = require('peer-id');
const P = require('bluebird');
const IoStreamController = require('./in-out-stream');
const { ApiController } = require('./api-controller');
const stream = require('stream');
const fs = require('fs');
const crypto = require('crypto');

async function main(api, input, output) {
    const hash = crypto.createHash('sha256');
    let idData = JSON.parse(fs.readFileSync('./test-client.json'));
    let peerId = await PeerId.createFromJSON(idData);
    let signalStarted;
    let started = new Promise((resolve, _) => {
        signalStarted = resolve;
    });

    hash.update(idData.privKey);
    let egoId = hash.digest('hex');

    console.info('I am', peerId.toB58String());

    output.on('data', (data) => {
        let str = data.toString();
        let obj = JSON.parse(str);
        if (/call-id-1/.test(str)) {
            signalStarted();
        }

        if (/peer:(connect|discovery)/.test(str)) {
            console.info("Send up:", str.substr(0, 60));
            return;
        }

        if (obj && obj.type === 'call' && obj.name === 'receive-message') {
            let [netId, proto, rcvFd] = obj.data;

            api.sendTo(
                {
                    networkId: netId.networkId,
                },
                "/helios/test-client-phone/proto/1.0.0",
                "Got your message!".repeat(1000 * 1000)
            ).then((rv) => {
                console.info("Sent direct reply to", netId.networkId);
            });

            let input = fs.createReadStream("/dev/null", {
                fd: rcvFd,
            });

            input.on("data", (chunk) => {
                console.info("received", chunk);
            });

            input.on("end", () => {
                console.info("closed stream after receive");
                input.destroy();
            });
        }

        console.info("Send up:", str);
    });

    input.write(JSON.stringify({
        type: 'call',
        name: 'start',
        id: 'call-id-1',
        data: [{
            networkId: JSON.stringify(peerId.toJSON()),
            egoId: egoId,
        }]
    }) + "\n");

    await started;
    console.info("Started, init protocols")
    api.addProtocolReceiver('/helios/test-client/proto/1.0.0', 'a-ctx');
    console.info("Publish my Ego")
    await api.provideService(`/helios/ego/peer/${egoId}`);
    console.info("Done...")

    input.write(JSON.stringify({
        type: 'call',
        name: 'subscribe',
        id: 'call-id-3',
        data: ['/helios/pubsub/helios'],
    }) + "\n");

    let c = 1;
    while (true) {
        input.write(JSON.stringify({
            type: 'call',
            name: 'publish',
            id: 'call-id-2',
            data: ['/helios/pubsub/nice/testing',
                JSON.stringify({
                    from: 'Teddy',
                    message: 'Hello, world: ' + (c++),
                }),
            ]
        }) + "\n");

        input.write(JSON.stringify({
            type: 'call',
            name: 'send-to',
            id: 'call-id-3',
            data: [

            ],
        }) + "\n");
        await P.delay(20000);
    }
}




let input = new stream.PassThrough();
let output = new stream.PassThrough();

let ioFake = new IoStreamController({
    input: input,
    output: output,
});

let api = new ApiController(ioFake);

main(api, input, output);
