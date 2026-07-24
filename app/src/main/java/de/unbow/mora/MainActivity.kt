package de.unbow.mora

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.unbow.mora.ui.MoraApp
import de.unbow.mora.ui.theme.MoraTheme

data class IncomingDocumentRequest(
    val id: Long,
    val uri: Uri? = null,
    val sharedText: String? = null,
    val suggestedName: String = "共享内容.md",
    val grantedFlags: Int,
    val canWriteHint: Boolean,
)

class MainActivity : ComponentActivity() {

    private var requestCounter = 0L
    private var incomingRequest by mutableStateOf<IncomingDocumentRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A configuration change recreates the Activity with the same launch Intent while the
        // ViewModel keeps the current document. Only parse the launch Intent for a fresh Activity;
        // genuinely new shares and opens are delivered through onNewIntent.
        incomingRequest = if (savedInstanceState == null) intent.toIncomingRequest() else null
        enableEdgeToEdge()

        setContent {
            MoraTheme {
                MoraApp(
                    incomingRequest = incomingRequest,
                    onIncomingRequestConsumed = { requestId ->
                        if (incomingRequest?.id == requestId) incomingRequest = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingRequest = intent.toIncomingRequest()
    }

    private fun Intent?.toIncomingRequest(): IncomingDocumentRequest? {
        val sourceIntent = this ?: return null
        val uri = when (sourceIntent.action) {
            Intent.ACTION_VIEW,
            Intent.ACTION_EDIT,
            -> sourceIntent.data ?: sourceIntent.clipData?.getItemAt(0)?.uri

            Intent.ACTION_SEND -> sourceIntent.streamUri()
                ?: sourceIntent.clipData?.getItemAt(0)?.uri

            else -> null
        }

        val sharedText = if (sourceIntent.action == Intent.ACTION_SEND && uri == null) {
            sourceIntent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?.takeIf(String::isNotBlank)
        } else {
            null
        }
        if (uri == null && sharedText == null) return null
        if (uri != null && uri.scheme != "content" && uri.scheme != "file") return null

        requestCounter += 1
        return IncomingDocumentRequest(
            id = requestCounter,
            uri = uri,
            sharedText = sharedText,
            suggestedName = sourceIntent.getStringExtra(Intent.EXTRA_TITLE)
                ?.takeIf(String::isNotBlank)
                ?: "共享内容.md",
            grantedFlags = sourceIntent.flags,
            canWriteHint = sourceIntent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0,
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}
