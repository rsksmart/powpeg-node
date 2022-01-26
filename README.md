# Welcome to RskJ Powpeg Node

## About

Powpeg node is a specialized rskj node which interacts with both RSK and Bitcoin.
This node is used by RSK PowPeg signatories to interact with the Bridge contract and to broadcast peg-out transactions to Bitcoin.

## Software Requirements
1. Java JDK 1.8
2. Bitcoin Core daemon (bitcoind) 0.17v or 0.18v 

## Setting up the project

### Required configurations

1. You will need a private key file to be used by the pegnatory to sign BTC/RSK transactions. Follow these steps to generate it.

- Using **GenNodeKeyId** class in the rskj project

- Set String generator = "federator1";

- Run the Class to generate a private key

- Create a file with only the private key as the content

- Add permission to the file by running **chmod 400 reg1.key**

Optionally you can generate three files, one per each signer to be used (BTC/RSK/MST)

2. Create the powpeg node config file.

This file include at least the bitcoin node connection information and the signers' configuration.

Down below there is an example you can use (**(For Local test / regtest only)**)

```
federator {
    enabled = true
    
    signers {
       BTC {
          type = "keyFile"
          path = "A/PATH/TO/YOUR/BTC-KEY.key" # This should point to your generated key file
       }
       RSK {
          type = "keyFile"
          path = "A/PATH/TO/YOUR/RSK-KEY.key" # This should point to your generated key file
       }
       MST {
          type = "keyFile"
          path = "A/PATH/TO/YOUR/MST-KEY.key" # This should point to your generated key file
       }
    }
    # peers for the bitcoin network
    bitcoinPeerAddresses = [
        "127.0.0.1:18444" #bitcoind p2p port.
    ]
}
```
**Note: You can use the same key file for BTC, RSK, and MST**

**Important:**
When setting up the Bitcoind Node host:port for the powpeg-node you MUST use the p2p port.
The powpeg-node will “connect” even if you setup the RPC port, but the connection won’t go through and the powpeg-node won’t start.

### Fetch the code!

```bash
git clone https://github.com/rsksmart/powpeg-node
cd powpeg-node
```

### Verify the code!


Before anything, you must ensure the security chain of the source code. For that, you must go through the following steps. For Linux based OS (Ubuntu for example) it's recommended install `gnupg-curl` to download the key through HTTPS.


1. Download sec channel public key

```bash
$ gpg --keyserver https://secchannel.rsk.co/release.asc --recv-keys 5DECF4415E3B8FA4
gpg: requesting key 5E3B8FA4 from https server secchannel.rsk.co
gpg: key 5E3B8FA4: public key "RSK Release Signing Key <support@rsk.co>"      imported
gpg: Total number processed: 1
gpg:               imported: 1  (RSA: 1)
```

2. Verify the downloaded key fingerprint

```bash
$ gpg --finger 5DECF4415E3B8FA4
pub   4096R/5E3B8FA4 2017-05-16 [expires: 2022-05-15]
      Key fingerprint = 1A92 D894 2171 AFA9 51A8  5736 5DEC F441 5E3B 8FA4
uid                  RSK Release Signing Key <support@rsk.co>
sub   4096R/A44DCC86 2017-05-16 [expires: 2022-05-15]
sub   4096R/5E488E87 2017-05-16 [expires: 2022-05-15]
sub   4096R/9FC3E7C2 2017-05-16 [expires: 2022-05-15]
```

3. Verify the `SHA256SUMS.asc` signature

```bash
$ gpg --verify SHA256SUMS.asc
gpg: Signature made mar 16 may 2017 16:47:56 ART
gpg:                using RSA key 0x67D06695A44DCC86
gpg: Good signature from "RSK Release Signing Key <support@rsk.co>" [ultimate]
Primary key fingerprint: 1A92 D894 2171 AFA9 51A8  5736 5DEC F441 5E3B 8FA4
    Subkey fingerprint: D135 DDC0 B54D 6EF3 5901  52DF 67D0 6695 A44D CC86
```

