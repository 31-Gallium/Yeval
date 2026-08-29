// Zone Mode Logic for YBox Dashboard

function initZoneUI() {
    const curMode = (typeof layoutMode !== 'undefined') ? layoutMode : ((typeof layout !== 'undefined' && layout && layout.layoutMode) ? layout.layoutMode : 'button');
    const toggle = document.getElementById('layoutModeToggle');
    if (toggle) {
        const opts = toggle.querySelectorAll('.toggle-option');
        opts.forEach(opt => {
            opt.classList.remove('active');
            if (opt.dataset.mode === curMode) opt.classList.add('active');
        });
    }
    
    // UI visibility
    const btnToolbar = document.getElementById('buttonToolbar');
    const zoneToolbar = document.getElementById('zoneToolbar');
    
    if (curMode === 'zone') {
        if (zoneToolbar) zoneToolbar.style.display = 'flex';
        if (btnToolbar) btnToolbar.style.display = 'none';
        const editTools = document.getElementById('zoneEditTools');
        if (editTools) editTools.style.visibility = 'visible';
        // Reset zone state
        zoneTool = 'edit';
        selectedZoneButton = null;
        currentZonePath = [];
        selectedZone = null;
        draggingZoneVertex = null;
        
        // Initialize history
        zoneHistory = [];
        zoneHistoryIndex = -1;
        saveZoneHistory();
        
        renderZoneInventory();
    } else {
        if (zoneToolbar) zoneToolbar.style.display = 'none';
        if (btnToolbar) btnToolbar.style.display = 'flex';
        const editTools = document.getElementById('zoneEditTools');
        if (editTools) editTools.style.visibility = 'hidden';
    }
}

function renderZoneInventory() {
    const container = document.getElementById('zoneInventory');
    if (!container) return;
    
    container.innerHTML = '';
    const assignedIds = zones.map(z => z.buttonId);
    
    ALL_ZONE_BUTTONS.forEach(id => {
        const btn = document.createElement('button');
        btn.className = 'toggle-option inv-btn';
        btn.id = 'zbtn-' + id;
        btn.textContent = id;
        
        const palette = (typeof ZONE_COLOR_PALETTE !== 'undefined' && ZONE_COLOR_PALETTE[id]) || null;
        if (palette && palette.solid) {
            btn.style.setProperty('--btn-zone-color', palette.solid);
        }
        
        if (assignedIds.includes(id)) {
            btn.classList.add('assigned');
        } else if (zoneTool === 'draw' && selectedZoneButton === id) {
            btn.classList.add('active-draw');
        }
        
        btn.onclick = () => {
            if (layoutMode !== 'zone') return;
            startZoneDrawing(id);
        };
        
        container.appendChild(btn);
    });
    
    // Update tools
    const isDefault = (typeof currentProfileId !== 'undefined' && currentProfileId === 'default');
    const btnUndo = document.getElementById('btnZoneUndo');
    const btnRedo = document.getElementById('btnZoneRedo');
    const btnClear = document.getElementById('btnZoneClear');
    const curveToggle = document.getElementById('curveModeToggle');
    
    if (btnUndo) btnUndo.disabled = isDefault || (zoneHistoryIndex <= 0);
    if (btnRedo) btnRedo.disabled = isDefault || (zoneHistoryIndex === zoneHistory.length - 1);
    if (btnClear) btnClear.disabled = isDefault;
    if (curveToggle) {
        curveToggle.style.pointerEvents = 'auto';
    }
    
    if (typeof updateSaveButtonState === 'function') updateSaveButtonState();
}

function saveZoneHistory() {
    const state = {
        zones: JSON.parse(JSON.stringify(zones)),
        currentPath: JSON.parse(JSON.stringify(currentZonePath)),
        selectedButton: selectedZoneButton,
        tool: zoneTool
    };
    zoneHistory = zoneHistory.slice(0, zoneHistoryIndex + 1);
    zoneHistory.push(state);
    zoneHistoryIndex++;
    renderZoneInventory();
}

function restoreZoneHistory(index) {
    if (index >= 0 && index < zoneHistory.length) {
        zoneHistoryIndex = index;
        const state = zoneHistory[zoneHistoryIndex];
        zones = JSON.parse(JSON.stringify(state.zones));
        currentZonePath = JSON.parse(JSON.stringify(state.currentPath));
        selectedZoneButton = state.selectedButton;
        zoneTool = state.tool;
        selectedZone = null;
        draggingZoneVertex = null;
        renderZoneInventory();
        render();
    }
}

