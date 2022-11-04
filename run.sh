#/bin/bash
JAR=out/artifacts/sshj_client_main_jar/sshj-client.main.jar
zip -d $JAR 'META-INF/*SF'
java -Dkotlin-logging.throwOnMessageError -cp $JAR MainKt www.petrum.net 22223 petrum /mnt/c/Users/petru/.ssh/id_rsa 'uname -a'

