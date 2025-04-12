app: NREPL_PORT=7300 clj -A:dev:test -J-XX:-OmitStackTraceInFastThrow -M -m brainard.dev
cljs: clj -A:shadow:dev -J-XX:-OmitStackTraceInFastThrow -M -m shadow.cljs.devtools.cli watch dev | grep --color=never -v DEBUG
sass: sass --watch resources/scss/main.scss resources/public/css/main.css
