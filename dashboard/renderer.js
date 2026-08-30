const { ipcRenderer } = require('electron');
const fs = require('fs');
const path = require('path');

const logContainer = document.getElementById('logContainer');
const statusText = document.getElementById('statusText');

function appendLog(message) {
  const div = document.createElement('div');
  div.textContent = `> ${message}`;
  logContainer.appendChild(div);
  // Auto-scroll to bottom
  logContainer.scrollTop = logContainer.scrollHeight;
}

// ==========================================
// SYSTEM TOGGLES
// ==========================================
async function initToggles() {
  const toggleWifi = document.getElementById('toggleWifi');
  const toggleUsb = document.getElementById('toggleUsb');
}
initToggles();

// ==========================================
// DEVICE MANAGER STATE
// ==========================================
let knownDevices = {};
let connectedSlots = [null, null, null, null]; // Holds IP string or null for each of the 4 slots
const systemOccupiedSlots = new Set();
const deviceListContainer = document.getElementById('deviceListContainer');
const deviceCountTitle = document.getElementById('deviceCountTitle');

window.getDeviceIdForSlot = function(slotStr) {
    if (!slotStr || !slotStr.startsWith('slot-')) return null;
    const slotIdx = parseInt(slotStr.split('-')[1]);
    const ip = connectedSlots[slotIdx];
    if (ip && knownDevices[ip]) {
        return knownDevices[ip].deviceId;
    }
    return null;
};
ipcRenderer.on('device-discovered', (event, data) => {
  const ip = data.ip || 'Unknown IP';
  const connectionType = 'Network';
  const battery = data.battery !== undefined ? data.battery : 100;
  const name = 'Android Phone';

  let shouldRender = false;
  if (!knownDevices[ip]) {
    knownDevices[ip] = { ip, name, connectionType, battery, status: 'active', lastSeen: Date.now() };
    shouldRender = true;
  } else {
    if (knownDevices[ip].status !== 'active') {
      knownDevices[ip].status = 'active';
      shouldRender = true;
    }
    if (knownDevices[ip].battery !== battery) {
      knownDevices[ip].battery = battery;
      shouldRender = true;
    }
    knownDevices[ip].lastSeen = Date.now();
  }
  if (shouldRender) {
    renderDeviceList();
  }
});

ipcRenderer.on('device-slot-assigned', (event, data) => {
    const { ip, deviceId, slot, connType } = data;
    if (slot >= 0 && slot < 4) {
        let changed = false;
        for (let i = 0; i < 4; i++) {
            if (i !== slot && connectedSlots[i] === ip) {
                connectedSlots[i] = null;
                changed = true;
            }
        }
        if (connectedSlots[slot] !== ip) {
            connectedSlots[slot] = ip;
            changed = true;
        }
        
        if (!knownDevices[ip]) {
            knownDevices[ip] = { ip, name: 'Android Phone' };
            changed = true;
        }
        if (knownDevices[ip].status !== 'active') {
            knownDevices[ip].status = 'active';
            changed = true;
        }
        if (connType && knownDevices[ip].connectionType !== connType) {
            knownDevices[ip].connectionType = connType;
            changed = true;
        }
        if (deviceId !== undefined && deviceId !== null) knownDevices[ip].deviceId = deviceId;
        knownDevices[ip].lastSeen = Date.now();
        
        if (changed) {
            renderDeviceList();
        }
        
        // Push the assigned slot layout to the newly connected/assigned device
        if (window.pushProfileToSlot) {
            setTimeout(() => window.pushProfileToSlot(slot), 300);
        } else if (window.generateSlotProfile) {
            setTimeout(() => window.generateSlotProfile(slot), 300);
        }
    }
});

ipcRenderer.on('force-profile-push', (event, data) => {
    const { ip } = data;
    let targetSlot = -1;
    for (let i = 0; i < 4; i++) {
        if (connectedSlots[i] === ip) {
            targetSlot = i;
            break;
        }
        // If request came from localhost (ADB reverse), it corresponds to the USB slot
        if ((ip === '127.0.0.1' || ip === 'localhost') && (connectedSlots[i] === 'USB')) {
            targetSlot = i;
            break;
        }
    }
    if (targetSlot !== -1 && window.pushProfileToSlot) {
        window.pushProfileToSlot(targetSlot);
    }
});

ipcRenderer.on('device-battery-update', (event, data) => {
    const { ip, deviceId, battery } = data;
    let targetIp = ip;
    
    // If we got a battery update via USB, we might only know the deviceId
    if (ip === "USB") {
        let found = false;
        if (knownDevices[ip] && knownDevices[ip].deviceId == deviceId) {
            targetIp = ip;
            found = true;
        }
        if (!found) {
            for (let k in knownDevices) {
                if (knownDevices[k].deviceId == deviceId) {
                    targetIp = k;
                    break;
                }
            }
        }
    }
    
    if (knownDevices[targetIp]) {
        const battChanged = (knownDevices[targetIp].battery !== battery);
        const statusChanged = (knownDevices[targetIp].status !== 'active');
        knownDevices[targetIp].status = 'active';
        knownDevices[targetIp].battery = battery;
        knownDevices[targetIp].lastSeen = Date.now();
        if (battChanged || statusChanged) {
            renderDeviceList();
        }
    }
});

