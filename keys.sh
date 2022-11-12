#!/bin/bash

if [[ $RESTORE == 1 ]]; then
    cd ~/.ssh/bak 
else
    cd ~/sshj-client
fi
pwd
cp -vp id_rsa* ~/.ssh
chmod 600 ~/.ssh/id_rsa*

