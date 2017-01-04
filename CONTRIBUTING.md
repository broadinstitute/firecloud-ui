# Contributing to Firecloud UI

## ClojureScript Style Conventions

For ClojureScript code, we follow the [Clojure Style Guide](https://github.com/bbatsov/clojure-style-guide) with exceptions noted here (subsections correspond to those in the Clojure Style Guide).

In Atom, the Lisp Paredit package formats code correctly. In IntelliJ, the Cursive plugin formats code correctly with a few configuration changes.

### Source Code Layout & Organization

Where feasible, avoid making lines longer than 100 characters. We feel the 80-character limit in the style guide is more restrictive than necessary.

While shorter functions are preferred, we do not adhere to the guide's suggestion, "Avoid functions longer than 10 LOC (lines of code). Ideally, most functions will be shorter than 5 LOC."

## DOM Node (HTML) Conventions

Prefer "rem" units over "em", "ex", "px", etc. for size values since these adjust according the user's selected font size and are generally easier to reason about.

## React Conventions

We use React components to encapsulate the style and behavior of individual page elements, then compose these components together to create a complete UI. We do not generally use separate CSS files for the [reasons outlined in this slide deck](https://speakerdeck.com/vjeux/react-css-in-js).

### Avoid Passing State

A component's state is considered private to that component. Do not pass the `state` atom to another component.

DO NOT DO THIS:
```clojure
(r/defc Foo ...)

(r/defc Bar
  {:render
   (fn [{:keys [state]}]
     [Foo {:parent-state state}])})
```

Instead, do something like this:
```clojure
(r/defc Foo ...)

(r/defc Bar
  {:render
   (fn [{:keys [state]}]
     [Foo {:handle-some-action (fn [value] (swap! state ...))}])})
```

### React Elements are not DOM Nodes

```clojure
(r/defc Foo
  {:render
   (fn []
     [:div {}
;;   ^ This is not a real <div> element. It is a vector that will be
;;     turned into a React Element by the function that calls `render`
;;     on this component.
      (r/create-element :div {})])})
;;     ^ Likewise, this is not a real <div> either. This creates a
;;       React Element directly.
```

In JavaScript, you can do things like:
```javascript
var myDiv = document.createElement('div', ...);
myDiv.focus()
```
or
```javascript
var myDiv = document.createElement('div', ...);
SomeThirdPartyLibraryThatTakesADomNode(myDiv)
```

React Elements may not be substituted for DOM nodes in these cases. You must use a `ref` (see React's documentation) to obtain access to the DOM node once React has rendered it into the browser window.

### State Set -> Read is Unreliable

[State updates may be asynchronous](https://facebook.github.io/react/docs/state-and-lifecycle.html#state-updates-may-be-asynchronous), meaning that `state` will not contain new values when set, but instead will have those values after the re-render. For example:

```clojure
(swap! state assoc :foo 17)
(get @state :foo) ; <- :foo is not 17 here!
```

### Avoid Infinite Loops

Changing state causes a re-render. If you change state in a lifecycle method such as `component-did-update`, this will re-render, which will call `component-did-update` again, resulting in a loop.
