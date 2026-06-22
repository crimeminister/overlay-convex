# overlay-convex

A Nixpkgs overlay tailored for the Convex Virtual Machine (CVM) ecosystem and its development tools.

This repository provides custom package definitions and overrides for the Nix package manager to ease integration, orchestration, and development of Convex-related applications.

---

## 📦 Packages Included

This overlay defines the following packages in `pkgs/`:

| Package | Directory | Description | Quick Links |
| :--- | :--- | :--- | :--- |
| **`convex`** | [`pkgs/convex/`](file:///home/robert/git/overlay-convex/pkgs/convex) | Decentralized platform for the Internet of Value. | [convex.world](https://convex.world/) |
| **`convex-ts-mode`** | [`pkgs/convex-ts-mode/`](file:///home/robert/git/overlay-convex/pkgs/convex-ts-mode) | Emacs major mode for Convex Lisp using tree-sitter. | [convex-ts-mode repo](https://github.com/crimeminister/convex-ts-mode) |
| **`tree-sitter-convex-lisp`** | [`pkgs/tree-sitter-convex-lisp/`](file:///home/robert/git/overlay-convex/pkgs/tree-sitter-convex-lisp) | Tree-sitter grammar for Convex Lisp. | [tree-sitter-convex-lisp repo](https://github.com/crimeminister/tree-sitter-convex-lisp) |

---

## 🛠️ Usage

To import this overlay into your Nix expressions, add it to your `overlays` list:

```nix
let
  pkgs = import <nixpkgs> {
    overlays = [ (import ./default.nix) ];
  };
in
# Now you can reference package names from the overlay, for example:
pkgs.convex
```

### Building Packages

To build a specific package from this overlay directly using `nix-build`:

```bash
nix-build -E 'let pkgs = import <nixpkgs> { overlays = [ (import ./default.nix) ]; }; in pkgs.<package-name>'
```

For example, to build `convex`:
```bash
nix-build -E 'let pkgs = import <nixpkgs> { overlays = [ (import ./default.nix) ]; }; in pkgs.convex'
```

---

## 🧑‍💻 Development and Maintenance

Detailed instructions for contributors and AI agents are documented in [.antigravity.md](file:///home/robert/git/overlay-convex/.antigravity.md).

### Code Formatting

We enforce strict formatting rules across the repository:
- **Nix files**: Format with `nixfmt`.
- **Clojure/Babashka files**: Format using `@chrisoakman/standard-clojure-style` (e.g. `npx @chrisoakman/standard-clojure-style fix <paths>`).

### Babashka Tasks

This repository defines tasks in [bb.edn](file:///home/robert/git/overlay-convex/bb.edn) using Babashka:

- **Run all update checks**: Check if new versions of packages are available upstream.
  ```bash
  bb check:update
  ```
- **Check updates for a single package**:
  ```bash
  bb check:update:<package-name>
  ```
  *(Supported packages: `convex`, `convex-ts-mode`, `tree-sitter-convex-lisp`)*
- **Run utility tests**: Run Clojure unit tests for the helper namespaces in [`test/`](file:///home/robert/git/overlay-convex/test).
  ```bash
  bb test
  ```
