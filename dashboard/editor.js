let zones = [];
let hovered = null;
let dragging = null;
let selectedButton = null;
let dragOff = { dx: 0, dy: 0 };
let profiles = {};
window.profiles = profiles;

const canvas = document.getElementById('editorCanvas');
const ctx = canvas.getContext('2d');

const imgPlay = new Image(); imgPlay.src = '../assets/play.svg';
const imgXbox = new Image(); imgXbox.src = '../assets/xbox.svg';
const imgView = new Image(); imgView.src = '../assets/view.svg';
const imgYeval = new Image(); imgYeval.src = '../assets/yeval_mini.svg';
let imgsLoaded = 0;
const checkLoad = () => { imgsLoaded++; if (imgsLoaded === 4 && typeof render === 'function') render(); };
imgPlay.onload = checkLoad;
imgXbox.onload = checkLoad;
imgView.onload = checkLoad;
imgYeval.onload = checkLoad;

// ── High-DPI setup — Canvas maps to 1080×2400 phone in landscape (20:9) ──
const dpr = window.devicePixelRatio || 1;
// Internal drawing size — 20:9 aspect ratio matching a 2400×1080 phone in landscape
const W = 1000, H = 450;
canvas.width = W * dpr;
canvas.height = H * dpr;
canvas.style.width = '100%';
ctx.scale(dpr, dpr);

// ── Grid — snappable ──
const GRID = 25; // 25px snap grid (exact 40 cols x 18 rows on 1000x450 canvas)
function snap(v, total) {
  const px = v * total;
  return Math.round(px / GRID) * GRID / total;
}

// ── Colors ──
const COL = {
  grid:       'rgba(255, 255, 255, 0.28)',
  gridMajor:  'rgba(255, 255, 255, 0.55)',
  guide:      'rgba(255, 255, 255, 0.22)',
  trigFill:   'rgba(255, 255, 255, 0.05)',
  trigStroke: 'rgba(255, 255, 255, 0.28)',
  bumpFill:   'rgba(255, 255, 255, 0.05)',
  bumpStroke: 'rgba(255, 255, 255, 0.28)',
  dpadFill:   'rgba(255, 255, 255, 0.05)',
  dpadStroke: 'rgba(255, 255, 255, 0.28)',
  dpadArrow:  'rgba(255, 255, 255, 0.92)',
  stickRing:  'rgba(255, 255, 255, 0.20)',
  stickCross: 'rgba(255, 255, 255, 0.25)',
  stickCap:   'rgba(34, 38, 50, 0.90)',
  stickCapHi: 'rgba(52, 60, 78, 0.95)',
  stickLabel: 'rgba(255, 255, 255, 0.70)',
  metaFill:   'rgba(255, 255, 255, 0.07)',
  metaStroke: 'rgba(255, 255, 255, 0.28)',
  // Face button ring colors
  faceA: { stroke: '#10b981', text: '#34d399', glow: 'rgba(16, 185, 129, 0.35)', fill: 'rgba(16, 185, 129, 0.08)' },
  faceB: { stroke: '#ef4444', text: '#f87171', glow: 'rgba(239, 68, 68, 0.35)', fill: 'rgba(239, 68, 68, 0.08)' },
  faceX: { stroke: '#0ea5e9', text: '#38bdf8', glow: 'rgba(14, 165, 233, 0.35)', fill: 'rgba(14, 165, 233, 0.08)' },
  faceY: { stroke: '#f59e0b', text: '#fbbf24', glow: 'rgba(245, 158, 11, 0.35)', fill: 'rgba(245, 158, 11, 0.08)' },
};

// ── Standardized sizes (in canvas px) — Calibrated with Delta Profile as 1.0x Midpoint ──
const SIZE = {
  FACE_R:    46,   // Scaled face action button radius (92px diameter)
  TRIG_W:    230,  // Trigger width (natural 2.5:1 ratio)
  TRIG_H:    92,   // Trigger height
  BUMP_W:    230,  // Bumper width (natural 2.5:1 ratio)
  BUMP_H:    92,   // Bumper height
  META_R:    28,   // Standard mini meta/menu button radius (56px diameter)
  STICK_R:   104,  // Analog stick outer ring radius (208px base)
  DPAD_SIZE: 76,   // D-Pad arm length (168px diameter)
};

