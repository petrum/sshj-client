# sshj-client
1. create with wizard a Kotlin project with gradle
2. added code in Main.kt
3. in build.gradle.kts file press Alt + Ins, then type in filter "sshj", add this dependency
4. you might need to "File/Repair IDE" to get the right info in "File/External Dependecies"
5. In order run in CLI you need to go into "File/Project Structure.../Actifact" and add a JAR "From module with dep" etc
6. then you "Build/Build Artifacts..."
7. remove the signature files generating the error:

petrum@gram /mnt/c/Users/petru/IdeaProjects/sshj-client/out/artifacts/sshj_client_main_jar[master*]$ java -cp sshj-client.main.jar MainKt www.petrum.net 22223 petrum **** 'uname -a'
Error: A JNI error has occurred, please check your installation and try again
Exception in thread "main" java.lang.SecurityException: Invalid signature file digest for Manifest main attributes
        at java.base/sun.security.util.SignatureFileVerifier.processImpl(SignatureFileVerifier.java:339)

petrum@gram /mnt/c/Users/petru/IdeaProjects/sshj-client/out/artifacts/sshj_client_main_jar[master*]$ zip -d sshj-client.main.jar 'META-INF/*SF'
deleting: META-INF/BC1024KE.SF
deleting: META-INF/BC2048KE.SF

8. finally you run it successful:

petrum@gram /mnt/c/Users/petru/IdeaProjects/sshj-client/out/artifacts/sshj_client_main_jar[master*]$ java -cp sshj-client.main.jar MainKt www.petrum.net 22223 petrum **** 'uname -a'
Program arguments: www.petrum.net, 22223, petrum, ****, uname -a
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
< session channel: id=0, recipient=0, localWin=[winSize=2097049], remoteWin=[winSize=2097152] >
Linux nuc 5.15.0-52-generic #58-Ubuntu SMP Thu Oct 13 08:03:55 UTC 2022 x86_64 x86_64 x86_64 GNU/Linux