ipcRenderer.on('device-transport-switched', (event, data) => {
    const { deviceId, connType } = data;
    let changed = false;
    for (let ip in knownDevices) {
        if (knownDevices[ip].deviceId == deviceId) {
            if (knownDevices[ip].connectionType !== connType || knownDevices[ip].status !== 'active') {
                knownDevices[ip].status = 'active';
                knownDevices[ip].connectionType = connType;
                changed = true;
            }
            knownDevices[ip].lastSeen = Date.now();
            break;
        }
    }
    if (changed) {
        renderDeviceList();
    }
});

ipcRenderer.on('device-transports-available', (event, data) => {
  const { deviceId, available } = data;
  let updated = false;
  for (let ip in knownDevices) {
    if (knownDevices[ip].deviceId == deviceId) {
      const prev = (knownDevices[ip].availableTransports || []).join(',');
      const curr = (available || []).join(',');
      if (prev !== curr || knownDevices[ip].status !== 'active') {
        knownDevices[ip].status = 'active';
        knownDevices[ip].availableTransports = available;
        updated = true;
      }
      knownDevices[ip].lastSeen = Date.now();
    }
  }
  if (updated) {
    renderDeviceList();
  }
});

ipcRenderer.on('system-slot-occupied', (event, data) => {
    const { slot } = data;
    if (slot >= 0 && slot < 4) {
        systemOccupiedSlots.add(slot);
        renderDeviceList();
    }
});

ipcRenderer.on('device-status-change', (event, data) => {
  const { deviceId, status } = data;
  let updated = false;
  for (let ip in knownDevices) {
    if (knownDevices[ip].deviceId == deviceId) {
      if (knownDevices[ip].status !== status) {
        knownDevices[ip].status = status;
        updated = true;
      }
    }
  }
  if (updated) renderDeviceList();
});

ipcRenderer.on('device-slot-freed', (event, data) => {
    const { slot } = data;
    if (slot >= 0 && slot < 4) {
        if (connectedSlots[slot] !== null) {
            connectedSlots[slot] = null;
            renderDeviceList();
        }
    }
});

// Clean up stale devices
setInterval(() => {
  const now = Date.now();
  let changed = false;
  for (let i = 0; i < 4; i++) {
      const ip = connectedSlots[i];
      if (ip && knownDevices[ip] && (now - knownDevices[ip].lastSeen) > 5000) {
          // Handled by C++ watchdog
      }
  }
}, 2000);

let draggedSlotIndex = null;

window.positionCustomDropdown = function(btn, dropdownList) {
  if (!btn || !dropdownList) return;
  dropdownList.style.top = '';
  dropdownList.style.bottom = '';
  dropdownList.style.maxHeight = '';
  dropdownList.style.overflowY = '';
  
  const btnRect = btn.getBoundingClientRect();
  const windowHeight = window.innerHeight;
  const padding = 16;
  
  const spaceBelow = windowHeight - btnRect.bottom - padding;
  const spaceAbove = btnRect.top - padding;
  
  const wasHidden = !dropdownList.classList.contains('show');
  if (wasHidden) {
    dropdownList.style.visibility = 'hidden';
    dropdownList.style.display = 'flex';
  }
  const dropdownHeight = dropdownList.offsetHeight || dropdownList.scrollHeight || 180;
  if (wasHidden) {
    dropdownList.style.display = '';
    dropdownList.style.visibility = '';
  }
  
  if (spaceBelow < dropdownHeight && spaceAbove > spaceBelow) {
    dropdownList.style.top = 'auto';
    dropdownList.style.bottom = 'calc(100% + 6px)';
    if (dropdownHeight > spaceAbove) {
      dropdownList.style.maxHeight = Math.max(120, spaceAbove - 8) + 'px';
      dropdownList.style.overflowY = 'auto';
    }
  } else {
    dropdownList.style.top = 'calc(100% + 6px)';
    dropdownList.style.bottom = 'auto';
    if (dropdownHeight > spaceBelow) {
      dropdownList.style.maxHeight = Math.max(120, spaceBelow - 8) + 'px';
      dropdownList.style.overflowY = 'auto';
    }
  }
};

