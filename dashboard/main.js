const { app, BrowserWindow, ipcMain, Tray, screen, Menu } = require('electron');
const path = require('path');
const fs = require('fs');
const { spawn, exec } = require('child_process');
const express = require('express');
const cors = require('cors');
const dgram = require('dgram');

let mainWindow;
let backendProcess = null;

const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
  process.exit(0);
}

app.on('second-instance', (event, commandLine, workingDirectory) => {
  if (mainWindow) {
    if (!mainWindow.isVisible()) mainWindow.show();
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  } else if (trayWindow && !trayWindow.isDestroyed()) {
      // If we only have the tray window, we might want to restore the main window
      restoreFromTray();
  }
});

// --- UDP Auto-Discovery Server ---
const userDataPath = app.getPath('userData');
const profilesDir = path.join(userDataPath, 'profiles');
if (!fs.existsSync(profilesDir)) {
  fs.mkdirSync(profilesDir, { recursive: true });
}

let serverId = null;
try {
  const cfgPath = path.join(userDataPath, 'config.json');
  const cfg = fs.existsSync(cfgPath) ? JSON.parse(fs.readFileSync(cfgPath, 'utf8')) : {};
  if (cfg.serverId) {
    serverId = cfg.serverId;
  } else {
    serverId = require('crypto').randomBytes(4).toString('hex');
    cfg.serverId = serverId;
    fs.writeFileSync(cfgPath, JSON.stringify(cfg, null, 2));
  }
} catch (e) {
  serverId = require('crypto').randomBytes(4).toString('hex');
}

let lastAndroidIp = null;
let activeSlots = 0;
function getBatteryPercentage(callback) {
  const { exec } = require('child_process');
  exec('WMIC Path Win32_Battery Get EstimatedChargeRemaining', (err, stdout) => {
    if (err) return callback(100); // Default to 100% on desktops
    const match = stdout.match(/\d+/);
    callback(match ? parseInt(match[0]) : 100);
  });
}

function sendToRenderer(channel, data) {
  try {
    if (mainWindow && !mainWindow.isDestroyed() && mainWindow.webContents && !mainWindow.webContents.isDestroyed()) {
      mainWindow.webContents.send(channel, data);
    }
  } catch (e) {}
}

const discoverySocket = dgram.createSocket('udp4');
let currentDiscoveryPort = 14568;

const interfaceSockets = new Map(); // localIp -> dgram.Socket

function getOrCreateInterfaceSocket(localIp) {
  if (interfaceSockets.has(localIp)) return interfaceSockets.get(localIp);
  try {
    const s = dgram.createSocket('udp4');
    s.bind(0, localIp, () => {
      try { s.setBroadcast(true); } catch(e) {}
    });
    s.on('error', () => {});
    interfaceSockets.set(localIp, s);
    return s;
  } catch (e) {
    return null;
  }
}

function findMatchingLocalIp(remoteIp) {
  try {
    const interfaces = require('os').networkInterfaces();
    const rParts = remoteIp.split('.');
    for (const name of Object.keys(interfaces)) {
      for (const net of interfaces[name]) {
        if (net.family === 'IPv4' && !net.internal) {
          const lParts = net.address.split('.');
          if (lParts[0] === rParts[0] && lParts[1] === rParts[1] && lParts[2] === rParts[2]) {
            return net.address;
          }
        }
      }
    }
  } catch (e) {}
  return null;
}

discoverySocket.on('message', (msg, rinfo) => {
  const message = msg.toString();
  if (message.startsWith('YEVAL_DISCOVER')) {
    let wifiAllowed = true;
    try {
      const cfgPath = path.join(userDataPath, 'config.json');
      const cfg = fs.existsSync(cfgPath) ? JSON.parse(fs.readFileSync(cfgPath, 'utf8')) : {};
      if (cfg.connectionRoute === 'usb_only' || cfg.enableWifi === false) {
        wifiAllowed = false;
      }
    } catch(e) {}
    if (!wifiAllowed) return; // Completely silence Wi-Fi discovery when disabled

    lastAndroidIp = rinfo.address;
    
    // Parse optional battery from "YEVAL_DISCOVER:<battery>"
    const parts = message.split(':');
    if (parts.length > 1) {
      const androidBattery = parseInt(parts[1]);
      if (!isNaN(androidBattery)) {
        sendToRenderer('device-battery-update', { ip: rinfo.address, battery: androidBattery });
      }
    }
    
    const hostname = require('os').hostname();
    getBatteryPercentage((pcBattery) => {
      // Format: YEVAL_SERVER : hostname : battery : slots/4 : UDP_PORT : HTTP_PORT : TCP_PORT : SERVER_ID
      const reply = Buffer.from(`YEVAL_SERVER:${hostname}:${pcBattery}:${activeSlots}/4:${dynamicUdpPort || 14567}:${dynamicHttpPort || 8080}:${dynamicTcpPort || 51230}:${serverId}`);
      const matchingLocalIp = findMatchingLocalIp(rinfo.address);
      const outSocket = (matchingLocalIp ? getOrCreateInterfaceSocket(matchingLocalIp) : null) || discoverySocket;
      
      outSocket.send(reply, 0, reply.length, rinfo.port, rinfo.address, (err) => {
        if (!err) {
          console.log(`[Discovery] Responded to ${rinfo.address}:${rinfo.port} from ${matchingLocalIp || '0.0.0.0'} with ${hostname} (PC Battery: ${pcBattery}%)`);
        }
      });
    });
  } else if (message.startsWith('YEVAL_DISCONNECT')) {
    const parts = message.split(':');
    const deviceId = parts.length > 1 ? parts[1] : null;
    console.log(`[Discovery] Received YEVAL_DISCONNECT from ${rinfo.address} (ID: ${deviceId})`);
    if (deviceId && backendProcess && backendProcess.stdin) {
      backendProcess.stdin.write(`KICK ${deviceId}\n`);
    }
    if (rinfo.address && backendProcess && backendProcess.stdin) {
      backendProcess.stdin.write(`KICK ${rinfo.address}\n`);
    }
    for (let ip in backendConnectedSlots) {
      if (ip === rinfo.address) {
        const freedSlot = backendConnectedSlots[ip];
        delete backendConnectedSlots[ip];
        sendToRenderer('device-slot-freed', { slot: freedSlot });
      }
    }
  }
});