function undoZone() { if (zoneHistoryIndex > 0) restoreZoneHistory(zoneHistoryIndex - 1); }
function redoZone() { if (zoneHistoryIndex < zoneHistory.length - 1) restoreZoneHistory(zoneHistoryIndex + 1); }
function clearAllZones() {
    if (zones.length > 0) {
        zones = [];
        zoneTool = 'edit';
        selectedZoneButton = null;
        currentZonePath = [];
        selectedZone = null;
        saveZoneHistory();
        render();
    }
}

function startZoneDrawing(id) {
    zoneTool = 'draw';
    selectedZoneButton = id;
    currentZonePath = [];
    selectedZone = null;
    saveZoneHistory();
    renderZoneInventory();
    render();
}

function cancelZoneDrawing() {
    zoneTool = 'edit';
    selectedZoneButton = null;
    currentZonePath = [];
    saveZoneHistory();
    renderZoneInventory();
    render();
}

function autoFillEmptySpace(clickPos) {
    if (!selectedZoneButton) return;

    const validZones = zones.filter(z => z.buttonId !== selectedZoneButton && z.vertices && z.vertices.length >= 3);

    // 1. Collect all initial segments: Canvas boundary + existing zone walls
    const rawSegments = [];
    rawSegments.push({ p1: { x: 0, y: 0 }, p2: { x: W, y: 0 }, isWall: true });
    rawSegments.push({ p1: { x: W, y: 0 }, p2: { x: W, y: H }, isWall: true });
    rawSegments.push({ p1: { x: W, y: H }, p2: { x: 0, y: H }, isWall: true });
    rawSegments.push({ p1: { x: 0, y: H }, p2: { x: 0, y: 0 }, isWall: true });

    validZones.forEach(zone => {
        const vs = zone.vertices;
        const n = vs.length;
        for (let i = 0; i < n; i++) {
            const v1 = vs[i];
            const v2 = vs[(i + 1) % n];
            const cx1 = Math.max(0, Math.min(W, v1.x));
            const cy1 = Math.max(0, Math.min(H, v1.y));
            const cx2 = Math.max(0, Math.min(W, v2.x));
            const cy2 = Math.max(0, Math.min(H, v2.y));
            if (Math.hypot(cx2 - cx1, cy2 - cy1) > 0.1) {
                rawSegments.push({
                    p1: { x: cx1, y: cy1 },
                    p2: { x: cx2, y: cy2 },
                    isWall: true
                });
            }
        }
    });

    // Segment-segment intersection helper
    function getSegmentIntersection(A, B, C, D) {
        const rx = B.x - A.x, ry = B.y - A.y;
        const sx = D.x - C.x, sy = D.y - C.y;
        const rxs = rx * sy - ry * sx;
        const qpx = C.x - A.x, qpy = C.y - A.y;
        const EPS = 1e-7;

        if (Math.abs(rxs) < EPS) return null;

        const t = (qpx * sy - qpy * sx) / rxs;
        const u = (qpx * ry - qpy * rx) / rxs;

        if (t >= -EPS && t <= 1 + EPS && u >= -EPS && u <= 1 + EPS) {
            return {
                x: A.x + Math.max(0, Math.min(1, t)) * rx,
                y: A.y + Math.max(0, Math.min(1, t)) * ry,
                t: Math.max(0, Math.min(1, t)),
                u: Math.max(0, Math.min(1, u))
            };
        }
        return null;
    }

    // 2. Intersect all segments against each other and split into atomic sub-segments
    const splitPoints = rawSegments.map(() => []);

    for (let i = 0; i < rawSegments.length; i++) {
        for (let j = i + 1; j < rawSegments.length; j++) {
            const s1 = rawSegments[i];
            const s2 = rawSegments[j];
            const inter = getSegmentIntersection(s1.p1, s1.p2, s2.p1, s2.p2);
            if (inter) {
                splitPoints[i].push({ x: inter.x, y: inter.y, t: inter.t });
                splitPoints[j].push({ x: inter.x, y: inter.y, t: inter.u });
            }
        }
    }

    // Also split when a segment endpoint lies along another segment
    for (let i = 0; i < rawSegments.length; i++) {
        const s = rawSegments[i];
        const segLen = zDistance(s.p1, s.p2);
        if (segLen < 1e-4) continue;

        for (let j = 0; j < rawSegments.length; j++) {
            if (i === j) continue;
            const other = rawSegments[j];
            [other.p1, other.p2].forEach(pt => {
                const d1 = zDistance(s.p1, pt);
                const d2 = zDistance(pt, s.p2);
                if (Math.abs(d1 + d2 - segLen) < 1e-3) {
                    const t = d1 / segLen;
                    if (t > 1e-4 && t < 1 - 1e-4) {
                        splitPoints[i].push({ x: pt.x, y: pt.y, t });
                    }
                }
            });
        }
    }

    // Construct atomic non-overlapping sub-segments
    const atomicSegments = [];
    for (let i = 0; i < rawSegments.length; i++) {
        const s = rawSegments[i];
        const pts = [{ x: s.p1.x, y: s.p1.y, t: 0 }, ...splitPoints[i], { x: s.p2.x, y: s.p2.y, t: 1 }];
        pts.sort((a, b) => a.t - b.t);

        const uniquePts = [];
        for (const p of pts) {
            if (uniquePts.length === 0 || zDistance(uniquePts[uniquePts.length - 1], p) > 0.01) {
                uniquePts.push(p);
            }
        }

        for (let k = 0; k < uniquePts.length - 1; k++) {
            const p1 = { x: Math.round(uniquePts[k].x * 100) / 100, y: Math.round(uniquePts[k].y * 100) / 100 };
            const p2 = { x: Math.round(uniquePts[k + 1].x * 100) / 100, y: Math.round(uniquePts[k + 1].y * 100) / 100 };
            if (zDistance(p1, p2) > 0.1) {
                atomicSegments.push({ p1, p2, isWall: s.isWall });
            }
        }
    }

    // 3. Build Planar Graph
    const vertexMap = new Map();
    function getVertexKey(pt) {
        return `${Math.round(pt.x * 10) / 10},${Math.round(pt.y * 10) / 10}`;
    }

    function getOrCreateVertex(pt) {
        const key = getVertexKey(pt);
        if (!vertexMap.has(key)) {
            vertexMap.set(key, {
                x: Math.round(pt.x * 10) / 10,
                y: Math.round(pt.y * 10) / 10,
                key,
                adj: []
            });
        }
        return vertexMap.get(key);
    }

    const addedEdges = new Set();
    atomicSegments.forEach(seg => {
        const v1 = getOrCreateVertex(seg.p1);
        const v2 = getOrCreateVertex(seg.p2);
        if (v1 === v2) return;

        const edgeKey1 = `${v1.key}->${v2.key}`;
        const edgeKey2 = `${v2.key}->${v1.key}`;
        const undirKey = v1.key < v2.key ? `${v1.key}--${v2.key}` : `${v2.key}--${v1.key}`;

        if (!addedEdges.has(undirKey)) {
            addedEdges.add(undirKey);
            const a1 = Math.atan2(v2.y - v1.y, v2.x - v1.x);
            const a2 = Math.atan2(v1.y - v2.y, v1.x - v2.x);
            v1.adj.push({ to: v2, angle: a1, key: edgeKey1, isWall: seg.isWall });
            v2.adj.push({ to: v1, angle: a2, key: edgeKey2, isWall: seg.isWall });
        }
    });

    vertexMap.forEach(v => {
        v.adj.sort((a, b) => a.angle - b.angle);
    });

    // Helper: sample point guaranteed inside polygon
    function getStrictPointInside(vs) {
        let cx = 0, cy = 0;
        for (const v of vs) { cx += v.x; cy += v.y; }
        cx /= vs.length; cy /= vs.length;
        if (zIsPointInPolygon({ x: cx, y: cy }, vs)) return { x: cx, y: cy };

        const n = vs.length;
        for (let i = 0; i < n; i++) {
            for (let j = i + 2; j < n; j++) {
                if (i === 0 && j === n - 1) continue;
                const mid = { x: (vs[i].x + vs[j].x) / 2, y: (vs[i].y + vs[j].y) / 2 };
                if (zIsPointInPolygon(mid, vs)) return mid;
            }
        }

        for (let i = 1; i < n - 1; i++) {
            const triCenter = {
                x: (vs[0].x + vs[i].x + vs[i + 1].x) / 3,
                y: (vs[0].y + vs[i].y + vs[i + 1].y) / 3
            };
            if (zIsPointInPolygon(triCenter, vs)) return triCenter;
        }
        return { x: cx, y: cy };
    }

    // 4. Extract all planar face cycles
    const visitedHalfEdges = new Set();
    const faces = [];

    vertexMap.forEach(startV => {
        startV.adj.forEach(edge => {
            if (visitedHalfEdges.has(edge.key)) return;

            const faceVertices = [];
            const faceHalfEdges = [];
            let currV = startV;
            let currEdge = edge;
            let loopGuard = 0;

            while (currEdge && !visitedHalfEdges.has(currEdge.key) && loopGuard < 2000) {
                loopGuard++;
                visitedHalfEdges.add(currEdge.key);
                faceVertices.push({ x: currV.x, y: currV.y });
                faceHalfEdges.push(currEdge);

                const nextV = currEdge.to;
                if (nextV === startV) break;

                const revAngle = Math.atan2(currV.y - nextV.y, currV.x - nextV.x);
                const outEdges = nextV.adj;
                let nextEdgeIndex = -1;

                for (let i = 0; i < outEdges.length; i++) {
                    if (outEdges[i].angle > revAngle + 1e-7) {
                        nextEdgeIndex = i;
                        break;
                    }
                }
                if (nextEdgeIndex === -1) nextEdgeIndex = 0;

                currEdge = outEdges[nextEdgeIndex];
                currV = nextV;
            }

            if (faceVertices.length >= 3) {
                const signedArea = zGetSignedPolygonArea(faceVertices);
                if (signedArea < -1.0) { // Bounded interior face in Y-down screen space
                    const samplePt = getStrictPointInside(faceVertices);
                    const isOccupied = validZones.some(z => zIsPointInPolygon(samplePt, z.vertices));
                    faces.push({
                        vertices: faceVertices,
                        halfEdges: faceHalfEdges,
                        area: Math.abs(signedArea),
                        samplePt,
                        isOccupied
                    });
                }
            }
        });
    });

    // 5. Select target unoccupied face
    const emptyFaces = faces.filter(f => !f.isOccupied);
    if (emptyFaces.length === 0) return;

    let targetFace = null;
    if (clickPos) {
        targetFace = emptyFaces.find(f => zIsPointInPolygon(clickPos, f.vertices));
        if (!targetFace) {
            let minDist = Infinity;
            emptyFaces.forEach(f => {
                const d = zDistance(clickPos, f.samplePt);
                if (d < minDist) {
                    minDist = d;
                    targetFace = f;
                }
            });
        }
    } else {
        targetFace = emptyFaces.reduce((max, f) => f.area > max.area ? f : max, emptyFaces[0]);
    }

    if (!targetFace) return;

    // 6. Merge connected unoccupied faces
    const componentFaces = [targetFace];
    const compVisited = new Set([targetFace]);
    const queue = [targetFace];

    while (queue.length > 0) {
        const currF = queue.shift();
        for (const otherF of emptyFaces) {
            if (compVisited.has(otherF)) continue;
            let sharesEdge = false;
            for (const he1 of currF.halfEdges) {
                for (const he2 of otherF.halfEdges) {
                    if (he1.to.key === he2.key.split('->')[0] && he2.to.key === he1.key.split('->')[0]) {
                        sharesEdge = true;
                        break;
                    }
                }
                if (sharesEdge) break;
            }
            if (sharesEdge) {
                compVisited.add(otherF);
                componentFaces.push(otherF);
                queue.push(otherF);
            }
        }
    }

    // 7. Extract the outer boundary of the merged component
    const compHalfEdgeKeys = new Set();
    componentFaces.forEach(f => f.halfEdges.forEach(he => compHalfEdgeKeys.add(he.key)));

    const boundaryEdges = [];
    componentFaces.forEach(f => {
        f.halfEdges.forEach(he => {
            const revKey = `${he.to.key}->${he.key.split('->')[0]}`;
            if (!compHalfEdgeKeys.has(revKey)) {
                const fromKey = he.key.split('->')[0];
                const fromV = vertexMap.get(fromKey);
                boundaryEdges.push({ from: fromV, to: he.to, key: he.key });
            }
        });
    });

    if (boundaryEdges.length < 3) return;

    const boundMap = new Map();
    boundaryEdges.forEach(e => { boundMap.set(e.from.key, e); });

    let startEdge = boundaryEdges[0];
    let minY = Infinity, minX = Infinity;
    boundaryEdges.forEach(e => {
        if (e.from.y < minY || (e.from.y === minY && e.from.x < minX)) {
            minY = e.from.y;
            minX = e.from.x;
            startEdge = e;
        }
    });

    const loop = [{ x: startEdge.from.x, y: startEdge.from.y }];
    let curr = startEdge;
    const visitedBound = new Set([curr.key]);

    for (let step = 0; step < boundaryEdges.length * 2; step++) {
        loop.push({ x: curr.to.x, y: curr.to.y });
        if (curr.to.key === startEdge.from.key) break;

        const nextEdge = boundMap.get(curr.to.key);
        if (!nextEdge || visitedBound.has(nextEdge.key)) break;

        visitedBound.add(nextEdge.key);
        curr = nextEdge;
    }

    if (loop.length < 3) return;

    // 8. Simplify collinear points
    const simplified = [];
    const n = loop.length;
    for (let i = 0; i < n; i++) {
        const prev = loop[(i - 1 + n) % n];
        const cur = loop[i];
        const next = loop[(i + 1) % n];

        const cross = (cur.x - prev.x) * (next.y - cur.y) - (cur.y - prev.y) * (next.x - cur.x);
        const distP1 = zDistance(prev, cur);
        const distP2 = zDistance(cur, next);

        if (Math.abs(cross) > 0.05 || distP1 < 1 || distP2 < 1) {
            simplified.push({
                x: Math.round(cur.x * 10) / 10,
                y: Math.round(cur.y * 10) / 10
            });
        }
    }

    const finalVertices = [];
    for (let i = 0; i < simplified.length; i++) {
        const currPt = simplified[i];
        const nextPt = simplified[(i + 1) % simplified.length];
        if (zDistance(currPt, nextPt) > 0.5) {
            finalVertices.push(currPt);
        }
    }

    if (finalVertices.length < 3 || zGetPolygonArea(finalVertices) < 50) return;

    // 9. Commit new zone
    zones = zones.filter(z => z.buttonId !== selectedZoneButton);
    zones.push({ buttonId: selectedZoneButton, vertices: finalVertices });

    zoneTool = 'edit';
    selectedZoneButton = null;
    currentZonePath = [];
    saveZoneHistory();
    render();
}