// ── Layout Template (Exact Delta Profile Layout as Default) ──
const defaultLayoutTemplate = {
  sticks: {
    left:  { id: 'LS', x: 0.125, y: 0.6667, scale: 1.0 },
    right: { id: 'RS', x: 0.625, y: 0.7778, scale: 1.0 }
  },
  triggers: [
    { id: 'LT', x: 0.125, y: 0.1111, scale: 1.0 },
    { id: 'RT', x: 0.875, y: 0.1111, scale: 1.0 }
  ],
  bumpers: [
    { id: 'LB', x: 0.175, y: 0.3333, scale: 1.0 },
    { id: 'RB', x: 0.825, y: 0.3333, scale: 1.0 }
  ],
  dpad: { x: 0.375, y: 0.7778, scale: 1.0 },
  faceButtons: [
    { id: 'Y', x: 0.875, y: 0.5556, scale: 1.0 },
    { id: 'X', x: 0.800, y: 0.7222, scale: 1.0 },
    { id: 'B', x: 0.950, y: 0.7222, scale: 1.0 },
    { id: 'A', x: 0.875, y: 0.8889, scale: 1.0 }
  ],
  metaButtons: [
    { id: 'BACK',  x: 0.425, y: 0.1667, scale: 1.0 },
    { id: 'GUIDE', x: 0.500, y: 0.2778, scale: 1.0 },
    { id: 'START', x: 0.575, y: 0.1667, scale: 1.0 }
  ],
  menuButton: { id: 'MENU', x: 0.500, y: 0.1111, scale: 1.0 },
  layoutMode: 'button',
  curveZones: true,
  rawZones: [
    { buttonId: 'LT', vertices: [{ x: 0, y: 0 }, { x: 200, y: 0 }, { x: 200, y: 150 }, { x: 0, y: 75 }] },
    { buttonId: 'LB', vertices: [{ x: 200, y: 0 }, { x: 425, y: 0 }, { x: 400, y: 75 }, { x: 200, y: 150 }] },
    { buttonId: 'START', vertices: [{ x: 425, y: 0 }, { x: 400, y: 75 }, { x: 500, y: 75 }, { x: 500, y: 0 }] },
    { buttonId: 'BACK', vertices: [{ x: 500, y: 0 }, { x: 500, y: 75 }, { x: 600, y: 75 }, { x: 575, y: 0 }] },
    { buttonId: 'MENU', vertices: [{ x: 400, y: 75 }, { x: 425, y: 125 }, { x: 500, y: 125 }, { x: 500, y: 75 }] },
    { buttonId: 'GUIDE', vertices: [{ x: 500, y: 75 }, { x: 600, y: 75 }, { x: 575, y: 125 }, { x: 500, y: 125 }] },
    { buttonId: 'RB', vertices: [{ x: 575, y: 0 }, { x: 800, y: 0 }, { x: 800, y: 150 }, { x: 600, y: 75 }] },
    { buttonId: 'RT', vertices: [{ x: 800, y: 0 }, { x: 1000, y: 0 }, { x: 1000, y: 75 }, { x: 800, y: 150 }] },
    { buttonId: 'DPAD', vertices: [{ x: 250, y: 175 }, { x: 0, y: 175 }, { x: 0, y: 425 }, { x: 250, y: 425 }] },
    { buttonId: 'LS', vertices: [{ x: 475, y: 175 }, { x: 500, y: 200 }, { x: 500, y: 400 }, { x: 475, y: 425 }, { x: 275, y: 425 }, { x: 250, y: 400 }, { x: 250, y: 200 }, { x: 275, y: 175 }] },
    { buttonId: 'RS', vertices: [{ x: 500, y: 200 }, { x: 525, y: 175 }, { x: 725, y: 175 }, { x: 750, y: 200 }, { x: 750, y: 400 }, { x: 725, y: 425 }, { x: 525, y: 425 }, { x: 500, y: 400 }] },
    { buttonId: 'X', vertices: [{ x: 875, y: 300 }, { x: 750, y: 175 }, { x: 750, y: 425 }] },
    { buttonId: 'Y', vertices: [{ x: 1000, y: 175 }, { x: 950, y: 200 }, { x: 900, y: 250 }, { x: 875, y: 300 }, { x: 750, y: 175 }] },
    { buttonId: 'A', vertices: [{ x: 1000, y: 425 }, { x: 950, y: 400 }, { x: 900, y: 350 }, { x: 875, y: 300 }, { x: 750, y: 425 }] },
    { buttonId: 'B', vertices: [{ x: 1000, y: 175 }, { x: 950, y: 200 }, { x: 900, y: 250 }, { x: 875, y: 300 }, { x: 900, y: 350 }, { x: 950, y: 400 }, { x: 1000, y: 425 }] }
  ],
  zones: [
    { buttonId: 'LT', vertices: [{ x: 0, y: 0 }, { x: 200, y: 0 }, { x: 200, y: 150 }, { x: 0, y: 75 }] },
    { buttonId: 'LB', vertices: [{ x: 200, y: 0 }, { x: 425, y: 0 }, { x: 400, y: 75 }, { x: 200, y: 150 }] },
    { buttonId: 'START', vertices: [{ x: 425, y: 0 }, { x: 400, y: 75 }, { x: 500, y: 75 }, { x: 500, y: 0 }] },
    { buttonId: 'BACK', vertices: [{ x: 500, y: 0 }, { x: 500, y: 75 }, { x: 600, y: 75 }, { x: 575, y: 0 }] },
    { buttonId: 'MENU', vertices: [{ x: 400, y: 75 }, { x: 425, y: 125 }, { x: 500, y: 125 }, { x: 500, y: 75 }] },
    { buttonId: 'GUIDE', vertices: [{ x: 500, y: 75 }, { x: 600, y: 75 }, { x: 575, y: 125 }, { x: 500, y: 125 }] },
    { buttonId: 'RB', vertices: [{ x: 575, y: 0 }, { x: 800, y: 0 }, { x: 800, y: 150 }, { x: 600, y: 75 }] },
    { buttonId: 'RT', vertices: [{ x: 800, y: 0 }, { x: 1000, y: 0 }, { x: 1000, y: 75 }, { x: 800, y: 150 }] },
    { buttonId: 'DPAD', vertices: [{ x: 250, y: 175 }, { x: 0, y: 175 }, { x: 0, y: 425 }, { x: 250, y: 425 }] },
    { buttonId: 'LS', vertices: [{ x: 475, y: 175 }, { x: 500, y: 200 }, { x: 500, y: 400 }, { x: 475, y: 425 }, { x: 275, y: 425 }, { x: 250, y: 400 }, { x: 250, y: 200 }, { x: 275, y: 175 }] },
    { buttonId: 'RS', vertices: [{ x: 500, y: 200 }, { x: 525, y: 175 }, { x: 725, y: 175 }, { x: 750, y: 200 }, { x: 750, y: 400 }, { x: 725, y: 425 }, { x: 525, y: 425 }, { x: 500, y: 400 }] },
    { buttonId: 'X', vertices: [{ x: 875, y: 300 }, { x: 750, y: 175 }, { x: 750, y: 425 }] },
    { buttonId: 'Y', vertices: [{ x: 1000, y: 175 }, { x: 950, y: 200 }, { x: 900, y: 250 }, { x: 875, y: 300 }, { x: 750, y: 175 }] },
    { buttonId: 'A', vertices: [{ x: 1000, y: 425 }, { x: 950, y: 400 }, { x: 900, y: 350 }, { x: 875, y: 300 }, { x: 750, y: 425 }] },
    { buttonId: 'B', vertices: [{ x: 1000, y: 175 }, { x: 950, y: 200 }, { x: 900, y: 250 }, { x: 875, y: 300 }, { x: 900, y: 350 }, { x: 950, y: 400 }, { x: 1000, y: 425 }] }
  ]
};

let layout = JSON.parse(JSON.stringify(defaultLayoutTemplate)); // Active layout
profiles = { default: JSON.parse(JSON.stringify(layout)) };
window.profiles = profiles;
let currentProfileId = 'default';
let originalLayoutJson = '';

function isCurveZones() {
    const active = document.querySelector('#curveModeToggle .toggle-option.active');
    return active ? active.dataset.mode === 'curve' : true;
}


// --- Button History ---
function saveButtonHistory() {
    if (!layout) return;
    const currentState = {
        sticks: JSON.parse(JSON.stringify(layout.sticks)),
        triggers: JSON.parse(JSON.stringify(layout.triggers)),
        bumpers: JSON.parse(JSON.stringify(layout.bumpers)),
        dpad: JSON.parse(JSON.stringify(layout.dpad)),
        faceButtons: JSON.parse(JSON.stringify(layout.faceButtons)),
        metaButtons: JSON.parse(JSON.stringify(layout.metaButtons)),
        menuButton: layout.menuButton ? JSON.parse(JSON.stringify(layout.menuButton)) : null
    };
    
    buttonHistory = buttonHistory.slice(0, buttonHistoryIndex + 1);
    buttonHistory.push(currentState);
    if (buttonHistory.length > 30) buttonHistory.shift();
    else buttonHistoryIndex++;
    
    updateButtonUndoRedoUI();
}

function updateButtonUndoRedoUI() {
    const btnUndo = document.getElementById('btnButtonUndo');
    const btnRedo = document.getElementById('btnButtonRedo');
    if (btnUndo) {
        btnUndo.disabled = buttonHistoryIndex <= 0;
        btnUndo.style.opacity = btnUndo.disabled ? '0.3' : '1';
    }
    if (btnRedo) {
        btnRedo.disabled = buttonHistoryIndex >= buttonHistory.length - 1;
        btnRedo.style.opacity = btnRedo.disabled ? '0.3' : '1';
    }
}

function undoButton() {
    if (buttonHistoryIndex > 0) {
        buttonHistoryIndex--;
        restoreButtonState(buttonHistory[buttonHistoryIndex]);
    }
}

function redoButton() {
    if (buttonHistoryIndex < buttonHistory.length - 1) {
        buttonHistoryIndex++;
        restoreButtonState(buttonHistory[buttonHistoryIndex]);
    }
}

function restoreButtonState(state) {
    if (!state) return;
    
    let wasDpad = (selectedButton && selectedButton === layout.dpad);
    let wasMenu = (selectedButton && layout.menuButton && selectedButton === layout.menuButton);

    layout.sticks = JSON.parse(JSON.stringify(state.sticks));
    layout.triggers = JSON.parse(JSON.stringify(state.triggers));
    layout.bumpers = JSON.parse(JSON.stringify(state.bumpers));
    layout.dpad = JSON.parse(JSON.stringify(state.dpad));
    layout.faceButtons = JSON.parse(JSON.stringify(state.faceButtons));
    layout.metaButtons = JSON.parse(JSON.stringify(state.metaButtons));
    layout.menuButton = state.menuButton ? JSON.parse(JSON.stringify(state.menuButton)) : null;
    
    // Refresh selectedButton reference if it exists
    if (selectedButton) {
        let found = false;
        if (wasDpad) {
            selectedButton = layout.dpad;
            found = true;
        } else if (wasMenu && layout.menuButton) {
            selectedButton = layout.menuButton;
            found = true;
        } else {
            const allItems = [...Object.values(layout.sticks), ...layout.triggers, ...layout.bumpers, ...layout.faceButtons, ...layout.metaButtons];
            for (const item of allItems) {
                if (item.id === selectedButton.id && item !== selectedButton) {
                    selectedButton = item;
                    found = true;
                    break;
                }
            }
        }
        if (!found) selectedButton = null;
    }
    
    updateButtonScaleSlider();
    render();
    updateSaveButtonState();
    updateButtonUndoRedoUI();
}

