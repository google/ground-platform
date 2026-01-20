const { execSync } = require('child_process');

const args = process.argv.slice(2);
const fixIndex = args.indexOf('--fix');
const shouldFix = fixIndex !== -1;

const extraArgs = [...args];
if (shouldFix) {
  extraArgs.splice(fixIndex, 1);
}

const argsStr = extraArgs.join(' ');

try {
  if (shouldFix) {
    console.log('Running buf format...');
    // buf format -w writes changes to files
    execSync(`npx buf format -w ${argsStr}`, { stdio: 'inherit' });
  }
  
  console.log('Running buf lint...');
  execSync(`npx buf lint ${argsStr}`, { stdio: 'inherit' });
} catch (error) {
  process.exit(1);
}