// Math helpers
function zDistance(p1, p2) { return Math.hypot(p1.x - p2.x, p1.y - p2.y); }
function zDoIntersect(A, B, C, D) {
    const ccw = (p1, p2, p3) => (p3.y - p1.y) * (p2.x - p1.x) > (p2.y - p1.y) * (p3.x - p1.x);
    return ccw(A, C, D) !== ccw(B, C, D) && ccw(A, B, C) !== ccw(A, B, D);
}
function zCausesSelfIntersection(newPoint) {
    if (currentZonePath.length < 2) return false;
    const A = currentZonePath[currentZonePath.length - 1];
    const B = newPoint;
    const prev = currentZonePath[currentZonePath.length - 2];
    if (Math.abs(zDistance(prev, A) - (zDistance(prev, B) + zDistance(B, A))) < 0.1) return true;
    for (let i = 0; i < currentZonePath.length - 2; i++) {
        const C = currentZonePath[i];
        const D = currentZonePath[i + 1];
        if (i === 0 && zDistance(newPoint, C) < 5) continue;
        if (zDoIntersect(A, B, C, D)) return true;
    }
    return false;
}
function zGetClosestVertex(pos, threshold = 15) {
    let closest = null;
    let minDist = threshold;
    zones.forEach(zone => {
        zone.vertices.forEach(v => {
            const d = zDistance(pos, v);
            if (d < minDist) { minDist = d; closest = { x: v.x, y: v.y }; }
        });
    });
    if (currentZonePath.length > 0) {
        const startNode = currentZonePath[0];
        if (zDistance(pos, startNode) < minDist) closest = { x: startNode.x, y: startNode.y };
    }
    return closest;
}
function zCalcSnap(pos) {
    const vertexSnap = zGetClosestVertex(pos, 15);
    if (vertexSnap) return vertexSnap;
    return {
        x: Math.max(0, Math.min(W, Math.round(pos.x / GRID) * GRID)),
        y: Math.max(0, Math.min(H, Math.round(pos.y / GRID) * GRID))
    };
}
function zIsPointInPolygon(p, vs) {
    let inside = false;
    for (let i = 0, j = vs.length - 1; i < vs.length; j = i++) {
        const xi = vs[i].x, yi = vs[i].y;
        const xj = vs[j].x, yj = vs[j].y;
        const intersect = ((yi > p.y) !== (yj > p.y))
            && (p.x < (xj - xi) * (p.y - yi) / (yj - yi) + xi);
        if (intersect) inside = !inside;
    }
    return inside;
}
function zGetCentroid(vertices) {
    let cx = 0, cy = 0;
    let area = 0;
    let j = vertices.length - 1;
    for (let i = 0; i < vertices.length; i++) {
        const p1 = vertices[j];
        const p2 = vertices[i];
        const cross = (p1.x * p2.y - p2.x * p1.y);
        cx += (p1.x + p2.x) * cross;
        cy += (p1.y + p2.y) * cross;
        area += cross;
        j = i;
    }
    area *= 0.5;
    if (Math.abs(area) > 0.1) {
        return { x: cx / (6 * area), y: cy / (6 * area) };
    }
    cx = 0; cy = 0;
    vertices.forEach(v => { cx += v.x; cy += v.y; });
    return { x: cx / vertices.length, y: cy / vertices.length };
}

