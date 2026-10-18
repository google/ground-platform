/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import http from 'node:http';
import { spawn, execFile } from 'node:child_process';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import net from 'node:net';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const BAOBAB_ROOT = path.resolve(__dirname, '../../');

// Command line argument and port parsing
let port = 8099;
if (process.env.PORT) {
  const p = parseInt(process.env.PORT, 10);
  if (!isNaN(p)) port = p;
}

const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
  if (argv[i] === '--port' && argv[i + 1]) {
    const p = parseInt(argv[++i], 10);
    if (!isNaN(p)) port = p;
  } else if (argv[i].startsWith('--port=')) {
    const p = parseInt(argv[i].split('=')[1], 10);
    if (!isNaN(p)) port = p;
  }
}

// Subsystem definitions
const SERVICE_DEFINITIONS = [
  {
    id: 'user-data-service',
    name: 'Server',
    code: 'SERVER',
    category: 'CORE',
    role: 'User data service (server/user-data-service): gRPC API the apps sync with',
    port: 8080,
    healthCheck: { type: 'http', path: '/healthz', expectedStatus: 200 },
    cwdRel: 'server/user-data-service',
    command: ['./gradlew', 'run', '--console=plain'],
    readyRegex: /User data service listening on port/i,
    endpoints: [
      { label: 'RPC inspector', view: 'rpc' },
      { label: 'Spanner data', view: 'spanner' },
      { label: 'Health check', path: '/healthz', type: 'api' }
    ]
  },
  {
    id: 'web-console',
    name: 'Web console',
    code: 'WEB',
    category: 'CORE',
    role: 'Admin web app (webApp): surveys, organizations, data',
    port: 8092,
    healthCheck: { type: 'http', path: '/', expectedStatus: 500, comparison: '<' },
    cwdRel: 'webApp',
    command: ['./gradlew', 'wasmJsBrowserDevelopmentRun', '--console=plain'],
    readyRegex: /webpack \d+.*compiled/i,
    endpoints: [
      { label: 'Surveys', path: '#surveys', type: 'route' },
      { label: 'Dashboard', path: '#dashboard', type: 'route' },
      { label: 'Organizations', path: '#organizations', type: 'route' },
      { label: 'Impact', path: '#impact', type: 'route' }
    ]
  },
  {
    id: 'mobile-app',
    name: 'Mobile app',
    code: 'MOBILE',
    category: 'CORE',
    role: 'Field data collection app in a simulated device (mobileWebApp)',
    port: 8093,
    healthCheck: { type: 'http', path: '/', expectedStatus: 500, comparison: '<' },
    cwdRel: 'mobileWebApp',
    command: ['./gradlew', 'wasmJsBrowserDevelopmentRun', '--console=plain'],
    readyRegex: /webpack \d+.*compiled/i,
    endpoints: [
      { label: 'With device frame', path: '?chrome=1', type: 'route' },
      { label: 'App only', path: '?chrome=0', type: 'route' }
    ]
  },
  {
    id: 'spanner-emulator',
    name: 'Spanner emulator',
    code: 'SPANNER',
    category: 'AUX',
    role: 'Local Cloud Spanner database (gRPC :9010, REST :9020). The server uses it if running when the server starts',
    port: 9010,
    auxPort: 9020,
    healthCheck: { type: 'tcp', port: 9010, fallbackPort: 9020 },
    cwdRel: '',
    command: ['bash', 'scripts/spanner-emulator.sh'],
    readyRegex: /Cloud Spanner emulator running/i,
    endpoints: [
      { label: 'Browse data', view: 'spanner' },
      { label: 'REST API', path: ':9020/v1/projects', type: 'api' }
    ]
  },
  {
    id: 'form-debugger',
    name: 'Form debugger',
    code: 'FORMS',
    category: 'AUX',
    role: 'XForms / ProtoForms / XPath debugging tool (devtools/formdebugger)',
    port: 8090,
    healthCheck: { type: 'http', path: '/', expectedStatus: 500, comparison: '<' },
    cwdRel: 'devtools/formdebugger',
    command: ['./gradlew', 'wasmJsBrowserDevelopmentRun', '--console=plain'],
    readyRegex: /webpack \d+.*compiled/i,
    endpoints: [
      { label: 'Open', path: '/', type: 'route' }
    ]
  }
];

