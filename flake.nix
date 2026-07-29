{
  description = "Android Launcher dev environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";
    flake-utils.url = "github:numtide/flake-utils/11707dc2f618dd54ca8739b309ec4fc024de578b";
  };

  outputs = { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
          config.android_sdk.accept_license = true;
        };

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "13.0";
          platformToolsVersion = "37.0.0";
          buildToolsVersions = [ "34.0.0" ];
          platformVersions = [ "34" ];
          includeEmulator = false;
          includeSources = false;
          includeSystemImages = false;
          useGoogleAPIs = false;
        };
      in
      {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jdk17
            gradle
            androidComposition.androidsdk
            git
          ];

          ANDROID_HOME = "${androidComposition.androidsdk}/libexec/android-sdk";
          JAVA_HOME = "${pkgs.jdk17.home}";

          shellHook = ''
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
        };
      });
}
