/**
 * Interactive Grid-based Parallax & Flowing Wave Background
 */
(function() {
    let canvas, ctx;
    let width = 0, height = 0;
    let dpr = 1;
    let animFrameId = null;
    let isRunning = false;

    // Mouse tracking with smooth spring/lerp interpolation
    const mouse = {
        x: -9999,
        y: -9999,
        targetX: -9999,
        targetY: -9999,
        parallaxX: 0,
        parallaxY: 0,
        isInside: false,
        intensity: 0 // Smooth fade-in/fade-out of mouse influence
    };

    // Configuration
    const GRID_GAP = 28; // Distance between dots
    const INFLUENCE_RADIUS = 130; // Radius of mouse interaction
    const REPEL_FORCE = 12; // Maximum pixel displacement from cursor
    const BASE_RADIUS = 1.1; // Base dot radius
    const HIGHLIGHT_RADIUS = 2.4; // Max radius when hovered

    // Accent color cache
    let accentR = 38, accentG = 192, accentB = 211; // Default Cyan

    function updateAccentFromDOM() {
        try {
            const activeNav = document.querySelector('.nav-item.active');
            if (activeNav) {
                const tab = activeNav.getAttribute('data-tab');
                if (tab === 'halo') {
                    // Electric Azure Blue
                    accentR = 56; accentG = 189; accentB = 248;
                } else if (tab === 'system') {
                    // Emerald / Green
                    accentR = 80; accentG = 250; accentB = 123;
                } else {
                    // Cyan / General
                    accentR = 38; accentG = 192; accentB = 211;
                }
            }
        } catch (e) {}
    }

    function resize() {
        if (!canvas) return;
        const rect = canvas.parentElement ? canvas.parentElement.getBoundingClientRect() : canvas.getBoundingClientRect();
        width = Math.floor(rect.width);
        height = Math.floor(rect.height);
        dpr = window.devicePixelRatio || 1;

        canvas.width = Math.floor(width * dpr);
        canvas.height = Math.floor(height * dpr);
        canvas.style.width = width + 'px';
        canvas.style.height = height + 'px';

        ctx = canvas.getContext('2d');
        ctx.scale(dpr, dpr);
    }

    function render(time) {
        if (!isRunning || !ctx) return;

        // Smoothly interpolate mouse position and intensity
        if (mouse.isInside) {
            mouse.x += (mouse.targetX - mouse.x) * 0.12;
            mouse.y += (mouse.targetY - mouse.y) * 0.12;
            mouse.intensity += (1 - mouse.intensity) * 0.1;
            
            const targetParallaxX = ((mouse.targetX / (width || 1)) - 0.5) * 12;
            const targetParallaxY = ((mouse.targetY / (height || 1)) - 0.5) * 12;
            mouse.parallaxX += (targetParallaxX - mouse.parallaxX) * 0.08;
            mouse.parallaxY += (targetParallaxY - mouse.parallaxY) * 0.08;
        } else {
            mouse.parallaxX += (0 - mouse.parallaxX) * 0.05;
            mouse.parallaxY += (0 - mouse.parallaxY) * 0.05;
            mouse.intensity += (0 - mouse.intensity) * 0.1;
        }

        ctx.clearRect(0, 0, width, height);

        const cols = Math.ceil(width / GRID_GAP) + 2;
        const rows = Math.ceil(height / GRID_GAP) + 2;
        const offsetX = (width % GRID_GAP) / 2 - GRID_GAP + mouse.parallaxX;
        const offsetY = (height % GRID_GAP) / 2 - GRID_GAP + mouse.parallaxY;

        const timeSec = time * 0.001;

        // Draw dots
        for (let r = 0; r < rows; r++) {
            for (let c = 0; c < cols; c++) {
                const originX = offsetX + c * GRID_GAP;
                const originY = offsetY + r * GRID_GAP;

                // Live flowing organic wave displacement & pulsation
                const wave1 = Math.sin(c * 0.2 + r * 0.15 + timeSec * 1.2);
                const flowX = Math.sin(timeSec * 0.8 + r * 0.25) * 1.8;
                const flowY = Math.cos(timeSec * 0.8 + c * 0.25) * 1.8;

                let posX = originX + flowX;
                let posY = originY + flowY;

                // Base wave alpha
                let alpha = 0.07 + (wave1 * 0.5 + 0.5) * 0.07;
                let dotRadius = BASE_RADIUS;
                let red = 255, green = 255, blue = 255;

                // Mouse interaction (Repulsion & Glow)
                if (mouse.intensity > 0.01) {
                    const dx = posX - mouse.x;
                    const dy = posY - mouse.y;
                    const distSq = dx * dx + dy * dy;

                    if (distSq < INFLUENCE_RADIUS * INFLUENCE_RADIUS) {
                        const dist = Math.sqrt(distSq);
                        const norm = (1 - (dist / INFLUENCE_RADIUS)) * mouse.intensity;
                        // Smooth cubic easing
                        const ease = norm * norm * (3 - 2 * norm);

                        // Elastic repel
                        const force = (1 - (dist / INFLUENCE_RADIUS)) * REPEL_FORCE * mouse.intensity;
                        const angle = Math.atan2(dy, dx);
                        posX += Math.cos(angle) * force;
                        posY += Math.sin(angle) * force;

                        // Vibrant proximity glow and size expansion
                        alpha = Math.min(0.85, alpha + ease * 0.7);
                        dotRadius = BASE_RADIUS + ease * (HIGHLIGHT_RADIUS - BASE_RADIUS);

                        // Blend toward active accent color
                        red = Math.round(255 + (accentR - 255) * ease);
                        green = Math.round(255 + (accentG - 255) * ease);
                        blue = Math.round(255 + (accentB - 255) * ease);
                    }
                }

                // Render dot
                ctx.beginPath();
                ctx.arc(posX, posY, dotRadius, 0, Math.PI * 2);
                ctx.fillStyle = `rgba(${red}, ${green}, ${blue}, ${alpha.toFixed(3)})`;
                ctx.fill();
            }
        }

        animFrameId = requestAnimationFrame(render);
    }

    function init() {
        canvas = document.getElementById('bgGridCanvas');
        if (!canvas) return;

        resize();

        const parent = canvas.parentElement || document.body;

        function updateMouseCoordinates(e) {
            const rect = canvas.getBoundingClientRect();
            const clientX = e.clientX !== undefined ? e.clientX : (e.touches && e.touches[0] ? e.touches[0].clientX : null);
            const clientY = e.clientY !== undefined ? e.clientY : (e.touches && e.touches[0] ? e.touches[0].clientY : null);

            if (clientX === null || clientY === null) return;

            // Check if coordinates are within the main container
            if (
                clientX >= rect.left &&
                clientX <= rect.right &&
                clientY >= rect.top &&
                clientY <= rect.bottom
            ) {
                const newX = clientX - rect.left;
                const newY = clientY - rect.top;

                if (!mouse.isInside) {
                    // First entry: instantly initialize to prevent sweeping from top-left
                    mouse.x = newX;
                    mouse.y = newY;
                    mouse.targetX = newX;
                    mouse.targetY = newY;
                    const initialParallaxX = ((newX / (width || 1)) - 0.5) * 12;
                    const initialParallaxY = ((newY / (height || 1)) - 0.5) * 12;
                    mouse.parallaxX = initialParallaxX;
                    mouse.parallaxY = initialParallaxY;
                    mouse.intensity = 0;
                    mouse.isInside = true;
                } else {
                    mouse.targetX = newX;
                    mouse.targetY = newY;
                }
            } else {
                mouse.isInside = false;
            }
        }

        // Track both regular mousemove and HTML5 drag events
        window.addEventListener('mousemove', updateMouseCoordinates, { passive: true, capture: true });
        window.addEventListener('dragover', updateMouseCoordinates, { passive: true, capture: true });
        window.addEventListener('drag', updateMouseCoordinates, { passive: true, capture: true });

        parent.addEventListener('mouseleave', () => {
            mouse.isInside = false;
        }, { passive: true });

        window.addEventListener('resize', resize, { passive: true });

        // Update accent color on navigation change
        document.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', () => {
                setTimeout(updateAccentFromDOM, 50);
            });
        });
        updateAccentFromDOM();

        // Pause/resume on visibility to preserve 0% background resources
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                stop();
            } else {
                start();
            }
        });

        start();
    }

    function start() {
        if (!isRunning) {
            isRunning = true;
            animFrameId = requestAnimationFrame(render);
        }
    }

    function stop() {
        isRunning = false;
        if (animFrameId) {
            cancelAnimationFrame(animFrameId);
            animFrameId = null;
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