function zGetPolygonArea(vertices) {
    return Math.abs(zGetSignedPolygonArea(vertices));
}

function zGetSignedPolygonArea(vertices) {
    let total = 0;
    for (let i = 0, l = vertices.length; i < l; i++) {
        const addX = vertices[i].x;
        const addY = vertices[i === vertices.length - 1 ? 0 : i + 1].y;
        const subX = vertices[i === vertices.length - 1 ? 0 : i + 1].x;
        const subY = vertices[i].y;
        total += (addX * addY) - (subX * subY);
    }
    return total * 0.5;
}

function zIsScreenEdge(p) {
    return p.x <= 0 || p.x >= W || p.y <= 0 || p.y >= H;
}

function zGenerateCurvedZones(originalZones) {
    if (!originalZones || originalZones.length === 0) return [];

    const curvedZones = [];

    originalZones.forEach(z => {
        if (!z.vertices || z.vertices.length < 3) {
            curvedZones.push({ buttonId: z.buttonId, vertices: z.vertices ? [...z.vertices] : [] });
            return;
        }

        const poly = z.vertices;
        const n = poly.length;

        // 1. Centroid of bounding cell
        const centroid = zGetCentroid(poly);

        // 2. Inset polygon vertices slightly toward centroid (uniform 4px padding gutter)
        const margin = 4.0;
        const insetPoly = poly.map(p => {
            const dx = centroid.x - p.x;
            const dy = centroid.y - p.y;
            const dist = Math.hypot(dx, dy);
            if (dist < 1) return { x: p.x, y: p.y };
            const ratio = Math.min(margin / dist, 0.22);
            return { x: p.x + dx * ratio, y: p.y + dy * ratio };
        });

        // 3. Generate smooth organic corner fillets inside the cell boundary (up to 50% for full circles & pills)
        const smoothedPath = [];

        for (let i = 0; i < n; i++) {
            const p1 = insetPoly[i];
            const p2 = insetPoly[(i + 1) % n];
            const p0 = insetPoly[(i - 1 + n) % n];

            const dPrev = Math.hypot(p1.x - p0.x, p1.y - p0.y);
            const dNext = Math.hypot(p2.x - p1.x, p2.y - p1.y);
            const r = Math.min(dPrev * 0.5, dNext * 0.5);

            if (r < 1) {
                smoothedPath.push(p1);
                continue;
            }

            const startX = p1.x + (p0.x - p1.x) * (r / dPrev);
            const startY = p1.y + (p0.y - p1.y) * (r / dPrev);
            const endX = p1.x + (p2.x - p1.x) * (r / dNext);
            const endY = p1.y + (p2.y - p1.y) * (r / dNext);

            // Sample quadratic Bezier curve along the corner fillet
            const steps = 8;
            for (let t = 0; t <= steps; t++) {
                const u = t / steps;
                const invU = 1 - u;
                const qx = invU * invU * startX + 2 * invU * u * p1.x + u * u * endX;
                const qy = invU * invU * startY + 2 * invU * u * p1.y + u * u * endY;
                smoothedPath.push({ x: qx, y: qy });
            }
        }

        curvedZones.push({ buttonId: z.buttonId, vertices: smoothedPath });
    });

    return curvedZones;
}