function getButtonScaleBounds(btn) {
    if (!btn) return { min: 40, max: 160 };
    // Menu & Meta mini buttons (midpoint is 100)
    if (btn.id === 'MENU' || btn === layout.menuButton || btn.id === 'BACK' || btn.id === 'START' || btn.id === 'GUIDE') {
        return { min: 50, max: 150 };
    }
    // Face action buttons, D-Pad, Sticks, Triggers, Bumpers (midpoint is 100)
    return { min: 40, max: 160 };
}

function updateButtonScaleSlider() {
    const slider = document.getElementById('buttonSizeSlider');
    const btnMinus = document.getElementById('btnSizeMinus');
    const btnPlus = document.getElementById('btnSizePlus');
    const pill = slider ? slider.parentElement : null;
    if (!slider) return;
    if (selectedButton) {
        slider.disabled = false;
        if (btnMinus) btnMinus.disabled = false;
        if (btnPlus) btnPlus.disabled = false;
        if (pill) {
            pill.style.opacity = '1';
            pill.style.pointerEvents = 'auto';
        }
        const bounds = getButtonScaleBounds(selectedButton);
        slider.min = bounds.min;
        slider.max = bounds.max;
        const s = selectedButton.scale || 1.0;
        slider.value = Math.max(bounds.min, Math.min(bounds.max, Math.round(s * 100)));
    } else {
        slider.disabled = true;
        if (btnMinus) btnMinus.disabled = true;
        if (btnPlus) btnPlus.disabled = true;
        if (pill) {
            pill.style.opacity = '0.4';
            pill.style.pointerEvents = 'none';
        }
        slider.min = 40;
        slider.max = 160;
        slider.value = 100;
    }
    const val = (slider.value - slider.min) / (slider.max - slider.min) * 100;
    slider.style.setProperty('--fill', `${val}%`);
}

function updatePlayerBadges() {
    const container = document.getElementById('editorPlayerBadges');
    if (!container) return;
    const badges = container.querySelectorAll('.slot-badge');
    const slotProfs = window.slotProfiles || ['default', 'default', 'default', 'default'];
    badges.forEach((b, idx) => {
        const isAssigned = (slotProfs[idx] === currentProfileId);
        b.classList.toggle('active', isAssigned);
    });
}
window.updateEditorPlayerBadges = updatePlayerBadges;

(() => {
    const btnUndo = document.getElementById('btnButtonUndo');
    const btnRedo = document.getElementById('btnButtonRedo');
    const slider = document.getElementById('buttonSizeSlider');
    const btnMinus = document.getElementById('btnSizeMinus');
    const btnPlus = document.getElementById('btnSizePlus');
    
    if (btnUndo) btnUndo.addEventListener('click', undoButton);
    if (btnRedo) btnRedo.addEventListener('click', redoButton);
    
    if (slider) {
        slider.addEventListener('input', (e) => {
            if (selectedButton) {
                selectedButton.scale = parseInt(e.target.value) / 100.0;
                render();
            }
            const val = (slider.value - slider.min) / (slider.max - slider.min) * 100;
            slider.style.setProperty('--fill', `${val}%`);
        });
        slider.addEventListener('change', (e) => {
            if (selectedButton) {
                saveButtonHistory();
                updateSaveButtonState();
            }
        });
    }
    
    if (btnMinus) {
        btnMinus.addEventListener('click', () => {
            if (selectedButton && slider) {
                slider.value = Math.max(parseInt(slider.min), parseInt(slider.value) - 5);
                selectedButton.scale = parseInt(slider.value) / 100.0;
                render();
                updateButtonScaleSlider();
                saveButtonHistory();
                updateSaveButtonState();
            }
        });
    }
    
    if (btnPlus) {
        btnPlus.addEventListener('click', () => {
            if (selectedButton && slider) {
                slider.value = Math.min(parseInt(slider.max), parseInt(slider.value) + 5);
                selectedButton.scale = parseInt(slider.value) / 100.0;
                render();
                updateButtonScaleSlider();
                saveButtonHistory();
                updateSaveButtonState();
            }
        });
    }
})();

function markOriginalState() {
  originalLayoutJson = JSON.stringify({
      curveZones: layout.curveZones !== false,
      rawZones: layout.rawZones || layout.zones || [],
      sticks: layout.sticks,
      triggers: layout.triggers,
      bumpers: layout.bumpers,
      dpad: layout.dpad,
      faceButtons: layout.faceButtons,
      metaButtons: layout.metaButtons,
      menuButton: layout.menuButton
  });
}

function updateSaveButtonState() {
  const btnSave = document.getElementById('btnSaveProfile');
  if (!btnSave) return;
  if (currentProfileId === 'default') {
      btnSave.disabled = true;
      btnSave.style.opacity = '0.5';
      btnSave.title = "Cannot save default profile";
      return;
  }
  
  let allAssigned = true;
  if (layoutMode === 'zone') {
      const assignedIds = zones.map(z => z.buttonId);
      // ALL_ZONE_BUTTONS is from zone_editor.js (GUIDE is an optional extra)
      if (typeof ALL_ZONE_BUTTONS !== 'undefined') {
          allAssigned = ALL_ZONE_BUTTONS.filter(id => id !== 'GUIDE').every(id => assignedIds.includes(id));
      }
  }
  
  const currentJson = JSON.stringify({
      curveZones: isCurveZones(),
      rawZones: zones,
      sticks: layout.sticks,
      triggers: layout.triggers,
      bumpers: layout.bumpers,
      dpad: layout.dpad,
      faceButtons: layout.faceButtons,
      metaButtons: layout.metaButtons,
      menuButton: layout.menuButton
  });
  
  const hasChanges = (currentJson !== originalLayoutJson);
  const canSave = hasChanges;
  
  btnSave.disabled = !canSave;
  btnSave.style.opacity = canSave ? '1' : '0.5';

  if (!allAssigned && hasChanges) btnSave.title = 'Save Draft (Incomplete Layout)';
  else if (!hasChanges) btnSave.title = 'No changes to save';
  else btnSave.title = '';
  
  const editorCard = document.getElementById('editorCardWrapper');
  if (editorCard) {
      if (!allAssigned) {
          editorCard.style.boxShadow = '0 0 0 2px var(--red), inset 0 4px 24px rgba(0,0,0,0.5)';
      } else if (hasChanges) {
          editorCard.style.boxShadow = '0 0 0 2px var(--accent-halo), inset 0 4px 24px rgba(0,0,0,0.5)';
      } else {
          editorCard.style.boxShadow = 'inset 0 4px 24px rgba(0,0,0,0.5)';
      }
  }
  
  const activeName = document.getElementById('activeProfileName');
  if (activeName) {
      activeName.textContent = layout.name || (currentProfileId === 'default' ? 'Default' : currentProfileId);
  }
}

// --- Zone Mode State ---
let layoutMode = localStorage.getItem('ybox_layout_mode') || 'button'; // 'button' | 'zone'
let zoneTool = 'edit'; // 'draw' | 'edit'
let selectedZoneButton = null;
let currentZonePath = [];
let zoneHistory = [];
let zoneHistoryIndex = -1;
let buttonHistory = [];
let buttonHistoryIndex = -1;
let selectedZone = null;
let draggingZoneVertex = null; // { zoneIndex, vertexIndex }
let zoneSnappedPos = {x:0, y:0};