// In-memory runtime state
const serviceStates = new Map();
const MAX_LOG_LINES = 1500;
let logSeq = 0;

for (const s of SERVICE_DEFINITIONS) {
  serviceStates.set(s.id, {
    ...s,
    cwd: path.resolve(BAOBAB_ROOT, s.cwdRel),
    state: 'OFFLINE', // 'OFFLINE' | 'STARTING' | 'ONLINE' | 'FAULT'
    managed: false,
    child: null,
    pid: null,
    uptimeSeconds: 0,
    rssMb: 0,
    cpuPercent: 0,
    lastLogLine: '',
    compilePhase: '',
    logs: [],
    lastExitCode: null,
    lastExitSignal: null
  });
}

// SSE Subscriber Set
const sseClients = new Set();

function broadcastSse(eventType, data) {
  const payload = `event: ${eventType}\ndata: ${JSON.stringify(data)}\n\n`;
  for (const client of sseClients) {
    try {
      client.write(payload);
    } catch (_) {
      sseClients.delete(client);
    }
  }
}

function broadcastStatus() {
  const status = buildStatusSync();
  broadcastSse('status', status);
}

function cleanAnsi(str) {
  return str.replace(/\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])/g, '');
}

function appendLog(service, rawLine, stream = 'stdout') {
  if (rawLine === undefined || rawLine === null) return;
  const text = cleanAnsi(String(rawLine)).trimEnd();
  if (!text) return;

  const entry = {
    seq: ++logSeq,
    serviceId: service.id,
    serviceCode: service.code,
    timestamp: new Date().toISOString(),
    text,
    stream
  };

  service.logs.push(entry);
  if (service.logs.length > MAX_LOG_LINES) {
    service.logs.shift();
  }

  broadcastSse('log', entry);
}

// Port and Process Telemetry Utilities
function checkTcpPort(targetPort, host = '127.0.0.1', timeoutMs = 500) {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    let resolved = false;

    socket.setTimeout(timeoutMs);
    socket.once('connect', () => {
      if (!resolved) {
        resolved = true;
        socket.destroy();
        resolve(true);
      }
    });

    socket.once('timeout', () => {
      if (!resolved) {
        resolved = true;
        socket.destroy();
        resolve(false);
      }
    });

    socket.once('error', () => {
      if (!resolved) {
        resolved = true;
        resolve(false);
      }
    });

    socket.connect(targetPort, host);
  });
}

function checkHttp(targetPort, targetPath = '/', host = '127.0.0.1', timeoutMs = 800) {
  return new Promise((resolve) => {
    const req = http.get(
      {
        host,
        port: targetPort,
        path: targetPath,
        timeout: timeoutMs
      },
      (res) => {
        res.resume();
        resolve(res.statusCode || null);
      }
    );

    req.once('timeout', () => {
      req.destroy();
      resolve(null);
    });

    req.once('error', () => {
      resolve(null);
    });
  });
}

function findPidForPort(targetPort) {
  return new Promise((resolve) => {
    // 1. Try ss -tlnp on Linux
    execFile('ss', ['-tlnp', `sport = :${targetPort}`], (err, stdout) => {
      if (!err && stdout) {
        const match = stdout.match(/pid=(\d+)/);
        if (match) {
          const pid = parseInt(match[1], 10);
          if (!isNaN(pid)) return resolve(pid);
        }
      }

      // 2. Fallback to lsof -iTCP:<port> -sTCP:LISTEN -t
      execFile('lsof', [`-iTCP:${targetPort}`, '-sTCP:LISTEN', '-t'], (err2, stdout2) => {
        if (!err2 && stdout2) {
          const lines = stdout2.trim().split('\n').filter(Boolean);
          if (lines.length > 0) {
            const pid = parseInt(lines[0].trim(), 10);
            if (!isNaN(pid)) return resolve(pid);
          }
        }
        resolve(null);
      });
    });
  });
}

