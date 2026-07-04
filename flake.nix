{
  description = "Android Launcher dev environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";
    devenv.url = "github:cachix/devenv";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = inputs:
    inputs.flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import inputs.nixpkgs {
          inherit system;
          config.allowUnfree = true;
          config.android_sdk.accept_license = true;
        };

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "13.0";
          platformToolsVersion = "35.0.1";
          buildToolsVersions = [ "34.0.0" ];
          platformVersions = [ "34" ];
          includeEmulator = false;
          includeSources = false;
          includeSystemImages = false;
          useGoogleAPIs = false;
        };
      in
      {
        devShells.default = inputs.devenv.lib.mkShell {
          inherit inputs pkgs;
          modules = [
            ({ pkgs, config, ... }: {
              packages = with pkgs; [
                jdk17
                androidComposition.androidsdk
                git
              ];

              env.ANDROID_HOME = "${androidComposition.androidsdk}/libexec/android-sdk";
              env.JAVA_HOME = "${pkgs.jdk17.home}";

              enterShell = ''
                export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
                echo "📱 ANDROID_HOME=$ANDROID_HOME"
                echo "☕ JAVA_HOME=$JAVA_HOME"
                java -version 2>&1 | head -n 1

                if [ ! -f gradlew ]; then
                  echo ""
                  echo "⚠️  gradlew missing. Bootstrap it with:"
                  echo "   gradle wrapper --gradle-version 8.4"
                fi
              '';
            })
          ];
        };
      });
}