const ZONE_COLOR_PALETTE = {
  'A':     { fill: 'rgba(16, 185, 129, 0.20)',  stroke: 'rgba(16, 185, 129, 0.85)', solid: '#10b981' },
  'B':     { fill: 'rgba(239, 68, 68, 0.20)',   stroke: 'rgba(239, 68, 68, 0.85)',  solid: '#ef4444' },
  'X':     { fill: 'rgba(59, 130, 246, 0.20)',  stroke: 'rgba(59, 130, 246, 0.85)', solid: '#3b82f6' },
  'Y':     { fill: 'rgba(245, 158, 11, 0.20)',  stroke: 'rgba(245, 158, 11, 0.85)', solid: '#f59e0b' },
  'LT':    { fill: 'rgba(6, 182, 212, 0.18)',   stroke: 'rgba(6, 182, 212, 0.80)',  solid: '#06b6d4' },
  'RT':    { fill: 'rgba(249, 115, 22, 0.18)',  stroke: 'rgba(249, 115, 22, 0.80)', solid: '#f97316' },
  'LB':    { fill: 'rgba(99, 102, 241, 0.18)',  stroke: 'rgba(99, 102, 241, 0.80)', solid: '#6366f1' },
  'RB':    { fill: 'rgba(236, 72, 153, 0.18)',  stroke: 'rgba(236, 72, 153, 0.80)', solid: '#ec4899' },
  'DPAD':  { fill: 'rgba(20, 184, 166, 0.18)',  stroke: 'rgba(20, 184, 166, 0.80)', solid: '#14b8a6' },
  'LS':    { fill: 'rgba(129, 140, 248, 0.18)', stroke: 'rgba(129, 140, 248, 0.80)',solid: '#818cf8' },
  'RS':    { fill: 'rgba(168, 85, 247, 0.18)',  stroke: 'rgba(168, 85, 247, 0.80)', solid: '#a855f7' },
  'START': { fill: 'rgba(132, 204, 22, 0.18)',  stroke: 'rgba(132, 204, 22, 0.80)', solid: '#84cc16' },
  'BACK':  { fill: 'rgba(217, 70, 239, 0.18)',  stroke: 'rgba(217, 70, 239, 0.80)', solid: '#d946ef' },
  'MENU':  { fill: 'rgba(148, 163, 184, 0.18)', stroke: 'rgba(148, 163, 184, 0.80)',solid: '#94a3b8' },
  'GUIDE': { fill: 'rgba(234, 179, 8, 0.18)',   stroke: 'rgba(234, 179, 8, 0.80)',  solid: '#eab308' }
};

const ZONE_COLORS = Object.fromEntries(
  Object.entries(ZONE_COLOR_PALETTE).map(([k, v]) => [k, v.fill])
);
const ALL_ZONE_BUTTONS = Object.keys(ZONE_COLOR_PALETTE);

const PROFILE_NAMES = ['alpha', 'beta', 'gamma', 'delta', 'epsilon', 'zeta', 'eta', 'theta'];

// ── Profile Management ──
const btnAddProfile = document.getElementById('btnAddProfile');
const userDataPath = require('electron').ipcRenderer.sendSync('get-user-data-path');
const profilesDir = path.join(userDataPath, 'profiles');

// Custom Dropdown Logic
const profileDropdownBtn = document.getElementById('profileDropdownBtn');
const profileDropdownText = document.getElementById('profileDropdownText');
const profileDropdownList = document.getElementById('profileDropdownList');

profileDropdownBtn.addEventListener('click', (e) => {
  e.stopPropagation();
  const isShowing = profileDropdownList.classList.contains('show');
  document.querySelectorAll('.custom-select-dropdown.show').forEach(d => {
    if (d !== profileDropdownList) d.classList.remove('show');
  });
  if (!isShowing) {
    if (typeof window.positionCustomDropdown === 'function') {
      window.positionCustomDropdown(profileDropdownBtn, profileDropdownList);
    }
    profileDropdownList.classList.add('show');
  } else {
    profileDropdownList.classList.remove('show');
  }
});
window.addEventListener('click', () => {
  profileDropdownList.classList.remove('show');
});

function addOptionToDropdown(id, text) {
  const opt = document.createElement('div');
  opt.className = 'custom-select-option';
  if (id === currentProfileId) opt.classList.add('selected');
  opt.dataset.value = id;
  const wrapper = document.createElement('div');
  wrapper.style.display = 'flex';
  wrapper.style.alignItems = 'center';
  wrapper.style.justifyContent = 'space-between';
  wrapper.style.width = '100%';
  
  const span = document.createElement('span');
  span.textContent = text;
  wrapper.appendChild(span);
  
  if (id !== 'default') {
      const delBtn = document.createElement('span');
      delBtn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"></path><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>';
      delBtn.style.color = 'var(--red)';
      delBtn.style.opacity = '0';
      delBtn.style.cursor = 'pointer';
      delBtn.style.padding = '4px';
      delBtn.style.display = 'flex';
      delBtn.title = 'Delete Profile';
      
      opt.addEventListener('mouseenter', () => { delBtn.style.opacity = '0.7'; });
      opt.addEventListener('mouseleave', () => { delBtn.style.opacity = '0'; });
      delBtn.addEventListener('mouseenter', () => { delBtn.style.opacity = '1'; });
      delBtn.addEventListener('mouseleave', () => { delBtn.style.opacity = '0.7'; });
      
      delBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          window.showConfirm(`Are you sure you want to delete the profile "${text}"?`, () => {
              delete profiles[id];
          const filePath = path.join(profilesDir, `${id}.json`);
          if (fs.existsSync(filePath)) {
            fs.unlinkSync(filePath);
          }
          opt.remove();
          
          if (window.slotProfiles) {
              for (let i = 0; i < window.slotProfiles.length; i++) {
                  if (window.slotProfiles[i] === id) {
                      window.slotProfiles[i] = 'default';
                      if (window.pushProfileToSlot) window.pushProfileToSlot(i);
                  }
              }
              if (window.saveSlotManager) window.saveSlotManager();
          }
          
          if (window.renderSlotCards) window.renderSlotCards();
          updateAddProfileButtonState();
          if (currentProfileId === id) {
            // Fallback to default forcefully without confirm dialog
            currentProfileId = 'default';
            profileDropdownText.textContent = 'Default';
            layout = profiles['default'];
            profileDropdownList.querySelectorAll('.custom-select-option').forEach(el => el.classList.remove('selected'));
            const defaultOpt = Array.from(profileDropdownList.children).find(c => c.dataset.value === 'default');
            if (defaultOpt) defaultOpt.classList.add('selected');
            zones = layout.rawZones || layout.zones || [];
            const curveOpts = document.querySelectorAll('#curveModeToggle .toggle-option');
            if (curveOpts.length) {
                curveOpts.forEach(o => o.classList.remove('active'));
                const activeOpt = Array.from(curveOpts).find(o => o.dataset.mode === (layout.curveZones !== false ? 'curve' : 'straight'));
                if (activeOpt) activeOpt.classList.add('active');
            }
            markOriginalState();
            buttonHistory = []; buttonHistoryIndex = -1; selectedButton = null; saveButtonHistory(); updateButtonScaleSlider();
            initZoneUI();
            if (typeof render === 'function') render();
            updateSaveButtonState();
            updatePlayerBadges();
          }
          });
      });
      
      wrapper.appendChild(delBtn);
  }
  
  opt.appendChild(wrapper);
  
  opt.addEventListener('click', (e) => {
    e.stopPropagation();
    if (currentProfileId === id) {
        profileDropdownList.classList.remove('show');
        return;
    }
    
    let hasChanges = (JSON.stringify({ curveZones: isCurveZones(), rawZones: zones, sticks: layout.sticks, triggers: layout.triggers, bumpers: layout.bumpers, dpad: layout.dpad, faceButtons: layout.faceButtons, metaButtons: layout.metaButtons, menuButton: layout.menuButton }) !== originalLayoutJson);
    if (hasChanges) {
        window.showConfirm('You have unsaved changes in this profile. Are you sure you want to switch? Your changes will be lost.', () => {
            // Reload the old profile from disk to discard in-memory changes
            const oldId = currentProfileId;
            if (oldId === 'default') {
                profiles.default = JSON.parse(JSON.stringify(defaultLayoutTemplate));
            } else {
                const filePath = path.join(profilesDir, `${oldId}.json`);
                if (fs.existsSync(filePath)) {
                    profiles[oldId] = JSON.parse(fs.readFileSync(filePath, 'utf8'));
                } else {
                    profiles[oldId] = JSON.parse(JSON.stringify(defaultLayoutTemplate));
                }
            }
            window.profiles = profiles;
            
            currentProfileId = id;
            profileDropdownText.textContent = text;
            layout = profiles[currentProfileId];
            profileDropdownList.querySelectorAll('.custom-select-option').forEach(el => el.classList.remove('selected'));
            opt.classList.add('selected');
            profileDropdownList.classList.remove('show');
            zones = layout.rawZones || layout.zones || [];
            const curveOpts = document.querySelectorAll('#curveModeToggle .toggle-option');
            if (curveOpts.length) {
                curveOpts.forEach(o => o.classList.remove('active'));
                const activeOpt = Array.from(curveOpts).find(o => o.dataset.mode === (layout.curveZones !== false ? 'curve' : 'straight'));
                if (activeOpt) activeOpt.classList.add('active');
            }
            markOriginalState();
            buttonHistory = []; buttonHistoryIndex = -1; selectedButton = null; saveButtonHistory(); updateButtonScaleSlider();
            initZoneUI();
            updateSaveButtonState();
            updatePlayerBadges();
        });
        profileDropdownList.classList.remove('show');
        return;
    }
    
    currentProfileId = id;
    profileDropdownText.textContent = text;
    layout = profiles[currentProfileId];
    
    profileDropdownList.querySelectorAll('.custom-select-option').forEach(el => el.classList.remove('selected'));
    opt.classList.add('selected');
    profileDropdownList.classList.remove('show');
    zones = layout.rawZones || layout.zones || [];
    const curveOpts = document.querySelectorAll('#curveModeToggle .toggle-option');
    if (curveOpts.length) {
        curveOpts.forEach(o => o.classList.remove('active'));
        const activeOpt = Array.from(curveOpts).find(o => o.dataset.mode === (layout.curveZones !== false ? 'curve' : 'straight'));
        if (activeOpt) activeOpt.classList.add('active');
    }
    markOriginalState();
    buttonHistory = []; buttonHistoryIndex = -1; selectedButton = null; saveButtonHistory(); updateButtonScaleSlider();
    initZoneUI();
    render();
    updateSaveButtonState();
    updatePlayerBadges();
    if (window.initSlotManager) window.initSlotManager();
  });
  
  profileDropdownList.appendChild(opt);
}