function tryBindDiscovery() {
  discoverySocket.bind(currentDiscoveryPort, () => {
    discoverySocket.setBroadcast(true);
    console.log(`[Discovery] Listening on UDP port ${currentDiscoveryPort}`);
  });
}

discoverySocket.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    if (currentDiscoveryPort < 14578) {
      currentDiscoveryPort++;
      tryBindDiscovery();
    } else {
      console.error('[Discovery] Failed to bind any port in range 14568-14578');
    }
  }
});

tryBindDiscovery();

// Proactively broadcast PC presence every 2s for zero-config discovery over USB tethering and Wi-Fi
setInterval(() => {
  try {
    const cfgPath = path.join(userDataPath, 'config.json');
    const cfg = fs.existsSync(cfgPath) ? JSON.parse(fs.readFileSync(cfgPath, 'utf8')) : {};
    const route = cfg.connectionRoute || 'auto';
    const enableWifi = (route === 'auto' || route === 'wifi_only');
    const enableUsb = (route === 'auto' || route === 'usb_only');

    const hostname = require('os').hostname();
    getBatteryPercentage((pcBattery) => {
      const payload = Buffer.from(`YEVAL_SERVER:${hostname}:${pcBattery}:${activeSlots}/4:${dynamicUdpPort || 14567}:${dynamicHttpPort || 8080}:${dynamicTcpPort || 51230}:${serverId}`);
      
      // Standard broadcast (Wi-Fi only)
      if (enableWifi) {
        try {
          discoverySocket.send(payload, 0, payload.length, 14568, '255.255.255.255', (err) => {});
        } catch (e) {}
      }

      // Interface-specific subnet broadcasts (forces egress through specific NICs)
      try {
        const interfaces = require('os').networkInterfaces();
        for (const name of Object.keys(interfaces)) {
          const lowerName = name.toLowerCase();
          const isWifi = lowerName.includes('wi-fi') || lowerName.includes('wifi') || lowerName.includes('wlan') || lowerName.includes('wireless');
          
          if (isWifi && !enableWifi) continue;
          if (!isWifi && !enableUsb) continue; // Ethernet/tethering belongs to USB

          for (const net of interfaces[name]) {
            if (net.family === 'IPv4' && !net.internal) {
              const parts = net.address.split('.');
              if (parts.length === 4) {
                const subnetBroadcast = `${parts[0]}.${parts[1]}.${parts[2]}.255`;
                const sock = getOrCreateInterfaceSocket(net.address) || discoverySocket;
                sock.send(payload, 0, payload.length, 14568, subnetBroadcast, (err) => {});
              }
            }
          }
        }
      } catch (e) {}
    });
  } catch (e) {}
}, 2000);

ipcMain.on('reload-android', (event, { deviceId, slotId }) => {
  if (deviceId && slotId) {
    if (backendProcess && backendProcess.stdin) {
      backendProcess.stdin.write(`RELOAD_DEVICE ${deviceId} ${slotId}\n`);
    } else {
      console.error('[Reload] Backend process not running, cannot send reload command');
    }
  }
});

ipcMain.on('get-user-data-path', (event) => {
  event.returnValue = userDataPath;
});

