app: NREPL_PORT=7300 clj -A:dev:test -J-XX:-OmitStackTraceInFastThrow -J-Dcom.sun.management.jmxremote -J-Dcom.sun.management.jmxremote.port=9099 -J-Dcom.sun.management.jmxremote.rmi.port=9099 -J-Dcom.sun.management.jmxremote.authenticate=false -J-Dcom.sun.management.jmxremote.ssl=false -J-Djava.rmi.server.hostname=localhost -M -m brainard.dev
cljs: clj -A:shadow:dev -J-XX:-OmitStackTraceInFastThrow -M -m shadow.cljs.devtools.cli watch dev | grep --color=never -v DEBUG
sass: npx sass --watch resources/scss/main.scss resources/public/css/main.css
