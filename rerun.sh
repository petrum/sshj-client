#!/bin/bash

rm ~/.ssh/id_rsa*
~/sshj-client/run.sh
~/sshj-client/deploykey.sh
ssh www.petrum.net -p 22223 uname
~/sshj-client/run.sh
~/sshj-client/run.sh /home/petrum/.ssh/bak/id_rsa
cp -p ~/.ssh/bak/id* ~/.ssh
