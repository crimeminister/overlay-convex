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
    version = "0.8.18";
    hash = "sha256-7kFL1SsQDU8JmAJxEqgD0QZVbklhp7GdhbU58DwLwjY=";
    # In newer nixpkgs (e.g. nixos-unstable with Maven >= 3.9.16), buildMavenPackage
    # additionally prunes volatile resolver prefix indexes (prefixes-*.txt in .m2/.meta),
    # which alters the fixed-output derivation hash compared to older nixpkgs (e.g. 26.05).
    mvnHash =
      if lib.versionAtLeast super.maven.version "3.9.16" then
        "sha256-hcbph8FhaDYQigbCKHYdCqyHNGAGjLge5xW78KLFCWk="
      else
        "sha256-uX0PG4snpDIRDleeibNsUj9h5eMX3pa8Gz+bhuK+JV0=";
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
