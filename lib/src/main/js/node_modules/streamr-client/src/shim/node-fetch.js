"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
// In browsers, the node-fetch package is replaced with this to use native fetch
exports.default = (((typeof window !== 'undefined' && typeof window.fetch !== 'undefined' && window.fetch.bind(window))
    || (typeof fetch !== 'undefined' && fetch) || undefined));
//# sourceMappingURL=node-fetch.js.map