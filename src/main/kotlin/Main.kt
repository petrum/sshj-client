
import mu.KotlinLogging
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.*
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
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
    return "ssh-rsa $publicKeyEncoded sshj-client2\n"
}
/*
fun PrivateKey.toPemString(): String {
    val privateKeyBase64: String = Base64.getEncoder().encodeToString(this.encoded)
    return privateKeyBase64.chunked(70).joinToString(
        separator = "\n",
        prefix = "-----BEGIN OPENSSH PRIVATE KEY-----\n",
        postfix = "\n-----END OPENSSH PRIVATE KEY-----\n"
    )
}
*/
fun PrivateKey.toPemString2(): String {
    val pemObject = PemObject("RSA PRIVATE KEY", this.encoded)
    val byteStream = ByteArrayOutputStream()
    val pemWriter = PemWriter(OutputStreamWriter(byteStream))
    pemWriter.writeObject(pemObject)
    pemWriter.close()
    return byteStream.toString()
}

fun auth(client: SSHClient, username: String, s: String)
{
    //val key: KeyProvider = client.loadKeys(s)
    val key = PKCS8KeyFile()
    key.init((File(s)))
    client.authPublickey(username, key)
}

fun persistKeys(kp: KeyPair, priKeyFile: String)
{
    val f = File(priKeyFile)
    val dir = f.parentFile.absolutePath

    val dos = DataOutputStream(FileOutputStream("$dir/id_rsa.pub"))
    dos.write(kp.public.toRFC4253().encodeToByteArray())
    dos.flush()

    val dos2 = DataOutputStream(FileOutputStream(f))
    dos2.write(kp.private.toPemString2().encodeToByteArray())
    dos2.flush()

    //PKCS8EncodedKeySpec()
    //X509EncodedKeySpec()
}

fun genKeys(priKeyFile: String)
{
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)
    val kp = kpg.genKeyPair()
    persistKeys(kp, priKeyFile)
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
        //ssh.loadKnownHosts()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
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