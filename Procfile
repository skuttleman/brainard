app: NREPL_PORT=7300 clj -A:dev -J-XX:-OmitStackTraceInFastThrow -m brainard.dev
cljs: clj -A:shadow:dev -J-XX:-OmitStackTraceInFastThrow -m shadow.cljs.devtools.cli watch dev | grep --color=never -v DEBUG
sass: sass --watch resources/private/scss/main.scss resources/public/css/main.css
