/// <reference types="node" />
import { Readable, TransformOptions } from 'stream';
/**
 * Convert browser ReadableStream to Node stream.Readable.
 */
export declare function WebStreamToNodeStream(webStream: ReadableStream | Readable, nodeStreamOptions?: TransformOptions): Readable;