function createSlotProfileControl(slotIndex) {
    const wrap = document.createElement('div');
    wrap.className = 'device-slot-profile';

    const curProfId = (window.slotProfiles && window.slotProfiles[slotIndex]) ? window.slotProfiles[slotIndex] : 'default';
    const curMode = (window.slotModes && window.slotModes[slotIndex]) ? window.slotModes[slotIndex] : 'button';
    const curProf = (window.profiles && window.profiles[curProfId]) ? window.profiles[curProfId] : null;
    const profDisplayName = curProf?.name || (curProfId === 'default' ? 'Default' : curProfId);

    // Dropdown wrapper
    const selectWrap = document.createElement('div');
    selectWrap.className = 'custom-select';

    const selectBtn = document.createElement('div');
    selectBtn.className = 'custom-select-btn';

    const selectText = document.createElement('span');
    selectText.className = 'custom-select-text';
    selectText.textContent = profDisplayName;
    selectText.style.overflow = 'hidden';
    selectText.style.textOverflow = 'ellipsis';
    selectText.style.whiteSpace = 'nowrap';

    const chevronSvg = document.createElement('div');
    chevronSvg.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-left: 8px; flex-shrink: 0;"><polyline points="6 9 12 15 18 9"></polyline></svg>';

    selectBtn.appendChild(selectText);
    selectBtn.appendChild(chevronSvg.firstChild);
    selectWrap.appendChild(selectBtn);

    const dropdownList = document.createElement('div');
    dropdownList.className = 'custom-select-dropdown';

    if (window.profiles) {
        Object.keys(window.profiles).forEach(pId => {
            const opt = document.createElement('div');
            opt.className = 'custom-select-option' + (pId === curProfId ? ' selected' : '');
            opt.textContent = window.profiles[pId]?.name || (pId === 'default' ? 'Default' : pId);
            opt.addEventListener('click', (e) => {
                e.stopPropagation();
                if (window.slotProfiles) window.slotProfiles[slotIndex] = pId;
                if (window.slotModes && window.slotModes[slotIndex] === 'zone' && window.profiles[pId]) {
                    if (typeof window.checkProfileZoneComplete === 'function' && !window.checkProfileZoneComplete(window.profiles[pId])) {
                        window.slotModes[slotIndex] = 'button';
                    }
                }
                if (typeof window.saveSlotManager === 'function') window.saveSlotManager();
                if (typeof window.pushProfileToSlot === 'function') window.pushProfileToSlot(slotIndex);
                renderDeviceList();
            });
            dropdownList.appendChild(opt);
        });
    }

    selectWrap.addEventListener('click', (e) => {
        e.stopPropagation();
        const isShowing = dropdownList.classList.contains('show');
        document.querySelectorAll('.custom-select-dropdown.show').forEach(el => {
            if (el !== dropdownList) el.classList.remove('show');
        });
        if (!isShowing) {
            window.positionCustomDropdown(selectBtn, dropdownList);
            dropdownList.classList.add('show');
        } else {
            dropdownList.classList.remove('show');
        }
    });
    selectWrap.appendChild(dropdownList);

    // Mode Toggle
    const canZone = curProf && typeof window.checkProfileZoneComplete === 'function' ? window.checkProfileZoneComplete(curProf) : false;
    const toggleWrap = document.createElement('div');
    toggleWrap.className = 'slot-mode-toggle';

    const btnBtn = document.createElement('div');
    btnBtn.className = 'slot-mode-btn' + (curMode === 'button' ? ' active' : '');
    btnBtn.title = 'Button Layout Mode';
    btnBtn.innerHTML = '<span style="display: inline-block; width: 14px; height: 14px; background-color: currentColor; -webkit-mask-image: url(\'../assets/gamepad.svg\'); -webkit-mask-size: contain; -webkit-mask-repeat: no-repeat; -webkit-mask-position: center;"></span>';
    btnBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        if (window.slotModes && window.slotModes[slotIndex] !== 'button') {
            window.slotModes[slotIndex] = 'button';
            if (typeof window.saveSlotManager === 'function') window.saveSlotManager();
            if (typeof window.pushProfileToSlot === 'function') window.pushProfileToSlot(slotIndex);
            renderDeviceList();
        }
    });

    const btnZone = document.createElement('div');
    btnZone.className = 'slot-mode-btn' + (curMode === 'zone' ? ' active' : '');
    btnZone.title = canZone ? 'Zone Layout Mode' : 'Zone Layout Incomplete (Assign all buttons in Editor)';
    btnZone.style.opacity = canZone ? '1' : '0.35';
    btnZone.style.cursor = canZone ? 'pointer' : 'not-allowed';
    btnZone.innerHTML = '<span style="display: inline-block; width: 14px; height: 14px; background-color: currentColor; -webkit-mask-image: url(\'../assets/layout.svg\'); -webkit-mask-size: contain; -webkit-mask-repeat: no-repeat; -webkit-mask-position: center;"></span>';
    if (canZone) {
        btnZone.addEventListener('click', (e) => {
            e.stopPropagation();
            if (window.slotModes && window.slotModes[slotIndex] !== 'zone') {
                window.slotModes[slotIndex] = 'zone';
                if (typeof window.saveSlotManager === 'function') window.saveSlotManager();
                if (typeof window.pushProfileToSlot === 'function') window.pushProfileToSlot(slotIndex);
                renderDeviceList();
            }
        });
    }

    toggleWrap.appendChild(btnBtn);
    toggleWrap.appendChild(btnZone);

    wrap.appendChild(selectWrap);
    wrap.appendChild(toggleWrap);
    return wrap;
}

