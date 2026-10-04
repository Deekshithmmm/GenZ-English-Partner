// Renders Kai with headless Edge (or Chrome) and three.js.
//   node render.mjs preview   -> preview/*.png: one still per loop, on a light backdrop, for checking the look
//   node render.mjs           -> frames/<loop>/NNN.png (transparent), then run build.py to make the app's assets
import { createServer } from 'node:http';
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';

import { chromium } from 'playwright-core';

const here = fileURLToPath(new URL('.', import.meta.url));
const mode = process.argv[2] ?? 'frames';
const FPS = 16;
const SIZE = 768;

const types = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript' };
const server = createServer((req, res) => {
  const path = normalize(join(here, decodeURIComponent(new URL(req.url, 'http://x').pathname)));
  if (!path.startsWith(here)) {
    res.writeHead(403).end();
    return;
  }
  try {
    const body = readFileSync(path.endsWith('\\') || path.endsWith('/') ? join(path, 'index.html') : path);
    res.writeHead(200, { 'Content-Type': types[extname(path)] ?? 'application/octet-stream' }).end(body);
  } catch {
    res.writeHead(404).end();
  }
});
await new Promise((resolve) => server.listen(0, resolve));
const port = server.address().port;

const browser = await chromium.launch({
  channel: process.env.BROWSER_CHANNEL ?? 'msedge',
  headless: true,
  args: ['--use-angle=d3d11', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
});
const page = await browser.newPage({ viewport: { width: SIZE, height: SIZE } });
page.on('console', (m) => m.type() === 'error' && console.error('page:', m.text()));
page.on('pageerror', (e) => console.error('page error:', e.message));

const save = (file, dataUrl) => writeFileSync(file, Buffer.from(dataUrl.split(',')[1], 'base64'));

try {
  const query = mode === 'preview' ? `?size=${SIZE}&preview=1` : `?size=${SIZE}`;
  await page.goto(`http://localhost:${port}/index.html${query}`);
  await page.waitForFunction(() => window.KAI_READY === true, null, { timeout: 60_000 });
  const loops = await page.evaluate(() => window.KAI.loops);

  if (mode === 'preview') {
    const out = join(here, 'preview');
    mkdirSync(out, { recursive: true });
    for (const name of Object.keys(loops)) {
      // Screenshots include the page's preview backdrop, so the stills show how Kai sits on a light background.
      await page.evaluate((n) => window.KAI.frame(n, 0.3), name);
      await page.screenshot({ path: join(out, `${name}.png`) });
    }
    await page.evaluate(() => window.KAI.setView('headshot'));
    await page.evaluate(() => window.KAI.frame('idle', 0));
    await page.screenshot({ path: join(out, 'headshot.png') });
    console.log(`preview stills in ${out}`);
  } else {
    const out = join(here, 'frames');
    rmSync(out, { recursive: true, force: true });
    for (const [name, seconds] of Object.entries(loops)) {
      mkdirSync(join(out, name), { recursive: true });
      const count = Math.round(seconds * FPS);
      for (let i = 0; i < count; i++) {
        const data = await page.evaluate(([n, t]) => window.KAI.frame(n, t), [name, i / count]);
        save(join(out, name, `${String(i).padStart(3, '0')}.png`), data);
      }
      console.log(`${name}: ${count} frames`);
    }
    await page.evaluate(() => window.KAI.setView('headshot'));
    mkdirSync(join(out, 'stills'), { recursive: true });
    save(join(out, 'stills', 'headshot.png'), await page.evaluate(() => window.KAI.frame('idle', 0)));
    await page.evaluate(() => window.KAI.setView('bust'));
    save(join(out, 'stills', 'bust.png'), await page.evaluate(() => window.KAI.frame('idle', 0)));
    writeFileSync(join(out, 'meta.json'), JSON.stringify({ fps: FPS, size: SIZE, loops }, null, 2));
    console.log(`frames in ${out}; next: python build.py`);
  }
} finally {
  await browser.close();
  server.close();
}
