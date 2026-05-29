#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');

// nyc's CLI `report` command filters out absolute paths from coverage JSON,
// so we use the programmatic API instead.
// If there is no coverage data (no files in target/nyc_output), exit gracefully.
const coverageDir = path.join(process.cwd(), 'target', 'nyc_output');


if (!fs.existsSync(coverageDir)) {
  console.log('No JS coverage data found (target/nyc_output missing), skipping nyc report.');
  console.log(fs.readFileSync(process.cwd() + '/foo.txt'), 'utf8');
  process.exit(1);
}
const files = fs.readdirSync(coverageDir).filter(f => f.endsWith('.json'));
if (files.length === 0) {
  console.log('No JS coverage data files found in target/nyc_output, skipping nyc report.');
  console.log(fs.readFileSync(process.cwd() + '/foo.txt'), 'utf8');
  process.exit(1);
}

const NYC = require('../node_modules/nyc');

const nyc = new NYC({
  cwd: process.cwd(),
  tempDir: 'target/nyc_output',
  include: ['**/*.js'],
  exclude: ['**/cljs-runtime/brainard.test.*'],
  reporter: ['lcov'],
  reportDir: 'target/coverage/js',
  sourceMap: true,
});

nyc.report()
  .then(() => console.log('JS coverage report written to target/coverage/js/lcov.info'))
  .catch(err => { console.error('nyc report failed:', err); process.exit(1); });
