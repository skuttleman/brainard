# Brainard

[Brainard](https://www.imdb.com/title/tt0054594/characters/nm0534045) is a tool for helping absent-minded people collect
their thoughts now, so they can forget them later.

## Dependencies

- Install [Clojure runtime](https://clojure.org/guides/getting_started)

## Run

```bash
$ clj -m brainard.core
```

## Dev: Run with a REPL

```bash
$ clj -e "(require 'brainard.core)(in-ns 'brainard.core)(def system (sys/start! \"duct.edn\"))" -r
```
