# README

# Welcome to RskJ Powpeg Node

## About

Powpeg node is a specialized rskj node which interacts with both RSK and Bitcoin. This node is used by RSK PowPeg signatories to interact with the Bridge contract and to broadcast peg-out transactions to Bitcoin.

## Software Requirements

1. Java JDK 1.8
2. Bitcoin Core daemon (bitcoind) 0.17v or 0.18v - make sure is the only installation related to bitcoin you have. 
3. Recommended [IntelliJ IDEA](https://www.jetbrains.com/idea/download) as this guide covers the setup with it
4. Linux or macOS, this guide covers the setup with the last one

**Not sure how to install any of these? See [software installation help](#software-installation-help)**

## Software installation help

### **Java JDK 1.8**

Although optional we recommend to install jenv to manage different Java versions, to do that run: `brew install jenv`

Add jenv to your path as it say on the output of jenv brew installation.

Download [oracle-jdk8](https://www.oracle.com/ar/java/technologies/javase/javase8-archive-downloads.html) install it.

**Note:** *if you have an Apple Silicon chip you may will get a notification saying that is not recommended to install a file that is for Intel chips, just ignore it and keep going with the installation.*

Add the just downloaded jdk to the jenv versions, run: `jenv add /Library/Java/JavaVirtualMachines/<ORACLE-JDK8-DIRECTORY>/Contents/Home/`

If you are using another java version in other projects, run `jenv local 1.8.0.352`. Otherwise you can set it globally by running: `jenv global 1.8.0.352`

To check that it worked, run `java -version` and it should show something like 

```
openjdk version "1.8.0_352"
OpenJDK Runtime Environment
OpenJDK 64-Bit Server VM 
```

*If not, try closing the terminal and open a new one cause sometimes it stays cached.*

### Bitcoind

We will use version `0.18.0`

```bash
curl -O https://bitcoin.org/bin/bitcoin-core-0.18.0/bitcoin-0.18.0-osx64.tar.gz
tar -zxf bitcoin-0.18.0-osx64.tar.gz
sudo mkdir -p /usr/local/bin
sudo cp bitcoin-0.18.0/bin/bitcoin* /usr/local/bin/.
rm -rf bitcoin-0.18.0
```

To validate it run `bitcoind -daemon`. Run `bitcoin-cli stop` afterwards.

## Setting up the project

### Required configurations

**Directory structure**

Create a directory (for example, “powpeg-project”) to hold the rskj node, the powpeg node and further configurations.

```bash
mkdir powpeg-project
cd powpeg-project
```

Inside the project directory clone the rskj repository.

```bash
git clone https://github.com/rsksmart/rskj
```

---

**Pegnatories**

You will need a private key file to be used by the pegnatory to sign BTC/RSK transactions. Follow these steps to generate it:

- Look for the **[GenNodeKeyId](https://github.com/rsksmart/rskj/blob/master/rskj-core/src/main/java/co/rsk/GenNodeKeyId.java)** class in rskj
- In that file replace `String generator = “”;` with `String generator = “federator1”;`
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

**Configuration file**

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

**Running Powpeg node using local RSKj source code**

To run the Powpeg node using a local version of RSKj instead of relying on the dependencies been resolved by Maven, you will have to add a customization file.
Search for `development-settings.gradle.sample` file inside powpeg-node directory, rename it to `DONT-COMMIT-settings.gradle` and make sure it only has the following content. Change `<ABSOLUTE-PATH-TO-RSKJ-SOURCE-CODE>` value to your local RSKj absolute path.

```
includeBuild('<ABSOLUTE-PATH-TO-RSKJ-SOURCE-CODE>') {
    dependencySubstitution {
        all { DependencySubstitution dependency ->
            if (dependency.requested instanceof ModuleComponentSelector
                    && dependency.requested.group == 'co.rsk'
                    && dependency.requested.module == 'rskj-core'
                    && dependency.requested.version.endsWith('SNAPSHOT')) {
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

### Fetch the code!

Inside the `powpeg-project` directory clone the powpeg-node repository.

```bash
git clone https://github.com/rsksmart/powpeg-node
```

```bash
cd powpeg-node
```

### Verify the code!

Before anything, you must ensure the security chain of the source code. For that, you must go through the following steps. For Linux based OS (Ubuntu for example) it’s recommended install `gnupg-curl` to download the key through HTTPS.

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

1. Verify the downloaded key fingerprint

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

1. Verify the `SHA256SUMS.asc` signature

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

1. Verify the `configure.sh` script

**Linux:**

```bash
sha256sum --check SHA256SUMS.asc
```

You should get something like:

```
configure.sh: OK
sha256sum: WARNING: 19 lines are improperly formatted
```

**MacOS:**

```bash
shasum --check SHA256SUMS.asc
```

You should get something like:

```
configure.sh: OK
sha256sum: WARNING: 19 lines are improperly formatted
```

### Compile the code!

Run configure script to configure secure environment.

```bash
./configure.sh
```

### Import and configure the project

To import the project to IntelliJ IDEA go to `File > New > Project from existing sources...` Select `powpeg-node/build.gradle` and import.

- **Create a new running configuration with name FedRunner** (go to `Run > Edit configurations > Add configuration > Application`)
- **Module*:*** `-cp federate-node.main`

*When you select the module, the java version should change automatically*

- **Main Class:** `co.rsk.federate.FederateRunner`
- **Program Arguments:** add `--regtest -–reset`

*Remove `--reset` if you want to keep your database after restarting the node*

- **VM options:** `-Drsk.conf.file=/<PATH_TO_CONF_FILE>` (to add VM options, go to `Modify Options > Add VM options`)


### Build

Then clean and build project using:

```bash
./gradlew clean build
```

### Scripts

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

### Finally run!

You can run it either with the FedRunner configuration from IntelliJ or manually from the command line.



---

**Command line**

In order to run the powpeg-node from the command line you will have to provide the path to the jar after gradle build, configuration file path, and a log file for debugging.

```bash
java -cp /<PATH-TO-POW-PEG-SOURCE-CODE>/build/libs/federate-node-SNAPSHOT-4.2.0.0-all.jar -Drsk.conf.file=/<PATH-TO-CONF-FILE>/regtest-fed.conf -Dlogback.configurationFile=/<PATH-TO-LOG-FILE>/logback.xml co.rsk.federate.FederateRunner --regtest --reset
```

## Report Security Vulnerabilities

We have a [vulnerability reporting guideline](SECURITY.md) for details on how to contact us to report a vulnerability.

## License

This software is released under the [MIT license](LICENSE).