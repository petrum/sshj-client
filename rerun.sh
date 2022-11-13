#!/bin/bash

rm ~/.ssh/id_rsa* # it removes the keys so they'll be recreated
~/sshj-client/run.sh /home/petrum/.ssh/id_rsa # this will just regenerate new keys as they are missing
~/sshj-client/deploykey.sh # it appends the new pub key to the remote server
ssh www.petrum.net -p 22223 uname # it checks it works with ssh
~/sshj-client/run.sh /home/petrum/.ssh/id_rsa # it uses the keys from exiting files
~/sshj-client/run.sh /home/petrum/.ssh/bak/id_rsa # it uses the keys from openssh backup files
cp -p ~/.ssh/bak/id* ~/.ssh # it restores the backup files for normal usage
