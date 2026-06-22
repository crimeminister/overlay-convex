# This is the entry point of the overlay.
# It's a function that takes two arguments:
# - self: The final set of packages after all overlays are applied.
# - super: The original set of packages before this overlay is applied.
self: super:

let
  inherit (super)
    stdenv
    lib
    pkg-config
    makeWrapper
    ripgrep
    nodejs
    libsecret
    ;
in
{
  # Decentralized platform for the Internet of Value.
  convex = self.callPackage ./pkgs/convex {
    version = "0.8.5";
    hash = "sha256-4mvrcRvf/M0XI3zSOziheOqU+KLE12ejP7DDn+Ssic0=";
    mvnHash = "sha256-GP/Uhef9yeGs9BIS+PlVq3jTHDOZCKmwBtrpsMuNYZA=";
  };

  # Tree-sitter parser for Convex Lisp.
  tree-sitter-convex-lisp = self.callPackage ./pkgs/tree-sitter-convex-lisp {
    version = "0.1.0";
    rev = "ba70b9bf0aa83f800f50fda887ed6a0b8b051e94";
    hash = "sha256-9jZ7blPehK+6Z2o4U7Id6WslpsfbEPKSt97SFE2ULow=";
  };

  # Emacs major mode for Convex Lisp using tree-sitter.
  convex-ts-mode = self.callPackage ./pkgs/convex-ts-mode {
    version = "0.1.0";
    rev = "b38fb95d2386cb4fa8ac1dda5829bb5d50623cef";
    hash = "sha256-eMnXxyC7gFZOqa55meScIIeupdPdU8iLxvnJ2GvNRBU=";
  };
}
