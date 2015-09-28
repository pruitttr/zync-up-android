package com.zync_up.zyncup;

import android.net.Uri;

public class SearchResult {
    public String mId;
    public String mName;
    public String mNumber;
    public Uri mPhoto;

    public SearchResult(String id, String name, String number, Uri photo) {
        this.mId = id;
        this.mName = name;
        this.mPhoto = photo;
        this.mNumber = number;
    }

    public String getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public String getNumber() { return mNumber; }

    public Uri getPhoto() {
        return mPhoto;
    }

    public static SearchResult constructResult(String id,
                                                String name,
                                                String number,
                                                Uri photo) {
        return new SearchResult(id, name, number, photo);
    }

}