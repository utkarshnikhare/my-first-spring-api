const fs = require('fs');
const path = require('path');
const s = fs.readFileSync(path.resolve(__dirname, 'my-first-spring-api/src/main/resources/static/js/seller.js'), 'utf8');
const i = s.indexOf("form.id === 'manageScheduleForm'");
if (i < 0) { console.log('MANAGE SCHEDULE SUBMIT BLOCK NOT FOUND'); process.exit(1); }
const seg = s.slice(i, i + 1600);
const lines = seg.split('\n');
console.log('--- MANAGE SCHEDULE SUBMIT BRANCH (first lines) ---');
for (let k = 0; k < 60; k++) { console.log((k + 1) + ': ' + lines[k]); }