function renderDeviceList() {
  if (!deviceListContainer) return;
  
  deviceListContainer.innerHTML = '';
  
  for (let i = 0; i < 4; i++) {
      const ip = connectedSlots[i];
      const device = ip ? knownDevices[ip] : null;
      
      const row = document.createElement('div');
      row.className = 'device-row';
      row.dataset.slot = i;
      
      if (systemOccupiedSlots.has(i)) {
          row.classList.add('system-slot');
          row.innerHTML = `
              <div class="slot-badge system">P${i + 1}</div>
              <div class="device-info">
                  <div class="device-name" style="color: #f97316; font-weight: 600;">System Controller</div>
                  <div class="device-meta">
                      <span style="color: rgba(249, 115, 22, 0.8);">Physical Controller / Used by Windows</span>
                  </div>
              </div>
          `;
          row.appendChild(createSlotProfileControl(i));
          
          const dragWrap = document.createElement('div');
          dragWrap.className = 'drag-handle';
          dragWrap.title = 'Locked';
          dragWrap.style.cssText = 'opacity: 0.15; cursor: not-allowed;';
          dragWrap.innerHTML = fs.readFileSync(path.join(__dirname, '../assets/drag.svg'), 'utf8').replace(/stroke="#000000"/gi, 'stroke="currentColor"').replace(/width="800px" height="800px"/gi, 'width="18" height="18"');
          row.appendChild(dragWrap);
          
          const btnDis = document.createElement('button');
          btnDis.className = 'btn-disconnect';
          btnDis.disabled = true;
          btnDis.title = 'Disconnect';
          btnDis.innerHTML = fs.readFileSync(path.join(__dirname, '../assets/exit.svg'), 'utf8').replace(/stroke="#000000"/gi, 'stroke="currentColor"').replace(/fill="#000000"/gi, 'fill="currentColor"').replace(/width="800px" height="800px"/gi, 'width="14" height="14"');
          row.appendChild(btnDis);
      } else if (device) {
          row.draggable = true;
          const isReconnecting = device.status === 'reconnecting';
          if (isReconnecting) {
              row.classList.add('reconnecting');
          }
          const batteryVal = device.battery !== undefined ? device.battery : 100;
          const isLowBattery = batteryVal <= 20;
          
          row.innerHTML = `
              <div class="slot-badge ${isReconnecting ? 'reconnecting' : ''}">P${i + 1}</div>
              <div class="device-info">
                  <div class="device-name">${device.name}</div>
                  <div class="device-meta">
                ${isReconnecting ? `
                  <span class="reconnecting-badge">
                    <span class="pulse-dot"></span>
                    Reconnecting...
                  </span>
                ` : `
                  <span class="battery-badge ${isLowBattery ? 'low' : ''}">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="2" y="7" width="16" height="10" rx="2" ry="2"></rect>
                      <line x1="22" y1="11" x2="22" y2="13"></line>
                      ${batteryVal > 30 ? '<line x1="6" y1="10" x2="6" y2="14"></line>' : ''}
                      ${batteryVal > 60 ? '<line x1="10" y1="10" x2="10" y2="14"></line>' : ''}
                      ${batteryVal > 90 ? '<line x1="14" y1="10" x2="14" y2="14"></line>' : ''}
                    </svg>
                    ${batteryVal}%
                  </span>
                `}
                
                ${(() => {
                  const available = device.availableTransports || [device.connectionType || 'Network'];
                  const active = device.connectionType || 'Network';
                  
                  const getBadge = (type, label, svg) => {
                    if (!available.includes(type)) return '';
                    const isActive = (active === type);
                    const stateClass = isActive ? 'active' : 'available';
                    const roleText = isActive ? 'Active' : 'Fallback';
                    return `<span class="connection-badge ${stateClass}" title="${label} (${roleText})">${svg}<span class="badge-label">${label}</span></span>`;
                  };
                  
                  const wifiRaw = fs.readFileSync(path.join(__dirname, '../assets/wifi.svg'), 'utf8');
                  const usbRaw = fs.readFileSync(path.join(__dirname, '../assets/usb.svg'), 'utf8');
                  
                  const processSvg = (svg) => svg.replace(/fill="#000000"/gi, 'fill="currentColor"').replace(/stroke="#000000"/gi, 'stroke="currentColor"').replace(/width="800px" height="800px"/gi, 'width="12" height="12"');
                  
                  const wifiSvg = processSvg(wifiRaw);
                  const usbSvg = processSvg(usbRaw);
                  
                  return getBadge('Network', 'Wi-Fi', wifiSvg) + getBadge('USB', 'USB', usbSvg);
                })()}
              </div>
              </div>
          `;
          
          row.appendChild(createSlotProfileControl(i));

          const dragWrap = document.createElement('div');
          dragWrap.className = 'drag-handle';
          dragWrap.title = 'Drag to reorder';
          dragWrap.innerHTML = fs.readFileSync(path.join(__dirname, '../assets/drag.svg'), 'utf8').replace(/stroke="#000000"/gi, 'stroke="currentColor"').replace(/width="800px" height="800px"/gi, 'width="18" height="18"');
          row.appendChild(dragWrap);

          const btnDisconnect = document.createElement('button');
          btnDisconnect.className = 'btn-disconnect';
          btnDisconnect.dataset.ip = device.ip;
          btnDisconnect.title = 'Disconnect';
          btnDisconnect.innerHTML = fs.readFileSync(path.join(__dirname, '../assets/exit.svg'), 'utf8').replace(/stroke="#000000"/gi, 'stroke="currentColor"').replace(/fill="#000000"/gi, 'fill="currentColor"').replace(/width="800px" height="800px"/gi, 'width="14" height="14"');
          row.appendChild(btnDisconnect);
          
          row.addEventListener('dragstart', (e) => {
              draggedSlotIndex = i;
              row.classList.add('dragging');
              e.dataTransfer.effectAllowed = 'move';
          });
          
          row.addEventListener('dragend', () => {
              draggedSlotIndex = null;
              row.classList.remove('dragging');
              document.querySelectorAll('.device-row').forEach(r => r.classList.remove('drag-over'));
          });
          
          btnDisconnect.addEventListener('click', () => {
              ipcRenderer.send('kick-device', device.ip);
              connectedSlots[i] = null;
              renderDeviceList();
          });
      } else {
          row.classList.add('empty-slot');
          row.innerHTML = `
              <div class="slot-badge empty">P${i + 1}</div>
              <div class="device-info">
                  <div class="device-name" style="color: var(--text-2); font-weight: 500;">No device connected</div>
              </div>
          `;
          
          row.appendChild(createSlotProfileControl(i));

          const dragWrap = document.createElement('div');
          dragWrap.className = 'drag-handle';
          dragWrap.title = 'Empty slot';
          dragWrap.style.cssText = 'opacity: 0.1; cursor: default;';
          dragWrap.innerHTML = `
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="8" y1="6" x2="21" y2="6"></line>
                  <line x1="8" y1="12" x2="21" y2="12"></line>
                  <line x1="8" y1="18" x2="21" y2="18"></line>
                  <line x1="3" y1="6" x2="3.01" y2="6"></line>
                  <line x1="3" y1="12" x2="3.01" y2="12"></line>
                  <line x1="3" y1="18" x2="3.01" y2="18"></line>
              </svg>
          `;
          row.appendChild(dragWrap);

          const btnDisconnect = document.createElement('button');
          btnDisconnect.className = 'btn-disconnect';
          btnDisconnect.disabled = true;
          btnDisconnect.title = 'Disconnect';
          btnDisconnect.innerHTML = fs.readFileSync(path.join(__dirname, '../assets/exit.svg'), 'utf8').replace(/stroke="#000000"/gi, 'stroke="currentColor"').replace(/fill="#000000"/gi, 'fill="currentColor"').replace(/width="800px" height="800px"/gi, 'width="14" height="14"');
          row.appendChild(btnDisconnect);
      }
      
      // Allow dropping on both populated and empty slots
      if (!systemOccupiedSlots.has(i)) {
          row.addEventListener('dragover', (e) => {
              e.preventDefault();
              if (draggedSlotIndex === null || draggedSlotIndex === i) return;
              row.classList.add('drag-over');
          });
          
          row.addEventListener('dragleave', () => {
              row.classList.remove('drag-over');
          });
          
          row.addEventListener('drop', (e) => {
              e.preventDefault();
              row.classList.remove('drag-over');
              if (draggedSlotIndex === null || draggedSlotIndex === i) return;
              
              const draggedIp = connectedSlots[draggedSlotIndex];
              if (!draggedIp) return;
              
              const targetIp = connectedSlots[i];
              
              if (targetIp) {
                  // Swap: move target to dragged's old slot, then move dragged to target's slot
                  // Update frontend immediately for responsiveness
                  connectedSlots[draggedSlotIndex] = targetIp;
                  connectedSlots[i] = draggedIp;
                  renderDeviceList();
                  
                  ipcRenderer.send('swap-devices', { ip1: draggedIp, slot1: draggedSlotIndex, ip2: targetIp, slot2: i });
                  if (window.pushProfileToSlot) {
                      setTimeout(() => {
                          window.pushProfileToSlot(draggedSlotIndex);
                          window.pushProfileToSlot(i);
                      }, 250);
                  }
              } else {
                  // Simple move to empty slot
                  connectedSlots[draggedSlotIndex] = null;
                  connectedSlots[i] = draggedIp;
                  renderDeviceList();
                  ipcRenderer.send('move-device', { ip: draggedIp, newSlot: i });
                  if (window.pushProfileToSlot) {
                      setTimeout(() => window.pushProfileToSlot(i), 250);
                  }
              }
          });
      }
      
      deviceListContainer.appendChild(row);
  }
  
  // Send state to main process to update Tray Menu
  const slotsData = connectedSlots.map(ip => ip ? knownDevices[ip] : null);
  ipcRenderer.send('tray-update-slots', slotsData);
}

