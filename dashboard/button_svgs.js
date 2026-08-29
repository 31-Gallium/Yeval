// ── Custom SVG Button Engine: Desaturated Nordic Frost (Muted Titanium & Pearl) ──
(() => {
  const RAW_SVGS = {
  "btn_rb.svg": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 80\" style=\"enable-background:new 0 0 200 80;\" xml:space=\"preserve\">\n<style type=\"text/css\">\n\t.st0{fill:#13151c;}\n\t.st1{fill:#1f222c;}\n\t.st2{fill:#8a90a0;}\n</style>\n<g id=\"Right_Bumper\">\n\t<g>\n\t\t<path id=\"rb_bottom\" class=\"st0\" d=\"M147,73.8H17c-6.6,0-12-5.4-12-12l0-22c0-19.8,16.2-36,36-36h142c6.6,0,12,5.4,12,12v10 C195,52.2,173.4,73.8,147,73.8z\"/>\n\t\t<path id=\"rb_top\" class=\"st1\" d=\"M147,68.8H22c-6.6,0-12-5.4-12-12v-17c0-19.8,16.2-36,36-36h137c6.6,0,12,5.4,12,12v5 C195,47.2,173.4,68.8,147,68.8z\"/>\n\t\t<g id=\"rb_label\" transform=\"translate(99.25, 41.7) scale(0.52) translate(-99.25, -41.7)\">\n\t\t\t<path class=\"st2\" d=\"M83.2,32.5v23.7h-9.6V23.8h12.4c3.8,0,6.7,0.9,8.8,2.8c2.3,2.1,3.5,4.7,3.5,7.9c0,3.3-1.4,6-4.3,8.3 l5.5,13.4H89.3l-4.7-10.4v-8.3h0.8c2,0,3-0.9,3-2.6c0-1.5-1.2-2.3-3.5-2.3H83.2z\"/>\n\t\t\t<path class=\"st2\" d=\"M102.6,23.8h11.8c7,0,10.5,2.7,10.5,8.2c0,2.7-1.2,5-3.6,6.8c3.5,1.5,5.2,4.2,5.2,8c0,3-1,5.3-3.1,7.1 c-1,0.8-2.1,1.4-3.2,1.8c-1.1,0.3-2.7,0.5-4.6,0.5h-1.8v-8.3h1.6c1,0,1.6-0.1,2-0.4c0.4-0.2,0.6-0.7,0.6-1.3c0-1-0.8-1.6-2.4-1.6 h-1.8V36c1.8,0,2.7-0.6,2.7-1.8c0-1.1-0.8-1.7-2.4-1.7h-1.8v23.7h-9.6V23.8z\"/>\n\t\t</g>\n\t</g>\n</g>\n</svg>\n",
  "btn_rt.svg": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 80\" style=\"enable-background:new 0 0 200 80;\" xml:space=\"preserve\">\n<style type=\"text/css\">\n\t.st0{fill:#13151c;}\n\t.st1{fill:#1f222c;}\n\t.st2{fill:#8a90a0;}\n</style>\n<g id=\"Right_Trigger\">\n\t<g id=\"rt\">\n\t\t<g>\n\t\t\t<path class=\"st0\" d=\"M149.2,61.6c13.3,5.6,28.6,16.6,36.2,10.6c2.8-2.2,3.6-5.8,3.8-8.7V17.4c0-0.2,0.6-5.4-3.5-8.5 c-3.7-2.8-8-1.5-8.4-1.4C125.1,7.4,73,7.4,20.8,7.3c-3-0.4-6.4-0.1-8.5,1.9c-5.6,5.5,1.3,21.1,6.8,29.6 c7.8,12,18.1,17.9,20.1,19.1C75.8,78.5,106.2,43.6,149.2,61.6z\"/>\n\t\t\t<path class=\"st1\" d=\"M160.7,70.3c18.2,6.7,23.3,3.7,25.2,2.1c2.7-2.3,3.6-5.8,3.8-8.7V17.5c0-6.6-5.4-12-12.1-12H20.7 c-1.3,0-7.1,0.3-8.9,3.9c-1.4,2.8,0.3,6.2,0.9,7.4C18.9,29,58.1,23.7,98.5,40c22.3,9,35.4,16.7,35.4,16.7 C140.8,60.8,148.4,65.8,160.7,70.3z\"/>\n\t\t</g>\n\t\t<g id=\"rt_label\" transform=\"translate(154.05, 34.0) scale(0.52) translate(-154.05, -34.0)\">\n\t\t\t<path class=\"st2\" d=\"M138.4,24.8v23.7h-9.6V16.1h12.4c3.8,0,6.7,0.9,8.8,2.8c2.3,2.1,3.5,4.7,3.5,7.9c0,3.3-1.4,6-4.3,8.3 l5.5,13.4h-10.1L139.8,38v-8.3h0.8c2,0,3-0.9,3-2.6c0-1.5-1.2-2.3-3.5-2.3H138.4z\"/>\n\t\t\t<path class=\"st2\" d=\"M171.9,25.4v23.1h-9.6V25.4h-7.4v-9.3h24.4v9.3H171.9z\"/>\n\t\t</g>\n\t</g>\n</g>\n</svg>\n",
  "btn_lb.svg": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 80\" style=\"enable-background:new 0 0 200 80;\" xml:space=\"preserve\">\n<style type=\"text/css\">\n\t.st0{fill:#13151c;}\n\t.st1{fill:#1f222c;}\n\t.st2{fill:#8a90a0;}\n</style>\n<g id=\"Left_Bumper\">\n\t<g>\n\t\t<path id=\"lb_bottom\" class=\"st0\" d=\"M147,5H17C10.4,5,5,10.4,5,17l0,22c0,19.8,16.2,36,36,36h142c6.6,0,12-5.4,12-12V53 C195,26.6,173.4,5,147,5z\"/>\n\t\t<path id=\"lb_top\" class=\"st1\" d=\"M142,5H17C10.4,5,5,10.4,5,17l0,17c0,19.8,16.2,36,36,36h137c6.6,0,12-5.4,12-12v-5 C190,26.6,168.4,5,142,5z\"/>\n\t\t<g id=\"lb_label\" transform=\"translate(99.25, 40.0) scale(0.52) translate(-99.25, -40.0)\">\n\t\t\t<path class=\"st2\" d=\"M79.6,23.8h9.6V43c0,1.5,0.2,2.5,0.7,3s1.4,0.7,2.9,0.7h0.5v9.4h-2.6c-3.5,0-6.2-1-8.2-3.1 c-2-2-2.9-4.9-2.9-8.5V23.8z\"/>\n\t\t\t<path class=\"st2\" d=\"M96.6,23.8h11.8c7,0,10.5,2.7,10.5,8.2c0,2.7-1.2,5-3.6,6.8c3.5,1.5,5.2,4.2,5.2,8c0,3-1,5.3-3.1,7.1 c-1,0.8-2.1,1.4-3.2,1.8c-1.1,0.3-2.7,0.5-4.6,0.5h-1.8v-8.3h1.6c1,0,1.6-0.1,2-0.4c0.4-0.2,0.6-0.7,0.6-1.3c0-1-0.8-1.6-2.4-1.6 h-1.8V36c1.8,0,2.7-0.6,2.7-1.8c0-1.1-0.8-1.7-2.4-1.7h-1.8v23.7h-9.6V23.8z\"/>\n\t\t</g>\n\t</g>\n</g>\n</svg>\n",
  "btn_lt.svg": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 80\" style=\"enable-background:new 0 0 200 80;\" xml:space=\"preserve\">\n<style type=\"text/css\">\n\t.st0{fill:#13151c;}\n\t.st1{fill:#1f222c;}\n\t.st2{fill:#8a90a0;}\n</style>\n<g id=\"Left_Trigger\">\n\t<g id=\"lt\">\n\t\t<g>\n\t\t\t<path class=\"st0\" d=\"M51.3,61.8C38,67.4,22.7,78.3,15.1,72.4c-2.8-2.2-3.6-5.8-3.8-8.7V17.5c0-0.2-0.6-5.4,3.5-8.5 c3.7-2.8,8-1.5,8.4-1.4c52.1-0.1,104.3-0.1,156.4-0.2c3-0.4,6.4-0.1,8.5,1.9c5.6,5.5-1.3,21.1-6.8,29.6 c-7.8,12-18.1,17.9-20.1,19.1C124.7,78.6,94.3,43.8,51.3,61.8z\"/>\n\t\t\t<path class=\"st1\" d=\"M39.7,70.4c-18.2,6.7-23.3,3.7-25.2,2.1c-2.7-2.3-3.6-5.8-3.8-8.7V17.7c0-6.6,5.4-12,12.1-12h156.9 c1.3,0,7.1,0.3,8.9,3.9c1.4,2.8-0.3,6.2-0.9,7.4c-6.2,12.2-45.3,6.9-85.7,23.2c-22.3,9-35.4,16.7-35.4,16.7 C59.6,60.9,52,65.9,39.7,70.4z\"/>\n\t\t</g>\n\t\t<g id=\"lt_label\" transform=\"translate(48.5, 32.3) scale(0.52) translate(-48.5, -32.3)\">\n\t\t\t<path class=\"st2\" d=\"M27.6,16.1h9.6v19.2c0,1.5,0.2,2.5,0.7,3s1.4,0.7,2.9,0.7h0.5v9.4h-2.6c-3.5,0-6.2-1-8.2-3.1 c-2-2-2.9-4.9-2.9-8.5V16.1z\"/>\n\t\t\t<path class=\"st2\" d=\"M61.9,25.4v23.1h-9.6V25.4h-7.4v-9.3h24.4v9.3H61.9z\"/>\n\t\t</g>\n\t</g>\n</g>\n</svg>\n",
  "btn_stick_base.svg": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<!-- Generator: Adobe Illustrator 26.0.1, SVG Export Plug-In . SVG Version: 6.00 Build 0)  -->\r\n<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"\r\n\t viewBox=\"0 0 100 100\" style=\"enable-background:new 0 0 100 100;\" xml:space=\"preserve\">\r\n<style type=\"text/css\">\r\n\t.st0{fill:#666666;}\r\n\t.st1{fill:#B3B3B3;}\r\n\t.st2{fill:#CCCCCC;}\r\n</style>\r\n<g id=\"DPAD\">\r\n</g>\r\n<g id=\"Thumbstick_Base\">\r\n\t<g>\r\n\t\t<g>\r\n\t\t\t<g>\r\n\t\t\t\t<path class=\"st0\" d=\"M90.2,50c0,3-0.3,6-0.9,9c0,0.1-0.1,0.2-0.3,0.2s-0.2-0.1-0.2-0.3c0,0,0,0,0,0c0.7-2.9,1-5.9,1.1-8.9\r\n\t\t\t\t\tc0-0.1,0.1-0.1,0.2-0.1C90.1,49.9,90.1,49.9,90.2,50z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M86.4,67.6c-1.2,2.8-2.8,5.4-4.6,7.9c-0.3,0.3-0.7,0.4-1.1,0.2c-0.3-0.3-0.4-0.7-0.2-1.1c0,0,0,0,0,0\r\n\t\t\t\t\tc1.9-2.2,3.6-4.7,5-7.3c0.1-0.2,0.4-0.3,0.6-0.2C86.5,67.1,86.5,67.4,86.4,67.6z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M75.5,82.2c-1.2,1-2.4,1.9-3.7,2.8c-1.3,0.9-2.6,1.6-4,2.3c-0.6,0.3-1.4,0-1.7-0.6s0-1.4,0.6-1.7l0,0\r\n\t\t\t\t\tc1.3-0.6,2.6-1.3,3.9-2c1.3-0.7,2.5-1.6,3.6-2.4c0.5-0.4,1.2-0.3,1.5,0.2C76.1,81.2,76,81.8,75.5,82.2z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M59,90.3c-1.5,0.3-3,0.6-4.6,0.7c-1.5,0.1-3.1,0.2-4.6,0.2c-0.7,0-1.2-0.6-1.2-1.3c0-0.7,0.6-1.2,1.2-1.2\r\n\t\t\t\t\tc2.9,0,5.8-0.4,8.6-1l0,0c0.7-0.2,1.4,0.3,1.6,1C60.2,89.5,59.8,90.2,59,90.3L59,90.3z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M40.7,89.9c-3-0.8-5.9-1.9-8.6-3.3c-0.4-0.2-0.5-0.7-0.3-1c0.2-0.4,0.6-0.5,1-0.3c2.7,1.2,5.5,2.1,8.4,2.7\r\n\t\t\t\t\tc0.5,0.1,0.9,0.6,0.8,1.2C41.8,89.7,41.3,90.1,40.7,89.9C40.7,90,40.7,89.9,40.7,89.9z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M24.5,81.5c-2.3-2-4.5-4.1-6.3-6.5c-0.2-0.2-0.1-0.5,0.1-0.6c0.2-0.2,0.5-0.1,0.6,0.1\r\n\t\t\t\t\tc1.8,2.3,3.9,4.4,6.3,6.3c0.2,0.2,0.3,0.5,0.1,0.8C25.1,81.7,24.8,81.7,24.5,81.5C24.5,81.5,24.5,81.5,24.5,81.5z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M13.4,67.3c-1.3-2.7-2.3-5.6-3.1-8.6c-0.1-0.3,0.1-0.7,0.5-0.8c0.3-0.1,0.7,0.1,0.8,0.5c0,0,0,0,0,0\r\n\t\t\t\t\tc0.6,2.9,1.5,5.7,2.7,8.4c0.1,0.2,0,0.5-0.2,0.6C13.8,67.6,13.5,67.5,13.4,67.3z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M9.2,49.6c0-3.1,0.2-6.1,0.9-9.2c0.1-0.6,0.7-0.9,1.3-0.8s0.9,0.7,0.8,1.3c0,0,0,0,0,0.1\r\n\t\t\t\t\tc-0.8,2.8-1.2,5.7-1.3,8.7c0,0.5-0.4,0.8-0.9,0.8C9.5,50.4,9.2,50.1,9.2,49.6z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M13,31.7c1.3-2.8,3-5.5,4.9-7.9c0.5-0.6,1.3-0.7,1.9-0.2c0.6,0.5,0.7,1.3,0.2,1.9c0,0,0,0,0,0\r\n\t\t\t\t\tc-1.8,2.2-3.5,4.6-4.8,7.2c-0.3,0.6-1.1,0.8-1.7,0.5C13,33,12.7,32.3,13,31.7z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M24.5,17.3c2.4-1.9,5.1-3.6,7.9-4.9c0.8-0.4,1.7,0,2.1,0.7c0.4,0.8,0,1.7-0.7,2.1c0,0,0,0,0,0\r\n\t\t\t\t\tc-2.6,1.2-5.1,2.7-7.4,4.4c-0.6,0.5-1.6,0.4-2-0.3C23.8,18.7,23.9,17.8,24.5,17.3z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M41.3,9.3c3.1-0.6,6.2-1,9.3-0.9c0.9,0,1.6,0.7,1.6,1.6c0,0.9-0.7,1.6-1.6,1.6c-2.9,0-5.8,0.2-8.6,0.8\r\n\t\t\t\t\tc-0.9,0.2-1.7-0.4-1.9-1.2C39.9,10.4,40.4,9.5,41.3,9.3L41.3,9.3z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M59.8,9.6c3,0.7,5.9,1.9,8.7,3.3c0.7,0.4,0.9,1.2,0.6,1.9c-0.3,0.7-1.2,0.9-1.8,0.6\r\n\t\t\t\t\tc-2.6-1.3-5.4-2.2-8.2-2.9c-0.8-0.2-1.3-1-1.1-1.8C58.2,10,59,9.4,59.8,9.6L59.8,9.6z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M76.2,18.2c2.3,2.1,4.4,4.4,6.2,6.8c0.3,0.4,0.2,0.8-0.2,1.1c-0.3,0.2-0.8,0.2-1.1-0.1\r\n\t\t\t\t\tc-1.9-2.3-4-4.3-6.3-6.1c-0.5-0.4-0.6-1.1-0.2-1.6C74.9,17.9,75.6,17.8,76.2,18.2C76.1,18.2,76.1,18.2,76.2,18.2z\"/>\r\n\t\t\t\t<path class=\"st0\" d=\"M86.8,33.1c0.6,1.4,1.1,2.8,1.5,4.3c0.4,1.4,0.8,2.9,1.1,4.4c0,0.1-0.1,0.2-0.2,0.3c-0.1,0-0.2-0.1-0.3-0.2\r\n\t\t\t\t\tc-0.3-1.5-0.8-2.9-1.2-4.3c-0.5-1.4-1.1-2.8-1.8-4.1c-0.1-0.2,0-0.5,0.2-0.6S86.6,32.8,86.8,33.1C86.8,33,86.8,33.1,86.8,33.1z\"\r\n\t\t\t\t\t/>\r\n\t\t\t</g>\r\n\t\t</g>\r\n\t</g>\r\n</g>\r\n<g id=\"Thumbstick_Knob\">\r\n</g>\r\n<g id=\"Right_Bumper\">\r\n</g>\r\n<g id=\"B\">\r\n\t<g id=\"Layer_1\">\r\n\t</g>\r\n</g>\r\n<g id=\"A\">\r\n</g>\r\n<g id=\"Y\">\r\n</g>\r\n<g id=\"X\">\r\n</g>\r\n<g id=\"Left_Bumper\">\r\n</g>\r\n<g id=\"Right_Trigger\">\r\n</g>\r\n<g id=\"Left_Trigger\">\r\n</g>\r\n</svg>\r\n",
  "btn_stick_knob.svg": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<!-- Generator: Adobe Illustrator 26.0.1, SVG Export Plug-In . SVG Version: 6.00 Build 0)  -->\r\n<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"\r\n\t viewBox=\"0 0 100 100\" style=\"enable-background:new 0 0 100 100;\" xml:space=\"preserve\">\r\n<style type=\"text/css\">\r\n\t.st0{fill:#666666;}\r\n\t.st1{fill:#B3B3B3;}\r\n\t.st2{fill:#CCCCCC;}\r\n</style>\r\n<g id=\"DPAD\">\r\n</g>\r\n<g id=\"Thumbstick_Base\">\r\n</g>\r\n<g id=\"Thumbstick_Knob\">\r\n\t<circle class=\"st1\" cx=\"50\" cy=\"50\" r=\"30\"/>\r\n</g>\r\n<g id=\"Right_Bumper\">\r\n</g>\r\n<g id=\"B\">\r\n\t<g id=\"Layer_1\">\r\n\t</g>\r\n</g>\r\n<g id=\"A\">\r\n</g>\r\n<g id=\"Y\">\r\n</g>\r\n<g id=\"X\">\r\n</g>\r\n<g id=\"Left_Bumper\">\r\n</g>\r\n<g id=\"Right_Trigger\">\r\n</g>\r\n<g id=\"Left_Trigger\">\r\n</g>\r\n</svg>\r\n",
  "btn_btn_a.svg": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<!-- Generator: Adobe Illustrator 26.0.1, SVG Export Plug-In . SVG Version: 6.00 Build 0)  -->\r\n<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"\r\n\t viewBox=\"0 0 100 100\" style=\"enable-background:new 0 0 100 100;\" xml:space=\"preserve\">\r\n<style type=\"text/css\">\r\n\t.st0{fill:#666666;}\r\n\t.st1{fill:#B3B3B3;}\r\n\t.st2{fill:#CCCCCC;}\r\n</style>\r\n<g id=\"DPAD\">\r\n</g>\r\n<g id=\"Thumbstick_Base\">\r\n</g>\r\n<g id=\"Thumbstick_Knob\">\r\n</g>\r\n<g id=\"Right_Bumper\">\r\n</g>\r\n<g id=\"B\">\r\n\t<g id=\"Layer_1\">\r\n\t</g>\r\n</g>\r\n<g id=\"A\">\r\n\t<g>\r\n\t\t<path class=\"st0\" d=\"M62.8,37.9v11.4H37.1V37.9c0-0.2,0.1-0.3,0.2-0.5s0.3-0.2,0.5-0.2h24.3c0.2,0,0.4,0.1,0.5,0.2\r\n\t\t\tC62.8,37.5,62.8,37.7,62.8,37.9z\"/>\r\n\t\t<path class=\"st0\" d=\"M50,10c-22.1,0-40,17.9-40,40c0,22.1,17.9,40,40,40s40-17.9,40-40C90,27.9,72.1,10,50,10z M70.3,70.3h-7.5\r\n\t\t\tV56.7H37.1v13.7h-7.5V37.5c0-1.4,0.4-2.7,1.1-3.9c0.7-1.2,1.7-2.1,2.9-2.9c1.2-0.7,2.5-1.1,3.9-1.1h24.9c1.4,0,2.8,0.4,4,1.1\r\n\t\t\tc1.2,0.7,2.2,1.7,2.9,2.9c0.7,1.2,1.1,2.5,1.1,3.9V70.3z\"/>\r\n\t</g>\r\n</g>\r\n<g id=\"Y\">\r\n</g>\r\n<g id=\"X\">\r\n</g>\r\n<g id=\"Left_Bumper\">\r\n</g>\r\n<g id=\"Right_Trigger\">\r\n</g>\r\n<g id=\"Left_Trigger\">\r\n</g>\r\n</svg>\r\n",
  "btn_btn_b.svg": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<!-- Generator: Adobe Illustrator 26.0.1, SVG Export Plug-In . SVG Version: 6.00 Build 0)  -->\r\n<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"\r\n\t viewBox=\"0 0 100 100\" style=\"enable-background:new 0 0 100 100;\" xml:space=\"preserve\">\r\n<style type=\"text/css\">\r\n\t.st0{fill:#666666;}\r\n\t.st1{fill:#B3B3B3;}\r\n\t.st2{fill:#CCCCCC;}\r\n</style>\r\n<g id=\"DPAD\">\r\n</g>\r\n<g id=\"Thumbstick_Base\">\r\n</g>\r\n<g id=\"Thumbstick_Knob\">\r\n</g>\r\n<g id=\"Right_Bumper\">\r\n</g>\r\n<g id=\"B\">\r\n\t<g id=\"Layer_1\">\r\n\t\t<g>\r\n\t\t\t<path class=\"st0\" d=\"M37.5,45.8c-0.1-0.1-0.2-0.3-0.2-0.5v-7.2c0-0.2,0.1-0.3,0.2-0.5s0.3-0.2,0.5-0.2h22.3\r\n\t\t\t\tc0.2,0,0.3,0.1,0.4,0.2c0.1,0.1,0.2,0.3,0.2,0.5v7.2c0,0.2-0.1,0.3-0.2,0.5c-0.1,0.1-0.3,0.2-0.4,0.2H38\r\n\t\t\t\tC37.8,45.9,37.7,45.9,37.5,45.8z\"/>\r\n\t\t\t<path class=\"st0\" d=\"M62.4,53.5c0.1,0.1,0.2,0.3,0.2,0.5v7.9c0,0.2-0.1,0.3-0.2,0.5c-0.2,0.1-0.3,0.2-0.5,0.2H38\r\n\t\t\t\tc-0.2,0-0.3-0.1-0.5-0.2c-0.1-0.1-0.2-0.3-0.2-0.5V54c0-0.2,0.1-0.4,0.2-0.5c0.1-0.1,0.3-0.2,0.5-0.2h23.9\r\n\t\t\t\tC62.1,53.3,62.2,53.4,62.4,53.5z\"/>\r\n\t\t\t<path class=\"st0\" d=\"M50,10c-22.1,0-40,17.9-40,40c0,22.1,17.9,40,40,40c22.1,0,40-17.9,40-40C90,27.9,72.1,10,50,10z M70,62.3\r\n\t\t\t\tc0,1.4-0.4,2.7-1.1,3.9c-0.7,1.2-1.7,2.1-2.8,2.8c-1.2,0.7-2.5,1.1-3.9,1.1H30V30h30.7c1.4,0,2.7,0.3,3.9,1\r\n\t\t\t\tc1.2,0.7,2.1,1.6,2.8,2.8c0.7,1.2,1.1,2.5,1.1,3.9v7.8c0,0.5,0,1-0.1,1.4c-0.1,0.5-0.2,0.9-0.4,1.3c0.6,0.8,1.1,1.7,1.5,2.7\r\n\t\t\t\tc0.4,0.9,0.6,1.9,0.6,2.8V62.3z\"/>\r\n\t\t</g>\r\n\t</g>\r\n</g>\r\n<g id=\"A\">\r\n</g>\r\n<g id=\"Y\">\r\n</g>\r\n<g id=\"X\">\r\n</g>\r\n<g id=\"Left_Bumper\">\r\n</g>\r\n<g id=\"Right_Trigger\">\r\n</g>\r\n<g id=\"Left_Trigger\">\r\n</g>\r\n</svg>\r\n",
  "btn_btn_x.svg": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<!-- Generator: Adobe Illustrator 26.0.1, SVG Export Plug-In . SVG Version: 6.00 Build 0)  -->\r\n<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"\r\n\t viewBox=\"0 0 100 100\" style=\"enable-background:new 0 0 100 100;\" xml:space=\"preserve\">\r\n<style type=\"text/css\">\r\n\t.st0{fill:#666666;}\r\n\t.st1{fill:#B3B3B3;}\r\n\t.st2{fill:#CCCCCC;}\r\n</style>\r\n<g id=\"DPAD\">\r\n</g>\r\n<g id=\"Thumbstick_Base\">\r\n</g>\r\n<g id=\"Thumbstick_Knob\">\r\n</g>\r\n<g id=\"Right_Bumper\">\r\n</g>\r\n<g id=\"B\">\r\n\t<g id=\"Layer_1\">\r\n\t</g>\r\n</g>\r\n<g id=\"A\">\r\n</g>\r\n<g id=\"Y\">\r\n</g>\r\n<g id=\"X\">\r\n\t<g>\r\n\t\t<path class=\"st0\" d=\"M50,10c-22.1,0-40,17.9-40,40s17.9,40,40,40s40-17.9,40-40S72.1,10,50,10z M70,32L54.8,50L70,68v2.1h-8\r\n\t\t\tL50,55.9L38,70.1h-8V68l15.1-18L30,32v-2.1h8L50,44.2L62,29.9h8V32z\"/>\r\n\t</g>\r\n</g>\r\n<g id=\"Left_Bumper\">\r\n</g>\r\n<g id=\"Right_Trigger\">\r\n</g>\r\n<g id=\"Left_Trigger\">\r\n</g>\r\n</svg>\r\n",
  "btn_btn_y.svg": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<!-- Generator: Adobe Illustrator 26.0.1, SVG Export Plug-In . SVG Version: 6.00 Build 0)  -->\r\n<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"\r\n\t viewBox=\"0 0 100 100\" style=\"enable-background:new 0 0 100 100;\" xml:space=\"preserve\">\r\n<style type=\"text/css\">\r\n\t.st0{fill:#666666;}\r\n\t.st1{fill:#B3B3B3;}\r\n\t.st2{fill:#CCCCCC;}\r\n</style>\r\n<g id=\"DPAD\">\r\n</g>\r\n<g id=\"Thumbstick_Base\">\r\n</g>\r\n<g id=\"Thumbstick_Knob\">\r\n</g>\r\n<g id=\"Right_Bumper\">\r\n</g>\r\n<g id=\"B\">\r\n\t<g id=\"Layer_1\">\r\n\t</g>\r\n</g>\r\n<g id=\"A\">\r\n</g>\r\n<g id=\"Y\">\r\n\t<g>\r\n\t\t<path class=\"st0\" d=\"M50,10c-22.1,0-40,17.9-40,40s17.9,40,40,40c22.1,0,40-17.9,40-40S72.1,10,50,10z M53.3,54.6V68h-6.7V54.5\r\n\t\t\tL30,32h7.8L50,47.2L62,32h8L53.3,54.6z\"/>\r\n\t</g>\r\n</g>\r\n<g id=\"X\">\r\n</g>\r\n<g id=\"Left_Bumper\">\r\n</g>\r\n<g id=\"Right_Trigger\">\r\n</g>\r\n<g id=\"Left_Trigger\">\r\n</g>\r\n</svg>\r\n",
  "btn_dpad.svg": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\" style=\"enable-background:new 0 0 100 100;\" xml:space=\"preserve\">\n<style type=\"text/css\">\n\t.st_base{fill:#13151c;}\n\t.st_up{fill:#1f222c;}\n\t.st_down{fill:#1f222c;}\n\t.st_left{fill:#1f222c;}\n\t.st_right{fill:#1f222c;}\n\t.st_diag{fill:#1f222c;}\n\t.st_center{fill:#1f222c;}\n</style>\n<g id=\"DPAD\">\n\t<path id=\"dpad_base\" class=\"st_base\" d=\"M10,64l0-28c0-3.3,2.7-6,6-6c9,9.2,23.2-5.1,14-14c0-3.3,2.7-6,6-6l28,0c3.3,0,6,2.7,6,6 c-9.2,9,5.1,23.2,14,14c3.3,0,6,2.7,6,6l0,28c0,3.3-2.7,6-6,6c-9-9.2-23.2,5.1-14,14c0,3.3-2.7,6-6,6l-28,0c-3.3,0-6-2.7-6-6 c9.2-9-5.1-23.2-14-14C12.7,70,10,67.3,10,64z\"/>\n\t<circle id=\"dpad_down_right\" class=\"st_diag\" cx=\"76\" cy=\"76\" r=\"5.5\"/>\n\t<circle id=\"dpad_up_right\" class=\"st_diag\" cx=\"76\" cy=\"24\" r=\"5.5\"/>\n\t<circle id=\"dpad_down_left\" class=\"st_diag\" cx=\"24\" cy=\"76\" r=\"5.5\"/>\n\t<circle id=\"dpad_up_left\" class=\"st_diag\" cx=\"24\" cy=\"24\" r=\"5.5\"/>\n\t<path id=\"dpad_up\" class=\"st_up\" d=\"M53,33.5h-6c-3.8,0-7-3.1-7-7v-6c0-3.8,3.1-7,7-7h6c3.8,0,7,3.1,7,7v6C60,30.4,56.9,33.5,53,33.5z\"/>\n\t<path id=\"dpad_right\" class=\"st_right\" d=\"M79,58.9h-6c-3.8,0-7-3.1-7-7v-6 c0-3.8,3.1-7,7-7h6c3.8,0,7,3.1,7,7v6C85.9,55.7,82.8,58.9,79,58.9z\"/>\n\t<path id=\"dpad_down\" class=\"st_down\" d=\"M53,86.4h-6c-3.8,0-7-3.1-7-7v-6c0-3.8,3.1-7,7-7h6c3.8,0,7,3.1,7,7v6C60,83.3,56.9,86.4,53,86.4z\"/>\n\t<path id=\"dpad_left\" class=\"st_left\" d=\"M26.9,60h-6c-3.8,0-7-3.1-7-7v-6c0-3.8,3.1-7,7-7h6c3.8,0,7,3.1,7,7v6C33.9,56.9,30.8,60,26.9,60z\"/>\n\t<path id=\"dpad_center\" class=\"st_center\" d=\"M53.4,60c-0.8,0.2-3.2,0.9-6,0c-0.2-0.1-5.4-1.7-7-7c-0.9-2.9-0.2-5.3,0-6c0.1-0.2,1.7-5.4,7-7 c2.9-0.9,5.3-0.2,6,0c5.2,1.6,6.9,6.7,7,7c0.2,0.8,0.9,3.2,0,6C58.8,58.3,53.7,59.9,53.4,60z\"/>\n</g>\n</svg>\n"
};

  const PALETTE = {
    idle: {
      st0: '#13151c', // Deep shadow bevel
      st1: '#1f222c', // Muted titanium-slate plate
      st2: '#8a90a0'  // Subtle muted pearl label (reduced brightness)
    },
    active: {
      st0: '#0369a1', // Vibrant active bevel
      st1: '#0ea5e9', // Bright Sky Cyan plate
      st2: '#ffffff'  // Crisp diamond white label on active
    },
    analogHalf: {
      st0: '#1e3a8a',
      st1: '#3b82f6',
      st2: '#ffffff'
    },
    // Desaturated Nordic jewel face button badges
    faceA_idle: { st0: '#059669', st1: '#151720', st2: '#ffffff' },
    faceA_active: { st0: '#10b981', st1: '#059669', st2: '#ffffff' },
    faceB_idle: { st0: '#dc2626', st1: '#151720', st2: '#ffffff' },
    faceB_active: { st0: '#ef4444', st1: '#dc2626', st2: '#ffffff' },
    faceX_idle: { st0: '#0284c7', st1: '#151720', st2: '#ffffff' },
    faceX_active: { st0: '#38bdf8', st1: '#0284c7', st2: '#ffffff' },
    faceY_idle: { st0: '#d97706', st1: '#151720', st2: '#ffffff' },
    faceY_active: { st0: '#fbbf24', st1: '#d97706', st2: '#ffffff' },
  };

  const imageCache = {};

  function getTintedSvgDataUrl(rawSvg, colors = {}) {
    let res = rawSvg;
    if (colors.st0) res = res.replace(/\.st0\s*\{[^}]*\}/g, '.st0{fill:' + colors.st0 + ';}');
    if (colors.st1) res = res.replace(/\.st1\s*\{[^}]*\}/g, '.st1{fill:' + colors.st1 + ';}');
    if (colors.st2) res = res.replace(/\.st2\s*\{[^}]*\}/g, '.st2{fill:' + colors.st2 + ';}');
    if (colors.st_base) res = res.replace(/\.st_base\s*\{[^}]*\}/g, '.st_base{fill:' + colors.st_base + ';}');
    if (colors.st_up) res = res.replace(/\.st_up\s*\{[^}]*\}/g, '.st_up{fill:' + colors.st_up + ';}');
    if (colors.st_down) res = res.replace(/\.st_down\s*\{[^}]*\}/g, '.st_down{fill:' + colors.st_down + ';}');
    if (colors.st_left) res = res.replace(/\.st_left\s*\{[^}]*\}/g, '.st_left{fill:' + colors.st_left + ';}');
    if (colors.st_right) res = res.replace(/\.st_right\s*\{[^}]*\}/g, '.st_right{fill:' + colors.st_right + ';}');
    if (colors.st_diag) res = res.replace(/\.st_diag\s*\{[^}]*\}/g, '.st_diag{fill:' + colors.st_diag + ';}');
    if (colors.st_center) res = res.replace(/\.st_center\s*\{[^}]*\}/g, '.st_center{fill:' + colors.st_center + ';}');
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(res);
  }

  function getButtonImage(svgFile, colors = {}) {
    const key = svgFile + '_' + JSON.stringify(colors);
    if (imageCache[key]) return imageCache[key];

    const raw = RAW_SVGS[svgFile];
    if (!raw) return null;

    const img = new Image();
    img.src = getTintedSvgDataUrl(raw, colors);
    img.onload = () => {
      if (typeof render === 'function') render();
    };
    imageCache[key] = img;
    return img;
  }

  window.ButtonSvgEngine = {
    PALETTE,
    getButtonImage,
    getTriggerImage: (id, active = false, analogValue = 0) => {
      const svgFile = id === 'LT' ? 'btn_lt.svg' : 'btn_rt.svg';
      let colors = active ? PALETTE.active : PALETTE.idle;
      if (analogValue > 0.1 && !active) {
        colors = analogValue > 0.5 ? PALETTE.active : PALETTE.analogHalf;
      }
      return getButtonImage(svgFile, colors);
    },
    getBumperImage: (id, active = false) => {
      const svgFile = id === 'LB' ? 'btn_lb.svg' : 'btn_rb.svg';
      return getButtonImage(svgFile, active ? PALETTE.active : PALETTE.idle);
    },
    getFaceButtonImage: (id, active = false) => {
      const svgFile = 'btn_btn_' + id.toLowerCase() + '.svg';
      let colors;
      if (id === 'A') colors = active ? PALETTE.faceA_active : PALETTE.faceA_idle;
      else if (id === 'B') colors = active ? PALETTE.faceB_active : PALETTE.faceB_idle;
      else if (id === 'X') colors = active ? PALETTE.faceX_active : PALETTE.faceX_idle;
      else if (id === 'Y') colors = active ? PALETTE.faceY_active : PALETTE.faceY_idle;
      else colors = active ? PALETTE.active : PALETTE.idle;
      return getButtonImage(svgFile, colors);
    },
    // Directional DPad Lighting
    getDpadImage: (directions = {}) => {
      const isUp = typeof directions === 'boolean' ? directions : !!directions.up;
      const isDown = typeof directions === 'boolean' ? directions : !!directions.down;
      const isLeft = typeof directions === 'boolean' ? directions : !!directions.left;
      const isRight = typeof directions === 'boolean' ? directions : !!directions.right;

      const colors = {
        st_base: '#13151c',
        st_up: isUp ? '#0ea5e9' : '#1f222c',
        st_down: isDown ? '#0ea5e9' : '#1f222c',
        st_left: isLeft ? '#0ea5e9' : '#1f222c',
        st_right: isRight ? '#0ea5e9' : '#1f222c',
        st_diag: (isUp && isLeft) || (isUp && isRight) || (isDown && isLeft) || (isDown && isRight) ? '#0ea5e9' : '#1f222c',
        st_center: '#1f222c'
      };

      return getButtonImage('btn_dpad.svg', colors);
    },
    getStickBaseImage: (active = false) => {
      return getButtonImage('btn_stick_base.svg', { st0: active ? '#38bdf8' : 'rgba(255,255,255,0.30)' });
    },
    getStickKnobImage: (active = false) => {
      return getButtonImage('btn_stick_knob.svg', { st1: active ? '#0284c7' : '#151720' });
    }
  };
})();
