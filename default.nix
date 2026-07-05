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
    version = "0.8.6";
    hash = "sha256-yNIKejAAx2IvKATnX6YTIuqoF45dd9+ci1p/VeTutrg=";
    mvnHash = "sha256-UqXlc/hlrwaEqN8M1XigqDUIBFnIWt/vWaNt4SMnVO8=";
  };

  # Tree-sitter parser for Convex Lisp.
  tree-sitter-convex-lisp = self.callPackage ./pkgs/tree-sitter-convex-lisp {
    version = "0.1.0";
    rev = "1a8aafa9cebe1fa220e21da0ac3ca8a2bdcfd802";
    hash = "sha256-jvvbeuhEF6jzFQPZUHSBGFHqEqTGF4FLUwpvbNkMWd8=";
  };

  # Emacs major mode for Convex Lisp using tree-sitter.
  convex-ts-mode = self.callPackage ./pkgs/convex-ts-mode {
    version = "0.1.0";
    rev = "41323a24676a46f568bca1c031ffaf7f4d798e84";
    hash = "sha256-dYeFyjCcaIanyfMltEfmmXGHLAIHaAfFAnAmnWV3PAg=";
  };
}