// IPC Event Listeners (from main process)
ipcRenderer.on('backend-log', (event, message) => {
  appendLog(message);
});

ipcRenderer.on('backend-status', (event, status) => {
  if (status === 'ready') {
    statusText.style.color = 'var(--green)';
    statusText.textContent = 'Ready';
    const modal = document.getElementById('vigemDriverModal');
    if (modal) modal.style.display = 'none';
  } else if (status === 'watchdog') {
    statusText.style.color = 'var(--orange)';
    statusText.textContent = 'Standby';
  } else if (status === 'stopped') {
    statusText.style.color = 'var(--text-2)';
    statusText.textContent = 'Offline';
  }
});

// ViGEm Driver Missing UI
ipcRenderer.on('backend-vigem-missing', () => {
  const modal = document.getElementById('vigemDriverModal');
  if (modal) modal.style.display = 'flex';
});

const installVigemBtn = document.getElementById('installVigemBtn');
const dismissVigemBtn = document.getElementById('dismissVigemBtn');
const vigemStatusMsg = document.getElementById('vigemStatusMsg');

if (installVigemBtn) {
  installVigemBtn.addEventListener('click', async () => {
    installVigemBtn.disabled = true;
    installVigemBtn.textContent = 'Launching Installer...';
    if (vigemStatusMsg) {
      vigemStatusMsg.style.display = 'block';
      vigemStatusMsg.textContent = 'Please complete the ViGEmBus setup wizard on screen.';
    }
    
    try {
      const res = await ipcRenderer.invoke('install-vigem-driver');
      if (res && res.success) {
        installVigemBtn.textContent = 'Waiting for driver...';
        // Check and restart backend periodically
        const checkInterval = setInterval(async () => {
          await ipcRenderer.invoke('restart-backend');
        }, 4000);
        setTimeout(() => clearInterval(checkInterval), 60000);
      } else {
        installVigemBtn.disabled = false;
        installVigemBtn.textContent = 'Retry Install';
        if (vigemStatusMsg) vigemStatusMsg.textContent = 'Failed to launch installer: ' + (res?.error || 'Unknown error');
      }
    } catch(err) {
      installVigemBtn.disabled = false;
      installVigemBtn.textContent = 'Retry Install';
      if (vigemStatusMsg) vigemStatusMsg.textContent = err.message;
    }
  });
}

