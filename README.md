# Welcome to RskJ Powpeg Node



## About

Powpeg node is a specialized rskj node which interacts with both Rootstock and Bitcoin.
This node is used by Rootstock PowPeg signatories to interact with the Bridge contract and to broadcast peg-out transactions to Bitcoin.


## Software Requirements

1. Java JDK 1.8
2. Bitcoin Core daemon (bitcoind) 24.0.1
3. A Java compatible IDE. Recommended [IntelliJ IDEA](https://www.jetbrains.com/idea/download) as this guide covers the setup with it


**Not sure how to install any of these? See [software installation help](#software-installation-help)**


## Software installation help
Disclaimer: this documentation will be specific for macOS operating system.

### Quick Setup

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

### Setup Details

#### **Java JDK 1.8**

Although optional we recommend to install jenv to manage different Java versions, to do that run: `brew install jenv`

Add jenv to your path as it says on the output of jenv brew installation.

```
To activate jenv, add the following to your ~/.zshrc:
  export PATH="$HOME/.jenv/bin:$PATH"
  eval "$(jenv init -)"
```

Download jdk and install it.

Add the just downloaded jdk to the jenv versions, run: `jenv add /Library/Java/JavaVirtualMachines/<JDK8-DIRECTORY>/Contents/Home/`

If you are using another java version in other projects, run `jenv local 1.8.0.352`. Otherwise you can set it globally by running: `jenv global 1.8.0.352`

To check that it worked, open a new terminal instance and run `java -version` command. It should output something like 

```
java version "1.8.0_351"
Java(TM) SE Runtime Environment (build 1.8.0_351-b10)
Java HotSpot(TM) 64-Bit Server VM (build 25.351-b10, mixed mode)
```

*If not, try closing the terminal and open a new one cause sometimes it stays cached.*

#### Bitcoind

To install version `24.0.1` follow instructions listed [here](https://bitcoincore.org/en/releases/24.0.1/)

To validate it run `bitcoind -daemon`.

It should output ```Bitcoin server starting```

Run `bitcoin-cli stop` afterwards.

#### Setting up the project
Create a directory (for example, “powpeg-project”) to hold the rskj node, the powpeg node and further configurations.

##### Fetch the code

Inside the `powpeg-project` directory clone the powpeg-node repository.

```bash
git clone https://github.com/rsksmart/powpeg-node
```

```bash
cd powpeg-node
```

##### [Optional] Verify the code

Before anything, you must ensure the security chain of the source code. For that, you must go through the following steps. For Linux based OS (Ubuntu for example) it’s recommended to install `gnupg-curl` to download the key through HTTPS.

1. Download sec channel public key

```bash
gpg --keyserver https://secchannel.rsk.co/SUPPORT.asc --recv-keys A6DBEAC640C5A14B
```

You should get something like:

```
gpg: key A6DBEAC640C5A14B: "IOV Labs Support <support@iovlabs.org>"      imported
gpg: Total number processed: 1
gpg:               imported: 1  (RSA: 1)
```

2. Verify the downloaded key fingerprint

```bash
gpg --finger A6DBEAC640C5A14B
```

You should get something like:

```
pub   rsa4096 2022-05-11 [C]
      1DC9 1579 9132 3D23 FD37  BAA7 A6DB EAC6 40C5 A14B
uid           [ unknown] IOV Labs Support <support@iovlabs.org>sub   rsa4096 2022-05-11 [S]
sub   rsa4096 2022-05-11 [E]
```

3. Verify the `SHA256SUMS.asc` signature

```bash
gpg --verify SHA256SUMS.asc
```

You should get something like:

```
gpg: Signature made Wed May 11 10:50:48 2022 -03
gpg:                using RSA key 1F1AA750373B90D9792DC3217997999EEA3A9079
gpg: Good signature from "IOV Labs Support <support@iovlabs.org>" [unknown]
gpg: WARNING: This key is not certified with a trusted signature!
gpg:          There is no indication that the signature belongs to the owner.
Primary key fingerprint: 1DC9 1579 9132 3D23 FD37  BAA7 A6DB EAC6 40C5 A14B
    Subkey fingerprint: 1F1A A750 373B 90D9 792D  C321 7997 999E EA3A 9079
```

4. Verify the `configure.sh` script


```bash
shasum --check SHA256SUMS.asc
```

You should get something like:

```
configure.sh: OK
sha256sum: WARNING: 19 lines are improperly formatted
```

##### Configure

Run configure script to configure secure environment.

```bash
./configure.sh
```

##### Required configurations

**1. Pegnatory private key**

You will need a private key file to be used by the pegnatory to sign BTC/RSK transactions. Follow these steps to generate it:

- Look for the **[GenNodeKeyId](https://github.com/rsksmart/rskj/blob/master/rskj-core/src/main/java/co/rsk/GenNodeKeyId.java)** class in rskj
- In that file, there is a `generator` variable that works as the seed of the private key.
Set the desired value to it. (For example, `String generator = “federator1”;`)

- Run the class to generate a privateKey, publicKey, publicKeyCompressed, address and nodeId

You should get an output like the following:

```
{
"privateKey": "405d0e226832757955482f9215447ac6306370578a67a4729c6afb71eeed2a94",
"publicKey": "04a7c006420129b2f5d8c03bddef483a13cdbe0c0e6b3c7a8b39f1c9557fd48d3082a8b606601bba9fd2f1870569f694de702171b4848519d2d68f8adc26979c90",
"publicKeyCompressed": "02a7c006420129b2f5d8c03bddef483a13cdbe0c0e6b3c7a8b39f1c9557fd48d30",
"address": "9d5242d00ea7d7b6d20489da0390a8486758570b",
"nodeId": "a7c006420129b2f5d8c03bddef483a13cdbe0c0e6b3c7a8b39f1c9557fd48d3082a8b606601bba9fd2f1870569f694de702171b4848519d2d68f8adc26979c90"
}
```

- Create a file named `reg1.key` with only the private key as the content
- Add permission to the file by running `chmod 400 reg1.key`

---

**2. Configuration file**

Create a `node.conf` file in powpeg-project directory (this exact file’s structure is for local test/regtest only):

```
federator{
    enabled = true
    
    signers {
       BTC {
          type = "keyFile"
          path = "<ABSOLUTE-PATH-TO-reg1.key-FILE>" # This should point to your generated key file
       }
       RSK {
          type = "keyFile"
          path = "<ABSOLUTE-PATH-TO-reg1.key-FILE>" # This should point to your generated key file
       }
       MST {
          type = "keyFile"
          path = "<ABSOLUTE-PATH-TO-reg1.key-FILE>" # This should point to your generated key file
       }
    }
    # peers for the bitcoin network
    bitcoinPeerAddresses = [
        "127.0.0.1:18444" #bitcoind p2p port.
    ]
}
```

**Note:** *optionally you can generate three files, one per each signer to be used (BTC/RSK/MST) but you can use the same key file for BTC, RSK, and MST*

**Important:** When setting up the Bitcoind Node host:port for the powpeg-node you MUST use the p2p port. The powpeg-node will “connect” even if you setup the RPC port, but the connection won’t go through and the powpeg-node won’t start.


---


**Using local source code for RSKj dependency**

To run the Powpeg node using a local version of RSKj instead of relying on the dependencies been resolved by Maven, you will have to add a customization file.


**Directory structure**

Inside the `powpeg-project` directory clone the rskj repository.

```bash
git clone https://github.com/rsksmart/rskj
```

---

Search for `development-settings.gradle.sample` file inside powpeg-node directory, rename it to `DONT-COMMIT-settings.gradle` and make sure it only has the following content. Change `<ABSOLUTE-PATH-TO-RSKJ-SOURCE-CODE>` value to your local RSKj absolute path.

```
includeBuild('<ABSOLUTE-PATH-TO-RSKJ-SOURCE-CODE>') {
    dependencySubstitution {
        all { DependencySubstitution dependency ->
            if (dependency.requested instanceof ModuleComponentSelector
                    && dependency.requested.group == 'co.rsk'
                    && dependency.requested.module == 'rskj-core'
                    && (dependency.requested.version.endsWith('SNAPSHOT') || dependency.requested.version.endsWith('RC'))) {
                def targetProject = project(":${dependency.requested.module}")
                if (targetProject != null) {
                    println('---- USING LOCAL ' + dependency.requested.displayName +' PROJECT ----')
                    dependency.useTarget targetProject
                }
            }
        }
    }
}
```

##### [Optional] Import and configure the project

To import the project to IntelliJ IDEA go to `File > New > Project from existing sources...` Select `powpeg-node/build.gradle` and import.

##### Build

Then clean and build project using:

```bash
./gradlew clean build
```

##### Run bitcoind

Create a new directory called datadir inside powpeg-project. In the next step you need to replace `<PATH_TO_DATA_DIR>` with this directory absolute path.

Create a new directory called scripts inside powpeg-project with the following scripts inside:


File `bitcoin-node.sh`

```bash
#!/bin/bash
bitcoind -regtest -printtoconsole -server -rpcuser=rsk -rpcpassword=rsk -rpcport=18332 -txindex -debug=net -deprecatedrpc=signrawtransaction -addresstype=legacy -deprecatedrpc=accounts -deprecatedrpc=generate -datadir=<PATH_TO_DATA_DIR>
```

File `btc-regtest.sh`

```bash
#!/bin/bash
bitcoin-cli -regtest --rpcuser=rsk --rpcpassword=rsk --rpcport=18332 $@
```


Start the Bitcoin node with (use another terminal as you will have to keep it running)

```bash
./scripts/bitcoin-node.sh
```


Generate 1 block with

```bash
 ./scripts/btc-regtest.sh generate 1
```

This should return the block hash as an object.


Get the amount of blocks available with

```bash
./scripts/btc-regtest getblockcount
```

This should return an integer.

- For more references, check **[https://github.com/BlockchainCommons/Learning-Bitcoin-from-the-Command-Line](https://github.com/BlockchainCommons/Learning-Bitcoin-from-the-Command-Line)**

##### Run PowPeg node!

You can run it either with the FedRunner configuration from IntelliJ or manually from the command line.

**FedRunner configuration from IntelliJ**
- **Create a new running configuration with name FedRunner** (go to `Run > Edit configurations > Add configuration > Application`)

- **Module:** `-cp federate-node.main`

    *When you select the module, the java version should change automatically*

- **Main Class:** `co.rsk.federate.FederateRunner`

- **Program Arguments:** add `--regtest -–reset`

    *Remove `--reset` if you want to keep your database after restarting the node*

- **VM options:** `-Drsk.conf.file=/<PATH_TO_CONF_FILE>` (to add VM options, go to `Modify Options > Add VM options`)

---

**Command line**

In order to run the powpeg-node from the command line you will have to provide the path to the jar after gradle build, configuration file path, and a log file for debugging.

```bash
java -cp /<PATH-TO-POW-PEG-SOURCE-CODE>/build/libs/<JAR-NAME>.jar -Drsk.conf.file=/<PATH-TO-CONF-FILE>/<CONF-FILE-NAME>.conf co.rsk.federate.FederateRunner --regtest --reset
```

-**Note:** Change PATH-TO-RSKJ-SOURCE-CODE value to your local Rskj path.
If you want to specify a directory to save the logs, add -Dlogback.configurationFile=/<PATH-TO-LOG-DIR>/logback.xml to the command.

---
## Report Security Vulnerabilities

We have a [vulnerability reporting guideline](SECURITY.md) for details on how to contact us to report a vulnerability.

---
## License

This software is released under the [MIT license](LICENSE).
