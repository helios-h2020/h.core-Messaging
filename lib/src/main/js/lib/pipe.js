
let nodeFdPass = null;
try {
    nodeFdPass = process._linkedBinding("node_fd_pass");
} catch (e) {
}

let pipe2 = null;

if (!nodeFdPass) {
    pipe2 = require("node-pipe2").pipe2;
}

function createPipe() {
    if (nodeFdPass) {
        return nodeFdPass.createPipe();
    }

    if (!pipe2) {
        return Promise.reject("Error creating pipe, no implementation.");
    }

    let p = new Promise((resolve, reject) => {
        pipe2((err, rfd, wfd) => {
            if (err) {
                reject(err);
                return;
            }

            resolve({
                readable: rfd,
                writable: wfd,
            });
        });
    });

    return p;
}

module.exports.create = createPipe;
