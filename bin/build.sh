#!/usr/bin/env sh

set -e

echo cleaning...
rm -rf resources/public/css/*
rm -rf resources/public/js/*

echo building scss
sass --style=compressed resources/scss/main.scss resources/public/css/main.css

echo building cljs
npm install
clj -A:shadow -m shadow.cljs.devtools.cli compile ui

clj -A:build:dev -M -e "(require '[brainard.build :as build])(build/default-uber)"