// Initial setup for default option
profileDropdownList.querySelector('.custom-select-option').addEventListener('click', (e) => {
  e.stopPropagation();
  currentProfileId = 'default';
  profileDropdownText.textContent = 'Default';
  layout = profiles['default'];
  profileDropdownList.querySelectorAll('.custom-select-option').forEach(el => el.classList.remove('selected'));
  e.target.classList.add('selected');
  profileDropdownList.classList.remove('show');
  zones = layout.rawZones || layout.zones || [];
  markOriginalState();
  buttonHistory = []; buttonHistoryIndex = -1; selectedButton = null; saveButtonHistory(); updateButtonScaleSlider();
  initZoneUI();
  render();
  updateSaveButtonState();
  updatePlayerBadges();
  if (window.initSlotManager) window.initSlotManager();
});

function snapLayout(lyt) {
  Object.values(lyt.sticks).forEach(s => { s.x = snap(s.x, W); s.y = snap(s.y, H); });
  lyt.triggers.forEach(t => { t.x = snap(t.x, W); t.y = snap(t.y, H); });
  lyt.bumpers.forEach(b => { b.x = snap(b.x, W); b.y = snap(b.y, H); });
  lyt.dpad.x = snap(lyt.dpad.x, W); lyt.dpad.y = snap(lyt.dpad.y, H);
  lyt.faceButtons.forEach(fb => { fb.x = snap(fb.x, W); fb.y = snap(fb.y, H); });
  lyt.metaButtons.forEach(m => { m.x = snap(m.x, W); m.y = snap(m.y, H); });
  if (lyt.menuButton) { lyt.menuButton.x = snap(lyt.menuButton.x, W); lyt.menuButton.y = snap(lyt.menuButton.y, H); }
  if (!lyt.layoutMode) lyt.layoutMode = 'button';
  if (!lyt.zones) lyt.zones = [];
}

function loadProfiles() {
  profiles.default = JSON.parse(JSON.stringify(defaultLayoutTemplate));
  
  if (fs.existsSync(profilesDir)) {
    const files = fs.readdirSync(profilesDir);
    files.forEach(file => {
      if (file.endsWith('.json') && file !== 'default-xbox.json' && file !== 'default.json' && !file.startsWith('slot-')) {
        const id = file.replace('.json', '');
        try {
          const data = JSON.parse(fs.readFileSync(path.join(profilesDir, file)));
          if (data && data.triggers && data.bumpers && data.faceButtons && data.metaButtons && data.menuButton) {
            profiles[id] = data;
            addOptionToDropdown(id, id.charAt(0).toUpperCase() + id.slice(1));
          } else {
            fs.unlinkSync(path.join(profilesDir, file));
            console.log(`Deleted outdated profile: ${file}`);
          }
        } catch(e) {
          console.error(`Failed to load profile ${file}:`, e);
        }
      }
    });
  }
  
  layout = profiles[currentProfileId];
  zones = layout.rawZones || layout.zones || [];
  const curveOpts = document.querySelectorAll('#curveModeToggle .toggle-option');
  if (curveOpts.length) {
      curveOpts.forEach(opt => opt.classList.remove('active'));
      const activeOpt = Array.from(curveOpts).find(opt => opt.dataset.mode === (layout.curveZones !== false ? 'curve' : 'straight'));
      if (activeOpt) activeOpt.classList.add('active');
  }
  markOriginalState();
  buttonHistory = []; buttonHistoryIndex = -1; selectedButton = null; saveButtonHistory(); updateButtonScaleSlider();
  if (typeof initZoneUI === 'function') initZoneUI();
  updateSaveButtonState();
  updateAddProfileButtonState();
  updatePlayerBadges();
  window.profiles = profiles;
  if (typeof initSlotManager === 'function') initSlotManager();
  else if (window.initSlotManager) window.initSlotManager();
}

function updateAddProfileButtonState() {
  const btn = document.getElementById('btnAddProfile');
  if (!btn) return;
  const existingIds = Object.keys(profiles);
  const nextId = PROFILE_NAMES.find(n => !existingIds.includes(n));
  if (!nextId) {
    btn.disabled = true;
    btn.style.opacity = '0.25';
    btn.style.cursor = 'not-allowed';
    btn.title = 'Maximum of 8 custom profiles reached';
  } else {
    btn.disabled = false;
    btn.style.opacity = '0.7';
    btn.style.cursor = 'pointer';
    btn.title = 'Add Custom Profile';
  }
}
window.updateAddProfileButtonState = updateAddProfileButtonState;

btnAddProfile.addEventListener('click', () => {
  const existingIds = Object.keys(profiles);
  const nextId = PROFILE_NAMES.find(n => !existingIds.includes(n));
  
  if (!nextId) {
    updateAddProfileButtonState();
    return;
  }
  
  profiles[nextId] = JSON.parse(JSON.stringify(defaultLayoutTemplate));
  snapLayout(profiles[nextId]);
  
  const text = nextId.charAt(0).toUpperCase() + nextId.slice(1);
  addOptionToDropdown(nextId, text);
  updateAddProfileButtonState();
  if (window.renderSlotCards) window.renderSlotCards();
  
  // Select it automatically
  const newOpt = Array.from(profileDropdownList.children).find(c => c.dataset.value === nextId);
  if (newOpt) newOpt.click();
});

// ── Drawing helpers ──
function roundedRect(x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + w - r, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + r);
  ctx.lineTo(x + w, y + h - r);
  ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  ctx.lineTo(x + r, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - r);
  ctx.lineTo(x, y + r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.closePath();
}

// Aerodynamic chamfered trigger shape (wider at top, contoured at bottom)
function triggerShape(cx, cy, w, h) {
  const topW = w * 1.0;
  const botW = w * 0.74;
  const r = 8;
  const x1 = cx - topW/2, x2 = cx + topW/2;
  const x3 = cx + botW/2, x4 = cx - botW/2;
  const y1 = cy - h/2, y2 = cy + h/2;
  ctx.beginPath();
  ctx.moveTo(x1 + r, y1);
  ctx.lineTo(x2 - r, y1);
  ctx.quadraticCurveTo(x2, y1, x2, y1 + r);
  ctx.lineTo(x3, y2 - r);
  ctx.quadraticCurveTo(x3, y2, x3 - r, y2);
  ctx.lineTo(x4 + r, y2);
  ctx.quadraticCurveTo(x4, y2, x4, y2 - r);
  ctx.lineTo(x1, y1 + r);
  ctx.quadraticCurveTo(x1, y1, x1 + r, y1);
  ctx.closePath();
}

