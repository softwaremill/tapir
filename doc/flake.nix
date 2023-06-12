{
  description = "Python shell flake";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";

    mach-nix.url = "github:davhau/mach-nix";
  };

  outputs = { self, nixpkgs, mach-nix, flake-utils, ... }:
    let
      pythonVersion = "python37";
    in
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        mach = mach-nix.lib.${system};

        pythonEnv = mach.mkPython {
          python = pythonVersion;
          requirements = builtins.readFile ./requirements.txt;
        };
      in
      {
        devShells.default = pkgs.mkShellNoCC {
          packages = [ pythonEnv ];

          shellHook = ''
            export PYTHONPATH="${pythonEnv}/bin/python"
          '';
        };
      }
    );
}
