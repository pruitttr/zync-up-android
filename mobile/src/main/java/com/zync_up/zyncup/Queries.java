package com.zync_up.zyncup;

import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;

/**
 * Queries for supporting Chips UI.
 */
public class Queries {

    public static final Query PHONE = new Query(new String[] {
            Contacts.DISPLAY_NAME,                          // 0
            Phone.NUMBER,                                   // 1
            Phone.TYPE,                                     // 2
            Phone.LABEL,                                    // 3
            Phone.CONTACT_ID,                               // 4
            Phone._ID,                                      // 5
            Contacts.PHOTO_THUMBNAIL_URI,                   // 6
            Contacts.DISPLAY_NAME_SOURCE,                   // 7
            Contacts.LOOKUP_KEY,                            // 8
        }, Phone.CONTENT_FILTER_URI, Phone.CONTENT_URI) {

    };

    static abstract class Query {
        private final String[] mProjection;
        private final Uri mContentFilterUri;
        private final Uri mContentUri;

        public static final int NAME = 0;                // String
        public static final int DESTINATION = 1;         // String
        public static final int DESTINATION_TYPE = 2;    // int
        public static final int DESTINATION_LABEL = 3;   // String
        public static final int CONTACT_ID = 4;          // long
        public static final int DATA_ID = 5;             // long
        public static final int PHOTO_THUMBNAIL_URI = 6; // String
        public static final int DISPLAY_NAME_SOURCE = 7; // int
        public static final int LOOKUP_KEY = 8;          // String

        public Query(String[] projection, Uri contentFilter, Uri content) {
            mProjection = projection;
            mContentFilterUri = contentFilter;
            mContentUri = content;
        }

        public String[] getProjection() {
            return mProjection;
        }

        public Uri getContentFilterUri() {
            return mContentFilterUri;
        }

        public Uri getContentUri() {
            return mContentUri;
        }
    }
}