function getProcessTelemetry(pid) {
  return new Promise((resolve) => {
    execFile('ps', ['-o', 'etimes=,rss=,pcpu=', '-p', String(pid)], (err, stdout) => {
      if (err || !stdout) return resolve(null);
      const parts = stdout.trim().split(/\s+/);
      if (parts.length >= 3) {
        const etimes = parseInt(parts[0], 10) || 0;
        const rssKb = parseInt(parts[1], 10) || 0;
        const pcpu = parseFloat(parts[2]) || 0;
        return resolve({
          uptimeSeconds: etimes,
          rssMb: Math.round(rssKb / 1024),
          cpuPercent: pcpu
        });
      }
      resolve(null);
    });
  });
}

async function readLocalProperties() {
  const localPropPath = path.join(BAOBAB_ROOT, 'local.properties');
  try {
    const content = await fsp.readFile(localPropPath, 'utf-8');
    const match = content.match(/^USER_DATA_SERVICE_URL=(.*)$/m);
    return {
      userDataServiceUrl: match ? match[1].trim() : null
    };
  } catch (_) {
    return { userDataServiceUrl: null };
  }
}

async function syncLocalProperties(targetUrl) {
  const localPropPath = path.join(BAOBAB_ROOT, 'local.properties');
  let content = '';
  try {
    content = await fsp.readFile(localPropPath, 'utf-8');
  } catch (_) {
    content = '';
  }

  const url = targetUrl || 'http://localhost:8080';
  const linePattern = /^USER_DATA_SERVICE_URL=.*$/m;
  let newContent = '';
  if (linePattern.test(content)) {
    newContent = content.replace(linePattern, `USER_DATA_SERVICE_URL=${url}`);
  } else {
    newContent = content
      ? `${content.trimEnd()}\nUSER_DATA_SERVICE_URL=${url}\n`
      : `USER_DATA_SERVICE_URL=${url}\n`;
  }

  await fsp.writeFile(localPropPath, newContent, 'utf-8');
  return { success: true, url, path: localPropPath };
}

// Build Status JSON
let cachedLocalProps = { userDataServiceUrl: null };
readLocalProperties().then((props) => {
  cachedLocalProps = props;
});

function buildStatusSync() {
  const servicesObj = {};
  for (const [id, s] of serviceStates.entries()) {
    servicesObj[id] = {
      id: s.id,
      name: s.name,
      code: s.code,
      category: s.category,
      role: s.role,
      port: s.port,
      auxPort: s.auxPort || null,
      state: s.state,
      managed: s.managed,
      pid: s.pid,
      uptimeSeconds: s.uptimeSeconds,
      rssMb: s.rssMb,
      cpuPercent: s.cpuPercent,
      lastLogLine: s.lastLogLine,
      compilePhase: s.compilePhase,
      endpoints: s.endpoints
    };
  }
  return {
    services: servicesObj,
    host: {
      hostname: os.hostname(),
      platform: os.platform(),
      uptime: os.uptime(),
      loadavg: os.loadavg()
    },
    localProperties: cachedLocalProps,
    timestamp: new Date().toISOString()
  };
}

// Periodic Telemetry Probe Loop (1500ms)
async function probeAllServices() {
  readLocalProperties().then((p) => {
    cachedLocalProps = p;
  });

  for (const [id, s] of serviceStates.entries()) {
    let portOpen = false;
    let healthy = false;

    if (s.id === 'spanner-emulator') {
      const p1 = await checkTcpPort(9010);
      const p2 = await checkTcpPort(9020);
      portOpen = p1 || p2;
      healthy = portOpen;
    } else {
      portOpen = await checkTcpPort(s.port);
      if (portOpen) {
        if (s.healthCheck.type === 'http') {
          const status = await checkHttp(s.port, s.healthCheck.path);
          if (status !== null) {
            if (s.healthCheck.comparison === '<') {
              healthy = status < s.healthCheck.expectedStatus;
            } else {
              healthy = status === s.healthCheck.expectedStatus;
            }
          }
        } else {
          healthy = true;
        }
      }
    }

    if (portOpen) {
      const pid = await findPidForPort(s.port) || (s.auxPort ? await findPidForPort(s.auxPort) : null);
      if (pid) {
        s.pid = pid;
        const telemetry = await getProcessTelemetry(pid);
        if (telemetry) {
          s.uptimeSeconds = telemetry.uptimeSeconds;
          s.rssMb = telemetry.rssMb;
          s.cpuPercent = telemetry.cpuPercent;
        }
      }

      if (s.child) {
        s.managed = true;
        s.state = healthy ? 'ONLINE' : 'STARTING';
      } else {
        s.managed = false;
        s.state = healthy ? 'ONLINE' : 'ONLINE';
      }
    } else {
      // Port is closed
      if (s.child) {
        s.managed = true;
        if (s.child.exitCode !== null) {
          s.state = s.lastExitCode === 0 ? 'OFFLINE' : 'FAULT';
          s.managed = false;
          s.child = null;
          s.pid = null;
        } else {
          s.state = 'STARTING';
        }
      } else {
        s.state = s.lastExitCode && s.lastExitCode !== 0 ? 'FAULT' : 'OFFLINE';
        s.managed = false;
        s.pid = null;
        s.uptimeSeconds = 0;
        s.rssMb = 0;
        s.cpuPercent = 0;
      }
    }
  }

  broadcastStatus();
}