function drawArrow(x, y, s, dir) {
  ctx.beginPath();
  if (dir === 'up') {
    ctx.moveTo(x, y - s); ctx.lineTo(x - s, y + s); ctx.lineTo(x + s, y + s);
  } else if (dir === 'down') {
    ctx.moveTo(x, y + s); ctx.lineTo(x - s, y - s); ctx.lineTo(x + s, y - s);
  } else if (dir === 'left') {
    ctx.moveTo(x - s, y); ctx.lineTo(x + s, y - s); ctx.lineTo(x + s, y + s);
  } else {
    ctx.moveTo(x + s, y); ctx.lineTo(x - s, y - s); ctx.lineTo(x - s, y + s);
  }
  ctx.closePath();
  ctx.fill();
}

function faceColor(id) {
  return COL['face' + id] || COL.faceA;
}

// ── Main Render ──
function drawDpadCross(cx, cy, scale = 1, active = false) {
    const arm = SIZE.DPAD_SIZE * scale;
    const thick = arm * 0.82;
    const r = 6; // Smooth rounded corners
  
    // Single connected path for the cross
    ctx.beginPath();
    ctx.moveTo(cx - thick/2 + r, cy - arm);
    ctx.lineTo(cx + thick/2 - r, cy - arm);
    ctx.quadraticCurveTo(cx + thick/2, cy - arm, cx + thick/2, cy - arm + r);
    ctx.lineTo(cx + thick/2, cy - thick/2);
    ctx.lineTo(cx + arm - r, cy - thick/2);
    ctx.quadraticCurveTo(cx + arm, cy - thick/2, cx + arm, cy - thick/2 + r);
    ctx.lineTo(cx + arm, cy + thick/2 - r);
    ctx.quadraticCurveTo(cx + arm, cy + thick/2, cx + arm - r, cy + thick/2);
    ctx.lineTo(cx + thick/2, cy + thick/2);
    ctx.lineTo(cx + thick/2, cy + arm - r);
    ctx.quadraticCurveTo(cx + thick/2, cy + arm, cx + thick/2 - r, cy + arm);
    ctx.lineTo(cx - thick/2 + r, cy + arm);
    ctx.quadraticCurveTo(cx - thick/2, cy + arm, cx - thick/2, cy + arm - r);
    ctx.lineTo(cx - thick/2, cy + thick/2);
    ctx.lineTo(cx - arm + r, cy + thick/2);
    ctx.quadraticCurveTo(cx - arm, cy + thick/2, cx - arm, cy + thick/2 - r);
    ctx.lineTo(cx - arm, cy - thick/2 + r);
    ctx.quadraticCurveTo(cx - arm, cy - thick/2, cx - arm + r, cy - thick/2);
    ctx.lineTo(cx - thick/2, cy - thick/2);
    ctx.lineTo(cx - thick/2, cy - arm + r);
    ctx.quadraticCurveTo(cx - thick/2, cy - arm, cx - thick/2 + r, cy - arm);
    ctx.closePath();
  
    // Fill and stroke
    ctx.fillStyle = active ? 'rgba(56, 189, 248, 0.16)' : 'rgba(255, 255, 255, 0.05)';
    ctx.fill();
    ctx.strokeStyle = active ? '#38bdf8' : 'rgba(255, 255, 255, 0.30)';
    ctx.lineWidth = active ? 2 : 1.4;
    ctx.stroke();

    // Center recessed pivot
    ctx.beginPath();
    ctx.arc(cx, cy, 7 * scale, 0, Math.PI * 2);
    ctx.fillStyle = active ? 'rgba(56, 189, 248, 0.25)' : 'rgba(255, 255, 255, 0.05)';
    ctx.fill();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
    ctx.lineWidth = 1;
    ctx.stroke();

    // Modern directional chevrons
    ctx.fillStyle = active ? '#38bdf8' : '#ffffff';
    drawArrow(cx, cy - arm * 0.65, 4.5 * scale, 'up');
    drawArrow(cx, cy + arm * 0.65, 4.5 * scale, 'down');
    drawArrow(cx - arm * 0.65, cy, 4.5 * scale, 'left');
    drawArrow(cx + arm * 0.65, cy, 4.5 * scale, 'right');
}

