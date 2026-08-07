package com.bodhalauncher.app.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.bodhalauncher.engine.SearchContact

/**
 * Search's contacts edge (#186): reads names from the contacts provider and
 * opens what was found. Only reads — matching and the lexical order are the
 * engine's `resolveSearch`, and nothing is ever stored (ADR 0009). Call and
 * message go through dial and compose screens, never placing a call directly,
 * so no CALL_PHONE permission exists to ask for.
 */
class ContactsReader(private val context: Context) {

    /** Every visible contact's name and keys; blank-named rows have nothing to match. */
    fun contacts(): List<SearchContact> {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )
        val rows = mutableListOf<SearchContact>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, projection, null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(2).orEmpty()
                val lookupKey = cursor.getString(1) ?: continue
                if (name.isNotBlank()) {
                    rows += SearchContact(contactId = cursor.getLong(0), lookupKey = lookupKey, name = name)
                }
            }
        }
        return rows
    }

    /**
     * The contact's primary phone number, or null when none is stored — what
     * decides whether the Actions node offers call and message (#186).
     */
    fun phoneNumber(contact: SearchContact): String? =
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contact.contactId.toString()),
            // The number marked primary first, so the one the user chose leads.
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    /**
     * Tapping a contact result opens it in the system contacts app — the
     * non-destructive default (#186): a mis-tap must not place a call.
     */
    fun open(contact: SearchContact) {
        val uri = ContactsContract.Contacts.getLookupUri(contact.contactId, contact.lookupKey)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The dialer with the number entered, not a placed call. */
    fun dial(number: String) {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** The messaging app's compose screen for the number. */
    fun message(number: String) {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
