package de.unbow.mora

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalMarkdownIntentContractTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext

    @Test
    fun applicationMarkdownViewAndEditResolveToMainActivity() {
        val expectedComponent = ComponentName(targetContext, MainActivity::class.java)

        listOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT).forEach { action ->
            val intent = markdownIntent(action).setPackage(targetContext.packageName)
            val matchingComponents = targetContext.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .map { resolved ->
                    ComponentName(
                        resolved.activityInfo.packageName,
                        resolved.activityInfo.name,
                    )
                }

            assertTrue(
                "$action application/markdown must resolve to MainActivity",
                expectedComponent in matchingComponents,
            )
        }
    }

    @Test
    fun viewAndEditGrantFlagsReachIncomingRequestUnchanged() {
        val grantedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

        listOf(Intent.ACTION_VIEW, Intent.ACTION_EDIT).forEach { action ->
            val sourceIntent = markdownIntent(action).apply {
                addFlags(grantedFlags)
                putExtra(Intent.EXTRA_TITLE, "external.md")
            }

            val request = parseIncomingRequest(sourceIntent)

            assertEquals(EXTERNAL_DOCUMENT_URI, request.uri)
            assertEquals("external.md", request.suggestedName)
            assertEquals(grantedFlags, request.grantedFlags)
        }
    }

    private fun markdownIntent(action: String): Intent = Intent(action).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(EXTERNAL_DOCUMENT_URI, MIME_TYPE_APPLICATION_MARKDOWN)
    }

    private fun parseIncomingRequest(sourceIntent: Intent): IncomingDocumentRequest {
        val result = AtomicReference<Result<IncomingDocumentRequest>>()
        instrumentation.runOnMainSync {
            result.set(
                runCatching {
                    val parser = MainActivity::class.java.getDeclaredMethod(
                        "toIncomingRequest",
                        Intent::class.java,
                    ).apply { isAccessible = true }
                    requireNotNull(
                        parser.invoke(MainActivity(), sourceIntent) as IncomingDocumentRequest?,
                    )
                },
            )
        }
        return requireNotNull(result.get()).getOrThrow()
    }

    companion object {
        private const val MIME_TYPE_APPLICATION_MARKDOWN = "application/markdown"
        private val EXTERNAL_DOCUMENT_URI = Uri.parse(
            "content://de.unbow.mora.test.documents/document/external_intent_contract",
        )
    }
}
