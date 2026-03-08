'use strict';
const WebSocket = require('ws');
const EventEmitter = require('events');

class AndroidNode extends EventEmitter {
  constructor(config = {}) {
    super();
    this.host = config.host || '127.0.0.1';
    this.port = config.port || 7788;
    this.ws = null;
    this._pending = new Map();
    this._nextId = 1;
    this._connected = false;
    this._reconnectDelay = 2000;
  }

  connect() {
    return new Promise((resolve, reject) => {
      const url = `ws://${this.host}:${this.port}`;
      this.ws = new WebSocket(url);
      this.ws.once('open', () => {
        this._connected = true;
        this._reconnectDelay = 2000;
        console.log('[AndroidNode] connected to ' + url);
        this.emit('connected');
        resolve();
      });
      this.ws.once('error', (err) => { if (!this._connected) reject(err); });
      this.ws.on('message', (data) => this._handleMessage(data.toString()));
      this.ws.on('close', () => {
        this._connected = false;
        this.emit('disconnected');
        setTimeout(() => {
          this._reconnectDelay = Math.min(this._reconnectDelay * 2, 30000);
          this.connect().catch(() => {});
        }, this._reconnectDelay);
      });
    });
  }

  disconnect() { if (this.ws) this.ws.terminate(); }

  _handleMessage(raw) {
    let msg; try { msg = JSON.parse(raw); } catch { return; }
    if (msg.id !== undefined && this._pending.has(msg.id)) {
      const { resolve, reject, timer } = this._pending.get(msg.id);
      this._pending.delete(msg.id); clearTimeout(timer);
      if (msg.error) reject(new Error(`CDP ${msg.error.code}: ${msg.error.message}`));
      else resolve(msg.result);
    } else if (msg.method) {
      this.emit('event', msg.method, msg.params);
      this.emit(msg.method, msg.params);
    }
  }

  send(method, params = {}, timeoutMs = 30000) {
    return new Promise((resolve, reject) => {
      if (!this._connected) return reject(new Error('not connected'));
      const id = this._nextId++;
      const timer = setTimeout(() => {
        this._pending.delete(id);
        reject(new Error(`timeout: ${method}`));
      }, timeoutMs);
      this._pending.set(id, { resolve, reject, timer });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }

  async snapshot() { return this.send('Android.dumpUiTree'); }
  async screenshot() { const r = await this.send('Android.screenshot'); return r.data; }
  async tap(x, y) { return this.send('Android.tap', { x, y }); }
  async type(text) { return this.send('Android.type', { text }); }
  async swipe(x1, y1, x2, y2, durationMs = 300) { return this.send('Android.swipe', { x1, y1, x2, y2, duration: durationMs }); }
  async keyEvent(keyCode, metaState = 0) { return this.send('Android.keyEvent', { keyCode, metaState }); }
  async navigate(pkg, activity = '') { return this.send('Android.launch', { pkg, activity }); }
  async shell(command) { const r = await this.send('Android.shell', { command }, 60000); return r.output; }

  async tapElement(selector) {
    const snap = await this.snapshot();
    if (snap.mode === 'screenshot_fallback') throw new Error('UI tree unavailable');
    const coords = findElement(snap.xml, selector);
    if (!coords) throw new Error('Element not found: ' + selector);
    return this.tap(coords.x, coords.y);
  }

  async wait(condition, options = {}) {
    const { timeoutMs = 15000, intervalMs = 500 } = options;
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const snap = await this.snapshot();
      if (condition(snap)) return snap;
      await new Promise(r => setTimeout(r, intervalMs));
    }
    throw new Error('wait: condition not met within ' + timeoutMs + 'ms');
  }

  async waitForActivity(pkg, activity, timeoutMs = 15000) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.off('Android.activityResumed', handler);
        reject(new Error('waitForActivity timeout'));
      }, timeoutMs);
      const handler = (params) => {
        if (params.pkg === pkg && (!activity || params.activity === activity)) {
          clearTimeout(timer); resolve(params);
        }
      };
      this.on('Android.activityResumed', handler);
    });
  }
}

function findElement(xml, sel) {
  const patterns = [
    new RegExp(`text="${sel.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')}"[^>]*bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]"`),
    new RegExp(`resource-id="[^"]*${sel.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')}[^"]*"[^>]*bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]"`),
  ];
  for (const re of patterns) {
    const m = re.exec(xml);
    if (m) return { x: (parseInt(m[1])+parseInt(m[3]))/2, y: (parseInt(m[2])+parseInt(m[4]))/2 };
  }
  return null;
}

module.exports = AndroidNode;
