package net.petrum.sshremote

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.json.JSONObject
import java.io.*
import java.math.BigInteger
import java.net.URL
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


fun info(s: String) {
    Log.i("MAIN", s)
}

fun debug(s: String) {
    Log.d("MAIN", s)
}

fun downloadFile(url: String): String {
    info("getting from url '$url'... ")
    val ret = URL(url).readText()
    info("got ${ret.length} bytes characters")
    return ret
}

fun getFile(n: String): String {
    val bufferedReader: BufferedReader = File(n).bufferedReader()
    return bufferedReader.use { it.readText() }
}

fun nowStr(): String {
    val s = SimpleDateFormat("yyyyMMdd hh:mm:ss", Locale.US)
    return s.format(Date())
}
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
    return "ssh-rsa $publicKeyEncoded sshremote2"
}

fun PrivateKey.toPemString(): String {
    val privateKeyBase64: String = Base64.getEncoder().encodeToString(this.encoded)
    return privateKeyBase64.chunked(80).joinToString(
        separator = "\n",
        prefix = "-----BEGIN RSA PRIVATE KEY-----\n",
        postfix = "\n-----END RSA PRIVATE KEY-----\n"
    )
}

fun loadPriKey(f: String): PrivateKey {
    val key = File(f).readText(Charsets.UTF_8)
    info("loading private key from '$f'")
    debug("loading private key from '$f':\n$key")
    val privateKeyPEM = key.replace(System.lineSeparator().toRegex(), "")
        .replace("-----END .* PRIVATE KEY-----".toRegex(), "")
        .replace("-----BEGIN .* PRIVATE KEY-----".toRegex(), "")
    debug("private key PEM: '${privateKeyPEM}'")
    val encoded: ByteArray = Base64.getDecoder().decode(privateKeyPEM)
    val keyFactory = KeyFactory.getInstance("RSA")
    val keySpec = PKCS8EncodedKeySpec(encoded)
    val res = keyFactory.generatePrivate(keySpec)
    info("done loading private key from '$f'")
    return res
}

fun readNBytes(ds: DataInputStream, len: Int): ByteArray {
    //return ds.readNBytes(len) // no supported in Java 11 used by Android Studio
    val buffer = ByteArray(len)
    val read = ds.read(buffer, 0, len)
    if (read != len) {
        throw Exception("Expected read $len bytes, but got $read instead")
    }
    return buffer
}

fun loadPubKey(f: String): PublicKey {
    val key = File(f).readText(Charsets.UTF_8)
    info("loading public key from '$f'")
    debug("loading public key from '$f': $key")
    val words = key.split(" ")
    if (words.size < 3) {
        throw Exception("Got too few words (size = ${words.size})")
    }
    val publicKeyPEM = words[1]
    debug("public key PEM: '${publicKeyPEM}'")
    val ds = DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(publicKeyPEM)))
    val size1 = ds.readInt()
    readNBytes(ds, size1)
    val expSize = ds.readInt()
    val exp = BigInteger(readNBytes(ds, expSize))
    val modulusSize = ds.readInt()
    val modulus = BigInteger(readNBytes(ds, modulusSize))
    info("size1 = $size1, expSize = $expSize, exp = $exp, modulusSize = $modulusSize, left = ${ds.available()}")
    if (ds.available() != 0) {
        throw Exception("still ${ds.available()} bytes left in the public key!")
    }
    val keyFactory = KeyFactory.getInstance("RSA")
    val keySpec = RSAPublicKeySpec(modulus, exp)
    val res = keyFactory.generatePublic(keySpec)
    info("done loading public key from '$f'")
    return res
}

fun savePubKey(k: PublicKey, f: String) {
    info("saving public key to '$f'")
    val dos = DataOutputStream(FileOutputStream(f))
    dos.write(k.toRFC4253().encodeToByteArray())
    dos.flush()
    //http://www.java2s.com/example/java-api/java/security/spec/pkcs8encodedkeyspec/pkcs8encodedkeyspec-1-0.html
    val k2 = loadPubKey(f)
    if (k != k2) {
        throw Exception("The public keys differ: $k vs $k2")
    }
}