const defaultXboxProfile = {
  id: "default-xbox",
  name: "Standard Xbox",
  layoutMode: "button",
  curveZones: true,
  leftStick: { id: "LS", x: 0.200, y: 0.556, scale: 1 },
  rightStick: { id: "RS", x: 0.675, y: 0.722, scale: 1 },
  triggers: [
    { id: "LT", x: 0.150, y: 0.111, scale: 1 },
    { id: "RT", x: 0.850, y: 0.111, scale: 1 }
  ],
  bumpers: [
    { id: "LB", x: 0.150, y: 0.222, scale: 1 },
    { id: "RB", x: 0.850, y: 0.222, scale: 1 }
  ],
  dpad: { x: 0.350, y: 0.778, scale: 1 },
  faceButtons: [
    { id: "Y", x: 0.825, y: 0.400, scale: 1 },
    { id: "X", x: 0.735, y: 0.520, scale: 1 },
    { id: "B", x: 0.915, y: 0.520, scale: 1 },
    { id: "A", x: 0.825, y: 0.640, scale: 1 }
  ],
  metaButtons: [
    { id: "BACK", x: 0.425, y: 0.167, scale: 1 },
    { id: "GUIDE", x: 0.500, y: 0.167, scale: 1 },
    { id: "START", x: 0.575, y: 0.167, scale: 1 }
  ],
  menuButton: { id: "MENU", x: 0.500, y: 0.056, scale: 1 },
  zones: [
    { buttonId: 'LT', vertices: [{x:0,y:0}, {x:200,y:0}, {x:200,y:75}, {x:0,y:75}] },
    { buttonId: 'LB', vertices: [{x:200,y:0}, {x:350,y:0}, {x:350,y:75}, {x:200,y:75}] },
    { buttonId: 'BACK', vertices: [{x:350,y:0}, {x:425,y:0}, {x:425,y:75}, {x:350,y:75}] },
    { buttonId: 'MENU', vertices: [{x:425,y:0}, {x:500,y:0}, {x:500,y:75}, {x:425,y:75}] },
    { buttonId: 'GUIDE', vertices: [{x:500,y:0}, {x:575,y:0}, {x:575,y:75}, {x:500,y:75}] },
    { buttonId: 'START', vertices: [{x:575,y:0}, {x:650,y:0}, {x:650,y:75}, {x:575,y:75}] },
    { buttonId: 'RB', vertices: [{x:650,y:0}, {x:800,y:0}, {x:800,y:75}, {x:650,y:75}] },
    { buttonId: 'RT', vertices: [{x:800,y:0}, {x:1000,y:0}, {x:1000,y:75}, {x:800,y:75}] },
    { buttonId: 'LS', vertices: [{x:0,y:75}, {x:500,y:75}, {x:500,y:275}, {x:300,y:275}, {x:0,y:325}] },
    { buttonId: 'DPAD', vertices: [{x:0,y:325}, {x:300,y:275}, {x:500,y:275}, {x:500,y:450}, {x:0,y:450}] },
    { buttonId: 'Y', vertices: [{x:675,y:75}, {x:1000,y:75}, {x:837.5,y:175}] },
    { buttonId: 'X', vertices: [{x:675,y:75}, {x:837.5,y:175}, {x:675,y:275}, {x:500,y:175}] },
    { buttonId: 'B', vertices: [{x:1000,y:75}, {x:1000,y:275}, {x:837.5,y:175}] },
    { buttonId: 'A', vertices: [{x:675,y:275}, {x:837.5,y:175}, {x:1000,y:275}, {x:837.5,y:325}] },
    { buttonId: 'RS', vertices: [{x:500,y:75}, {x:500,y:175}, {x:675,y:275}, {x:837.5,y:325}, {x:1000,y:275}, {x:1000,y:450}, {x:500,y:450}] }
  ]
};

const syncApp = express();
syncApp.use(cors());

syncApp.get('/profiles/:profileId', (req, res) => {
  let file = req.params.profileId;
  if (!file.endsWith('.json')) file += '.json';
  const filePath = path.join(profilesDir, file);
  if (fs.existsSync(filePath)) {
    return res.sendFile(filePath);
  }
  const prof = JSON.parse(JSON.stringify(defaultXboxProfile));
  const slotMatch = file.match(/slot-(\d)/);
  if (slotMatch) {
    prof.id = `slot-${slotMatch[1]}`;
    prof.name = `Player ${parseInt(slotMatch[1]) + 1}`;
  }
  res.json(prof);
});

syncApp.use('/profiles', express.static(profilesDir));
syncApp.get('/discovery', (req, res) => {
  try {
    const cfgPath = path.join(userDataPath, 'config.json');
    const cfg = fs.existsSync(cfgPath) ? JSON.parse(fs.readFileSync(cfgPath, 'utf8')) : {};
    if (cfg.enableUsb === false || cfg.connectionRoute === 'wifi_only') {
      return res.status(404).send('USB discovery disabled');
    }
  } catch(e) {}

  const hostname = require('os').hostname();
  getBatteryPercentage((battery) => {
    res.json({ hostname: hostname, battery: battery, slotsText: `${activeSlots}/4`, serverId: serverId });
  });
});

let backendConnectedSlots = {}; // ip -> slot
let deviceIdToIp = {}; // deviceId -> ip

  syncApp.get('/api/request-reload', (req, res) => {
  console.log(`[Sync Server] GET /api/request-reload from ${req.ip} (client=${req.query.client}, deviceId=${req.query.deviceId})`);
  let clientIp = req.query.client;
  
  if (!clientIp) {
      clientIp = req.ip;
      if (clientIp.includes('::ffff:')) clientIp = clientIp.split('::ffff:')[1];
      else if (clientIp === '::1') clientIp = '127.0.0.1';
  }
  
  console.log(`[Sync Server] Current backendConnectedSlots state:`, backendConnectedSlots);
  console.log(`[Sync Server] Current deviceIdToIp state:`, deviceIdToIp);
  
  let targetSlot;
  let reqDeviceId = req.query.deviceId ? String(req.query.deviceId).trim() : null;
  let unsignedDeviceId = reqDeviceId ? (Number(reqDeviceId) >>> 0).toString() : null;
  
  // 1. Primary bulletproof resolution via deviceId (supporting signed/unsigned matching)
  if (unsignedDeviceId && deviceIdToIp[unsignedDeviceId]) {
      let mappedIp = deviceIdToIp[unsignedDeviceId];
      targetSlot = backendConnectedSlots[mappedIp];
  } else if (reqDeviceId && deviceIdToIp[reqDeviceId]) {
      let mappedIp = deviceIdToIp[reqDeviceId];
      targetSlot = backendConnectedSlots[mappedIp];
  }
  
  // 2. Fallbacks via IP
  if (targetSlot === undefined && clientIp) targetSlot = backendConnectedSlots[clientIp];
  if (targetSlot === undefined && req.ip) targetSlot = backendConnectedSlots[req.ip];
  
  // 3. Fallbacks if single connection or USB
  if (targetSlot === undefined) {
      const keys = Object.keys(backendConnectedSlots);
      if (keys.length === 1) {
          targetSlot = backendConnectedSlots[keys[0]];
      } else if (backendConnectedSlots['USB'] !== undefined) {
          targetSlot = backendConnectedSlots['USB'];
      }
  }
  
  console.log(`[Sync Server] Resolving reload request to targetSlot ${targetSlot}`);
  
  sendToRenderer('force-profile-push', { ip: clientIp });
  
  if (targetSlot !== undefined) {
      res.json({ success: true, slotId: 'slot-' + targetSlot });
  } else {
      res.json({ success: true, debugConnectedSlots: backendConnectedSlots });
  }
});

