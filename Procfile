app: clj -J-XX:-OmitStackTraceInFastThrow -m brainard.core
cljs: clj -A:shadow-cljs -J-XX:-OmitStackTraceInFastThrow -m shadow.cljs.devtools.cli watch dev | grep --color=never -v DEBUG
sass: sass --watch resources/private/scss/main.scss resources/public/css/main.css
