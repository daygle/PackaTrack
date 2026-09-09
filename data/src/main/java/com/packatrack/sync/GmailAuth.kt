package com.packatrack.sync

import android.content.Context
import android.content.Intent
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import java.util.Collections

/**
 * Single home for Google account/credential handling used by Gmail Smart Import.
 * The Settings UI (account picking) and the background import worker
 * (authenticated Gmail service) both go through here, so the Google API client
 * dependency stays confined to :data.
 */
object GmailAuth {

    /**
     * Intent that shows Google's account picker. On RESULT_OK the selected
     * account name is available under [android.accounts.AccountManager.KEY_ACCOUNT_NAME].
     */
    fun newChooseAccountIntent(context: Context): Intent =
        readonlyCredential(context).newChooseAccountIntent()

    /** An authenticated Gmail service for a previously picked account. */
    fun gmailService(context: Context, accountName: String): Gmail =
        Gmail.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            readonlyCredential(context).setSelectedAccountName(accountName),
        ).setApplicationName("PackaTrack").build()

    private fun readonlyCredential(context: Context): GoogleAccountCredential =
        GoogleAccountCredential.usingOAuth2(context, Collections.singleton(GmailScopes.GMAIL_READONLY))
}