syncApp.get('/api/disconnect', (req, res) => {
  let reqDeviceId = req.query.deviceId ? String(req.query.deviceId).trim() : null;
  console.log(`[Sync Server] GET /api/disconnect from ${req.ip} (deviceId=${reqDeviceId})`);
  if (reqDeviceId && backendProcess && backendProcess.stdin) {
    backendProcess.stdin.write(`KICK ${reqDeviceId}\n`);
  }
  if (req.ip && backendProcess && backendProcess.stdin) {
    let ip = req.ip;
    if (ip.includes('::ffff:')) ip = ip.split('::ffff:')[1];
    backendProcess.stdin.write(`KICK ${ip}\n`);
  }
  res.json({ success: true });
});

let dynamicUdpPort = 0;
let dynamicTcpPort = 0;
let dynamicHttpPort = 0;

const server = syncApp.listen(0, '0.0.0.0', () => {
  dynamicHttpPort = server.address().port;
  console.log(`[Sync Server] Hosting profiles and discovery on HTTP port ${dynamicHttpPort}`);
});

let tray = null;
let trayWindow = null;

ipcMain.handle('get-config', (event) => {
  try {
    if (fs.existsSync(configPath)) {
      return JSON.parse(fs.readFileSync(configPath, 'utf8'));
    }
  } catch (e) {}
  return {};
});

function initTrayWindow() {
  if (!trayWindow || trayWindow.isDestroyed()) {
    trayWindow = new BrowserWindow({
      width: 280,
      height: 380,
      show: false,
      frame: false,
      fullscreenable: false,
      resizable: false,
      transparent: true,
      backgroundColor: '#00000000',
      hasShadow: false,
      skipTaskbar: true,
      alwaysOnTop: true,
      webPreferences: {
        nodeIntegration: true,
        contextIsolation: false
      }
    });

    trayWindow.loadFile('tray-menu.html');

    trayWindow.on('blur', () => {
      if (trayWindow && !trayWindow.isDestroyed() && trayWindow.isVisible()) {
        trayWindow.hide();
      }
    });
  }
}

function createTray() {
  if (tray && !tray.isDestroyed()) return;
  const iconPath = path.join(__dirname, '../assets/Yeval.ico');
  try {
    tray = new Tray(iconPath);
    tray.setToolTip('Yeval Dashboard');

    tray.on('click', () => {
      restoreFromTray();
    });

    tray.on('right-click', () => {
      if (trayWindow && trayWindow.isVisible()) {
        trayWindow.hide();
        return;
      }

      if (trayWindow && !trayWindow.isDestroyed()) {
        const { x, y, width, height } = tray.getBounds();
        const { width: winWidth, height: winHeight } = trayWindow.getBounds();
        const primaryDisplay = screen.getPrimaryDisplay();
        
        let posX = Math.round(x + (width / 2) - (winWidth / 2));
        let posY = Math.round(y - winHeight - 6);
        
        if (y < primaryDisplay.workAreaSize.height / 2) {
          posY = Math.round(y + height + 6);
        }
        
        if (posX + winWidth > primaryDisplay.bounds.width) {
          posX = primaryDisplay.bounds.width - winWidth - 10;
        }
        if (posX < 0) posX = 10;

        trayWindow.setPosition(posX, posY, false);
        trayWindow.showInactive();
        setTimeout(() => {
          if (trayWindow && !trayWindow.isDestroyed() && trayWindow.isVisible()) {
            trayWindow.focus();
          }
        }, 50);
      }
    });
  } catch (e) {
    console.error('[Tray] Failed to create tray:', e);
  }
}

function restoreFromTray() {
  if (mainWindow && !mainWindow.isDestroyed()) {
    if (!mainWindow.isVisible()) {
      mainWindow.show();
    }
    if (mainWindow.isMinimized()) {
      mainWindow.restore();
    }
    mainWindow.focus();
  }
  if (trayWindow && !trayWindow.isDestroyed()) {
    trayWindow.hide();
  }
  if (tray && !tray.isDestroyed()) {
    tray.destroy();
    tray = null;
  }
}

ipcMain.on('tray-hide-window', () => {
  if (trayWindow && !trayWindow.isDestroyed()) trayWindow.hide();
});

ipcMain.on('tray-action-settings', () => {
  restoreFromTray();
});

ipcMain.on('tray-action-exit', () => {
  app.quit();
});