fun savePriKey(k: PrivateKey, f: String) {
    info("saving private key to '$f'")
    val dos = DataOutputStream(FileOutputStream(f))
    dos.write(k.toPemString().encodeToByteArray())
    dos.flush()
    val k2 = loadPriKey(f)
    if (k != k2) {
        throw Exception("The private keys differ: $k vs $k2")
    }
}

fun save(s: String, f: String) {
    val dos = DataOutputStream(FileOutputStream(f))
    dos.write(s.encodeToByteArray())
    dos.flush()
    dos.close()
}

data class CmdResult(val code: Int, val out: String, val err: String, val bId: Int)

class MainActivity : AppCompatActivity() {
    private var url: String? = null
    private var commands = ""
    private var crtButtonId = 100
    private val prefName = "pref"
    private val logVisibleProp = "logVisible"
    private val logSizeProp = "logSize"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupBouncyCastle()
        if (File(urlFName).exists()) {
            url = getFile(urlFName)
        }
        try {
            refresh()
            setLogVisibility()
            val size = floatPref
            if (size > 0) {
                log.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            }
        } catch (e: Exception) {
            appendUILog(e.toString())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    private fun generateKeys(): KeyPair {
        Log.i("MAIN", "generateKeys()")
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        return kpg.genKeyPair()
    }

    private val urlFName: String
        get() {
            val path = applicationContext.filesDir
            return "$path/" + applicationContext.getString(R.string.url)
        }

    private val publicKeyFName: String
        get() {
            val path = applicationContext.filesDir
            return "$path/" + applicationContext.getString(R.string.key_public)
        }

    private val privateKeyFName: String
        get() {
            val path = applicationContext.filesDir
            return "$path/" + applicationContext.getString(R.string.key_private)
        }

    private fun saveKeys(kp: KeyPair) {
        Log.i("MAIN", "saveKeys()")
        savePubKey(kp.public, publicKeyFName)
        savePriKey(kp.private, privateKeyFName)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_generate -> saveKeys(generateKeys())
            R.id.menu_share -> sharePubkey()
            R.id.menu_delkeys -> delKeys()
            R.id.menu_import -> import()
            R.id.menu_refresh -> refresh()
            R.id.menu_logvisible -> toggleLogVisibility()
            R.id.menu_exit -> finishAffinity()
        }
        return true
    }
    //https://www.dropbox.com/s/rb853fyb2d31f1k/commands.json?dl=1
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu != null) {
            val keysExist = File(publicKeyFName).exists()
            menu.findItem(R.id.menu_generate).isEnabled = !keysExist
            menu.findItem(R.id.menu_delkeys).isEnabled = keysExist
            menu.findItem(R.id.menu_share).isEnabled = keysExist
            val urlExists = File(urlFName).exists()
            menu.findItem(R.id.menu_refresh).isEnabled = urlExists
            menu.findItem(R.id.menu_logvisible).isChecked = boolPref
        }
        return true
    }

    private fun sharePubkey() {
        //http://code.tutsplus.com/tutorials/android-sdk-implement-a-share-intent--mobile-8433
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.type = "text/plain"
        val shareBody: String = getFile(publicKeyFName)
        sharingIntent.putExtra(
            Intent.EXTRA_SUBJECT,
            "Public key from sshremote on " + nowStr()
        )
        sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody)
        startActivity(Intent.createChooser(sharingIntent, "Share via"))
    }

    private fun delKeys() {
        info("removing the key files")
        File(publicKeyFName).delete()
        File(privateKeyFName).delete()
    }

    private fun import() {
        val input = EditText(this@MainActivity)
        if (File(urlFName).exists()) {
            input.setText(getFile(urlFName))
        }
        AlertDialog.Builder(this@MainActivity)
            .setTitle("Import commands")
            .setMessage("URL")
            .setView(input)
            .setPositiveButton("Ok") { _, _ ->
                val value: Editable = input.text
                save(value.toString(), urlFName)
            }.setNegativeButton("Cancel") { _, _ -> }.show()
    }

    private fun refresh() {
        clearAllButtons()
        commands = ""
        val urlFile = urlFName
        if (File(urlFile).exists()) {
            startDownload(getFile(urlFile))
        }
    }

    private fun downloadCommands(url: String) {
        try {
            commands = downloadFile(url)
            info("downloaded size = ${commands.length} from '$url'")
            runOnUiThread {
                drawButtons(commands)
            }
        } catch (e: Exception) {
            runOnUiThread {
                appendUILog(e.toString())
            }
        }
    }

    private fun clearAllButtons() {
        val table = findViewById<TableLayout>(R.id.btnsTL)
        table.removeAllViews()
    }

    private fun startDownload(url: String) {
        CoroutineScope(Dispatchers.IO).launch{
            // runs on UI thread
            downloadCommands(url)
        }
        info("startDownload('$url') finished...")
        clearAllButtons()
    }

    private fun setText(b: Button, c: Int, suffix: String) {
        val name = b.getTag(R.id.NAME) as String
        val confirmation = b.getTag(R.id.CONFIRMATION) as Boolean
        var text = name
        if (confirmation) text += "?"
        text += suffix
        val spanString = SpannableString(text)
        //spanString.setSpan(new StyleSpan(Typeface.BOLD), 0, spanString.length(), 0);
        spanString.setSpan(StyleSpan(Typeface.NORMAL), 0, spanString.length, 0)
        b.text = spanString
        b.setTextColor(c)
    }

    private fun getNewButton(serverName: String, port: Int, user: String, cmdObj: JSONObject): Button {
        val b = Button(this)
        b.isAllCaps = false
        val bId = ++crtButtonId
        b.id = bId
        val confirmation = cmdObj.has("confirmation") && cmdObj.getBoolean("confirmation")
        val cmdName = cmdObj.getString("name")
        val cmd = cmdObj.getString("cmd")
        b.setTag(R.id.NAME, cmdName)
        b.setTag(R.id.CONFIRMATION, confirmation)
        setText(b, Color.BLACK, "")
        b.setOnClickListener {
            if (!confirmation)
                startExecute(user, serverName, port, cmd, bId)
            else
                AlertDialog.Builder(this)
                    .setTitle("Confirmation")
                    .setMessage("Are you sure you want to execute '$cmd'?")
                    .setPositiveButton("Yes") { _, _ -> startExecute(user, serverName, port, cmd, bId) }
                    .setNegativeButton("No", null)
                    .show()
        }
        return b
    }

    private fun drawButtons(json: String) {
        if (json.isEmpty())
            return
        val o = JSONObject(json)
        val table = findViewById<TableLayout>(R.id.btnsTL)
        table.removeAllViews()
        val cols = o.getInt("cols")
        val servers = o.getJSONArray("servers")
        var n = 0
        var row = TableRow(this)
        for (i in 0 until servers.length()) {
            val server = servers.getJSONObject(i)
            val serverName = server.getString("name")
            val port = server.getInt("port")
            val user = server.getString("user")
            val commands = server.getJSONArray("commands")
            for (j in 0 until commands.length()) {
                val cmdObj = commands.getJSONObject(j)
                val b = getNewButton(serverName, port, user, cmdObj)
                if (n % cols == 0) {
                    row = TableRow(this)
                    row.layoutParams = TableLayout.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
                        TableRow.LayoutParams.WRAP_CONTENT)
                    table.addView(row)
                }
                row.addView(b)
                n++
            }
        }
    }

    private fun asyncExecute(user: String, host: String, port: Int, cmdStr: String, bId: Int): CmdResult {
        info("asyncExecute('$user', '$host', $port, '$cmdStr', $bId)")
        val ssh = SSHClient()
        //ssh.loadKnownHosts()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connect(host, port)
        try {
            auth(ssh, user)
            val session = ssh.startSession()
            try {
                val cmd = session.exec(cmdStr)
                info(cmd.toString())
                cmd.join(1000, TimeUnit.SECONDS)
                val out = String(IOUtils.readFully(cmd.inputStream).toByteArray(), Charsets.UTF_8)
                val err = String(IOUtils.readFully(cmd.errorStream).toByteArray(), Charsets.UTF_8)
                val code = cmd.exitStatus
                info("'$code', '$err', '$out'")
                return CmdResult(code, out, err, bId)
            } finally {
                session.close()
            }
        } finally {
            ssh.disconnect()
        }
    }

    private fun auth(client: SSHClient, username: String)
    {
        val text = File(privateKeyFName).readText(Charsets.UTF_8)
        if (text.contains("OPENSSH")) {
            val kp: KeyProvider = client.loadKeys(privateKeyFName)
            client.authPublickey(username, kp)
        }
        else {
            val kp = KeyPair(loadPubKey(publicKeyFName), loadPriKey(privateKeyFName))
            client.authPublickey(username, KeyPairWrapper(kp))
        }
    }

    private fun startExecute(user: String, host: String, port: Int, cmd: String, bId: Int) {
        info("startExecute('$user', '$host', $port, '$cmd', $bId)")
        val btn = findViewById<Button>(bId)
        setText(btn, Color.DKGRAY, "...")
        CoroutineScope(Dispatchers.IO).launch{
            try {
                val res = asyncExecute(user, host, port, cmd, bId)
                showResult(res)
            }
            catch (e: Exception) {
                appendUILog(e.toString())
                setUIButtonText(findViewById(bId), Color.RED, "")
            }
        }
    }
    // https://github.com/web3j/web3j/issues/915
    private fun setupBouncyCastle() {
        val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            ?: // Web3j will set up the provider lazily when it's first used.
            return
        if (provider.javaClass == BouncyCastleProvider::class.java) {
            // BC with same package name, shouldn't happen in real life.
            return
        }
        // Android registers its own BC provider. As it might be outdated and might not include
        // all needed ciphers, we substitute it with a known BC bundled in the app.
        // Android's BC has its package rewritten to "com.android.org.bouncycastle" and because
        // of that it's possible to have another BC implementation loaded in VM.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    private fun appendUILog(s: String) {
        val logView = log
        runOnUiThread {
            logView.append("$s\n")
            scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun setUIButtonText(btn: Button, color: Int, suffix: String) {
        runOnUiThread { setText(btn, color, suffix) }
    }

    private fun showResult(res: CmdResult) {
        val btn = findViewById<Button>(res.bId)
        setUIButtonText(btn, Color.BLACK, if (res.code == 0) "" else " (${res.code})")
        appendUILog(res.err)
        appendUILog(res.out)
    }

    private val log: TextView
        get() = findViewById(R.id.log)

    private val scrollLog: ScrollView
        get() = findViewById(R.id.logScrollView)

    private fun setLogVisibility() {
        scrollLog.visibility =
            if (boolPref) View.VISIBLE else View.GONE
    }

    private fun toggleLogVisibility() {
        boolPref = !boolPref
        setLogVisibility()
    }

    private var boolPref: Boolean
        get() {
            val settings = getSharedPreferences(prefName, MODE_PRIVATE)
            return settings.getBoolean(logVisibleProp, true)
        }
        set(v) {
            getSharedPreferences(prefName, MODE_PRIVATE).edit {
                putBoolean(logVisibleProp, v)
            }
        }

    private var floatPref: Float
        get() {
            val settings = getSharedPreferences(prefName, MODE_PRIVATE)
            return settings.getFloat(logSizeProp, 0f)
        }
        set(v) {
            getSharedPreferences(prefName, MODE_PRIVATE).edit {
                putFloat(logSizeProp, v)
            }
        }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                val currentSizeSp = log.textSize / resources.displayMetrics.scaledDensity
                val newSizeSp = currentSizeSp + 2
                log.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSizeSp)
                floatPref = newSizeSp
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val currentSizeSp = log.textSize / resources.displayMetrics.scaledDensity
                val newSizeSp = currentSizeSp - 2
                if (newSizeSp > 0) {
                    log.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSizeSp)
                    floatPref = newSizeSp
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
