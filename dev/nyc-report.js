#!/usr/bin/env node
'use strict';

// nyc's CLI `report` command filters out absolute paths from coverage JSON,
// so we use the programmatic API instead.
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