ipcMain.on('set-connection-route', (event, route) => {
  console.log(`[Config] Setting connection route: ${route}`);
  const enableWifi = (route === 'auto' || route === 'wifi_only');
  const enableUsb = (route === 'auto' || route === 'usb_only');

  try {
    const configPath = path.join(userDataPath, 'config.json');
    const config = fs.existsSync(configPath) ? JSON.parse(fs.readFileSync(configPath, 'utf8')) : {};
    config.connectionRoute = route;
    config.enableWifi = enableWifi;
    config.enableUsb = enableUsb;
    fs.writeFileSync(configPath, JSON.stringify(config));
  } catch (e) {
    console.error('Error saving connection route to config:', e);
  }

  // If Wi-Fi was disabled, broadcast a shutdown packet so mobile clients immediately drop the Wi-Fi route
  if (!enableWifi) {
    try {
      const shutdownPayload = Buffer.from(`YEVAL_SHUTDOWN:${serverId}`);
      discoverySocket.send(shutdownPayload, 0, shutdownPayload.length, 14568, '255.255.255.255', () => {});
    } catch(e) {}
  }

  if (!enableUsb) {
    const adbPath = process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk', 'platform-tools', 'adb.exe') : 'adb';
    exec(`"${adbPath}" reverse --remove tcp:14569 && "${adbPath}" reverse --remove tcp:8080`, () => {});
  } else {
    setupAdbTunnel();
  }

  if (backendProcess && backendProcess.stdin) {
    backendProcess.stdin.write(`TOGGLE wifi ${enableWifi ? '1' : '0'}\n`);
    backendProcess.stdin.write(`TOGGLE usb ${enableUsb ? '1' : '0'}\n`);
  }
});

ipcMain.on('tray-action-disconnect', () => {
  if (lastAndroidIp) {
    const cmd = Buffer.from('YEVAL_KICK');
    discoverySocket.send(cmd, 0, cmd.length, 14569, lastAndroidIp, (err) => {
      if (err) console.error('[Kick] Failed to send kick command', err);
      else console.log(`[Kick] Kicked ${lastAndroidIp}`);
    });
    
    // Also notify main window if needed
    if (mainWindow && !mainWindow.isDestroyed()) {
      // It handles its own watchdog timeout, but we can fast-track it if needed
    }
  }
});

ipcMain.on('tray-update-slots', (event, slotsData) => {
  if (trayWindow && !trayWindow.isDestroyed()) {
    trayWindow.webContents.send('tray-update-slots', slotsData);
  }
});

ipcMain.on('window-tray', () => {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.hide();
    createTray();
  }
});

ipcMain.on('window-min', () => {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.minimize();
  }
});

let isForceClosing = false;
ipcMain.on('window-close', () => {
  sendToRenderer('request-close-check');
});

ipcMain.on('force-close', () => {
  isForceClosing = true;
  app.quit();
});

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    backgroundColor: '#00000000', // Transparent
    transparent: true,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false // Simplifies IPC for this prototype
    },
    frame: false,
    autoHideMenuBar: true,
    icon: path.join(__dirname, '../assets/Yeval.svg')
  });

  mainWindow.loadFile('index.html');
}

app.whenReady().then(() => {
  createWindow();
  initTrayWindow();
  startBackend();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});
app.on('window-all-closed', () => {
  if (backendProcess) {
    backendProcess.kill();
  }
  if (process.platform !== 'darwin') app.quit();
});

app.on('before-quit', () => {
  try {
    const shutdownPayload = Buffer.from(`YEVAL_SHUTDOWN:${serverId}`);
    discoverySocket?.send(shutdownPayload, 0, shutdownPayload.length, 14568, '255.255.255.255', () => {});
    for (const [ip, sock] of interfaceSockets.entries()) {
      const parts = ip.split('.');
      if (parts.length === 4) {
        sock.send(shutdownPayload, 0, shutdownPayload.length, 14568, `${parts[0]}.${parts[1]}.${parts[2]}.255`, () => {});
      }
    }
  } catch (e) {}
  if (tray && !tray.isDestroyed()) {
    try { tray.destroy(); } catch(e) {}
    tray = null;
  }
  if (backendProcess) {
    backendProcess.kill();
  }
});