if (dismissVigemBtn) {
  dismissVigemBtn.addEventListener('click', () => {
    const modal = document.getElementById('vigemDriverModal');
    if (modal) modal.style.display = 'none';
  });
}

// ==========================================
// PANDORA UI LOGIC
// ==========================================

// Window Controls
const trayBtn = document.getElementById('tray-btn');
if (trayBtn) {
  trayBtn.addEventListener('click', () => {
    ipcRenderer.send('window-tray');
  });
}
document.getElementById('min-btn').addEventListener('click', () => {
  ipcRenderer.send('window-min');
});
document.getElementById('close-btn').addEventListener('click', () => {
  ipcRenderer.send('window-close');
});

// Sidebar Toggle
const sidebar = document.getElementById('sidebar');
const toggleBtn = document.getElementById('toggle-sidebar');
if (toggleBtn && sidebar) {
  toggleBtn.addEventListener('click', () => {
    sidebar.classList.toggle('expanded');
  });
}

// Tab Switching
const navItems = document.querySelectorAll('.nav-item');
const tabPages = document.querySelectorAll('.tab-page');
const pillText = document.getElementById('pill-text');
const pillDot = document.querySelector('.pill-dot');
const pagePill = document.getElementById('page-pill');

if (pillDot) {
  pillDot.style.background = 'var(--accent-general)';
}

navItems.forEach(item => {
  item.addEventListener('click', () => {
    // Remove active from all nav items and hide all pages
    navItems.forEach(n => n.classList.remove('active'));
    tabPages.forEach(p => p.classList.remove('active'));

    // Set clicked as active
    item.classList.add('active');
    
    // Show corresponding page
    const targetId = item.getAttribute('data-tab');
    document.getElementById(`tab-${targetId}`).classList.add('active');

    // Update Top Pill & Global Accent Variable
    const label = item.getAttribute('data-label');
    const color = item.getAttribute('data-color');
    document.documentElement.style.setProperty('--accent', color);
    if (pillText) pillText.textContent = label;
    if (pillDot) pillDot.style.background = color;
    if (pagePill) pagePill.style.color = color;
    
    if (targetId === 'halo' && typeof window.updateEditorPlayerBadges === 'function') {
      window.updateEditorPlayerBadges();
    }
  });
});

