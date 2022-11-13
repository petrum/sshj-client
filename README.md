# sshj-client

### First Kolin project
1. create a Kotlin project in IntelliJ IDEA, with the wizard, using gradle
2. added the sshj client code in Main.kt
3. in build.gradle.kts file press Alt + Ins, then type "sshj" in search, add this dependency, as well as "slf4j-simple" (used by sshj):
```
dependencies {
    implementation("com.hierynomus:sshj:0.34.0")
    implementation("org.slf4j:slf4j-simple:2.0.3")
    testImplementation(kotlin("test"))
}
```
5. you might need to "File/Repair IDE" to get the right info in "File/External Dependecies". This is how it should look:

![Ext dep](https://github.com/petrum/sshj-client/blob/master/external-dep.png?raw=true)

6. In order run in CLI outside IDE you need to go into "File/Project Structure.../Actifact" and add a JAR "From module with dep" etc

![Ext dep](https://github.com/petrum/sshj-client/blob/master/artifact.png?raw=true)

8. then you "Build/Build Artifacts..."
9. remove the signature files generating the error:
```
petrum@gram /mnt/c/Users/petru/IdeaProjects/sshj-client/out/artifacts/sshj_client_main_jar[master*]$ java -cp sshj-client.main.jar MainKt www.petrum.net 22223 petrum **** 'uname -a'
Error: A JNI error has occurred, please check your installation and try again
Exception in thread "main" java.lang.SecurityException: Invalid signature file digest for Manifest main attributes
        at java.base/sun.security.util.SignatureFileVerifier.processImpl(SignatureFileVerifier.java:339)

petrum@gram /mnt/c/Users/petru/IdeaProjects/sshj-client/out/artifacts/sshj_client_main_jar[master*]$ zip -d sshj-client.main.jar 'META-INF/*SF'
deleting: META-INF/BC1024KE.SF
deleting: META-INF/BC2048KE.SF
```
8. finally you run it successful:
```
petrum@gram /mnt/c/Users/petru/IdeaProjects/sshj-client/out/artifacts/sshj_client_main_jar[master*]$ java -cp sshj-client.main.jar MainKt www.petrum.net 22223 petrum /mnt/c/Users/petru/.ssh/id_rsa 'uname -a'
Program arguments: www.petrum.net, 22223, petrum, /mnt/c/Users/petru/.ssh/id_rsa, uname -a
[main] INFO net.schmizz.sshj.transport.random.JCERandom - Creating new SecureRandom.
[main] INFO net.schmizz.sshj.transport.TransportImpl - Client identity string: SSH-2.0-SSHJ_0.34.0
[main] INFO net.schmizz.sshj.transport.TransportImpl - Server identity string: SSH-2.0-OpenSSH_8.9p1 Ubuntu-3
[main] INFO com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile - Read key type: ssh-rsa
< session channel: id=0, recipient=0, localWin=[winSize=2097049], remoteWin=[winSize=2097152] >
Linux nuc 5.15.0-52-generic #58-Ubuntu SMP Thu Oct 13 08:03:55 UTC 2022 x86_64 x86_64 x86_64 GNU/Linux
```


### Next steps
1. Use sshj lib to autheticate using password: done
2. Using sshj to connect with public key using existing id_rsa/id_rsa.pub: done
3. Generate the key files using java security libraries, and then connect using ssh: done
4. using same generated key files to connect from java/sshj: done, but this I struggled with most
  1. first it was sa strange as the sshj generated files did work from external ssh
  2. I wanted to use java security libraries, and avoid bouncycastle dependencies 
  3. Then I hoped using bouncycastle library will sole the issue, but it didn't (actually I got same errors)
  4. Issue was the `client.loadkeys(file)` that didn't load the keys as expected
  5. I was able to solve it by creating my own function to load the keys from key files