function startBackend() {
  if (backendProcess) return;

  const isPackaged = app.isPackaged;
  const backendPath = isPackaged 
    ? path.join(process.resourcesPath, 'backend/YevalMobileBackend.exe')
    : path.join(__dirname, '../windows/build/Release/MobileControllerBackend.exe');
  
  sendToRenderer('backend-log', `Starting backend at ${backendPath}...`);
  
  try {
    backendProcess = spawn(backendPath, { windowsHide: true });

    let stdoutBuffer = '';

    backendProcess.stdout.on('data', (data) => {
      const text = data.toString();
      console.log(`[Backend] ${text.trim()}`);
      require('fs').appendFileSync(path.join(__dirname, 'backend_debug.log'), `[Backend] ${text.trim()}\n`);
      stdoutBuffer += text;
      
      const lines = stdoutBuffer.split('\n');
      stdoutBuffer = lines.pop(); // keep the incomplete line in the buffer
      
      for (let line of lines) {
        line = line.replace('\r', '');
        sendToRenderer('backend-log', line);
        
        if (line.includes('Initialized virtual gamepad subsystem successfully')) {
          sendToRenderer('backend-status', 'ready');
          
          // Send initial route and toggle states to backend
          try {
            const cfgFile = path.join(userDataPath, 'config.json');
            const cfg = fs.existsSync(cfgFile) ? JSON.parse(fs.readFileSync(cfgFile, 'utf8')) : {};
            const route = cfg.connectionRoute || 'auto';
            const enableWifi = (route === 'auto' || route === 'wifi_only');
            const enableUsb = (route === 'auto' || route === 'usb_only');
            backendProcess.stdin.write(`TOGGLE wifi ${enableWifi ? '1' : '0'}\n`);
            backendProcess.stdin.write(`TOGGLE usb ${enableUsb ? '1' : '0'}\n`);
          } catch(e) {}
        }
        
        const sysMatch = line.match(/System occupies XInput slot (\d)/);
        if (sysMatch) {
            const slot = parseInt(sysMatch[1]);
            sendToRenderer('system-slot-occupied', { slot });
        }
        
        const udpMatch = line.match(/Listening on UDP port (\d+)/);
        if (udpMatch) {
            dynamicUdpPort = parseInt(udpMatch[1]);
        }
        
        const tcpMatch = line.match(/Listening for ADB forwarded TCP connections on 127\.0\.0\.1:(\d+)/);
        if (tcpMatch) {
            dynamicTcpPort = parseInt(tcpMatch[1]);
        }
        
        const ipMatch = line.match(/New device connected from ([\w\.]+) \(ID: (\d+)\)\. Assigned to slot (\d)/);
        if (ipMatch) {
            const ip = ipMatch[1];
            const deviceId = ipMatch[2];
            const slot = parseInt(ipMatch[3]);
            
            for (let k in backendConnectedSlots) {
                if (backendConnectedSlots[k] === slot) delete backendConnectedSlots[k];
            }
            backendConnectedSlots[ip] = slot;
            deviceIdToIp[deviceId] = ip;
            
            activeSlots = Math.min(4, activeSlots + 1);
            sendToRenderer('backend-status', 'ready');
            
            let connType = 'Network';
            if (ip === 'USB') {
                connType = 'USB';
            } else {
                // Detect USB tethering: check if the device IP is on a
                // non-Wi-Fi adapter's subnet (e.g. Ethernet adapter from tethering)
                try {
                    const os = require('os');
                    const interfaces = os.networkInterfaces();
                    const ipParts = ip.split('.').map(Number);
                    let onWifi = false;
                    let onEthernet = false;
                    for (const [name, addrs] of Object.entries(interfaces)) {
                        for (const addr of addrs) {
                            if (addr.family === 'IPv4' && !addr.internal) {
                                const localParts = addr.address.split('.').map(Number);
                                if (localParts[0] === ipParts[0] && localParts[1] === ipParts[1] && localParts[2] === ipParts[2]) {
                                    const lowerName = name.toLowerCase();
                                    if (lowerName.includes('wi-fi') || lowerName.includes('wifi') || lowerName.includes('wlan') || lowerName.includes('wireless')) {
                                        onWifi = true;
                                    } else {
                                        onEthernet = true;
                                    }
                                }
                            }
                        }
                    }
                    if (onEthernet && !onWifi) connType = 'USB';
                } catch(e) {}
            }
            
            sendToRenderer('device-slot-assigned', { ip, deviceId, slot, connType });
        }
        
        // Parse move: [Main] Moved device <IP> to slot X
        const moveMatch = line.match(/Moved device ([\w\.]+) to slot (\d)/);
        if (moveMatch) {
            const ip = moveMatch[1];
            const slot = parseInt(moveMatch[2]);
            
            for (let k in backendConnectedSlots) {
                if (backendConnectedSlots[k] === slot) delete backendConnectedSlots[k];
            }
            backendConnectedSlots[ip] = slot;
            
            sendToRenderer('device-slot-assigned', { ip, slot });
        }
        
        // Parse swap: [Main] Swapped device <IP1> to slot X and <IP2> to slot Y
        const swapMatch = line.match(/Swapped device ([\w\.]+) to slot (\d) and ([\w\.]+) to slot (\d)/);
        if (swapMatch) {
            const ip1 = swapMatch[1];
            const slot1 = parseInt(swapMatch[2]);
            const ip2 = swapMatch[3];
            const slot2 = parseInt(swapMatch[4]);
            
            backendConnectedSlots[ip1] = slot1;
            backendConnectedSlots[ip2] = slot2;
            
            sendToRenderer('device-slot-assigned', { ip: ip1, slot: slot1 });
            sendToRenderer('device-slot-assigned', { ip: ip2, slot: slot2 });
        }
        
        const switchMatch = line.match(/Device (\d+) (?:switched|failed over) to transport (\d)/);
        if (switchMatch) {
          const deviceId = switchMatch[1];
          const typeId = parseInt(switchMatch[2]);
          let connType = 'Unknown';
          if (typeId === 1) connType = 'Network';
          if (typeId === 2) connType = 'USB';
          sendToRenderer('device-transport-switched', { deviceId, connType });
        }

        const availMatch = line.match(/Device (\d+) available transports: (\d+)/);
        if (availMatch) {
          const deviceId = availMatch[1];
          const mask = parseInt(availMatch[2]);
          let available = [];
          if (mask & (1 << 1)) available.push('Network');
          if (mask & (1 << 2)) available.push('USB');
          sendToRenderer('device-transports-available', { deviceId, available });
        }
        
        const batteryMatch = line.match(/Device ([\w\.]+) \(ID: (\d+)\) battery updated: (\d+)%/);
        if (batteryMatch) {
            const ip = batteryMatch[1];
            const deviceId = batteryMatch[2];
            const battery = parseInt(batteryMatch[3]);
            sendToRenderer('device-battery-update', { ip, deviceId, battery });
        }
        
        const statusMatch = line.match(/Device (\d+) status: (active|reconnecting)/);
        if (statusMatch) {
            const deviceId = statusMatch[1];
            const status = statusMatch[2];
            sendToRenderer('device-status-change', { deviceId, status });
        }
        
        // Parse timeout or disconnect: Connection timeout / Transport disconnected for slot X
        if (line.includes('Disconnecting controller')) {
            activeSlots = Math.max(0, activeSlots - 1);
            sendToRenderer('backend-status', 'watchdog');
            
            const slotMatch = line.match(/slot (\d)/);
            if (slotMatch) {
                const slot = parseInt(slotMatch[1]);
                for (let k in backendConnectedSlots) {
                    if (backendConnectedSlots[k] === slot) delete backendConnectedSlots[k];
                }
                sendToRenderer('device-slot-freed', { slot });
            }
        }
        
        // Check for missing ViGEmBus kernel driver
        if (line.includes('Failed to connect to bus driver') || line.includes('Ensure ViGEmBus is installed')) {
            sendToRenderer('backend-vigem-missing');
        }
      }
    });

    backendProcess.stderr.on('data', (data) => {
      const text = data.toString();
      console.error(`[Backend ERROR] ${text.trim()}`);
      require('fs').appendFileSync(path.join(__dirname, 'backend_debug.log'), `[Backend ERROR] ${text.trim()}\n`);
      sendToRenderer('backend-log', `ERROR: ${text.trim()}`);

      if (text.includes('Failed to connect to bus driver') || text.includes('Ensure ViGEmBus is installed')) {
        sendToRenderer('backend-vigem-missing');
      }
    });

    backendProcess.on('close', (code) => {
      console.log(`[Backend] Exited with code ${code}`);
      sendToRenderer('backend-log', `Backend process exited with code ${code}.`);
      sendToRenderer('backend-status', 'stopped');
      backendProcess = null;
      // Auto-restart after 3 seconds if not deliberately killed
      setTimeout(startBackend, 3000);
    });
    
  } catch (err) {
    console.error(`[Backend Spawn Error] ${err.message}`);
    sendToRenderer('backend-log', `Failed to start backend: ${err.message}`);
    backendProcess = null;
    setTimeout(startBackend, 5000); // Retry later
  }
}

