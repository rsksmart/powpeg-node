# Quick Setup for macos x64

In case you would like to try a fool-proof setup for macos from scratch, you can try the following steps.
You can also skip some of the steps if it is not applicable, for example, you don't need to install Java 1.8 if you have it already installed.

You can also try your own variations. But the example provided here makes it easy for anybody to setup a powpeg node from scratch in an easy way.

## Steps

1. Install Java OpenJDK 1.8 from the binaries (if it is not installed yet)
    1. You can download it from here: `https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u-2021-05-08-08-14/OpenJDK8U-jdk_x64_mac_hotspot_2021-05-08-08-14.tar.gz`
        1. `cd` to the directory where you want to download it. It could be like `cd /Library/Java/JavaVirtualMachines/`
        2. Download it like this: `sudo curl -o adoptopenjdk-8.tar.gz -L https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u-2021-05-08-08-14/OpenJDK8U-jdk_x64_mac_hotspot_2021-05-08-08-14.tar.gz`
        3. Unzip it like this: `sudo tar -zxvf adoptopenjdk-8.tar.gz`
        4. The unzipped directory could be named like this `jdk8u302-b01`. Run an `ls` command to see it.
    2. Add it to the `.bash_profile`.
        1. Open the `.bash_profile` file with `nano` like this `nano ~/.bash_profile` and add the following 2 `export` lines:
        2. `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk8u302-b01/Contents/Home/bin`
        3. `export PATH="$JAVA_HOME/bin:$PATH"`
        4. Save it and run the command: `source ~/.bash_profile` and restart the terminal. After this, the `java -version` command should return info.
2. Install bitcoind
    1. `cd` to the directory where you want to download it. It could be `/Library/Bitcoin` (if it doesn't exit, you can create it running the command `sudo mkdir /Library/Bitcoin`)
    2. You can download version 0.18.1 (ideal for regtest) from here: `https://bitcoincore.org/bin/bitcoin-core-0.18.1/bitcoin-0.18.1-osx64.tar.gz`
    3. You can download version 24.0.1 `https://bitcoincore.org/bin/bitcoin-core-24.0.1/bitcoin-24.0.1-x86_64-apple-darwin.tar.gz`
    4. Download it like this: `sudo curl -o bitcoind18.tar.gz -L https://bitcoincore.org/bin/bitcoin-core-0.18.1/bitcoin-0.18.1-osx64.tar.gz`
    5. Unzip it like this: `sudo tar -zxvf bitcoind18.tar.gz`
    6. Run the `ls` command to see the actual name of the unzipped directory. It should be like `bitcoin-0.18.1`
    7. Add the path `export PATH="$PATH:/Library/Bitcoin/bitcoin-0.18.1/bin"` to the `/.zshrc` file
        1. Open the `.zshrc` file with nano like this: `nano ~/.zshrc` and add the following line in it and save it: `export PATH="$PATH:/Library/Bitcoin/bitcoin-0.18.1/bin"`
    8. Run `source ~/.zshrc` command, close and reopen the terminal.
    9. Running an instance of bitcoind
        1. To run it in regtest mode, run this command: `bitcoind -deprecatedrpc=generate -addresstype=legacy -regtest -printtoconsole -server -rpcuser=rsk -rpcpassword=rsk -rpcport=18332 -txindex -datadir=/Library/Bitcoin/data`
        2. You can also put this command into an `bitcoin-regtest.sh` file, make it executable with `sudo chmod +x bitcoin-regtest.sh` and run it like `./bitcoin-regtest.sh`
        3. To run it in regular mode, simply run the command: `bitcoind`
    10. Generate some blocks
        1. To generate, for example, 200 regtest bitcoin blocks, run: `./btc-regtest.sh generate 200`
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
