# Welcome to RskJ Powpeg Node

## About

Powpeg node is a specialized rskj node which interacts with both RSK and Bitcoin.
This node is used by RSK PowPeg signatories to interact with the Bridge contract and to broadcast peg-out transactions to Bitcoin.

## Software Requirements
1. Java JDK 1.8
2. Bitcoin Core daemon (bitcoind) 0.17v or 0.18v 

## Setting up the project

Before anything, you must ensure the security chain of the source code. For that, you must go through the following steps. For Linux based OS (Ubuntu for example) it's recommended install `gnupg-curl` to download the key through HTTPS.

1. Download sec channel public key

```bash
 $ gpg --keyserver https://secchannel.rsk.co/ --recv-keys FD4FDAFD7D174BB2
 gpg: requesting key 7D174BB2 from https server secchannel.rsk.co
 gpg: /home/user/.gnupg/trustdb.gpg: trustdb created
 gpg: key 7D174BB2: public key "Sec Channel <secchannel@rsk.co>" imported
 gpg: Total number processed: 1
 gpg:               imported: 1  (RSA: 1)
```

2. Verify the downloaded key fingerprint

```bash
$ gpg --finger FD4FDAFD7D174BB2
pub   4096R/7D174BB2 2016-10-14 [expires: 2020-10-14]
	  Key fingerprint = 1310 29B2 D95E 815A 48DA  B443 FD4F DAFD 7D17 4BB2
uid                  Sec Channel <secchannel@rsk.co>
sub   4096R/498C250A 2016-10-14 [expires: 2020-10-14]
```

3. Clone the repo

```bash
git clone https://github.com/rsksmart/powpeg-node
cd powpeg-node
```

4. Verify the `SHA256SUMS.asc` signature

```bash
$ gpg --verify SHA256SUMS.asc
gpg: Signature made Thu 13 Apr 2017 02:51:34 PM UTC using RSA key ID 7D174BB2
gpg: Good signature from "Sec Channel <secchannel@rsk.co>"
gpg: WARNING: This key is not certified with a trusted signature!
gpg:          There is no indication that the signature belongs to the owner.
Primary key fingerprint: 1310 29B2 D95E 815A 48DA  B443 FD4F DAFD 7D17 4BB2
```

5. Verify the `configure.sh` script

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

6. Run configure script to configure secure environment.

```bash
./configure.sh
 ```

7. Steps to generate a Private Key File

- Using **GenNodeKeyId** class in the project

- Set String generator = "pegnatorie1";

- Run the Class to generate a private key

- Create a file **reg1.key** with only the private key as the content

- Add permission to the file by running **chmod 400 reg1.key**


8. Creating the signers configuration. **(For Local test / regtest only)**

Create a file **regtest-fed.conf** with the config below

### Signer's configurations
```
federator {
    # keep it false if you don't want to interact with bitcoin
    enabled = true
    
    signers {
       BTC {
          type = "keyFile"
          path = "A/PATH/TO/YOUR/BTC-KEY.key"
       }
       RSK {
          type = "keyFile"
          path = "A/PATH/TO/YOUR/RSK-KEY.key"
       }
       MST {
          type = "keyFile"
          path = "A/PATH/TO/YOUR/MST-KEY.key"
       }
    }
    # peers for the bitcoin network
    bitcoinPeerAddresses = [
        "127.0.0.1:18332" #bitcoind host and port, change if running on a different port.
    ]
}
```
**Note: You can use the same key file for BTC, RSK, and MST**

9. Steps to import and configure the project.

*To import the project to [IntelliJ IDEA](https://www.jetbrains.com/idea/download):*

You can import the project to your IDE. For example, [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/). To import go to `File | New | Project from existing sources...` Select `powpeg-node/build.gradle` and import.

- Create a New Application configuration with name **FedRunner**

**VM options**

- -Drsk.conf.file=/<PATH-TO-CONF-FILE>/regtest-fed.conf
- -Dlogback.configurationFile=/<PATH-TO-LOG-FILE>/logback.xml
- -Dblockchain.config.hardforkActivationHeights.iris300=0

**Program Arguments**

- --regtest --reset

*Remove `--reset` if you want to keep your database after restarting the node*

**Module**

- -cp federate-node.main

**Main Class**

- co.rsk.federate.FederateRunner

Then clean and build project using **./gradlew clean build**

10. Install and run Bitcoind

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

*To run from command line:*

```bash
./gradlew run -PmainClass=co.rsk.federate.FederateRunner
```

## Report Security Vulnerabilities

We have a [vulnerability reporting guideline](SECURITY.md) for details on how to contact us to report a vulnerability.

## License

This software is released under the [MIT license](LICENSE).