ipcMain.handle('install-vigem-driver', async () => {
  try {
    const isPackaged = app.isPackaged;
    const bundledInstaller = isPackaged
      ? path.join(process.resourcesPath, 'prereqs/ViGEmBusSetup.exe')
      : path.join(__dirname, 'prereqs/ViGEmBusSetup.exe');

    let targetInstaller = bundledInstaller;

    if (!fs.existsSync(bundledInstaller)) {
      const https = require('https');
      const tempInstaller = path.join(app.getPath('temp'), 'ViGEmBusSetup.exe');
      const file = fs.createWriteStream(tempInstaller);
      
      await new Promise((resolve, reject) => {
        https.get('https://github.com/nefarius/ViGEmBus/releases/download/v1.22.0/ViGEmBus_1.22.0_x64_x86_arm64.exe', response => {
          if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
            https.get(response.headers.location, redirectResp => {
              redirectResp.pipe(file);
              file.on('finish', () => { file.close(resolve); });
            }).on('error', reject);
          } else {
            response.pipe(file);
            file.on('finish', () => { file.close(resolve); });
          }
        }).on('error', reject);
      });
      targetInstaller = tempInstaller;
    }

    const { shell } = require('electron');
    await shell.openPath(targetInstaller);
    return { success: true };
  } catch (err) {
    console.error('[ViGEm Install Error]', err);
    return { success: false, error: err.message };
  }
});

ipcMain.handle('restart-backend', async () => {
  if (backendProcess) {
    try { backendProcess.kill(); } catch(e) {}
    backendProcess = null;
  }
  setTimeout(() => {
    startBackend();
  }, 1000);
  return { success: true };
});

// Set up ADB reverse port forwarding for the USB connection
// We run this periodically so that if a device is plugged in later, the tunnel is established.
function setupAdbTunnel() {
  if (!dynamicTcpPort || !dynamicHttpPort) {
      setTimeout(setupAdbTunnel, 2000);
      return;
  }
  
  try {
    const cfgPath = path.join(userDataPath, 'config.json');
    const cfg = fs.existsSync(cfgPath) ? JSON.parse(fs.readFileSync(cfgPath, 'utf8')) : {};
    if (cfg.enableUsb === false) {
      setTimeout(setupAdbTunnel, 5000);
      return; // Skip setting up tunnel if USB is disabled
    }
  } catch (e) {}

  const adbPath = process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk', 'platform-tools', 'adb.exe') : 'adb';
  
  exec(`"${adbPath}" reverse --list`, (error, stdout, stderr) => {
    if (!error && stdout && stdout.includes(`tcp:14569 tcp:${dynamicTcpPort}`) && stdout.includes(`tcp:8080 tcp:${dynamicHttpPort}`)) {
      // Tunnel is already established
      setTimeout(setupAdbTunnel, 5000);
      return;
    }
    
    // Set up both Gamepad State tunnel (14569) and Discovery/Profile Tunnel (8080)
    exec(`"${adbPath}" reverse tcp:14569 tcp:${dynamicTcpPort} && "${adbPath}" reverse tcp:8080 tcp:${dynamicHttpPort}`, (err, out, std) => {
      if (err && !err.message.includes('no devices/emulators found')) {
        // console.error(`[ADB Tunnel Error] ${err.message}`);
      }
      setTimeout(setupAdbTunnel, 5000);
    });
  });
}
setupAdbTunnel();

