const fs = require('fs');
const path = require('path');

// Try to resolve karma/lib/server.js regardless of hoisting
let karmaServerPath;
try {
  // Try resolving from the script's location (web/scripts)
  const karmaMain = require.resolve('karma');
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

const replacement = `webServer.close((err) => {
           // FORCE EXIT to avoid ERR_SERVER_NOT_RUNNING crash during cleanup
           const exitCode = code || 0;
           console.log("Force exiting Karma with code", exitCode);
           process.exit(exitCode);
        })`;

// 1. Try matching the arrow function version (seen in CI)
// Matches: webServer.close(() => { ... })
// It needs to match strictly enough to be safe but flexible on whitespace
const arrowRegex = /webServer\.close\(\(\)\s*=>\s*\{[\s\S]*?removeAllListeners\(\)\s*\}\)\s*\}/;

// 2. Try matching the function version (seen locally/older versions)
// Matches: webServer.close(function (err) { ... })
const functionRegex = /webServer\.close\(function\s*\((?:err)?\)\s*\{[\s\S]*?disconnectBrowsers\(code\)\s*\}\)/;

let newContent = content;

if (arrowRegex.test(content)) {
    console.log('Detected arrow function syntax in Karma server.js');
    // We need to be careful with replace because the regex might match more or less than we want to replace.
    // The arrow regex targets the whole block ending with `}) }` or similar.
    // Let's rely on a slightly strict string match for the arrow version if possible, or use the regex carefully.
    
    // The snippet from CI:
    // webServer.close(() => {
    //   clearTimeout(closeTimeout)
    //   removeAllListeners()
    // })
    
    // We can try to match the start of the block and replace the whole `webServer.close(...)` call.
    newContent = content.replace(arrowRegex, replacement + '\n  }');
} else if (functionRegex.test(content)) {
    console.log('Detected function syntax in Karma server.js');
    newContent = content.replace(functionRegex, replacement);
} else {
    // Fallback: try simple string matching for known patterns
    const arrowTarget = `webServer.close(() => {
        clearTimeout(closeTimeout)
        removeAllListeners()
      })`;
      
    if (content.includes('webServer.close(() => {')) {
         // It definitely has the arrow function, but maybe whitespace differs.
         // Let's try to replace just the call logic.
         // We can't easily rely on exact string match for multi-line if whitespace varies.
         // But let's try a best-effort simpler regex if the above failed.
         const looseArrowRegex = /webServer\.close\(\(\)\s*=>\s*\{[^}]*\}\)/;
         if (looseArrowRegex.test(content)) {
             newContent = content.replace(looseArrowRegex, replacement);
         }
    }
}

if (newContent === content) {
    console.error('ERROR: Could not find target code in Karma server.js to patch.');
    console.log('Snippet of file around line 400:');
    const lines = content.split('\n');
    const start = Math.max(0, lines.findIndex(l => l.includes('webServer.close')) - 5);
    console.log(lines.slice(start, start + 15).join('\n'));
    process.exit(1);
} else {
    fs.writeFileSync(karmaServerPath, newContent, 'utf8');
    console.log('Karma patched successfully.');
}
