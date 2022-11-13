#/bin/bash

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd $SRC
JAR=out/artifacts/sshj_client_main_jar/sshj-client.main.jar
zip -q -d $JAR 'META-INF/*SF' 2>/dev/null
KEY=$1
java -Dkotlin-logging.throwOnMessageError -cp $JAR MainKt www.petrum.net 22223 petrum $KEY 'uname'