ipcMain.on('reload-android', (event, { deviceId, slotId }) => {
  console.log(`[Reload] Pushing reload for ${slotId} to device ${deviceId}`);
  const reloadPayload = Buffer.from(`YEVAL_RELOAD:${slotId}`);
  
  // 1. Broadcast over UDP 14569 (reload listener port on phone)
  try { discoverySocket.send(reloadPayload, 0, reloadPayload.length, 14569, '255.255.255.255', () => {}); } catch (e) {}

  // 2. Send to all interface subnets
  const interfaces = require('os').networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name]) {
      if (net.family === 'IPv4' && !net.internal) {
        const parts = net.address.split('.');
        if (parts.length === 4) {
          const subnetBroadcast = `${parts[0]}.${parts[1]}.${parts[2]}.255`;
          const sock = getOrCreateInterfaceSocket(net.address) || discoverySocket;
          try { sock.send(reloadPayload, 0, reloadPayload.length, 14569, subnetBroadcast, () => {}); } catch (e) {}
        }
      }
    }
  }

  // 3. Send directly to all known connected device IPs
  for (let ip in backendConnectedSlots) {
    if (ip !== 'USB' && ip !== '127.0.0.1' && ip !== 'Bluetooth' && ip !== 'USB/BT') {
      try { discoverySocket.send(reloadPayload, 0, reloadPayload.length, 14569, ip, () => {}); } catch (e) {}
    }
  }

  // 4. Send via ADB local forward (for USB connections)
  try { discoverySocket.send(reloadPayload, 0, reloadPayload.length, 14569, '127.0.0.1', () => {}); } catch (e) {}
});

ipcMain.on('kick-device', (event, ip) => {
  console.log(`Kicking device: ${ip}`);
  
  if (backendProcess && backendProcess.stdin) {
    backendProcess.stdin.write(`KICK ${ip}\n`);
  }
  
  if (ip === 'USB' || ip === 'Bluetooth' || ip === 'USB/BT') {
    return; // Cannot send UDP kick to these pseudonyms
  }
  
  // Send kick command to Android app on port 14569
  const cmd = Buffer.from('YEVAL_KICK');
  discoverySocket.send(cmd, 0, cmd.length, 14569, ip, (err) => {
    if (err) console.error('[Kick] Failed to send kick command', err);
    else console.log(`[Kick] Sent kick command to ${ip}:14569`);
  });
});

ipcMain.on('set-release-mode', (event, mode) => {
  console.log(`[Config] Setting slot release mode: ${mode}`);
  let timeoutMs = 15000;
  let isAuto = true;
  if (mode === 'instant') {
    timeoutMs = 0;
    isAuto = true;
  } else if (mode === 'auto_15') {
    timeoutMs = 15000;
    isAuto = true;
  } else if (mode === 'auto_30') {
    timeoutMs = 30000;
    isAuto = true;
  } else if (mode === 'manual') {
    timeoutMs = 0;
    isAuto = false;
  }
  if (backendProcess && backendProcess.stdin) {
    backendProcess.stdin.write(`CONFIG release_mode ${isAuto ? 'auto' : 'manual'} ${timeoutMs}\n`);
  }
});

ipcMain.on('move-device', (event, { ip, newSlot }) => {
    for (let k in backendConnectedSlots) {
        if (backendConnectedSlots[k] === newSlot) delete backendConnectedSlots[k];
    }
    backendConnectedSlots[ip] = newSlot;
    if (backendProcess && backendProcess.stdin) {
        backendProcess.stdin.write(`MOVE ${ip} ${newSlot}\n`);
    }
});

ipcMain.on('swap-devices', (event, { ip1, slot1, ip2, slot2 }) => {
    backendConnectedSlots[ip1] = slot1;
    backendConnectedSlots[ip2] = slot2;
    if (backendProcess && backendProcess.stdin) {
        backendProcess.stdin.write(`SWAP ${ip1} ${slot1} ${ip2} ${slot2}\n`);
    }
});

function broadcastShutdown() {
  try {
    const payload = Buffer.from(`YEVAL_SHUTDOWN:${serverId}`);
    try { discoverySocket.send(payload, 0, payload.length, 14568, '255.255.255.255', () => {}); } catch (e) {}
    try { discoverySocket.send(payload, 0, payload.length, 14569, '255.255.255.255', () => {}); } catch (e) {}

    const interfaces = require('os').networkInterfaces();
    for (const name of Object.keys(interfaces)) {
      for (const net of interfaces[name]) {
        if (net.family === 'IPv4' && !net.internal) {
          const parts = net.address.split('.');
          if (parts.length === 4) {
            const subnetBroadcast = `${parts[0]}.${parts[1]}.${parts[2]}.255`;
            const sock = getOrCreateInterfaceSocket(net.address) || discoverySocket;
            try { sock.send(payload, 0, payload.length, 14568, subnetBroadcast, () => {}); } catch (e) {}
            try { sock.send(payload, 0, payload.length, 14569, subnetBroadcast, () => {}); } catch (e) {}
          }
        }
      }
    }

    for (let ip in backendConnectedSlots) {
      if (ip !== 'USB' && ip !== '127.0.0.1') {
        try { discoverySocket.send(payload, 0, payload.length, 14568, ip, () => {}); } catch (e) {}
        try { discoverySocket.send(payload, 0, payload.length, 14569, ip, () => {}); } catch (e) {}
      }
    }
  } catch (e) {}
}

app.on('before-quit', () => {
  broadcastShutdown();
  if (backendProcess) {
    try { backendProcess.kill(); } catch (e) {}
    backendProcess = null;
  }
});

