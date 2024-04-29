# Quick Setup for macOS x64

The following steps provide a quick setup guide for macOS x64. Some of the steps may be skipped if it is not applicable. For example, no need to install Java 1.8 if already installed.

## Steps

1. Install Java OpenJDK 1.8 from the binaries (if not installed yet)
    1. Download link: `https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u-2021-05-08-08-14/OpenJDK8U-jdk_x64_mac_hotspot_2021-05-08-08-14.tar.gz`
        1. `cd` into the directory to download it. Example: `cd /Library/Java/JavaVirtualMachines/`
        2. Download: `sudo curl -o adoptopenjdk-8.tar.gz -L https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u-2021-05-08-08-14/OpenJDK8U-jdk_x64_mac_hotspot_2021-05-08-08-14.tar.gz`
        3. Unzip: `sudo tar -zxvf adoptopenjdk-8.tar.gz`
    2. Add JAVA_HOME environment variable to bash
        1. Open `.bash_profile` file with some text editor. Example: `nano ~/.bash_profile`
        2. Add the following 2 `export` lines:
           1. `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk8u302-b01/Contents/Home/bin` (replace the path with the actual path)
           2. `export PATH="$JAVA_HOME/bin:$PATH"`
        3. Restart the terminal. Run command `java -version` to validate the system detects the installed version of Java.
2. Install bitcoind
    1. `cd` into the directory to download it. Example: `cd /Library/Bitcoin/`
    2. Download version 24.0.1: `sudo curl -o bitcoind18.tar.gz -L https://bitcoincore.org/bin/bitcoin-core-0.18.1/bitcoin-0.18.1-osx64.tar.gz`
    3. Unzip: `sudo tar -zxvf bitcoind18.tar.gz`
    4. Add bitcoind to bash PATH 
       1. Open `.bash_profile` file with some text editor. Example: `nano ~/.bash_profile`
       2. Add the following line: `export PATH="$PATH:/Library/Bitcoin/bitcoin-0.18.1/bin"` (replace the path with the actual path)
       3. Restart the terminal
    5. Running an instance of bitcoind
        1. To run in regtest mode, run the following command: `bitcoind -deprecatedrpc=generate -addresstype=legacy -regtest -printtoconsole -server -rpcuser=rsk -rpcpassword=rsk -rpcport=18332 -txindex -datadir=/Library/Bitcoin/data`
        2. To simplify, the command can be included in a bash file `bitcoin-regtest.sh`. To make it executable run `sudo chmod +x bitcoin-regtest.sh`
    6. Generate some blocks
        1. To generate, for example, 200 regtest bitcoin blocks, run: `./btc-regtest.sh generate 200` // TODO: where did btc-regtest.sh file come from? 
3. Create a `powpeg-project` folder anywhere you like
    1. For example: `mkdir /Library/powpeg-project`
4. Setup the rskj project
    1. `cd` to the `powpeg-project` directory: `cd /Library/powpeg-project`
    2. Clone it from here: `https://github.com/rsksmart/rskj`
        1. `git clone https://github.com/rsksmart/rskj.git`
        2. `cd` to the cloned `rskj` directory: `cd rskj`
    3. Run the `configure.sh`
        1. You will probably have to make it executable first with: `sudo chmod +x configure.sh`
        2. And run it from a terminal like this: `./configure.sh`
5. Setup this project (powpeg-node: `https://github.com/rsksmart/powpeg-node`)
    1. `cd` to the `powpeg-project` directory: `cd /Library/powpeg-project`
    2. `git clone https://github.com/rsksmart/powpeg-node.git`
    3. `cd` to the cloned `powpeg-node` directory: `cd powpeg-node`
    4. Run the `configure.sh` file present in the root directory
        1. You will probably have to make it executable with: `sudo chmod +x configure.sh`
        2. And run it from a terminal like this: `./configure.sh`
    5. Make a copy of the `development-settings.gradle.sample` file and rename it to `DONT-COMMIT-settings.gradle`
        1. Remove the line `# Sample configuration to build rskj from the directory /home/user/another/dir/rskj`
        2. Remove the line `# Rename it to DONT-COMMIT-settings.gradle for use in your local environment`
        3. Replace the `'/home/user/another/dir/rskj/'` with the relative or absolute path where the `rskj` project is, for example: `/Library/powpeg-project/rskj`
    6. Create a `fed.conf` file and set it up
        1. Check the config file sample in `src/main/resources/config/fed-sample.conf`, copy it, rename it to `fed.conf` and update it as you need.
    7. Optionally create a `logback.xml` file for the logging
        1. Check the config file sample in `src/main/resources/config/logback-sample.xml`, copy it, rename it to `logback.xml` and update it as you need, adding or removing classes and their log level.
    8. Build the powpeg project
        1. Run: `./gradlew clean build`
        2. To build it without running the tests, run: `./gradlew clean build -x test`
        3. `cd` into `/Library/powpeg-project/powpeg-node/build/libs/` directory to see the version of the `federate-node-SNAPSHOT-<version>-all.jar` file, so you can run it in the following step.
    9. Run the project
        1. Resetting the rsk db (replace `<version>` with the actual version of the `.jar`): `java -cp /Library/powpeg-project/powpeg-node/build/libs/federate-node-SNAPSHOT-<version>-all.jar -Drsk.conf.file=/Library/powpeg-project/powpeg-node/src/main/resources/config/fed-sample.conf -Dlogback.configurationFile=/Library/powpeg-project/powpeg-node/logback.xml co.rsk.federate.FederateRunner --regtest --reset`
        2. Without resetting the rsk db (replace `<version>` with the actual version of the `.jar`): `java -cp /Library/powpeg-project/powpeg-node/build/libs/federate-node-SNAPSHOT-<version>-all.jar -Drsk.conf.file=/Library/powpeg-project/powpeg-node/src/main/resources/config/fed-sample.conf -Dlogback.configurationFile=/Library/powpeg-project/powpeg-node/logback.xml co.rsk.federate.FederateRunner --regtest`
