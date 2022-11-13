#!/bin/bash
set -e
function display()
{
    echo " - $@"
}

if [[ ! -f ~/.ssh/bak/id_rsa ]]; then
  display backing up the keys
  mkdir -p ~/.ssh/bak
  cp -vp ~/.ssh/id_rsa* ~/.ssh/bak
fi

display removing the keys...
rm ~/.ssh/id_rsa* # it removes the keys so they'll be recreated

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd $SRC

display regenerating new keys as they are missing...
./run.sh ~/.ssh/id_rsa
display appending the new pub key to the remote server...
./deploykey.sh
display ssh with newly created sshj keys...
ssh www.petrum.net -p 22223 uname
display checking the new keys from new created files...
./run.sh ~/.ssh/id_rsa
display checking the keys from openssh backup files...
./run.sh ~/.ssh/bak/id_rsa
display restoring the backup files for normal usage...
cp -pv ~/.ssh/bak/id* ~/.ssh
display done
