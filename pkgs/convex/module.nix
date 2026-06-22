{
  config,
  pkgs,
  lib,
  ...
}:

with lib;

let
  cfg = config.services.convex;

  bootstrapScript = pkgs.writeShellScriptBin "convex-bootstrap" ''
    set -euo pipefail

    export PATH="${cfg.package}/bin:${pkgs.coreutils}/bin:$PATH"

    if [ -f ${cfg.dataDir}/.initialized ]; then
      echo "Convex database already initialized. Skipping."
      exit 0
    fi

    ${optionalString (cfg.bootstrap.storepassFile != null) ''
      export CONVEX_KEYSTORE_PASSWORD="$(cat "${cfg.bootstrap.storepassFile}")"
    ''}
    ${optionalString (cfg.bootstrap.keypassFile != null) ''
      export CONVEX_KEY_PASSWORD="$(cat "${cfg.bootstrap.keypassFile}")"
    ''}

    if [ "${cfg.bootstrap.mode}" = "genesis" ]; then
      echo "Bootstrapping Convex Network Genesis..."
      convex peer genesis \
        --keystore "${cfg.keystore}" \
        -e "${cfg.dataDir}/etch.db" \
        ${optionalString (cfg.key != null) "-k ${cfg.key}"} \
        ${optionalString (cfg.peerKey != null) "--peer-key ${cfg.peerKey}"} \
        ${
          optionalString (
            cfg.bootstrap.governanceKey != null
          ) "--governance-key ${cfg.bootstrap.governanceKey}"
        } \
        --noninteractive
    elif [ "${cfg.bootstrap.mode}" = "create" ]; then
      echo "Registering Peer on Convex Network..."
      convex peer create \
        --keystore "${cfg.keystore}" \
        -e "${cfg.dataDir}/etch.db" \
        ${optionalString (cfg.key != null) "-k ${cfg.key}"} \
        ${optionalString (cfg.peerKey != null) "--peer-key ${cfg.peerKey}"} \
        --host "${if cfg.host != null then cfg.host else "peer.convex.live"}" \
        --port ${toString cfg.peerPort} \
        --noninteractive
    fi

    touch "${cfg.dataDir}/.initialized"
    echo "Convex bootstrapping complete!"
  '';

  startArgs = [
    "--keystore"
    cfg.keystore
    "-e"
    "${cfg.dataDir}/etch.db"
    "--peer-port"
    (toString cfg.peerPort)
    "--api-port"
    (toString cfg.apiPort)
    "--noninteractive"
  ]
  ++ optional (cfg.host != null) "--host ${cfg.host}"
  ++ optional (cfg.key != null) "-k ${cfg.key}"
  ++ optional (cfg.peerKey != null) "--peer-key ${cfg.peerKey}"
  ++ optional cfg.norest "--norest"
  ++ optional cfg.reset "--reset"
  ++ cfg.extraArgs;
