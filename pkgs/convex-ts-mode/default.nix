{
  lib,
  fetchFromGitHub,
  emacsPackages,
  version,
  rev,
  hash,
}:

emacsPackages.trivialBuild {
  pname = "convex-ts-mode";
  inherit version;

  src = fetchFromGitHub {
    owner = "crimeminister";
    repo = "convex-ts-mode";
    inherit rev hash;
  };

  meta = with lib; {
    description = "Emacs major mode for Convex Lisp using tree-sitter";
    homepage = "https://github.com/crimeminister/convex-ts-mode";
    license = licenses.mit;
    maintainers = [ ];
  };
}