function render() {
  ctx.clearRect(0, 0, W, H);

  if (layoutMode === 'zone') {
    if (typeof renderZones === 'function') renderZones();
    return;
  }

  // 1. 3x4 Hierarchical Dot Grid (Starting from edges: 4 cols x 3 rows of 250px x 150px)
  for (let gx = 0; gx <= W; gx += GRID) {
    for (let gy = 0; gy <= H; gy += GRID) {
      const is3x4Major = (Math.round(gx) % 250 === 0 && Math.round(gy) % 150 === 0);
      ctx.beginPath();
      ctx.arc(gx, gy, is3x4Major ? 1.8 : 0.9, 0, Math.PI * 2);
      ctx.fillStyle = is3x4Major ? 'rgba(255, 255, 255, 0.45)' : 'rgba(255, 255, 255, 0.14)';
      ctx.fill();
    }
  }

  // 2. Center guidelines
  ctx.save();
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.14)';
  ctx.setLineDash([4, 6]);
  ctx.lineWidth = 1;
  ctx.beginPath(); ctx.moveTo(W/2, 0); ctx.lineTo(W/2, H); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(0, H/2); ctx.lineTo(W, H/2); ctx.stroke();
  ctx.restore();

  // 3. Triggers (LT / RT) — Custom SVG with dynamic color shading
  layout.triggers.forEach(t => {
    const active = hovered === t || dragging === t || selectedButton === t;
    const cx = t.x * W, cy = t.y * H;
    const tw = SIZE.TRIG_W * (t.scale || 1);
    const th = SIZE.TRIG_H * (t.scale || 1);
    
    const img = window.ButtonSvgEngine ? window.ButtonSvgEngine.getTriggerImage(t.id, active) : null;
    if (img && img.complete) {
      ctx.drawImage(img, cx - tw / 2, cy - th / 2, tw, th);
    }
  });

  // 4. Bumpers (LB / RB) — Custom SVG with dynamic color shading
  layout.bumpers.forEach(b => {
    const active = hovered === b || dragging === b || selectedButton === b;
    const cx = b.x * W, cy = b.y * H;
    const bw = SIZE.BUMP_W * (b.scale || 1);
    const bh = SIZE.BUMP_H * (b.scale || 1);
    
    const img = window.ButtonSvgEngine ? window.ButtonSvgEngine.getBumperImage(b.id, active) : null;
    if (img && img.complete) {
      ctx.drawImage(img, cx - bw / 2, cy - bh / 2, bw, bh);
    }
  });

  // 5. Sticks (LS / RS) — Custom SVG Outer Track & Inner Floating Knob
  layout.sticks = layout.sticks || {};
  Object.values(layout.sticks).forEach(s => {
    const active = hovered === s || dragging === s || selectedButton === s;
    const px = s.x * W, py = s.y * H;
    const r = SIZE.STICK_R * (s.scale || 1);
    const baseSize = r * 2.0;
    const knobSize = r * 1.2;

    const imgBase = window.ButtonSvgEngine ? window.ButtonSvgEngine.getStickBaseImage(active) : null;
    const imgKnob = window.ButtonSvgEngine ? window.ButtonSvgEngine.getStickKnobImage(active) : null;

    if (imgBase && imgBase.complete) {
      ctx.drawImage(imgBase, px - baseSize / 2, py - baseSize / 2, baseSize, baseSize);
    }
    if (imgKnob && imgKnob.complete) {
      ctx.drawImage(imgKnob, px - knobSize / 2, py - knobSize / 2, knobSize, knobSize);
    }

    // Centered Stick Label inside knob
    ctx.fillStyle = active ? '#38bdf8' : '#ffffff';
    ctx.font = '800 ' + Math.round(13 * (s.scale || 1)) + 'px Inter, system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(s.id, px, py + 0.5);
  });

  // 6. D-Pad — Custom SVG Partitioned Cross & Directional Nodes
  const dpad = layout.dpad;
  const dActive = hovered === dpad || dragging === dpad || selectedButton === dpad;
  const dcx = dpad.x * W, dcy = dpad.y * H;
  const dpadSize = SIZE.DPAD_SIZE * 2.2 * (dpad.scale || 1);
  const imgDpad = window.ButtonSvgEngine ? window.ButtonSvgEngine.getDpadImage(dActive) : null;
  if (imgDpad && imgDpad.complete) {
    ctx.drawImage(imgDpad, dcx - dpadSize / 2, dcy - dpadSize / 2, dpadSize, dpadSize);
  }

  // 7. Face buttons (A, B, X, Y) — Custom SVG with Signature Xbox Hue Badges
  layout.faceButtons.forEach(fb => {
    const active = hovered === fb || dragging === fb || selectedButton === fb;
    const px = fb.x * W, py = fb.y * H;
    const r = SIZE.FACE_R * (fb.scale || 1);
    const btnSize = r * 2.1;

    const imgFb = window.ButtonSvgEngine ? window.ButtonSvgEngine.getFaceButtonImage(fb.id, active) : null;
    if (imgFb && imgFb.complete) {
      ctx.drawImage(imgFb, px - btnSize / 2, py - btnSize / 2, btnSize, btnSize);
    }
  });

  // 8. Meta buttons (START, BACK, GUIDE) — Standardized mini buttons
  layout.metaButtons.forEach(m => {
    const active = hovered === m || dragging === m || selectedButton === m;
    const px = m.x * W, py = m.y * H;
    const r = SIZE.META_R * (m.scale || 1);

    ctx.beginPath();
    ctx.arc(px, py, r, 0, Math.PI * 2);
    ctx.fillStyle = active ? 'rgba(56, 189, 248, 0.20)' : 'rgba(255, 255, 255, 0.06)';
    ctx.fill();
    ctx.strokeStyle = active ? '#38bdf8' : 'rgba(255, 255, 255, 0.30)';
    ctx.lineWidth = active ? 2 : 1.2;
    if (active) {
      ctx.shadowColor = '#38bdf8';
      ctx.shadowBlur = 8;
    }
    ctx.stroke();
    ctx.shadowBlur = 0;

    const img = (m.id === 'START') ? imgPlay : (m.id === 'BACK' ? imgView : (m.id === 'GUIDE' ? imgXbox : null));
    if (img && img.complete) {
        ctx.save();
        if (active) {
            ctx.filter = 'drop-shadow(0 0 6px rgba(56,189,248,0.9)) brightness(1.2)';
        }
        const iconSize = m.id === 'GUIDE' ? r * 1.3 : r * 1.15;
        ctx.drawImage(img, px - iconSize/2, py - iconSize/2, iconSize, iconSize);
        ctx.restore();
    }
  });

  // 9. Menu Button (Draggable Dynamic Island / Menu toggle) — Standardized mini button
  if (layout.menuButton) {
    const mb = layout.menuButton;
    const active = hovered === mb || dragging === mb || selectedButton === mb;
    const px = mb.x * W, py = mb.y * H;
    const r = SIZE.META_R * (mb.scale || 1);

    ctx.beginPath();
    ctx.arc(px, py, r, 0, Math.PI * 2);
    ctx.fillStyle = active ? 'rgba(56, 189, 248, 0.20)' : 'rgba(255, 255, 255, 0.06)';
    ctx.fill();
    ctx.strokeStyle = active ? '#38bdf8' : 'rgba(255, 255, 255, 0.30)';
    ctx.lineWidth = active ? 2 : 1.2;
    if (active) {
      ctx.shadowColor = '#38bdf8';
      ctx.shadowBlur = 8;
    }
    ctx.stroke();
    ctx.shadowBlur = 0;

    if (imgYeval && imgYeval.complete) {
        ctx.save();
        if (active) {
            ctx.filter = 'drop-shadow(0 0 6px rgba(56,189,248,0.9)) brightness(1.2)';
        }
        const iconSize = r * 1.3;
        ctx.drawImage(imgYeval, px - iconSize/2, py - iconSize/2, iconSize, iconSize);
        ctx.restore();
    }
  }
}

// ── Hit-testing (100% Pixel-Perfect Hitboxes) ──
function getMousePos(e) {
  const rect = canvas.getBoundingClientRect();
  return {
    x: (e.clientX - rect.left) * (W / rect.width),
    y: (e.clientY - rect.top) * (H / rect.height)
  };
}

function allDraggables() {
  const items = [];
  // Sticks: Circular hitbox matching exact outer boundary radius
  Object.values(layout.sticks).forEach(s => items.push({ 
    ref: s, 
    cx: s.x * W, 
    cy: s.y * H, 
    hr: SIZE.STICK_R * (s.scale || 1), 
    type: 'circle' 
  }));
  // Triggers: Exact rectangular bounds
  layout.triggers.forEach(t => items.push({ 
    ref: t, 
    cx: t.x * W, 
    cy: t.y * H, 
    hw: (SIZE.TRIG_W / 2) * (t.scale || 1), 
    hh: (SIZE.TRIG_H / 2) * (t.scale || 1), 
    type: 'rect' 
  }));
  // Bumpers: Exact rectangular bounds
  layout.bumpers.forEach(b => items.push({ 
    ref: b, 
    cx: b.x * W, 
    cy: b.y * H, 
    hw: (SIZE.BUMP_W / 2) * (b.scale || 1), 
    hh: (SIZE.BUMP_H / 2) * (b.scale || 1), 
    type: 'rect' 
  }));
  // D-Pad: Exact geometric cross
  const dp = layout.dpad;
  const dpScale = dp.scale || 1;
  items.push({ 
    ref: dp, 
    cx: dp.x * W, 
    cy: dp.y * H, 
    arm: SIZE.DPAD_SIZE * dpScale, 
    thick: SIZE.DPAD_SIZE * dpScale * 0.82, 
    type: 'dpad' 
  });
  // Face Buttons: Exact circular radius
  layout.faceButtons.forEach(fb => items.push({ 
    ref: fb, 
    cx: fb.x * W, 
    cy: fb.y * H, 
    hr: SIZE.FACE_R * (fb.scale || 1), 
    type: 'circle' 
  }));
  // Meta Buttons: Exact circular mini radius
  layout.metaButtons.forEach(m => items.push({ 
    ref: m, 
    cx: m.x * W, 
    cy: m.y * H, 
    hr: SIZE.META_R * (m.scale || 1), 
    type: 'circle' 
  }));
  // Menu Button: Exact circular mini radius
  if (layout.menuButton) items.push({ 
    ref: layout.menuButton, 
    cx: layout.menuButton.x * W, 
    cy: layout.menuButton.y * H, 
    hr: SIZE.META_R * (layout.menuButton.scale || 1), 
    type: 'circle' 
  });
  return items;
}

function hitTest(pos) {
  const items = allDraggables().reverse(); // top-most first
  for (const item of items) {
    const dx = Math.abs(pos.x - item.cx);
    const dy = Math.abs(pos.y - item.cy);

    if (item.type === 'rect') {
      if (dx <= item.hw && dy <= item.hh) return item.ref;
    } else if (item.type === 'dpad') {
      // Pixel-perfect cross hit test: vertical bar OR horizontal bar
      if ((dx <= item.thick / 2 && dy <= item.arm) || (dx <= item.arm && dy <= item.thick / 2)) {
        return item.ref;
      }
    } else { // circle
      if (Math.hypot(pos.x - item.cx, pos.y - item.cy) <= item.hr) return item.ref;
    }
  }
  return null;
}

