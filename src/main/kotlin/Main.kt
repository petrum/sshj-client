import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import java.util.concurrent.TimeUnit
fun main(args: Array<String>) {
    println("Program arguments: ${args.joinToString()}")
    //https://www.javadoc.io/doc/com.hierynomus/sshj/0.11.0/net/schmizz/sshj/SSHClient.html
    val ssh = SSHClient()
    ssh.loadKnownHosts()
    ssh.connect(args[0], args[1].toInt())
    try {
        ssh.authPassword(args[2], args[3])
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