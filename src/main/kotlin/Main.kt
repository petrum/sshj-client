
import mu.KotlinLogging
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.io.*
import java.math.BigInteger
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

//https://blog.oddbit.com/post/2011-05-08-converting-openssh-public-keys
//https://superuser.com/questions/1477472/openssh-public-key-file-format

fun PublicKey.toRFC4253(): String {
    // "You need to downcast to the transparent java.security.interfaces.RSAPublicKey type.
    // Then you can access the modulus and public exponents."
    // https://stackoverflow.com/questions/20897065/how-to-get-exponent-and-modulus-value-of-rsa-public-key-from-pfx-file-pem-file-i
    val rsaPublicKey = this as RSAPublicKey
    val byteOs = ByteArrayOutputStream()
    val dos = DataOutputStream(byteOs)
    // https://stackoverflow.com/questions/3531506/using-public-key-from-authorized-keys-with-java-security/14582408#14582408
    dos.writeInt("ssh-rsa".toByteArray().size)
    dos.write("ssh-rsa".toByteArray())
    dos.writeInt(rsaPublicKey.publicExponent.toByteArray().size)
    dos.write(rsaPublicKey.publicExponent.toByteArray())
    dos.writeInt(rsaPublicKey.modulus.toByteArray().size)
    dos.write(rsaPublicKey.modulus.toByteArray())
    val publicKeyEncoded = Base64.getEncoder().encodeToString(byteOs.toByteArray())
    return "ssh-rsa $publicKeyEncoded sshj-client2"
}

fun PrivateKey.toPemString(): String {
    val privateKeyBase64: String = Base64.getEncoder().encodeToString(this.encoded)
    return privateKeyBase64.chunked(70).joinToString(
        separator = "\n",
        prefix = "-----BEGIN RSA PRIVATE KEY-----\n",
        postfix = "\n-----END RSA PRIVATE KEY-----\n"
    )
}
/*
fun PrivateKey.toPemString2(): String {
    //val pemObject = PemObject("OPENSSH PRIVATE KEY", this.encoded) // this fails with ssh
    val pemObject = PemObject("RSA PRIVATE KEY", PKCS8EncodedKeySpec(this.encoded).encoded)
    val byteStream = ByteArrayOutputStream()
    val pemWriter = PemWriter(OutputStreamWriter(byteStream))
    pemWriter.writeObject(pemObject)
    pemWriter.close()
    return byteStream.toString()
}

*/
fun loadPriKey(f: String): PrivateKey {
    val key = File(f).readText(Charsets.UTF_8)
    log.info("loading private key from '$f'")
    log.debug("loading private key from '$f':\n$key")
    val privateKeyPEM = key.replace(System.lineSeparator().toRegex(), "")
        .replace("-----END .* PRIVATE KEY-----".toRegex(), "")
        .replace("-----BEGIN .* PRIVATE KEY-----".toRegex(), "")
    log.debug("private key PEM: '${privateKeyPEM}'")

    val encoded: ByteArray = Base64.getDecoder().decode(privateKeyPEM)

    val keyFactory = KeyFactory.getInstance("RSA")
    val keySpec = PKCS8EncodedKeySpec(encoded)
    val res = keyFactory.generatePrivate(keySpec)
    log.info("done loading private key from '$f'")
    return res
}

