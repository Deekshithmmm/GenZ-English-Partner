// Opens the app on a phone that's on the same Wi-Fi or hotspot as this laptop, over https so the mic works there.
//
//   npm run phone        (with the app (npm run web) and the backend already running)
//
// Phones only let a web page use the microphone over https (or on localhost). This serves the app at
// https://<laptop IP>:8443 with a self-signed certificate, made with openssl and kept in .expo/phone-https/:
// /api goes to the backend on :8080 and everything else to Metro on :8081. The first time, the phone warns about the
// certificate: tap Advanced, then Proceed. Plain http://<laptop IP>:8081 works too, except for the mic.
import { execFileSync } from 'node:child_process';
import { X509Certificate } from 'node:crypto';
import dgram from 'node:dgram';
import { existsSync, mkdirSync, readFileSync } from 'node:fs';
import http from 'node:http';
import https from 'node:https';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PORT = Number(process.env.PHONE_PORT ?? 8443);
const METRO_PORT = Number(process.env.METRO_PORT ?? 8081);
const API_PORT = Number(process.env.API_PORT ?? 8080);
const CERT_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '.expo', 'phone-https');
const DAY_MS = 86_400_000;

/** Headers that belong to one connection, not to the request or answer being passed on. */
const HOP_BY_HOP = new Set([
  'connection',
  'keep-alive',
  'proxy-connection',
  'transfer-encoding',
  'te',
  'trailer',
  'upgrade',
]);

function isPrivate(ip) {
  const [a, b] = ip.split('.').map(Number);
  return a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
}

/** This laptop's IPv4 addresses on private networks: Wi-Fi or hotspot, Ethernet, and virtual adapters. */
function lanAddresses() {
  return Object.values(os.networkInterfaces())
    .flat()
    .filter((a) => a && a.family === 'IPv4' && !a.internal && isPrivate(a.address))
    .map((a) => a.address);
}

/** The address this laptop reaches the internet from (its Wi-Fi or hotspot), found without sending anything. */
function mainAddress() {
  return new Promise((resolve) => {
    const socket = dgram.createSocket('udp4');
    socket.once('error', () => {
      socket.close();
      resolve(null);
    });
    socket.connect(53, '8.8.8.8', () => {
      const { address } = socket.address();
      socket.close();
      resolve(address);
    });
  });
}

/** openssl from the PATH, or the one that comes with Git for Windows. */
function openssl() {
  const candidates = [
    process.env.OPENSSL,
    'openssl',
    'C:\\Program Files\\Git\\mingw64\\bin\\openssl.exe',
    'C:\\Program Files\\Git\\usr\\bin\\openssl.exe',
  ].filter(Boolean);
  for (const candidate of candidates) {
    try {
      execFileSync(candidate, ['version'], { stdio: 'ignore' });
      return candidate;
    } catch {
      // Try the next one.
    }
  }
  throw new Error('openssl was not found. Install Git for Windows (it includes openssl), or set OPENSSL to its path.');
}

/** A certificate for every address the phone might use, made again when the laptop gets a new address. */
function certificate(addresses) {
  const certFile = path.join(CERT_DIR, 'cert.pem');
  const keyFile = path.join(CERT_DIR, 'key.pem');
  if (existsSync(certFile) && existsSync(keyFile)) {
    const cert = new X509Certificate(readFileSync(certFile));
    const fresh = (cert.validToDate ?? new Date(cert.validTo)).getTime() - Date.now() > 7 * DAY_MS;
    if (fresh && addresses.every((ip) => cert.checkIP(ip))) {
      return { cert: readFileSync(certFile), key: readFileSync(keyFile) };
    }
  }
  mkdirSync(CERT_DIR, { recursive: true });
  const names = ['DNS:localhost', 'IP:127.0.0.1', ...addresses.map((ip) => `IP:${ip}`)];
  execFileSync(
    openssl(),
    [
      'req',
      '-x509',
      '-newkey',
      'rsa:2048',
      '-nodes',
      '-sha256',
      '-days',
      '397',
      '-subj',
      '/CN=GenZ English Partner (dev)',
      '-addext',
      `subjectAltName=${names.join(',')}`,
      '-addext',
      'extendedKeyUsage=serverAuth',
      '-keyout',
      keyFile,
      '-out',
      certFile,
    ],
    { stdio: 'pipe' },
  );
  return { cert: readFileSync(certFile), key: readFileSync(keyFile) };
}

