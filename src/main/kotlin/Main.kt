
import mu.KotlinLogging
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper
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
        .replace("-----BEGIN RSA PRIVATE KEY-----".toRegex(), "")
        .replace("-----END RSA PRIVATE KEY-----".toRegex(), "")
    log.debug("private key PEM: '${privateKeyPEM}'")

    val encoded: ByteArray = Base64.getDecoder().decode(privateKeyPEM)

    val keyFactory = KeyFactory.getInstance("RSA")
    val keySpec = PKCS8EncodedKeySpec(encoded)
    return keyFactory.generatePrivate(keySpec)
}

fun loadPubKey(f: String): PublicKey {
    val key = File(f).readText(Charsets.UTF_8)
    log.info("loading public key from '$f'")
    log.debug("loading public key from '$f': $key")
    val publicKeyPEM = key.replace("ssh-rsa ", "")
        .replace(System.lineSeparator().toRegex(), "")
        .replace(" sshj-client2", "")
        .replace(" petrum@gram", "")

    log.debug("public key PEM: '${publicKeyPEM}'")
    val ds = DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(publicKeyPEM)))
    val size1 = ds.readInt()

    ds.readNBytes(size1)
    val expSize = ds.readInt()
    val exp = BigInteger(ds.readNBytes(expSize))
    val modulusSize = ds.readInt()
    val modulus = BigInteger(ds.readNBytes(modulusSize))
    log.info("size1 = $size1, expSize = $expSize, exp = $exp, modulusSize = $modulusSize, left = ${ds.available()}")
    val keyFactory = KeyFactory.getInstance("RSA")
    //val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyPEM))
    val keySpec = RSAPublicKeySpec(modulus, exp)
    return keyFactory.generatePublic(keySpec)
}

fun savePubKey(k: PublicKey, f: String) {
    log.info("saving public key to '$f'")
    val dos = DataOutputStream(FileOutputStream(f))
    dos.write(k.toRFC4253().encodeToByteArray())
    dos.flush()
    //http://www.java2s.com/example/java-api/java/security/spec/pkcs8encodedkeyspec/pkcs8encodedkeyspec-1-0.html
    //val k2 = loadPubKey("/home/petrum/.ssh/bak/id_rsa.pub")
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
    //val k2 = loadPriKey("/home/petrum/.ssh/bak/id_rsa")
    val k2 = loadPriKey(f)
    if (!k.equals(k2)) {
        log.error("The private keys differ: $k vs $k2")
    }
}

fun auth(client: SSHClient, username: String, f: String)
{
    //val key: KeyProvider = client.loadKeys(f)

    //val key = PKCS8KeyFile()
    //key.init((File(s)))

    val kp = KeyPair(loadPubKey(pri2pub(f)), loadPriKey(f))
    client.authPublickey(username, KeyPairWrapper(kp))
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

    log.info("generated the keys")
}

fun main(args: Array<String>) {
    try {
        log.info("Program arguments: ${args.joinToString()}")
        //https://www.javadoc.io/doc/com.hierynomus/sshj/0.11.0/net/schmizz/sshj/SSHClient.html
        if (args.size < 5) {
            log.error("args are: www.petrum.net 22223 petrum id_rsa 'uname -a'")
            exitProcess(-1)
        }
        val priKFile = args[3]
        val f = File(priKFile)
        if (!f.exists()) {
            genKeys(priKFile)
            exitProcess(0)
        }
        val ssh = SSHClient()
        ssh.loadKnownHosts()
        //ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connect(args[0], args[1].toInt())
        try {
            auth(ssh, args[2], priKFile)
            val session = ssh.startSession()
            try {
                val cmd = session.exec(args[4])
                log.info(cmd.toString())
                cmd.join(1, TimeUnit.SECONDS)
                System.out.println(IOUtils.readFully(cmd.inputStream).toString())
            } finally {
                session.close()
            }
        } finally {
            ssh.disconnect()
        }
    }
    catch (e: Exception) {
        log.error(e.toString())
    }
}