// Start probing periodically
setInterval(probeAllServices, 1500);
// Run initial probe immediately
probeAllServices();

// Process Lifecycle Management
async function startService(id, options = {}) {
  const service = serviceStates.get(id);
  if (!service) throw new Error(`Unknown service: ${id}`);

  if (service.state === 'ONLINE' || service.state === 'STARTING') {
    return { success: false, message: `Service ${id} is already ${service.state}` };
  }

  // Pre-flight check: ensure port is not occupied
  const existingPid = await findPidForPort(service.port);
  if (existingPid) {
    return {
      success: false,
      message: `Port ${service.port} is already bound by external PID ${existingPid}`
    };
  }

  const childEnv = { ...process.env };

  // Spanner configuration for User Data Service
  if (id === 'user-data-service') {
    const spannerService = serviceStates.get('spanner-emulator');
    const isSpannerActive = spannerService && spannerService.state === 'ONLINE';
    if (options.useSpanner || isSpannerActive) {
      childEnv.SPANNER_EMULATOR_HOST = 'localhost:9010';
      appendLog(
        service,
        `[LAUNCHPAD] Spanner emulator is running, so starting the server with SPANNER_EMULATOR_HOST=localhost:9010`,
        'stdout'
      );
    }
  }

  // Ensure Node path and user bin in PATH
  const userBin = path.join(os.homedir(), 'bin');
  if (childEnv.PATH && !childEnv.PATH.includes(userBin)) {
    childEnv.PATH = `${userBin}:${childEnv.PATH}`;
  }

  let child;
  try {
    child = spawn(service.command[0], service.command.slice(1), {
      cwd: service.cwd,
      env: childEnv,
      detached: true,
      stdio: ['ignore', 'pipe', 'pipe']
    });
  } catch (err) {
    service.state = 'FAULT';
    appendLog(service, `[LAUNCHPAD ERROR] Failed to spawn: ${err.message}`, 'stderr');
    broadcastStatus();
    return { success: false, message: err.message };
  }

  service.child = child;
  service.pid = child.pid;
  service.managed = true;
  service.state = 'STARTING';
  service.lastExitCode = null;
  service.lastExitSignal = null;
  service.compilePhase = 'Initializing...';

  appendLog(
    service,
    `[LAUNCHPAD] Process spawned (PID ${child.pid}) in ${service.cwdRel || '.'}`,
    'stdout'
  );

  let stdoutBuffer = '';
  child.stdout.on('data', (chunk) => {
    stdoutBuffer += chunk.toString('utf-8');
    const lines = stdoutBuffer.split('\n');
    stdoutBuffer = lines.pop();
    for (const l of lines) {
      appendLog(service, l, 'stdout');
      const clean = cleanAnsi(l).trim();
      service.lastLogLine = clean;

      const taskMatch = clean.match(/> Task :([^\s]+)/);
      if (taskMatch) service.compilePhase = taskMatch[1];
      if (/webpack \d+.*compil/i.test(clean)) {
        service.compilePhase = 'Webpack compiling...';
      }

      if (service.readyRegex && service.readyRegex.test(clean)) {
        service.state = 'ONLINE';
        service.compilePhase = 'Ready';
        broadcastStatus();
      }
    }
  });

  let stderrBuffer = '';
  child.stderr.on('data', (chunk) => {
    stderrBuffer += chunk.toString('utf-8');
    const lines = stderrBuffer.split('\n');
    stderrBuffer = lines.pop();
    for (const l of lines) {
      appendLog(service, l, 'stderr');
      const clean = cleanAnsi(l).trim();
      service.lastLogLine = clean;
    }
  });

  child.on('error', (err) => {
    appendLog(service, `[LAUNCHPAD ERROR] Process runtime error: ${err.message}`, 'stderr');
    service.state = 'FAULT';
    service.managed = false;
    service.child = null;
    broadcastStatus();
  });

  child.on('exit', (code, signal) => {
    service.lastExitCode = code;
    service.lastExitSignal = signal;
    appendLog(
      service,
      `[LAUNCHPAD] Process exited with code ${code ?? 'null'}, signal ${signal ?? 'none'}`,
      'stdout'
    );
    if (service.child === child) {
      service.child = null;
      service.managed = false;
      service.state = (code === 0 || signal === 'SIGTERM') ? 'OFFLINE' : 'FAULT';
      service.pid = null;
    }
    broadcastStatus();
  });

  broadcastStatus();
  return { success: true, message: `Started ${service.name}`, pid: child.pid };
}