function targetPort(url) {
  return url.startsWith('/api/') ? API_PORT : METRO_PORT;
}

function passOn(headers) {
  return Object.fromEntries(Object.entries(headers).filter(([name]) => !HOP_BY_HOP.has(name)));
}

function isListening(port) {
  return new Promise((resolve) => {
    const probe = net.connect(port, 'localhost');
    probe.setTimeout(1500);
    probe.once('connect', () => {
      probe.destroy();
      resolve(true);
    });
    probe.once('timeout', () => {
      probe.destroy();
      resolve(false);
    });
    probe.once('error', () => resolve(false));
  });
}

function notRunning(port) {
  return port === API_PORT
    ? `The backend isn't answering on port ${API_PORT}. Start it, then reload.`
    : `The app isn't running on port ${METRO_PORT}. Start it with npm run web, then reload.`;
}

const addresses = lanAddresses();
const main = await mainAddress();

// The Host header is passed on unchanged: Metro only answers pages whose origin matches the host they asked.
const server = https.createServer(certificate(addresses), (req, res) => {
  const port = targetPort(req.url ?? '/');
  const upstream = http.request(
    { host: 'localhost', port, method: req.method, path: req.url, headers: passOn(req.headers) },
    (answer) => {
      res.writeHead(answer.statusCode ?? 502, answer.statusMessage, passOn(answer.headers));
      answer.pipe(res);
    },
  );
  upstream.on('error', () => {
    if (!res.headersSent) {
      res.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' });
    }
    res.end(notRunning(port));
  });
  // A phone that gives up on an answer (Luna skipped ahead) stops the work behind it too.
  res.on('close', () => {
    if (!res.writableFinished) {
      upstream.destroy();
    }
  });
  req.pipe(upstream);
});

// Live reload talks over a WebSocket.
server.on('upgrade', (req, socket, head) => {
  const upstream = net.connect(targetPort(req.url ?? '/'), 'localhost', () => {
    const lines = [`${req.method} ${req.url} HTTP/${req.httpVersion}`];
    for (let i = 0; i < req.rawHeaders.length; i += 2) {
      lines.push(`${req.rawHeaders[i]}: ${req.rawHeaders[i + 1]}`);
    }
    upstream.write(`${lines.join('\r\n')}\r\n\r\n`);
    if (head.length) {
      upstream.write(head);
    }
    upstream.pipe(socket).pipe(upstream);
  });
  upstream.on('error', () => socket.destroy());
  socket.on('error', () => upstream.destroy());
});

server.on('error', (error) => {
  console.error(
    error.code === 'EADDRINUSE' ? `Port ${PORT} is already in use; is npm run phone already running?` : error.message,
  );
  process.exit(1);
});

server.listen(PORT, async () => {
  const shown = main && isPrivate(main) ? [main] : addresses;
  console.log('\nLuna on your phone');
  console.log('  1. Connect the phone to the same Wi-Fi or hotspot as this laptop.');
  if (shown.length === 0) {
    console.log("  2. This laptop isn't on a Wi-Fi or hotspot network right now. Connect it, then run this again.");
  } else {
    console.log(`  2. On the phone, open ${shown.map((ip) => `https://${ip}:${PORT}`).join('  or  ')}`);
    console.log('     The first time, it warns about the certificate: tap Advanced, then Proceed.');
    console.log(`  (http://${shown[0]}:${METRO_PORT} works too, but phones keep the mic off on http pages.)`);
  }
  for (const port of [METRO_PORT, API_PORT]) {
    if (!(await isListening(port))) {
      console.log(`\n  ! ${notRunning(port)}`);
    }
  }
  if (process.platform === 'win32') {
    console.log(
      '\n  If the phone can\'t open it, Windows Firewall may be blocking it. In an admin PowerShell, run once:\n' +
        `  New-NetFirewallRule -DisplayName "GenZ English Partner (phone on the same Wi-Fi)" -Direction Inbound ` +
        `-Protocol TCP -LocalPort ${API_PORT},${METRO_PORT},${PORT} -RemoteAddress LocalSubnet -Action Allow`,
    );
  }
  console.log('\nPress Ctrl+C to stop.');
});
