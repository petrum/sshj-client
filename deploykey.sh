#!/bin/bash

ssh -i ~/.ssh/bak/id_rsa www.petrum.net -p 22223 "grep -v sshj-client2 .ssh/authorized_keys > .ssh/authorized_keys2; mv .ssh/authorized_keys2 .ssh/authorized_keys"
cat ~/.ssh/id_rsa.pub | ssh -i ~/.ssh/bak/id_rsa www.petrum.net -p 22223 "cat >> .ssh/authorized_keys"
chmod 600 ~/.ssh/id_rsa*