// Initial render to show empty slots
renderDeviceList();

// Slot Release Mode custom dropdown
const releaseModeDropdown = document.getElementById('releaseModeDropdown');
const releaseModeDropdownBtn = document.getElementById('releaseModeDropdownBtn');
const releaseModeDropdownText = document.getElementById('releaseModeDropdownText');
const releaseModeDropdownList = document.getElementById('releaseModeDropdownList');

if (releaseModeDropdownBtn && releaseModeDropdownList) {
  const savedMode = localStorage.getItem('slotReleaseMode') || 'auto_15';
  
  const labels = {
    'instant': 'Instant Release',
    'auto_15': 'Auto-Release (15s)',
    'auto_30': 'Auto-Release (30s)',
    'manual': 'Manual Hold'
  };
  
  if (labels[savedMode] && releaseModeDropdownText) {
    releaseModeDropdownText.textContent = labels[savedMode];
  }
  
  releaseModeDropdownList.querySelectorAll('.custom-select-option').forEach(opt => {
    opt.classList.toggle('selected', opt.dataset.value === savedMode);
  });
  
  ipcRenderer.send('set-release-mode', savedMode);

  releaseModeDropdownBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    const isShowing = releaseModeDropdownList.classList.contains('show');
    document.querySelectorAll('.custom-select-dropdown.show').forEach(d => {
      if (d !== releaseModeDropdownList) d.classList.remove('show');
    });
    if (!isShowing) {
      window.positionCustomDropdown(releaseModeDropdownBtn, releaseModeDropdownList);
      releaseModeDropdownList.classList.add('show');
    } else {
      releaseModeDropdownList.classList.remove('show');
    }
  });

  releaseModeDropdownList.querySelectorAll('.custom-select-option').forEach(opt => {
    opt.addEventListener('click', (e) => {
      e.stopPropagation();
      const mode = opt.dataset.value;
      if (releaseModeDropdownText) releaseModeDropdownText.textContent = opt.textContent;
      
      releaseModeDropdownList.querySelectorAll('.custom-select-option').forEach(o => o.classList.remove('selected'));
      opt.classList.add('selected');
      releaseModeDropdownList.classList.remove('show');
      
      localStorage.setItem('slotReleaseMode', mode);
      ipcRenderer.send('set-release-mode', mode);
    });
  });

  window.addEventListener('click', () => {
    releaseModeDropdownList.classList.remove('show');
  });
}

// Connection Route custom dropdown
const connectionRouteDropdown = document.getElementById('connectionRouteDropdown');
const connectionRouteDropdownBtn = document.getElementById('connectionRouteDropdownBtn');
const connectionRouteDropdownText = document.getElementById('connectionRouteDropdownText');
const connectionRouteDropdownList = document.getElementById('connectionRouteDropdownList');

if (connectionRouteDropdownBtn && connectionRouteDropdownList) {
  const savedRoute = localStorage.getItem('connectionRoute') || 'auto';
  
  const routeLabels = {
    'auto': 'Auto (Seamless Failover)',
    'usb_only': 'Wired Only (USB)',
    'wifi_only': 'Wireless Only (Wi-Fi)'
  };
  
  if (routeLabels[savedRoute] && connectionRouteDropdownText) {
    connectionRouteDropdownText.textContent = routeLabels[savedRoute];
  }
  
  connectionRouteDropdownList.querySelectorAll('.custom-select-option').forEach(opt => {
    opt.classList.toggle('selected', opt.dataset.value === savedRoute);
  });
  
  ipcRenderer.send('set-connection-route', savedRoute);

  connectionRouteDropdownBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    const isShowing = connectionRouteDropdownList.classList.contains('show');
    document.querySelectorAll('.custom-select-dropdown.show').forEach(d => {
      if (d !== connectionRouteDropdownList) d.classList.remove('show');
    });
    if (!isShowing) {
      window.positionCustomDropdown(connectionRouteDropdownBtn, connectionRouteDropdownList);
      connectionRouteDropdownList.classList.add('show');
    } else {
      connectionRouteDropdownList.classList.remove('show');
    }
  });

  connectionRouteDropdownList.querySelectorAll('.custom-select-option').forEach(opt => {
    opt.addEventListener('click', (e) => {
      e.stopPropagation();
      const newRoute = opt.dataset.value;
      const prevRoute = localStorage.getItem('connectionRoute') || 'auto';
      if (newRoute === prevRoute) {
        connectionRouteDropdownList.classList.remove('show');
        return;
      }

      // Check if any active player will lose their connection
      const activeDevices = Object.values(knownDevices);
      let willDisconnect = false;
      let impactedPlayerName = '';

      for (const dev of activeDevices) {
        if (newRoute === 'usb_only' && dev.connType !== 'USB') {
          willDisconnect = true;
          impactedPlayerName = dev.name || 'a player';
          break;
        }
        if (newRoute === 'wifi_only' && dev.connType === 'USB') {
          willDisconnect = true;
          impactedPlayerName = dev.name || 'a player';
          break;
        }
      }

      const applyRouteChange = () => {
        if (connectionRouteDropdownText) connectionRouteDropdownText.textContent = opt.textContent;
        connectionRouteDropdownList.querySelectorAll('.custom-select-option').forEach(o => o.classList.remove('selected'));
        opt.classList.add('selected');
        connectionRouteDropdownList.classList.remove('show');
        localStorage.setItem('connectionRoute', newRoute);
        ipcRenderer.send('set-connection-route', newRoute);
      };

      if (willDisconnect && typeof window.showConfirm === 'function') {
        connectionRouteDropdownList.classList.remove('show');
        const targetName = newRoute === 'usb_only' ? 'Wired Only (USB)' : 'Wireless Only (Wi-Fi)';
        window.showConfirm(
          `Switching to "${targetName}" will disable the active connection for ${impactedPlayerName}. Proceed?`,
          applyRouteChange
        );
      } else {
        applyRouteChange();
      }
    });
  });

  window.addEventListener('click', () => {
    connectionRouteDropdownList.classList.remove('show');
  });
}

