#!/bin/bash

if [[ ! -f ~/.ssh/bak/id_rsa ]]; then
  echo - backing up the keys
  mkdir -p ~/.ssh/bak
  cp -vp ~/.ssh/id_rsa* ~/.ssh/bak
fi

echo - removing the keys...
rm ~/.ssh/id_rsa* # it removes the keys so they'll be recreated

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd $SRC

echo - regenerating new keys as they are missing...
./run.sh /home/petrum/.ssh/id_rsa
echo - appending the new pub key to the remote server...
./deploykey.sh
echo - checking if new sshj created keys work with ssh...
ssh www.petrum.net -p 22223 uname
echo - checking the new keys from new created files...
./run.sh /home/petrum/.ssh/id_rsa
echo - checking the keys from openssh backup files...
./run.sh /home/petrum/.ssh/bak/id_rsa
echo - restoreing the backup files for normal usage...
cp -pv ~/.ssh/bak/id* ~/.ssh
echo Done
