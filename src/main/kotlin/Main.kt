
import mu.KotlinLogging
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

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
    return "ssh-rsa $publicKeyEncoded sshj-client"
}
fun PrivateKey.toPemString(): String {
    val privateKeyBase64: String = Base64.getEncoder().encodeToString(this.encoded)
    return privateKeyBase64.chunked(70).joinToString(
        separator = "\n",
        prefix = "-----BEGIN RSA PRIVATE KEY-----\n",
        postfix = "\n-----END RSA PRIVATE KEY-----\n"
    )
}

private val log = KotlinLogging.logger {}
fun auth(client: SSHClient, username: String, s: String)
{
    val f = File(s)
    if (f.exists()) {
        val keys: KeyProvider = client.loadKeys(f.path)
        client.authPublickey(username, keys)
    }
    else {
        client.authPassword(username, s)
    }
}

fun persistKeys(kp: KeyPair)
{
    val dos = DataOutputStream(FileOutputStream("id_rsa.pub"))
    dos.write(kp.public.toRFC4253().encodeToByteArray())
    dos.flush()

    val dos2 = DataOutputStream(FileOutputStream("id_rsa"))
    dos2.write(kp.private.toPemString().encodeToByteArray())
    dos2.flush()
}

fun genKeys()
{
    try {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.genKeyPair()
        persistKeys(kp)
    }
    catch (e: NoSuchAlgorithmException) {
        System.out.println("Exception thrown : " + e)
    }
}

fun main(args: Array<String>) {
    //log.info{"Hello world!".toInt()}
    log.info("Program arguments: ${args.joinToString()}")
    //https://www.javadoc.io/doc/com.hierynomus/sshj/0.11.0/net/schmizz/sshj/SSHClient.html
    if (args.size < 5) {
        System.err.println("args are: www.petrum.net 22223 petrum id_rsa 'uname -a'")
        exitProcess(-1)
    }
    val f = File(args[3])
    if (!f.exists()) {
        genKeys()
        System.err.println("generated the keys")
        exitProcess(0)
    }
    val ssh = SSHClient()
    ssh.loadKnownHosts()
    ssh.connect(args[0], args[1].toInt())
    try {
        auth(ssh, args[2], args[3])
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