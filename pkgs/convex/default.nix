{
  lib,
  fetchFromGitHub,
  jre,
  makeWrapper,
  maven,
  version,
  hash,
  mvnHash,
}:

(maven.buildMavenPackage rec {
  pname = "convex";
  inherit version;
  buildOffline = true;

  src = fetchFromGitHub {
    owner = "Convex-Dev";
    repo = "convex";
    tag = "${version}";
    inherit hash;
  };

  inherit mvnHash;

  nativeBuildInputs = [ makeWrapper ];

  mvnParameters = "-DskipTests";

  manualMvnArtifacts = [
    "org.apache.maven.plugins:maven-install-plugin:3.1.2"
    "org.apache.maven.plugins:maven-assembly-plugin:3.7.1"
    "io.javalin.community.openapi:openapi-annotation-processor:7.2.2"
  ];

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin $out/share/convex
    install -Dm644 convex-integration/target/convex.jar $out/share/convex/convex.jar

    makeWrapper ${jre}/bin/java $out/bin/convex \
      --add-flags "-jar $out/share/convex/convex.jar"

    runHook postInstall
  '';

  passthru.module = ./module.nix;

  meta = with lib; {
    description = "Decentralized platform for the Internet of Value";
    homepage = "https://convex.world";
    license = {
      fullName = "Convex Public Licence v0.9";
      url = "https://github.com/Convex-Dev/convex/blob/develop/LICENSE.md";
    };
    maintainers = [ ];
  };
}).overrideAttrs
  (oldAttrs: {
    buildPhase = ''
      runHook preBuild
      mvnDeps=$(cp -dpR $fetchedMavenDeps/.m2 ./ && chmod +w -R .m2 && pwd)
      runHook afterDepsSetup
      mvn install -o -nsu "-Dmaven.repo.local=$mvnDeps/.m2" $mvnParameters
      runHook postBuild
    '';
  })
