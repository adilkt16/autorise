// Node.js polyfill for ReadableStream
global.ReadableStream = global.ReadableStream || require('web-streams-polyfill').ReadableStream;
global.WritableStream = global.WritableStream || require('web-streams-polyfill').WritableStream;
global.TransformStream = global.TransformStream || require('web-streams-polyfill').TransformStream;
