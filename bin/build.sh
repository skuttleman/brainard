#!/usr/bin/env sh

set -e

echo cleaning...
rm -rf resources/public/css/*
rm -rf resources/public/js/*

echo building scss
sass --style=compressed resources/private/scss/main.scss resources/public/css/main.css

echo building cljs
npm install
clj -A:shadow -m shadow.cljs.devtools.cli compile ui

clj -A:dev -M -e "(require 'build)(build/default-uber)"
