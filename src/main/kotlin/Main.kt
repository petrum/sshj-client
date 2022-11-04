import mu.KotlinLogging
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.io.File
import java.io.FileOutputStream
import java.security.*
import java.util.*
import java.util.concurrent.TimeUnit


private val log = KotlinLogging.logger {}
fun auth(client: SSHClient, username: String, s: String)
{
    val f = File(s)
    if (f.exists()) {
        val keys: KeyProvider = client.loadKeys(f.getPath())
        client.authPublickey(username, keys)
    }
    else {
        client.authPassword(username, s)
    }
}

fun genKeys()
{
    try {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.genKeyPair()
        val priKey: PrivateKey = kp.getPrivate()
        val pubKey: PublicKey = kp.getPublic()
        System.out.println("Keypair : " + kp.toString())
        System.out.println("priKey : " + priKey.toString())
        System.out.println("pubKey : " + kp.public.toString())
    }
    catch (e: NoSuchAlgorithmException) {
        System.out.println("Exception thrown : " + e);
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
            cmd.join(1, TimeUnit.SECONDS)
            log.info(cmd.toString())
            System.out.println(IOUtils.readFully(cmd.getInputStream()).toString())
        } finally {
            session.close()
        }
    } finally {
        ssh.disconnect()
    }
}