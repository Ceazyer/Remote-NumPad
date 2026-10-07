const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const net = require('node:net');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const edge = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function freePort() {
  const server = net.createServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  return port;
}

async function connectToEdge(port) {
  let target;
  for (let attempt = 0; attempt < 40; attempt++) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/json/list`);
      target = (await response.json()).find((item) => item.type === 'page');
      if (target) break;
    } catch { /* Edge is still starting. */ }
    await pause(100);
  }
  assert.ok(target, 'Edge debugging target should become available');

  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', reject, { once: true });
  });
  let nextId = 0;
  const pending = new Map();
  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data);
    if (!message.id) return;
    const pair = pending.get(message.id);
    if (!pair) return;
    pending.delete(message.id);
    if (message.error) pair.reject(new Error(message.error.message));
    else pair.resolve(message.result);
  });
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++nextId;
    pending.set(id, { resolve, reject });
    socket.send(JSON.stringify({ id, method, params }));
  });
  return { socket, send };
}

async function evaluate(send, expression) {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
  return result.result.value;
}

test('approved calculator layout and both themes fit phones and preserve one-click commands', async () => {
  const server = http.createServer((request, response) => {
    const pathname = new URL(request.url, 'http://localhost').pathname;
    const files = {
      '/': ['index.html', 'text/html'],
      '/style.css': ['style.css', 'text/css'],
      '/app.js': ['app.js', 'text/javascript']
    };
    if (!files[pathname]) {
      response.writeHead(404).end();
      return;
    }
    const [name, type] = files[pathname];
    response.writeHead(200, { 'content-type': `${type}; charset=utf-8` });
    response.end(fs.readFileSync(path.join(root, 'wwwroot', name)));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));

  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'numpad-layout-'));
  let browser;
  try {
    const port = await freePort();
    spawn(edge, [
      '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
      `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, 'about:blank'
    ], { detached: true, stdio: 'ignore' }).unref();
    browser = await connectToEdge(port);
    await browser.send('Page.enable');
    await browser.send('Page.addScriptToEvaluateOnNewDocument', { source: `
      window.__sent = [];
      window.WebSocket = class extends EventTarget {
        static OPEN = 1;
        readyState = 1;
        constructor() { super(); setTimeout(() => this.dispatchEvent(new Event('open')), 0); }
        send(command) { window.__sent.push(command); }
        close() { this.readyState = 3; }
      };
    ` });

    for (const [width, height] of [
      [375, 667], [393, 851], [320, 568], [667, 375], [851, 393]
    ]) {
      await browser.send('Emulation.setDeviceMetricsOverride', {
        width, height, deviceScaleFactor: 1, mobile: true,
        screenWidth: width, screenHeight: height
      });
      await browser.send('Page.navigate', { url: `http://127.0.0.1:${server.address().port}/` });
      for (let attempt = 0; attempt < 30; attempt++) {
        if (await evaluate(browser.send, 'Boolean(document.querySelector(".number-key"))')) break;
        await pause(50);
      }

      for (const theme of ['light', 'dark']) {
        await evaluate(browser.send, `(() => {
          if (document.documentElement.dataset.theme !== '${theme}') document.querySelector('[data-theme-toggle]').click();
          return document.documentElement.dataset.theme;
        })()`);
      for (const mode of ['input', 'tools']) {
        const result = await evaluate(browser.send, `(() => {
          document.querySelector('[data-page-tab="${mode}"]')?.click();
          const buttons = [...document.querySelectorAll('button')].filter((button) => {
            const box = button.getBoundingClientRect();
            return box.width > 0 && box.height > 0;
          });
          const numberButtons = buttons.filter((button) => button.classList.contains('number-key'));
          return {
            viewportHeight: innerHeight,
            pageHeight: Math.max(document.documentElement.scrollHeight, document.body.scrollHeight),
            minNumberHeight: Math.min(...numberButtons.map((button) => button.getBoundingClientRect().height)),
            selected: document.querySelector('[data-page-tab="${mode}"]')?.getAttribute('aria-selected'),
            theme: document.documentElement.dataset.theme,
            keypadCommands: [...document.querySelectorAll('.number-grid [data-command]')].map(button => button.dataset.command),
            boxes: Object.fromEntries([...document.querySelectorAll('.number-grid [data-command]')].map(button => {
              const box = button.getBoundingClientRect();
              return [button.dataset.command, {left: box.left, right: box.right, top: box.top, bottom: box.bottom}];
            })),
            auxiliaryBoxes: Object.fromEntries([...document.querySelectorAll('.utility-panel [data-command]')].map(button => {
              const box = button.getBoundingClientRect();
              return [button.dataset.command, {left: box.left, top: box.top, right: box.right, bottom: box.bottom}];
            })),
            groupBoxes: ['.file-grid', '.direction-grid'].map(selector => {
              const box = document.querySelector(selector).getBoundingClientRect();
              return {width: box.width, height: box.height, top: box.top, bottom: box.bottom};
            }),
            directionHits: (() => {
              const up = document.querySelector('.direction-grid [data-command="UP"]').getBoundingClientRect();
              const group = document.querySelector('.direction-grid').getBoundingClientRect();
              const hit = (x, y) => document.elementFromPoint(x, y)?.closest('[data-command]')?.dataset.command || null;
              return {body: hit(up.left + up.width / 2, up.top + up.height * .4),
                corner: hit(up.left + up.width * .85, up.top + up.height * .8),
                center: hit(group.left + group.width / 2, group.top + group.height / 2)};
            })(),
            outlineEnvelope: (() => {
              const boxes = [...document.querySelectorAll('.dpad-outline')].map(path => {
                const b = path.getBBox(), s = path.ownerSVGElement.getBoundingClientRect();
                return {left: s.left + b.x * s.width / 100, top: s.top + b.y * s.height / 100,
                  right: s.left + (b.x + b.width) * s.width / 100, bottom: s.top + (b.y + b.height) * s.height / 100};
              });
              return boxes.length ? {width: Math.max(...boxes.map(b => b.right)) - Math.min(...boxes.map(b => b.left)),
                height: Math.max(...boxes.map(b => b.bottom)) - Math.min(...boxes.map(b => b.top))} : {width: 0, height: 0};
            })(),
            shortButtons: buttons.filter(button => {
              const box = button.getBoundingClientRect();
              return box.height < 48 || box.width < 48;
            }).map(button => button.getAttribute('aria-label') || button.textContent.trim()),
            visibleCommands: buttons.map((button) => button.dataset.command).filter(Boolean),
            clipped: buttons.filter((button) => {
              const box = button.getBoundingClientRect();
              return box.top < 0 || box.bottom > innerHeight + 1 || box.left < 0 || box.right > innerWidth + 1;
            }).map((button) => button.textContent.trim())
          };
        })()`);
        assert.ok(result.pageHeight <= result.viewportHeight + 1,
          `${width}x${height} ${mode} scrolls: ${result.pageHeight} > ${result.viewportHeight}`);
        assert.equal(result.selected, 'true', `${mode} tab did not become active`);
        assert.equal(result.theme, theme);
        assert.deepEqual(result.shortButtons, [], `${width}x${height} ${mode} has small touch targets`);
        assert.ok(result.visibleCommands.includes({input: 'NEXT_CELL', tools: 'AUTO_SUM'}[mode]),
          `${mode} controls are not visible`);
        assert.deepEqual(result.clipped, [], `${width}x${height} ${mode} has clipped buttons`);
        for (const command of ['COPY', 'PASTE', 'SAVE', 'SAVE_AS', 'UP', 'LEFT', 'RIGHT', 'DOWN']) {
          assert.ok(result.visibleCommands.includes(command), `${command} missing from ${mode}`);
        }
        const aux = result.auxiliaryBoxes;
        assert.ok(aux.UP.top < aux.LEFT.top && aux.LEFT.top < aux.DOWN.top, 'directions must form a cross');
        assert.equal(aux.LEFT.top, aux.RIGHT.top);
        assert.equal(aux.UP.left, aux.DOWN.left);
        assert.equal(aux.COPY.top, aux.PASTE.top);
        assert.equal(aux.SAVE.top, aux.SAVE_AS.top);
        assert.equal(aux.COPY.left, aux.SAVE.left);
        assert.ok(aux.PASTE.right <= aux.LEFT.left, 'file controls must be left of directions');
        assert.deepEqual(result.groupBoxes[0], result.groupBoxes[1], 'file grid and direction pad must have equal size and alignment');
        assert.equal(result.directionHits.body, 'UP');
        assert.notEqual(result.directionHits.corner, 'UP', 'pointed key must not accept its transparent corner');
        assert.equal(result.directionHits.center, null, 'center gap must not activate any command');
        assert.ok(Math.abs(result.outlineEnvelope.width - result.groupBoxes[0].width) < 1, 'painted pad width must match the file grid');
        assert.ok(Math.abs(result.outlineEnvelope.height - result.groupBoxes[0].height) < 1, 'painted pad height must match the file grid');
        if (mode === 'input') {
          assert.deepEqual(result.keypadCommands, [
            'UNDO', 'PREV_CELL', 'EDIT', 'DELETE', '7', '8', '9', 'BACKSPACE',
            '4', '5', '6', '-', '1', '2', '3', 'NEXT_CELL', '0', '.', 'ENTER'
          ]);
          const box = result.boxes;
          assert.ok(Math.abs(box['0'].left - box['7'].left) < 1);
          assert.ok(Math.abs(box['0'].right - box['8'].right) < 1, 'zero must span exactly two columns');
          assert.ok(Math.abs(box['.'].left - box['9'].left) < 1, 'decimal must be in column three');
          assert.ok(box.UNDO.top < box['7'].top);
        } else {
          for (const command of ['EDIT', 'UNDO', 'AUTO_SUM', 'FORMULA_IF']) {
            assert.ok(result.visibleCommands.includes(command), `${command} is missing from the tools page`);
          }
        }
        if (process.env.NUMPAD_SCREENSHOT_DIR && width === 393) {
          fs.mkdirSync(process.env.NUMPAD_SCREENSHOT_DIR, { recursive: true });
          const shot = await browser.send('Page.captureScreenshot', { format: 'png' });
          fs.writeFileSync(path.join(process.env.NUMPAD_SCREENSHOT_DIR, `web-${theme}-${mode}.png`), Buffer.from(shot.data, 'base64'));
        }
      }
      }
      const sends = await evaluate(browser.send, `(() => {
        document.querySelector('[data-page-tab="input"]').click();
        window.__sent.length = 0;
        document.querySelector('[data-theme-toggle]').click();
        document.querySelector('[data-page-tab="tools"]').click();
        document.querySelector('[data-page-tab="input"]').click();
        const uiSendCount = window.__sent.length;
        for (const command of ['COPY', 'PASTE', 'SAVE', 'SAVE_AS', 'UP', 'LEFT', 'RIGHT', 'DOWN']) document.querySelector('.utility-panel [data-command="' + command + '"]').click();
        const auxiliaryCommands = window.__sent.splice(0);
        for (let i = 0; i < 100; i++) document.querySelector('.number-grid [data-command="7"]').click();
        return {uiSendCount, auxiliaryCommands, commands: window.__sent, theme: document.documentElement.dataset.theme};
      })()`);
      assert.equal(sends.uiSendCount, 0, 'theme and page controls must not send input');
      assert.deepEqual(sends.auxiliaryCommands, ['COPY', 'PASTE', 'SAVE', 'SAVE_AS', 'UP', 'LEFT', 'RIGHT', 'DOWN']);
      assert.deepEqual(sends.commands, Array(100).fill('7'));
      await browser.send('Page.reload');
      await pause(120);
      assert.equal(await evaluate(browser.send, 'document.documentElement.dataset.theme'), sends.theme,
        'theme preference must survive reload');
    }
  } finally {
    if (browser?.socket.readyState === WebSocket.OPEN) {
      await browser.send('Browser.close').catch(() => {});
      browser.socket.close();
    }
    await new Promise((resolve) => server.close(resolve));
    await pause(200);
    fs.rmSync(profile, { recursive: true, force: true, maxRetries: 10, retryDelay: 100 });
  }
});