function zoneMouseMove(e) {
    const pos = getMousePos(e); // uses getMousePos from editor.js
    if (zoneTool === 'draw') {
        zoneSnappedPos = zCalcSnap(pos);
    } else if (zoneTool === 'edit' && draggingZoneVertex) {
        zoneSnappedPos = zCalcSnap(pos);
        zones[draggingZoneVertex.zoneIndex].vertices[draggingZoneVertex.vertexIndex] = { ...zoneSnappedPos };
    }
    render();
}

function zoneMouseDown(e) {
    if (e.button === 2) return; // handled by context menu

    const pos = getMousePos(e);
    if (zoneTool === 'draw') {
        if (zCausesSelfIntersection(zoneSnappedPos)) return;

        if (currentZonePath.length >= 3 && zDistance(zoneSnappedPos, currentZonePath[0]) < 5) {
            zones.push({
                buttonId: selectedZoneButton,
                vertices: [...currentZonePath]
            });
            zoneTool = 'edit';
            selectedZoneButton = null;
            currentZonePath = [];
            saveZoneHistory();
        } else {
            currentZonePath.push({ ...zoneSnappedPos });
            saveZoneHistory();
        }
    } 
    else if (zoneTool === 'edit') {
        if (selectedZone) {
            const zIndex = zones.findIndex(z => z.buttonId === selectedZone.buttonId);
            for (let i = 0; i < selectedZone.vertices.length; i++) {
                if (zDistance(pos, selectedZone.vertices[i]) < 10) {
                    draggingZoneVertex = { zoneIndex: zIndex, vertexIndex: i };
                    return;
                }
            }
        }
        
        selectedZone = null;
        const activeCurve = document.querySelector('#curveModeToggle .toggle-option.active');
        const isCurve = activeCurve && activeCurve.dataset.mode === 'curve';
        const hitTestList = isCurve ? zGenerateCurvedZones(zones) : zones;

        for (let i = hitTestList.length - 1; i >= 0; i--) {
            if (zIsPointInPolygon(pos, hitTestList[i].vertices)) {
                // Always select the RAW zone for editing
                selectedZone = zones.find(z => z.buttonId === hitTestList[i].buttonId);
                break;
            }
        }
        render();
    }
}

