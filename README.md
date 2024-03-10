# Brainard

[Brainard](https://www.imdb.com/title/tt0054594/characters/nm0534045) is a tool for helping absent-minded people collect
their thoughts now, so they can forget them later.

## Dependencies

- Install [Clojure runtime](https://clojure.org/guides/getting_started)
- Install [Foreman](http://blog.daviddollar.org/2011/05/06/introducing-foreman.html)
- Install [NodeJs](https://nodejs.org/en/download/package-manager/)
- Install [JDK](https://docs.oracle.com/en/java/javase/16/install/overview-jdk-installation.html#GUID-8677A77F-231A-40F7-98B9-1FD0B48C346A)
- Install [Sass](https://sass-lang.com/install)

## Dev

```bash
$ foreman start
```



- tree
  - edit node text
- errors framework
- cleanup localstorage script
- save commands/events to datascript for debugging
  - rewind store
- web (split middleware) framework











- ^{:arglists '([{:as attrs}])}
- whet framework
- whet tests





















# CLJ + CLJS = GOAT
- libs
  - core.async!
  - bidi
  - malli *
  - timbre *
  - re-frame (defacto)
  - hiccup | reagent †
  - those not seen here
    - camel-snake-kebab
    - medley
    - integrant
    - core.match
    - clj-http | cljs-http
    - transit-clj | transit-cljs
      +- meet brainard
  - utils
  - nREPLs
  - EDN!!!
    +- specs
    +- routing
  - change on the fly
    +- hydrating
  - hiccup + reagent
  - stubs
  - limitations
    - cljc-friendly components
    - hiccup+reagent-friendly components
      - life cycle ---> initial page render
