#/bin/bash
set -e
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd $SRC
JAR=out/artifacts/sshj_client_main_jar/sshj-client.main.jar
if unzip -l $JAR | grep 'META-INF/.*SF' ; then
  zip -q -d $JAR 'META-INF/*SF'
fi
KEY=$1
java -Dkotlin-logging.throwOnMessageError -cp $JAR MainKt www.petrum.net 22223 petrum $KEY 'uname'
