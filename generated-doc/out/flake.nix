{
  description = "Python shell flake";

  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
  inputs.flake-utils.url = "github:numtide/flake-utils";

  outputs = { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        python = pkgs.python3.withPackages (ps: with ps; [
          pip
        ]);
      in 
      {
        devShell = pkgs.mkShell {
          buildInputs = [ python ];
  
            shellHook = ''
            # Create a Python virtual environment and activate it
            python -m venv .env
            source .env/bin/activate
            # Install the Python dependencies from requirements.txt
            if [ -f requirements.txt ]; then
              pip install -r requirements.txt
            fi
          '';
        };
      }
    );
} 