function zoneMouseUp(e) {
    if (draggingZoneVertex) {
        saveZoneHistory(); 
        draggingZoneVertex = null;
    }
}

function zoneContextMenu(e) {
    e.preventDefault();
    if (zoneTool === 'draw') {
        if (currentZonePath.length === 0) {
            const clickPos = getMousePos(e);
            autoFillEmptySpace(clickPos);
        } else {
            cancelZoneDrawing();
        }
    } else {
        selectedZone = null;
        render();
    }
}

function renderZones() {
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

    // Center guidelines
    ctx.save();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.14)';
    ctx.setLineDash([4, 6]);
    ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(W/2, 0); ctx.lineTo(W/2, H); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(0, H/2); ctx.lineTo(W, H/2); ctx.stroke();
    ctx.restore();

    // 2. Draw completed zones
    let renderZonesList = zones;
    const activeCurve = document.querySelector('#curveModeToggle .toggle-option.active');
    if (activeCurve && activeCurve.dataset.mode === 'curve') {
        renderZonesList = zGenerateCurvedZones(zones);
    }

    renderZonesList.forEach(zone => {
        const isSelected = selectedZone && zone.buttonId === selectedZone.buttonId;
        const palette = (typeof ZONE_COLOR_PALETTE !== 'undefined' && ZONE_COLOR_PALETTE[zone.buttonId]) || {
            fill: (typeof ZONE_COLORS !== 'undefined' && ZONE_COLORS[zone.buttonId]) || 'rgba(255,255,255,0.18)',
            stroke: 'rgba(255,255,255,0.7)',
            solid: '#ffffff'
        };
        
        ctx.beginPath();
        if(zone.vertices.length > 0) {
            ctx.moveTo(zone.vertices[0].x, zone.vertices[0].y);
            for (let i = 1; i < zone.vertices.length; i++) {
                ctx.lineTo(zone.vertices[i].x, zone.vertices[i].y);
            }
            ctx.closePath();
            
            ctx.lineJoin = 'round';
            
            if (isSelected) {
                ctx.fillStyle = palette.fill.replace(/0\.\d+\)/, '0.40)');
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 2.5;
                ctx.shadowColor = palette.solid || '#38bdf8';
                ctx.shadowBlur = 10;
            } else {
                ctx.fillStyle = palette.fill;
                ctx.strokeStyle = palette.stroke;
                ctx.lineWidth = 1.5;
                ctx.shadowColor = 'transparent';
                ctx.shadowBlur = 0;
            }
            ctx.fill();
            ctx.stroke();
            ctx.shadowBlur = 0;

            const centroid = zGetCentroid(zone.vertices);
            
            const img = (zone.buttonId === 'START') ? imgPlay : 
                        (zone.buttonId === 'BACK') ? imgView : 
                        (zone.buttonId === 'GUIDE') ? imgXbox : 
                        (zone.buttonId === 'MENU') ? imgYeval : null;
            
            if (img && img.complete) {
                ctx.save();
                ctx.filter = 'drop-shadow(0px 1px 3px rgba(0,0,0,0.8)) brightness(1.2)';
                const iconSize = (zone.buttonId === 'GUIDE' || zone.buttonId === 'MENU') ? 28 : 22;
                ctx.drawImage(img, centroid.x - iconSize/2, centroid.y - iconSize/2, iconSize, iconSize);
                ctx.restore();
            } else {
                ctx.save();
                ctx.fillStyle = '#ffffff';
                ctx.font = '700 18px Inter, system-ui, sans-serif';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.shadowColor = 'rgba(0,0,0,0.9)';
                ctx.shadowBlur = 4;
                ctx.shadowOffsetY = 1;
                ctx.fillText(zone.buttonId, centroid.x, centroid.y);
                ctx.restore();
            }
            if (isSelected) {
                ctx.fillStyle = '#ffffff';
                ctx.strokeStyle = 'rgba(0,0,0,0.6)';
                ctx.lineWidth = 1;
                const rawZone = zones.find(z => z.buttonId === zone.buttonId);
                if (rawZone) {
                    rawZone.vertices.forEach(v => {
                        ctx.fillRect(v.x - 5, v.y - 5, 10, 10);
                        ctx.strokeRect(v.x - 5, v.y - 5, 10, 10);
                    });
                }
            }
        }
    });

    // 3. Draw in-progress path
    if (currentZonePath.length > 0) {
        ctx.beginPath();
        ctx.moveTo(currentZonePath[0].x, currentZonePath[0].y);
        for (let i = 1; i < currentZonePath.length; i++) {
            ctx.lineTo(currentZonePath[i].x, currentZonePath[i].y);
        }
        
        const selfIntersect = zCausesSelfIntersection(zoneSnappedPos);
        
        ctx.lineTo(zoneSnappedPos.x, zoneSnappedPos.y);
        
        ctx.strokeStyle = selfIntersect ? '#ef4444' : '#3b82f6';
        ctx.setLineDash([5, 5]);
        ctx.lineWidth = 2;
        ctx.stroke();
        ctx.setLineDash([]);
        
        ctx.fillStyle = '#3b82f6';
        currentZonePath.forEach(p => {
            ctx.beginPath(); ctx.arc(p.x, p.y, 4, 0, Math.PI * 2); ctx.fill();
        });
        
        if (currentZonePath.length >= 3 && zDistance(zoneSnappedPos, currentZonePath[0]) < 5) {
            ctx.fillStyle = '#4ade80';
            ctx.beginPath(); ctx.arc(currentZonePath[0].x, currentZonePath[0].y, 6, 0, Math.PI * 2); ctx.fill();
        }
    }

    // 4. Draw snap cursor
    if (zoneTool === 'draw' || draggingZoneVertex) {
        const selfIntersect = zoneTool === 'draw' && zCausesSelfIntersection(zoneSnappedPos);
        ctx.fillStyle = selfIntersect ? 'rgba(239,68,68,0.8)' : 'rgba(255,255,255,0.8)';
        ctx.beginPath();
        ctx.arc(zoneSnappedPos.x, zoneSnappedPos.y, 5, 0, Math.PI * 2);
        ctx.fill();
    }
}