// ── Input ──
  let isDraggingMoved = false;
  let dragStartX = 0, dragStartY = 0;

  canvas.addEventListener('mousedown', e => {
    if (currentProfileId === 'default') return; // Locked
    if (layoutMode === 'zone') return zoneMouseDown(e);
    const pos = getMousePos(e);
    const hit = hitTest(pos);
    if (hit) {
      dragging = hit;
      isDraggingMoved = false;
      dragStartX = hit.x;
      dragStartY = hit.y;
      if (selectedButton !== hit) {
          selectedButton = hit;
          updateButtonScaleSlider();
      }
      dragOff.dx = hit.x - pos.x / W;
      dragOff.dy = hit.y - pos.y / H;
      canvas.style.cursor = 'grabbing';
      render();
    } else {
      if (selectedButton) {
          selectedButton = null;
          updateButtonScaleSlider();
          render();
      }
    }
  });

canvas.addEventListener('mousemove', e => {
  if (currentProfileId === 'default') return; // Locked
  if (layoutMode === 'zone') return zoneMouseMove(e);
  const pos = getMousePos(e);
    if (dragging) {
      const newX = Math.max(0.05, Math.min(0.95, pos.x / W + dragOff.dx));
      const newY = Math.max(0.05, Math.min(0.95, pos.y / H + dragOff.dy));
      if (Math.abs(newX - dragStartX) > 0.001 || Math.abs(newY - dragStartY) > 0.001) {
          isDraggingMoved = true;
      }
      dragging.x = newX;
      dragging.y = newY;
      render();
  } else {
    const hit = hitTest(pos);
    if (hit !== hovered) {
      hovered = hit;
      canvas.style.cursor = hit ? 'grab' : 'default';
      render();
    }
  }
});

canvas.addEventListener('mouseup', e => {
  if (currentProfileId === 'default') return; // Locked
  if (layoutMode === 'zone') return zoneMouseUp(e);
  if (dragging) {
    if (isDraggingMoved) {
      // Snap to grid
      dragging.x = snap(dragging.x, W);
      dragging.y = snap(dragging.y, H);
      saveButtonHistory();
      updateSaveButtonState();
    }
    dragging = null;
    canvas.style.cursor = hovered ? 'grab' : 'default';
    render();
  }
});

canvas.addEventListener('mouseleave', e => {
  if (currentProfileId === 'default') return; // Locked
  if (layoutMode === 'zone') {
      draggingZoneVertex = null;
      render();
      return;
  }
  if (dragging) {
    dragging.x = snap(dragging.x, W);
    dragging.y = snap(dragging.y, H);
  }
  dragging = null;
  hovered = null;
  canvas.style.cursor = 'default';
  render();
  updateSaveButtonState();
});

canvas.addEventListener('contextmenu', e => {
  if (layoutMode === 'zone') return zoneContextMenu(e);
  e.preventDefault();
});

loadProfiles();
render();

document.getElementById('btnResetProfile').addEventListener('click', () => {
    window.showConfirm("Reset this profile to the default layout?", () => {
        layout = JSON.parse(JSON.stringify(defaultLayoutTemplate));
        layout.id = currentProfileId;
        window.profiles[currentProfileId] = layout;
        layout.name = currentProfileId === 'default' ? 'Default' : currentProfileId.charAt(0).toUpperCase() + currentProfileId.slice(1);
        layout.type = 'standard';
        
        zones = layout.rawZones || layout.zones || [];
        
        // Update layoutMode toggle UI
        const modeOpts = document.querySelectorAll('#layoutModeToggle .toggle-option');
        if (modeOpts.length) {
            modeOpts.forEach(opt => opt.classList.remove('active'));
            const activeMode = Array.from(modeOpts).find(opt => opt.dataset.mode === layoutMode);
            if (activeMode) activeMode.classList.add('active');
        }
        
        // Update curveToggle UI
        const curveOpts = document.querySelectorAll('#curveModeToggle .toggle-option');
        if (curveOpts.length) {
            curveOpts.forEach(opt => opt.classList.remove('active'));
            const activeCurve = Array.from(curveOpts).find(opt => opt.dataset.mode === (layout.curveZones !== false ? 'curve' : 'straight'));
            if (activeCurve) activeCurve.classList.add('active');
        }
        
        saveProfile();
        markOriginalState();
    buttonHistory = []; buttonHistoryIndex = -1; selectedButton = null; saveButtonHistory(); updateButtonScaleSlider();
        initZoneUI();
        render();
        updateSaveButtonState();
    });
});

// ── Save ──
window.saveProfile = function() {
  // Sync state to layout
  layout.layoutMode = layoutMode;
  layout.curveZones = isCurveZones();
  layout.rawZones = JSON.parse(JSON.stringify(zones));
  layout.zones = layout.curveZones ? zGenerateCurvedZones(zones) : JSON.parse(JSON.stringify(zones));
  
  layout.id = currentProfileId === 'default' ? 'default-xbox' : currentProfileId;
  layout.name = currentProfileId === 'default' ? 'Default' : currentProfileId.charAt(0).toUpperCase() + currentProfileId.slice(1);
  layout.type = 'standard';

  if (currentProfileId !== 'default') {
    fs.writeFileSync(
      path.join(profilesDir, `${currentProfileId}.json`),
      JSON.stringify(layout, null, 2)
    );
  }

  // Check if complete
  let isComplete = true;
  if (layout.layoutMode === 'zone') {
      const mappedZones = layout.zones.map(z => z.buttonId);
      isComplete = ALL_ZONE_BUTTONS.every(b => mappedZones.includes(b));
  }
  
  if (!isComplete) {
      const btn = document.getElementById('btnSaveProfile');
      if (btn) {
          btn.style.animation = 'none';
          btn.offsetHeight; // trigger reflow
          btn.style.animation = 'pulseRed 0.5s 3';
      }
      // If incomplete, don't overwrite default-xbox.json with an incomplete zone layout.
      // But we still want to save the user's progress to default-xbox if they are editing the default profile,
      // so we will save it but relay it as 'button' mode instead as a contingency.
  }

  const relayedLayoutMode = (layout.layoutMode === 'zone' && !isComplete) ? 'button' : layout.layoutMode;

  const fullProfile = {
    id: 'default-xbox',
    name: currentProfileId === 'default' ? 'Default' : currentProfileId.charAt(0).toUpperCase() + currentProfileId.slice(1),
    type: 'standard',
    layoutMode: relayedLayoutMode,
    curveZones: layout.curveZones,
    rawZones: layout.rawZones,
    zones: layout.zones,
    sticks: layout.sticks,
    triggers: layout.triggers,
    bumpers: layout.bumpers,
    dpad: layout.dpad,
    faceButtons: layout.faceButtons,
    metaButtons: layout.metaButtons,
    menuButton: layout.menuButton
  };
  fs.writeFileSync(
    path.join(profilesDir, 'default-xbox.json'),
    JSON.stringify(fullProfile, null, 2)
  );
  
  if (window.slotProfiles && window.pushProfileToSlot) {
      for (let i = 0; i < 4; i++) {
          if (window.slotProfiles[i] === currentProfileId) {
              window.pushProfileToSlot(i);
          }
      }
  }
    markOriginalState();
    buttonHistory = []; buttonHistoryIndex = -1; selectedButton = null; saveButtonHistory(); updateButtonScaleSlider();
    updateSaveButtonState();
};


document.getElementById('btnSaveProfile').addEventListener('click', window.saveProfile);

ipcRenderer.on('request-close-check', () => {
    let hasChanges = false;
    try {
        const currentJson = JSON.stringify({ curveZones: isCurveZones(), rawZones: zones, sticks: layout.sticks, triggers: layout.triggers, bumpers: layout.bumpers, dpad: layout.dpad, faceButtons: layout.faceButtons, metaButtons: layout.metaButtons, menuButton: layout.menuButton });
        hasChanges = (currentJson !== originalLayoutJson);
    } catch (e) {}

    if (hasChanges) {
        window.showConfirm('You have unsaved changes. Are you sure you want to close? Your changes will be lost.', () => {
            ipcRenderer.send('force-close');
        });
    } else {
        ipcRenderer.send('force-close');
    }
});