async function stopService(id) {
  const service = serviceStates.get(id);
  if (!service) throw new Error(`Unknown service: ${id}`);

  appendLog(service, `[LAUNCHPAD] Stopping service ${service.name}...`, 'stdout');

  // 1. If managed child process, kill process group
  if (service.child && service.child.pid) {
    try {
      process.kill(-service.child.pid, 'SIGTERM');
    } catch (_) {
      try {
        service.child.kill('SIGTERM');
      } catch (__) {}
    }
  }

  // 2. Terminate any process currently listening on service port(s)
  const portsToClean = [service.port];
  if (service.auxPort) portsToClean.push(service.auxPort);

  for (const p of portsToClean) {
    const pid = await findPidForPort(p);
    if (pid) {
      appendLog(service, `[LAUNCHPAD] Sending SIGTERM to port ${p} PID ${pid}...`, 'stdout');
      try {
        process.kill(pid, 'SIGTERM');
      } catch (_) {}

      // Escalate to SIGKILL after 2.5s if still listening
      setTimeout(async () => {
        const stillListeningPid = await findPidForPort(p);
        if (stillListeningPid === pid) {
          appendLog(
            service,
            `[LAUNCHPAD] Port ${p} PID ${pid} still active after 2.5s; sending SIGKILL...`,
            'stderr'
          );
          try {
            process.kill(pid, 'SIGKILL');
          } catch (_) {}
        }
      }, 2500);
    }
  }

  service.child = null;
  service.managed = false;
  return { success: true, message: `Stop initiated for ${service.name}` };
}

async function restartService(id, options = {}) {
  const service = serviceStates.get(id);
  if (!service) throw new Error(`Unknown service: ${id}`);

  await stopService(id);

  // Poll until target port is fully released (up to 7 seconds)
  const deadline = Date.now() + 7000;
  while (Date.now() < deadline) {
    const portOpen = await checkTcpPort(service.port);
    if (!portOpen) break;
    await new Promise((r) => setTimeout(r, 200));
  }

  return await startService(id, options);
}

// Request Body Parser
function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
      if (body.length > 2e6) {
        req.destroy();
        reject(new Error('Payload too large'));
      }
    });
    req.on('end', () => {
      if (!body) return resolve({});
      try {
        resolve(JSON.parse(body));
      } catch (err) {
        reject(err);
      }
    });
    req.on('error', reject);
  });
}

