
import mu.KotlinLogging
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.*
import java.util.*
import java.util.concurrent.TimeUnit
fun PublicKey.toPemString(): String {
    val publicKeyBase64: String = Base64.getEncoder().encodeToString(this.encoded)
    return publicKeyBase64.chunked(64).joinToString(
        separator = "\n",
        prefix = "-----BEGIN PUBLIC KEY-----\n",
        postfix = "\n-----END PUBLIC KEY-----\n"
    )
}
fun PrivateKey.toPemString(): String {
    val privateKeyBase64: String = Base64.getEncoder().encodeToString(this.encoded)
    return privateKeyBase64.chunked(64).joinToString(
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
    val dos = DataOutputStream(FileOutputStream("rsaPublicKey.txt"))
    dos.write(kp.public.toPemString().encodeToByteArray())
    dos.flush()

    val dos2 = DataOutputStream(FileOutputStream("rsaPrivateKey.txt"))
    dos2.write(kp.private.toPemString().encodeToByteArray())
    dos2.flush()
}

fun genKeys()
{
    try {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.genKeyPair()
        log.info("public key: " + kp.public.encoded)
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
    genKeys()
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