#/bin/bash
JAR=out/artifacts/sshj_client_main_jar/sshj-client.main.jar
zip -d $JAR 'META-INF/*SF'
#KEY=/mnt/c/Users/petru/.ssh/id_rsa
#if [[ ! -f $KEY ]]; then
#    KEY=/home/petrum/.ssh/id_rsa
#fi
KEY=~/.ssh/id_rsa
java -Dkotlin-logging.throwOnMessageError -cp $JAR MainKt www.petrum.net 22223 petrum $KEY 'uname -a'