4. Verify the `configure.sh` script

Linux:

```bash
$ sha256sum --check SHA256SUMS.asc
configure.sh: OK
sha256sum: WARNING: 19 lines are improperly formatted
```

MacOs:

```bash
$ shasum --check SHA256SUMS.asc
configure.sh: OK
sha256sum: WARNING: 19 lines are improperly formatted
```

### Compile the code!

1. Run configure script to configure secure environment.

```bash
./configure.sh
 ```

2. Steps to import and configure the project.

*To import the project to [IntelliJ IDEA](https://www.jetbrains.com/idea/download):*

You can import the project to your IDE. For example, [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/). To import go to `File | New | Project from existing sources...` Select `powpeg-node/build.gradle` and import.

- Create a New Application configuration with name **FedRunner**

**VM options**

- -Drsk.conf.file=/<PATH-TO-CONF-FILE>/regtest-fed.conf

**Program Arguments**

- --regtest --reset

*Remove `--reset` if you want to keep your database after restarting the node*

**Module**

- -cp federate-node.main

**Main Class**

- co.rsk.federate.FederateRunner

Then clean and build project using **./gradlew clean build**

### Need help running a Bitcoin node?
      
In order to install Bitcoind, you can follow these steps. We will use version ``` 0.18.0 ```, but it can be any other valid one (i.e ``` 0.17.1 ```).

```bash
   curl -O https://bitcoin.org/bin/bitcoin-core-0.18.0/bitcoin-0.18.0-osx64.tar.gz
   tar -zxf bitcoin-0.18.0-osx64.tar.gz
   sudo mkdir -p /usr/local/bin
   sudo cp bitcoin-0.18.0/bin/bitcoin* /usr/local/bin/.
   rm -rf bitcoin-0.18.0*
   As a validation, you can run *bitcoind -daemon*. Run *bitcoin-cli stop* afterwards.
```

Create the scripts below as a file and its content:

**bitcoin-node.sh**
```bash
#!/bin/bash

bitcoind -regtest -printtoconsole -server -rpcuser=rsk -rpcpassword=rsk -rpcport=18332 -txindex -debug=net -deprecatedrpc=signrawtransaction -addresstype=legacy -deprecatedrpc=accounts -deprecatedrpc=generate -datadir=PATH/TO/DATA/DIRECTORY
```
**btc-regtest.sh**
```bash
#!/bin/bash

bitcoin-cli -regtest --rpcuser=rsk --rpcpassword=rsk --rpcport=18332 $@
```

- Run the **bitcoin-node.sh** script to start the Bitcoin node
- Generate 1 block using the command **./btc-regtest.sh generate 1**
- Run **./btc-regtest getblockcount** to see the amount of blocks available


### Running Powpeg node using local RSKj source code

If you need to run the Powpeg node using a local version of RSKj instead of relying on the dependencies been resolved by Maven, you will have to add a customization file.
Create a file named `DONT-COMMIT-settings.gradle` with the following content:

```gradle
includeBuild('<PATH-TO-RSKJ-SOURCE-CODE>') {
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

**Note:** Change PATH-TO-RSKJ-SOURCE-CODE value to your local Rskj path.


### Steps to run the project.
Note: In order to run the powpeg-node from the command line you will have to provide the path to the jar after gradle build, configuration file path, and a log file for debugging.

*To run from command line:*

```bash
java -cp /<PATH-TO-POW-PEG-SOURCE-CODE>/build/libs/federate-node-SNAPSHOT-2.2.0.0-all.jar -Drsk.conf.file=/<PATH-TO-CONF-FILE>/regtest-fed.conf -Dlogback.configurationFile=/<PATH-TO-LOG-FILE>/logback.xml co.rsk.federate.FederateRunner --regtest --reset
```
    
## Report Security Vulnerabilities

We have a [vulnerability reporting guideline](SECURITY.md) for details on how to contact us to report a vulnerability.

## License

This software is released under the [MIT license](LICENSE).
