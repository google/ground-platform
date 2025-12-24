const fs = require('fs');
const path = require('path');

// Try to resolve karma/lib/server.js regardless of hoisting
let karmaServerPath;
try {
  // Try resolving from the script's location (web/scripts)
  // We want to find the karma installed for this project
  // Since we are in web/scripts, we might be in a monorepo
  // checking ../node_modules first
  
  // Best bet: use require.resolve if possible, but we don't want to load it, just find it.
  // We can assume standard locations if require.resolve fails or returns unexpected.
  
  // require.resolve might point to main, which is lib/index.js usually.
  // server.js is in lib/server.js
  const karmaMain = require.resolve('karma'); // path to karma/lib/index.js or similar
  const karmaDir = path.dirname(karmaMain);
  karmaServerPath = path.join(karmaDir, 'server.js');
} catch (e) {
  // Fallback to relative paths
  const possiblePaths = [
    '../node_modules/karma/lib/server.js',
    '../../node_modules/karma/lib/server.js'
  ];
  
  for (const p of possiblePaths) {
    const resolved = path.resolve(__dirname, p);
    if (fs.existsSync(resolved)) {
      karmaServerPath = resolved;
      break;
    }
  }
}

if (!karmaServerPath || !fs.existsSync(karmaServerPath)) {
  console.warn('WARNING: Could not find karma/lib/server.js to patch. Tests might fail in CI.');
  process.exit(0);
}

console.log(`Patching Karma at: ${karmaServerPath}`);

let content = fs.readFileSync(karmaServerPath, 'utf8');

if (content.includes('Force exiting Karma')) {
  console.log('Karma already patched.');
  process.exit(0);
}

// target string to replace
// using regex to be robust against formatting changes if any
const regex = /webServer\.close\(function\s*\((?:err)?\)\s*\{\s*if\s*\(err\)\s*\{\s*log\.error\('Error stopping web-server: '\s*\+\s*err\.message\)\s*\}\s*disconnectBrowsers\(code\)\s*\}\)/;

// Simple string match fallback if regex feels risky (but regex handles spaces better)
// The original code in karma 6.4.4 lib/server.js:
//     webServer.close(function (err) {
//       if (err) {
//         log.error('Error stopping web-server: ' + err.message)
//       }
//       disconnectBrowsers(code)
//     })

const replacement = `webServer.close((err) => {
           // FORCE EXIT to avoid ERR_SERVER_NOT_RUNNING crash during cleanup
           const exitCode = code || 0;
           console.log("Force exiting Karma with code", exitCode);
           process.exit(exitCode);
        })`;

const newContent = content.replace(regex, replacement);

if (newContent === content) {
    // Try stricter replacement if regex failed (maybe different formatting)
    console.log('Regex match failed, trying simple replacement...');
    // We can try to match just the webServer.close part if we are careful
    const simpleTarget = `webServer.close(function (err) {
      if (err) {
        log.error('Error stopping web-server: ' + err.message)
      }
      disconnectBrowsers(code)
    })`;
    
    if (content.includes(simpleTarget)) {
       content = content.replace(simpleTarget, replacement);
       fs.writeFileSync(karmaServerPath, content, 'utf8');
       console.log('Karma patched successfully (simple match).');
    } else {
       console.error('ERROR: Could not find target code in Karma server.js to patch.');
       // Dump a snippet for debugging in logs
       console.log('Snippet of file around line 400:');
       const lines = content.split('\n');
       const start = Math.max(0, lines.findIndex(l => l.includes('webServer.close')) - 5);
       console.log(lines.slice(start, start + 15).join('\n'));
       process.exit(1);
    }
} else {
    fs.writeFileSync(karmaServerPath, newContent, 'utf8');
    console.log('Karma patched successfully.');
}