fun loadPubKey(f: String): PublicKey {
    val key = File(f).readText(Charsets.UTF_8)
    log.info("loading public key from '$f'")
    log.debug("loading public key from '$f': $key")
    val words = key.split(" ")
    if (words.size < 3) {
        throw Exception("Got too few words (size = ${words.size})")
    }
    val publicKeyPEM = words[1]
    log.debug("public key PEM: '${publicKeyPEM}'")
    val ds = DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(publicKeyPEM)))
    val size1 = ds.readInt()

    ds.readNBytes(size1)
    val expSize = ds.readInt()
    val exp = BigInteger(ds.readNBytes(expSize))
    val modulusSize = ds.readInt()
    val modulus = BigInteger(ds.readNBytes(modulusSize))
    log.info("size1 = $size1, expSize = $expSize, exp = $exp, modulusSize = $modulusSize, left = ${ds.available()}")
    if (ds.available() != 0) {
        log.error("still ${ds.available()} bytes left in the public key!")
    }
    val keyFactory = KeyFactory.getInstance("RSA")
    //val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyPEM))
    val keySpec = RSAPublicKeySpec(modulus, exp)
    val res = keyFactory.generatePublic(keySpec)
    log.info("done loading public key from '$f'")
    return res
}

fun savePubKey(k: PublicKey, f: String) {
    log.info("saving public key to '$f'")
    val dos = DataOutputStream(FileOutputStream(f))
    dos.write(k.toRFC4253().encodeToByteArray())
    dos.flush()
    //http://www.java2s.com/example/java-api/java/security/spec/pkcs8encodedkeyspec/pkcs8encodedkeyspec-1-0.html
    val k2 = loadPubKey(f)
    if (!k.equals(k2)) {
        log.error("The public keys differ: $k vs $k2")
    }
}
fun savePriKey(k: PrivateKey, f: String) {
    log.info("saving private key to '$f'")
    val dos = DataOutputStream(FileOutputStream(f))
    dos.write(k.toPemString().encodeToByteArray())
    dos.flush()
    val k2 = loadPriKey(f)
    if (!k.equals(k2)) {
        log.error("The private keys differ: $k vs $k2")
    }
}

fun auth(client: SSHClient, username: String, f: String)
{
    val text = File(f).readText(Charsets.UTF_8)
    if (text.contains("OPENSSH")) {
        val kp: KeyProvider = client.loadKeys(f)
        client.authPublickey(username, kp)
    }
    else {
        val kp = KeyPair(loadPubKey(pri2pub(f)), loadPriKey(f))
        client.authPublickey(username, KeyPairWrapper(kp))
    }
}
fun pri2pub(s: String): String {
    val f = File(s)
    return "${f.parentFile.absolutePath}/id_rsa.pub"
}

fun genKeys(f: String)
{
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)
    val kp = kpg.genKeyPair()

    savePubKey(kp.public, pri2pub(f))
    savePriKey(kp.private, f)
}

fun executeRemote(host: String, port: Int, username: String, priKeyFile: String, cmdStr: String): String {
    val ssh = SSHClient()
    ssh.loadKnownHosts()
    //ssh.addHostKeyVerifier(PromiscuousVerifier())
    ssh.connect(host, port)
    try {
        auth(ssh, username, priKeyFile)
        val session = ssh.startSession()
        try {
            val cmd = session.exec(cmdStr)
            log.info(cmd.toString())
            cmd.join(1, TimeUnit.SECONDS)
            return IOUtils.readFully(cmd.inputStream).toString()
        } finally {
            session.close()
        }
    } finally {
        ssh.disconnect()
    }
}
fun main(args: Array<String>) {
    try {
        log.info("Program arguments: ${args.joinToString()}")
        //https://www.javadoc.io/doc/com.hierynomus/sshj/0.11.0/net/schmizz/sshj/SSHClient.html
        if (args.size < 5) {
            log.error("args are: www.petrum.net 22223 petrum id_rsa 'uname -a'")
            exitProcess(-1)
        }
        val priKeyFile = args[3]
        val f = File(priKeyFile)
        if (!f.exists()) {
            log.info("The file '${f}' doesn't exists, generating new keys...")
            genKeys(priKeyFile)
            log.info("generated the keys, exiting...")
            exitProcess(0)
        }
        System.out.print(executeRemote(args[0], args[1].toInt(), args[2], priKeyFile, args[4]))
    }
    catch (e: Exception) {
        log.error(e.toString())
    }
}