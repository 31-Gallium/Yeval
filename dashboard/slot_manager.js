const fs_slot = require('fs');
const path_slot = require('path');
const { ipcRenderer: ipc_slot } = require('electron');

let slotProfiles = ['default', 'default', 'default', 'default'];
let slotModes = ['button', 'button', 'button', 'button'];

function initSlotManager() {
    const userDataPath = ipc_slot.sendSync('get-user-data-path');
    const p = path_slot.join(userDataPath, 'slot_state.json');
    if (fs_slot.existsSync(p)) {
        try {
            const data = JSON.parse(fs_slot.readFileSync(p, 'utf8'));
            if (data.profiles) slotProfiles = data.profiles;
            if (data.modes) slotModes = data.modes;
        } catch(e){}
    }
    // Keep window refs in sync after reassignment
    window.slotProfiles = slotProfiles;
    window.slotModes = slotModes;
    
    // Ensure all 4 slots have their slot-X.json written to disk with default layout
    for (let i = 0; i < 4; i++) {
        generateSlotProfile(i);
    }
    
    renderSlotCards();
}

function saveSlotManager() {
    const userDataPath = ipc_slot.sendSync('get-user-data-path');
    const p = path_slot.join(userDataPath, 'slot_state.json');
    fs_slot.writeFileSync(p, JSON.stringify({ profiles: slotProfiles, modes: slotModes }));
}

function renderSlotCards() {
    // Validate: reset any slot pointing to a deleted profile
    let needsSave = false;
    for (let i = 0; i < 4; i++) {
        if (window.profiles && !window.profiles[slotProfiles[i]]) {
            slotProfiles[i] = 'default';
            slotModes[i] = 'button';
            needsSave = true;
        }
    }
    if (needsSave) saveSlotManager();
    
    if (typeof renderDeviceList === 'function') {
        renderDeviceList();
    }
    if (typeof window.updateEditorPlayerBadges === 'function') {
        window.updateEditorPlayerBadges();
    }
}

function checkProfileZoneComplete(prof) {
    if (!prof.zones) return false;
    if (typeof ALL_ZONE_BUTTONS === 'undefined') return false; 
    const mapped = prof.zones.map(z => z.buttonId);
    return ALL_ZONE_BUTTONS.every(b => mapped.includes(b));
}

function generateSlotProfile(slotIndex) {
    const profId = slotProfiles[slotIndex] || 'default';
    const mode = slotModes[slotIndex] || 'button';
    let prof = (window.profiles && window.profiles[profId]) ? window.profiles[profId] : (window.profiles ? window.profiles['default'] : null);
    
    // If window.profiles isn't populated yet, try loading from disk
    if (!prof) {
        try {
            const userDataPath = ipc_slot.sendSync('get-user-data-path');
            const targetFile = (profId === 'default' || profId === 'default-xbox') ? 'default-xbox.json' : `${profId}.json`;
            const diskPath = path_slot.join(userDataPath, 'profiles', targetFile);
            if (fs_slot.existsSync(diskPath)) {
                prof = JSON.parse(fs_slot.readFileSync(diskPath, 'utf8'));
            }
        } catch(e) {}
    }

    if (prof) {
        const slotJson = JSON.parse(JSON.stringify(prof));
        slotJson.layoutMode = mode;
        
        if (!slotJson.id) slotJson.id = profId === 'default' ? 'default-xbox' : profId;
        if (!slotJson.name) slotJson.name = profId === 'default' ? 'Default' : profId.charAt(0).toUpperCase() + profId.slice(1);
        if (!slotJson.type) slotJson.type = 'standard';
        
        const userDataPath = ipc_slot.sendSync('get-user-data-path');
        const p = path_slot.join(userDataPath, 'profiles', 'slot-' + slotIndex + '.json');
        fs_slot.writeFileSync(p, JSON.stringify(slotJson, null, 2));
        console.log(`[SlotManager] Saved slot-${slotIndex}.json with profile ${profId} (mode: ${mode})`);
    } else {
        console.warn(`[SlotManager] Could not find profile for id: ${profId}`);
    }
}

function pushProfileToSlot(slotIndex) {
    generateSlotProfile(slotIndex);
    
    let deviceId = null;
    if (window.getDeviceIdForSlot) {
        deviceId = window.getDeviceIdForSlot('slot-' + slotIndex);
    }
    
    if (deviceId) {
        console.log(`[SlotManager] Sending reload-android for slot-${slotIndex} to device ${deviceId}`);
        ipc_slot.send('reload-android', { deviceId, slotId: 'slot-' + slotIndex });
    } else {
        console.warn(`[SlotManager] Cannot send reload-android for slot-${slotIndex}: deviceId is null`);
    }
}

window.renderSlotCards = renderSlotCards;
window.initSlotManager = initSlotManager;
window.slotProfiles = slotProfiles;
window.slotModes = slotModes;
window.generateSlotProfile = generateSlotProfile;
window.pushProfileToSlot = pushProfileToSlot;
window.saveSlotManager = saveSlotManager;
window.checkProfileZoneComplete = checkProfileZoneComplete;

// Auto-initialize immediately on startup
if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            initSlotManager();
        });
    } else {
        initSlotManager();
    }
}
