# Umbrella Express

Express middleware for [Umbrella](https://github.com/datasprayio/umbrella)
bot protection.

## Install

```bash
npm install umbrella-express
```

## Usage

```js
const express = require('express');
const {umbrella} = require('umbrella-express');

const app = express();

const protect = umbrella({
    org: 'my-org',
    apiKey: process.env.UMBRELLA_API_KEY,
    // Static assets do not need checking
    excludePath: /\.(css|js|png|jpg|gif|svg|ico|woff2?|ttf)$/,
});

// Performs the first ping and starts the background config refresh.
// Never rejects: a failure leaves Umbrella disabled rather than blocking startup.
await protect.start();

app.use(protect);
```

## Options

| Option | Description |
| --- | --- |
| `org` | Your organization name (required) |
| `apiKey` | Your API key (required) |
| `basePath` | Override the API endpoint |
| `nodeId` | Identifies this process; defaults to hostname plus a session id |
| `excludePath` | `RegExp`; matching requests skip Umbrella entirely |
| `includePath` | `RegExp`; when set, only matching requests are checked |
| `fetchApi` | Custom fetch implementation |
| `onError` | Receives errors that are otherwise swallowed to stay fail-open |

## Behaviour

The server decides the operation mode. In `BLOCKING` mode the middleware waits
for a decision; in `MONITOR` mode it reports asynchronously and always calls
`next()`; in `DISABLED` mode it does nothing.

Only an explicit block decision ends the request — the middleware responds with
the status the server names, or `403` if it names none. Every other outcome,
including timeouts and API errors, calls `next()`.

Each checked request gets an `umbrellaSpentTimeMs` property recording the time
spent in Umbrella.

Call `protect.stop()` to end the background refresh on shutdown.
