const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');
const SHOTS = path.join(__dirname, 'docs-tools', 'v2v3-shots');
if (!fs.existsSync(SHOTS)) fs.mkdirSync(SHOTS, { recursive: true });
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const BASE = process.env.BASE || 'http://localhost:8093';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const stamp = 'cline' + Date.now().toString(36);