/* =========================================
   UNIVERSAL CUSTOM TOOLTIP SYSTEM
   ========================================= */
function initCustomTooltips() {
    let tooltipEl = document.getElementById('custom-tooltip-bubble');
    if (!tooltipEl) {
        tooltipEl = document.createElement('div');
        tooltipEl.id = 'custom-tooltip-bubble';
        document.body.appendChild(tooltipEl);
    }

    let activeTarget = null;
    let showTimer = null;

    function showTip(el, text) {
        if (!text || !text.trim()) return;
        activeTarget = el;
        tooltipEl.textContent = text.trim();

        const rect = el.getBoundingClientRect();
        tooltipEl.style.display = 'block';
        tooltipEl.classList.remove('show');
        
        const tipRect = tooltipEl.getBoundingClientRect();

        let top = rect.top - tipRect.height - 8;
        let left = rect.left + rect.width / 2;

        // If too close to top of viewport, flip to bottom
        if (top < 8) {
            top = rect.bottom + 8;
        }

        // Clamp horizontally so it doesn't overflow screen
        const halfWidth = tipRect.width / 2;
        if (left - halfWidth < 8) {
            left = halfWidth + 8;
        } else if (left + halfWidth > window.innerWidth - 8) {
            left = window.innerWidth - 8 - halfWidth;
        }

        tooltipEl.style.top = `${top}px`;
        tooltipEl.style.left = `${left}px`;
        
        if (activeTarget === el) {
            tooltipEl.classList.add('show');
        }
    }

    function hideTip() {
        if (showTimer) {
            clearTimeout(showTimer);
            showTimer = null;
        }
        activeTarget = null;
        tooltipEl.classList.remove('show');
        setTimeout(() => {
            if (!activeTarget) tooltipEl.style.display = 'none';
        }, 150);
    }

    document.addEventListener('mouseover', (e) => {
        const target = e.target.closest('[title], [data-tooltip]');
        if (!target) {
            hideTip();
            return;
        }

        // If already tracking this target, don't restart timer
        if (activeTarget === target) return;

        // Transfer title to data-tooltip to suppress native OS tooltip
        if (target.hasAttribute('title')) {
            const titleVal = target.getAttribute('title');
            if (titleVal) {
                target.setAttribute('data-tooltip', titleVal);
            }
            target.removeAttribute('title');
        }

        const tipText = target.getAttribute('data-tooltip');
        if (tipText) {
            if (showTimer) {
                clearTimeout(showTimer);
                showTimer = null;
            }
            activeTarget = target;
            showTimer = setTimeout(() => {
                if (activeTarget === target) {
                    showTip(target, tipText);
                }
            }, 1250);
        } else {
            hideTip();
        }
    }, true);

    document.addEventListener('mouseout', (e) => {
        if (activeTarget && (e.target === activeTarget || activeTarget.contains(e.target))) {
            const related = e.relatedTarget;
            if (!related || !activeTarget.contains(related)) {
                hideTip();
            }
        }
    }, true);

    document.addEventListener('mousedown', hideTip, true);
    window.addEventListener('scroll', hideTip, true);
    window.addEventListener('blur', hideTip);
}

// Global auto-close for all custom dropdowns on click outside or blur
document.addEventListener('click', (e) => {
    if (!e.target.closest('.custom-select')) {
        document.querySelectorAll('.custom-select-dropdown.show').forEach(d => {
            d.classList.remove('show');
        });
    }
});
window.addEventListener('blur', () => {
    document.querySelectorAll('.custom-select-dropdown.show').forEach(d => {
        d.classList.remove('show');
    });
});

if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initCustomTooltips);
    } else {
        initCustomTooltips();
    }
}

