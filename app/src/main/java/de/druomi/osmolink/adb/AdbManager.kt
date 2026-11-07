package de.druomi.osmolink.adb

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/**
 * Talks to this phone's own adbd over Wireless debugging (same idea as Shizuku/LADB),
 * so the app can run the one shell command it needs without a PC or root.
 */
class AdbManager private constructor(ctx: Context) : AbsAdbConnectionManager() {
    private val key: PrivateKey
    private val cert: Certificate

    init {
        api = Build.VERSION.SDK_INT
        val dir = File(ctx.filesDir, "adb").apply { mkdirs() }
        val kf = File(dir, "key.pk8")
        val cf = File(dir, "cert.der")
        if (kf.exists() && cf.exists()) {
            key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes()))
            cert = CertificateFactory.getInstance("X.509").generateCertificate(cf.inputStream())
        } else {
            val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            val kp = kpg.generateKeyPair()
            val name = X500Name("CN=DualDrop")
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now), Date(now - 86_400_000L),
                Date(now + 30L * 365 * 86_400_000L), name, kp.public
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
            cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            key = kp.private
            kf.writeBytes(key.encoded)
            cf.writeBytes(cert.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = key
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "DualDrop"

    /** Runs a shell command and returns its combined output. Caller must be connected. */
    fun exec(cmd: String): String {
        openStream("shell:$cmd 2>&1").use { s ->
            // libadb throws "Stream closed" instead of returning -1 once the shell has exited and the
            // queue is drained, so treat that as end-of-output.
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (true) {
                val n = try { s.read(buf, 0, buf.size) } catch (e: java.io.IOException) { if (s.isClosed) -1 else throw e }
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toString("UTF-8").trim()
        }
    }

    companion object {
        private const val TAG = "OsmoAdb"
        @Volatile private var inst: AdbManager? = null
        fun get(ctx: Context): AdbManager =
            inst ?: synchronized(this) { inst ?: AdbManager(ctx.applicationContext).also { inst = it } }

        /**
         * Connect (mDNS discovery of the TLS port) and run. Retries with a fresh connection: right after
         * pairing, or right after Wireless debugging was switched on, the first stream can be dropped.
         */
        fun run(ctx: Context, cmd: String, timeoutMs: Long = 12_000, attempts: Int = 3): Result<String> {
            var last: Throwable? = null
            repeat(attempts) { i ->
                val r = runCatching {
                    val m = get(ctx)
                    if (!m.isConnected) {
                        m.setHostAddress("127.0.0.1")
                        check(m.connectTls(ctx, timeoutMs)) { "Wireless debugging not reachable" }
                    }
                    m.exec(cmd).also { Log.i(TAG, "$cmd -> $it") }
                }
                if (r.isSuccess) return r
                last = r.exceptionOrNull()
                Log.w(TAG, "run failed (attempt ${i + 1}/$attempts): $cmd", last)
                runCatching { get(ctx).disconnect() }
                Thread.sleep(1500)
            }
            return Result.failure(last ?: IllegalStateException("adb failed"))
        }
    }
}