// HTTP Server
const server = http.createServer(async (req, res) => {
  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = parsedUrl.pathname;
  const method = req.method.toUpperCase();

  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  try {
    // 1. Static Root / Dashboard UI
    if (pathname === '/' || pathname === '/index.html') {
      const htmlPath = path.join(__dirname, 'index.html');
      try {
        const html = await fsp.readFile(htmlPath, 'utf-8');
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(html);
        return;
      } catch (err) {
        res.writeHead(500, { 'Content-Type': 'text/plain' });
        res.end(`Failed to read index.html: ${err.message}`);
        return;
      }
    }

    // 2. Status API
    if (pathname === '/api/status' && method === 'GET') {
      const status = buildStatusSync();
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(status, null, 2));
      return;
    }

    // 3. SSE Stream
    if (pathname === '/api/events' && method === 'GET') {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive'
      });
      res.write(`event: status\ndata: ${JSON.stringify(buildStatusSync())}\n\n`);

      sseClients.add(res);

      const heartbeat = setInterval(() => {
        try {
          res.write(': heartbeat\n\n');
        } catch (_) {
          clearInterval(heartbeat);
          sseClients.delete(res);
        }
      }, 15000);

      req.on('close', () => {
        clearInterval(heartbeat);
        sseClients.delete(res);
      });
      return;
    }

    // 4. Logs Query API
    if (pathname === '/api/logs' && method === 'GET') {
      const serviceId = parsedUrl.searchParams.get('service');
      const limit = parseInt(parsedUrl.searchParams.get('limit') || '500', 10);

      let logList = [];
      if (serviceId && serviceStates.has(serviceId)) {
        logList = [...serviceStates.get(serviceId).logs];
      } else {
        for (const s of serviceStates.values()) {
          logList.push(...s.logs);
        }
        logList.sort((a, b) => a.seq - b.seq);
      }

      if (logList.length > limit) {
        logList = logList.slice(-limit);
      }

      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ logs: logList }));
      return;
    }

    // 5. Clear Logs API
    const clearLogsMatch = pathname.match(/^\/api\/logs\/([^/]+)\/clear$/);
    if (clearLogsMatch && method === 'POST') {
      const target = clearLogsMatch[1];
      if (target === 'all') {
        for (const s of serviceStates.values()) {
          s.logs = [];
        }
      } else if (serviceStates.has(target)) {
        serviceStates.get(target).logs = [];
      } else {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: `Service ${target} not found` }));
        return;
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, message: `Logs cleared for ${target}` }));
      return;
    }

    // 6. Service Lifecycle Control: Start, Stop, Restart
    const serviceControlMatch = pathname.match(/^\/api\/service\/([^/]+)\/(start|stop|restart)$/);
    if (serviceControlMatch && method === 'POST') {
      const serviceId = serviceControlMatch[1];
      const action = serviceControlMatch[2];
      const body = await readJsonBody(req);

      if (!serviceStates.has(serviceId)) {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: `Unknown service ${serviceId}` }));
        return;
      }

      let result;
      if (action === 'start') {
        result = await startService(serviceId, body);
      } else if (action === 'stop') {
        result = await stopService(serviceId);
      } else if (action === 'restart') {
        result = await restartService(serviceId, body);
      }

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(result));
      return;
    }

    // 7. Fleet Operations
    if (pathname === '/api/fleet/start-core' && method === 'POST') {
      const coreIds = ['user-data-service', 'web-console', 'mobile-app'];
      const results = {};
      for (const cid of coreIds) {
        try {
          results[cid] = await startService(cid);
        } catch (err) {
          results[cid] = { success: false, message: err.message };
        }
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, results }));
      return;
    }

    if (pathname === '/api/fleet/stop-all' && method === 'POST') {
      const results = {};
      for (const [id] of serviceStates.entries()) {
        try {
          results[id] = await stopService(id);
        } catch (err) {
          results[id] = { success: false, message: err.message };
        }
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, results }));
      return;
    }

    // 8. Config Sync: local.properties
    if (pathname === '/api/config/sync-local-properties' && method === 'POST') {
      const body = await readJsonBody(req);
      const targetUrl = body.url || 'http://localhost:8080';
      const result = await syncLocalProperties(targetUrl);
      cachedLocalProps = await readLocalProperties();
      broadcastStatus();
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(result));
      return;
    }

    // Fallthrough 404
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Not Found', path: pathname }));
  } catch (err) {
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: err.message || 'Internal Server Error' }));
  }
});

server.listen(port, '0.0.0.0', () => {
  console.log(`Collect One dev launchpad running at http://localhost:${port}`);
  console.log(`Monitoring ${serviceStates.size} services in ${BAOBAB_ROOT}`);
});
