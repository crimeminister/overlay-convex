{
  lib,
  fetchFromGitHub,
  tree-sitter,
  version,
  rev,
  hash,
}:

tree-sitter.buildGrammar {
  language = "convex_lisp";
  inherit version;

  src = fetchFromGitHub {
    owner = "crimeminister";
    repo = "tree-sitter-convex-lisp";
    inherit rev hash;
  };

  meta = with lib; {
    description = "Tree-sitter grammar for Convex Lisp";
    homepage = "https://github.com/crimeminister/tree-sitter-convex-lisp";
    license = licenses.mit;
    maintainers = [ ];
  };
}
