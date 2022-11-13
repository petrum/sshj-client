#!/bin/bash

rm ~/.ssh/id_rsa*
~/sshj-client/run.sh
~/sshj-client/deploykey.sh
ssh www.petrum.net -p 22223 uname
~/sshj-client/run.sh
cp -p ~/.ssh/bak/id* ~/.ssh
