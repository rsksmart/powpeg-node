# Welcome to RskJ Powpeg Node

## About

Powpeg node is a specialized rskj node which interacts with both RSK and Bitcoin.
This node is used by RSK PowPeg signatories to interact with the Bridge contract and to broadcast peg-out transactions to Bitcoin.

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

**Now you're ready to run the project.**

*To run from command line:*

```bash
./gradlew run -PmainClass=co.rsk.federate.FederateRunner
```

*To import the project to [IntelliJ IDEA](https://www.jetbrains.com/idea/download):*

You can import the project to your IDE. For example, [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/). To import go to `File | New | Project from existing sources...` Select `federate-node/build.gradle` and import. After building, run `co.rsk.federate.FederateRunner`

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

## Report Security Vulnerabilities

We have a [vulnerability reporting guideline](SECURITY.md) for details on how to contact us to report a vulnerability.

## License

This software is released under the [MIT license](LICENSE).

