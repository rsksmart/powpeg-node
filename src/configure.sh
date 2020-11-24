#!/bin/bash

PLATFORM=''
GRADLE_WRAPPER="0f49043be582d7a39b671f924c66bd9337b92fa88ff5951225acc60560053067"
DOWNLOADED_HASH=''
DOWNLOAD_FILE=$(dd if=/dev/urandom bs=64 count=1 2>/dev/null| od -t x8 -A none  | tr -d ' '\\n)
unamestr=`uname`

function platform() {
        if [[ "$unamestr" == 'Linux' ]]; then
                PLATFORM='linux'
        elif [[ "$unamestr" == 'Darwin' ]]; then
                PLATFORM='mac'
        fi
}

function downloadJar(){
        if [ ! -d ./libs ]; then
                mkdir ./libs
        fi
        curl https://deps.rsklabs.io/gradle-wrapper.jar -o ~/$DOWNLOAD_FILE
        if [[ $PLATFORM == 'linux' ]]; then
                DOWNLOADED_HASH=$(sha256sum ~/${DOWNLOAD_FILE} | cut -d' ' -f1)
        elif [[ $PLATFORM == 'mac' ]]; then
                DOWNLOADED_HASH=$(shasum -a 256 ~/${DOWNLOAD_FILE} | cut -d' ' -f1)
        fi
        if [[ $GRADLE_WRAPPER != $DOWNLOADED_HASH ]]; then
                rm -f ~/${DOWNLOAD_FILE}
echo "sale2"
                exit 1
        else
                mv ~/${DOWNLOAD_FILE} ./gradle/wrapper/gradle-wrapper.jar
                rm -f ~/${DOWNLOAD_FILE}
        fi
}

platform
downloadJar
exit 0