in
{
  options.services.convex = {
    enable = mkEnableOption "Enable Convex peer network node service";

    package = mkPackageOption pkgs "convex" { };

    user = mkOption {
      type = types.str;
      default = "convex";
      description = "User account under which the Convex peer service runs.";
    };

    group = mkOption {
      type = types.str;
      default = "convex";
      description = "Group under which the Convex peer service runs.";
    };

    dataDir = mkOption {
      type = types.str;
      default = "/var/lib/convex";
      description = "The state directory where Convex stores its keystore and etch database.";
    };

    peerPort = mkOption {
      type = types.port;
      default = 18888;
      description = "Port number for the peer server to listen on.";
    };

    apiPort = mkOption {
      type = types.port;
      default = 8080;
      description = "Port number for the REST API server to listen on.";
    };

    host = mkOption {
      type = types.nullOr types.str;
      default = null;
      description = "Hostname/IP of the remote peer to connect to (defaults to peer.convex.live for joining, or none to disable remote peer connection).";
    };

    keystore = mkOption {
      type = types.str;
      default = "${cfg.dataDir}/keystore.pfx";
      description = "Path to the keystore file.";
    };

    key = mkOption {
      type = types.nullOr types.str;
      default = null;
      description = "Hex public key prefix to use from the keystore.";
    };

    peerKey = mkOption {
      type = types.nullOr types.str;
      default = null;
      description = "Peer public key prefix to use from the keystore.";
    };

    norest = mkOption {
      type = types.bool;
      default = false;
      description = "Disable the REST API server.";
    };

    reset = mkOption {
      type = types.bool;
      default = false;
      description = "Reset and delete the etch database if it exists on startup.";
    };

    extraArgs = mkOption {
      type = types.listOf types.str;
      default = [ ];
      description = "Extra command-line arguments passed to the convex peer start command.";
    };

    secretsFile = mkOption {
      type = types.nullOr types.path;
      default = null;
      description = ''
        Path to an EnvironmentFile containing secrets such as:
        - CONVEX_KEYSTORE_PASSWORD
        - CONVEX_KEY_PASSWORD
        - CONVEX_PEER_KEY_PASSWORD
      '';
    };

    bootstrap = {
      mode = mkOption {
        type = types.enum [
          "genesis"
          "create"
          "existing"
        ];
        default = "existing";
        description = ''
          How to bootstrap the peer's state database (Etch) on first startup:
          - "genesis": Initialize a new genesis block for a new Convex network.
          - "create": Register as a peer on an existing network (requires an active remote peer specified by services.convex.host).
          - "existing": Skip automatic bootstrapping; assume the Etch database is already configured.
        '';
      };

      governanceKey = mkOption {
        type = types.nullOr types.str;
        default = null;
        description = "Network Governance Key (Ed25519 public key in hex) used when bootstrap.mode is 'genesis'.";
      };

      storepassFile = mkOption {
        type = types.nullOr types.path;
        default = null;
        description = "Path to a file containing the keystore integrity password, used during bootstrapping.";
      };

      keypassFile = mkOption {
        type = types.nullOr types.path;
        default = null;
        description = "Path to a file containing the key password, used during bootstrapping.";
      };
    };
  };

  config = mkIf cfg.enable {
    users.users.${cfg.user} = {
      isSystemUser = true;
      group = cfg.group;
      home = cfg.dataDir;
      createHome = false;
      description = "Convex peer service daemon user";
    };

    users.groups.${cfg.group} = { };

    systemd.services = {
      convex-bootstrap = mkIf (cfg.bootstrap.mode != "existing") {
        description = "Bootstrap Convex Database and Keystore";
        wantedBy = [ "multi-user.target" ];
        after = [ "network.target" ];

        serviceConfig = {
          Type = "oneshot";
          User = cfg.user;
          Group = cfg.group;
          RemainAfterExit = true;

          ExecStartPre = "${pkgs.coreutils}/bin/mkdir -p ${cfg.dataDir}";
          ExecStart = "${bootstrapScript}/bin/convex-bootstrap";

          StateDirectory = "convex";
          WorkingDirectory = cfg.dataDir;
          ReadWritePaths = [ cfg.dataDir ];
        };
      };

      convex = {
        description = "Convex Peer Node";
        wantedBy = [ "multi-user.target" ];
        after = [
          "network.target"
        ]
        ++ optional (cfg.bootstrap.mode != "existing") "convex-bootstrap.service";
        requires = optional (cfg.bootstrap.mode != "existing") "convex-bootstrap.service";

        serviceConfig = {
          ExecStart = "${cfg.package}/bin/convex peer start ${concatStringsSep " " startArgs}";
          User = cfg.user;
          Group = cfg.group;
          Restart = "always";
          RestartSec = "10s";

          StateDirectory = "convex";
          WorkingDirectory = cfg.dataDir;
          NoNewPrivileges = true;
          PrivateTmp = true;
          ProtectSystem = "strict";
          ProtectHome = true;
          ReadWritePaths = [ cfg.dataDir ];
        }
        // optionalAttrs (cfg.secretsFile != null) {
          EnvironmentFile = cfg.secretsFile;
        };
      };
    };
  };
}