// Global hook setups
window.initZoneUI = initZoneUI;

function setupZoneEvents() {
    const toggle = document.getElementById('layoutModeToggle');
    if (toggle) {
        toggle.querySelectorAll('.toggle-option').forEach(opt => {
            opt.addEventListener('click', (e) => {
                const targetOpt = e.target.closest('.toggle-option');
                if (!targetOpt) return;
                
                layoutMode = targetOpt.dataset.mode;
                localStorage.setItem('ybox_layout_mode', layoutMode);
                initZoneUI();
                if (typeof render === 'function') render();
                if (typeof updateSaveButtonState === 'function') updateSaveButtonState();
            });
        });
    }
    
    const curveToggle = document.getElementById('curveModeToggle');
    if (curveToggle) {
        curveToggle.querySelectorAll('.toggle-option').forEach(opt => {
            opt.addEventListener('click', (e) => {
                curveToggle.querySelectorAll('.toggle-option').forEach(o => o.classList.remove('active'));
                
                const targetOpt = e.target.closest('.toggle-option');
                if (!targetOpt) return;
                
                targetOpt.classList.add('active');
                
                if (typeof layout !== 'undefined' && layout) layout.curveZones = (targetOpt.dataset.mode === 'curve');
                if (typeof render === 'function') render();
                if (typeof updateSaveButtonState === 'function') updateSaveButtonState();
                if (typeof window.saveProfile === 'function') {
                    window.saveProfile();
                }
            });
        });
    }

    document.getElementById('btnZoneUndo')?.addEventListener('click', undoZone);
    document.getElementById('btnZoneRedo')?.addEventListener('click', redoZone);
    document.getElementById('btnZoneClear')?.addEventListener('click', clearAllZones);

    window.addEventListener('keydown', e => {
        if (layoutMode !== 'zone' || currentProfileId === 'default') return;
        if (e.ctrlKey && e.key.toLowerCase() === 'z') { e.preventDefault(); undoZone(); }
        if (e.ctrlKey && e.key.toLowerCase() === 'y') { e.preventDefault(); redoZone(); }
        if ((e.key === 'Delete' || e.key === 'Backspace') && selectedZone) {
            zones = zones.filter(z => z.buttonId !== selectedZone.buttonId);
            selectedZone = null;
            saveZoneHistory();
            render();
        }
        if (e.key === 'Escape' && zoneTool === 'draw') {
            cancelZoneDrawing();
        }
    });

    initZoneUI();
}

if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', setupZoneEvents);
    } else {
        setupZoneEvents();
    }
}
