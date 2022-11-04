import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.io.File
import java.util.concurrent.TimeUnit

import mu.KotlinLogging

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
private val log = KotlinLogging.logger {}
fun main(args: Array<String>) {
    log.info {"This is an info level log message!"}
    println("Program arguments: ${args.joinToString()}")
    //https://www.javadoc.io/doc/com.hierynomus/sshj/0.11.0/net/schmizz/sshj/SSHClient.html
    val ssh = SSHClient()
    ssh.loadKnownHosts()
    ssh.connect(args[0], args[1].toInt())
    try {
        auth(ssh, args[2], args[3])
        val session = ssh.startSession()
        try {
            val cmd = session.exec(args[4])
            cmd.join(1, TimeUnit.SECONDS)
            System.out.println(cmd)
            System.out.println(IOUtils.readFully(cmd.getInputStream()).toString())
        } finally {
            session.close()
        }
    } finally {
        ssh.disconnect()
    }
}