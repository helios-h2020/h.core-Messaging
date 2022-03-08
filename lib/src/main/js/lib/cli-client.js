'use string';

// const PeerId = require('peer-id');
const P = require('bluebird');
const IoStreamController = require('./in-out-stream');
const { ApiController } = require('./api-controller');
const stream = require('stream');
const fs = require('fs');
const crypto = require('crypto');
const { program } = require('commander');
const log = require("debug")("helios:cli-client");
const logDht = require("debug")("helios:cli-client:dht");



async function main(api, input, output) {
    const hash = crypto.createHash('sha256');
    let idData;
    let peerId;
    if (program.file) {
        idData = JSON.parse(fs.readFileSync(program.file));
        peerId = await PeerId.createFromJSON(idData);
    } else {
        peerId = await PeerId.create();
        idData = peerId.toJSON();
    }
    let signalStarted;
    let swarmKey = fs.readFileSync(program.swarmKey);

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
    });

    await api.start({
            networkId: JSON.stringify(peerId.toJSON()),
            egoId: egoId,
        },
        {
            swarmKey: swarmKey,
        }
    );

    console.info("Started, init protocols")
    api.addProtocolReceiver('/helios/test-client/proto/1.0.0', 'a-ctx');
    console.info("Done...")


    let subId1 = await api.subscribe("/helios/pubsub/helios/tag/cats");
    let subIdJarkko = await api.subscribe("/helios/pubsub/blaa");
    let subId2 = await api.subscribe("/helios/pubsub-dev/test");

    if (program.provide) {
        await api.provideService(program.provide);
    }

    let c = 1;
    while (true) {
        try {
            await api.publish("/helios/pubsub-dev/test", `Hello ${c}`);
            if (program.lookup) {
                logDht("Start query for", program.lookup);
                let foo = await api.findService(program.lookup);
                logDht("End query for", program.lookup, foo);
            }
        } catch (e) {
            log("Error:", e);
        }
        await P.delay(10000);
    }
}




program
    .version('0.0.1')
    .option('-f, --file <file>', 'Peer id json file')
    .option('-s, --swarm-key <file>', 'Private swarm key file for private net')
    .option('-p, --provide <service>', 'Announce providing of service')
    .option('-l, --lookup <file>', 'Lookup service')
    .parse(process.argv);


let input = new stream.PassThrough();
let output = new stream.PassThrough();

let ioFake = new IoStreamController({
    input: input,
    output: output,
});

let api = new ApiController(ioFake);

main(api, input, output);

