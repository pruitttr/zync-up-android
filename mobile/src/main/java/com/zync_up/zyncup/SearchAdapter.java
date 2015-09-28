/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zync_up.zyncup;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Directory;
import android.support.v4.util.LruCache;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for showing a recipient list.
 */
public class SearchAdapter extends BaseAdapter implements Filterable {
    
    private static final String TAG = "SearchAdapter";

    private static final boolean DEBUG = true;

    /**
     * The preferred number of results to be retrieved. This number may be
     * exceeded if there are several directories configured, because we will use
     * the same limit for all directories.
     */
    private static final int DEFAULT_PREFERRED_MAX_RESULT_COUNT = 10;

    /**
     * The number of extra entries requested to allow for duplicates. Duplicates
     * are removed from the overall result.
     */
    static final int ALLOWANCE_FOR_DUPLICATES = 5;

    // This is ContactsContract.PRIMARY_ACCOUNT_NAME. Available from ICS as hidden
    static final String PRIMARY_ACCOUNT_NAME = "name_for_primary_account";
    // This is ContactsContract.PRIMARY_ACCOUNT_TYPE. Available from ICS as hidden
    static final String PRIMARY_ACCOUNT_TYPE = "type_for_primary_account";

    /** The number of photos cached in this Adapter. */
    private static final int PHOTO_CACHE_SIZE = 200;

    /**
     * The "Waiting for more contacts" message will be displayed if search is not complete
     * within this many milliseconds.
     */
    private static final int MESSAGE_SEARCH_PENDING_DELAY = 1000;
    /** Used to prepare "Waiting for more contacts" message. */
    private static final int MESSAGE_SEARCH_PENDING = 1;

    public static final int QUERY_TYPE_EMAIL = 0;
    public static final int QUERY_TYPE_PHONE = 1;

    private final Queries.Query mQuery;
    private final int mQueryType;

    private boolean showMobileOnly = true;

    /**
     * Model object for a {@link Directory} row.
     */
    public final static class DirectorySearchParams {
        public long directoryId;
        public String directoryType;
        public String displayName;
        public String accountName;
        public String accountType;
        public CharSequence constraint;
        public DirectoryFilter filter;
    }

    private static class PhotoQuery {
        public static final String[] PROJECTION = {
            Photo.PHOTO
        };

        public static final int PHOTO = 0;
    }

    protected static class DirectoryListQuery {

        public static final Uri URI =
                Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "directories");
        public static final String[] PROJECTION = {
            Directory._ID,              // 0
            Directory.ACCOUNT_NAME,     // 1
            Directory.ACCOUNT_TYPE,     // 2
            Directory.DISPLAY_NAME,     // 3
            Directory.PACKAGE_NAME,     // 4
            Directory.TYPE_RESOURCE_ID, // 5
        };

        public static final int ID = 0;
        public static final int ACCOUNT_NAME = 1;
        public static final int ACCOUNT_TYPE = 2;
        public static final int DISPLAY_NAME = 3;
        public static final int PACKAGE_NAME = 4;
        public static final int TYPE_RESOURCE_ID = 5;
    }

    /** Used to temporarily hold results in Cursor objects. */
    protected static class TemporaryResult {
        public final String mContactId;
        public final String mName;
        public final String mNumber;
        public final Uri mPhoto;

        public TemporaryResult(
                String contactId,
                String name,
                String number,
                Uri photo) {
            this.mContactId = contactId;
            this.mName = name;
            this.mNumber = number;
            this.mPhoto = photo;

        }

        public TemporaryResult(Cursor cursor) {
            this.mContactId = cursor.getString(Queries.Query.CONTACT_ID);
            this.mName = cursor.getString(Queries.Query.NAME);
            this.mNumber = cursor.getString(Queries.Query.DESTINATION);
            this.mPhoto = Uri.parse(cursor.getString(Queries.Query.PHOTO_THUMBNAIL_URI));
        }
    }

    /**
     * Used to pass results from {@link DefaultFilter#performFiltering(CharSequence)} to
     * {@link DefaultFilter#publishResults(CharSequence, Filter.FilterResults)}
     */
    private static class DefaultFilterResult {
        public final List<SearchResult> entries;
        public final LinkedHashMap<String, List<SearchResult>> searchResultMap;
        public final List<SearchResult> nonAggregatedResults;
        public final Set<String> existingDestinations;
        public final List<DirectorySearchParams> paramsList;

        public DefaultFilterResult(List<SearchResult> entries,
                LinkedHashMap<String, List<SearchResult>> searchResultMap,
                List<SearchResult> nonAggregatedResults,
                Set<String> existingDestinations,
                List<DirectorySearchParams> paramsList) {
            this.entries = entries;
            this.searchResultMap = searchResultMap;
            this.nonAggregatedResults = nonAggregatedResults;
            this.existingDestinations = existingDestinations;
            this.paramsList = paramsList;
        }
    }

    /**
     * An asynchronous filter used for loading two data sets: email rows from the local
     * contact provider and the list of {@link Directory}'s.
     */
    private final class DefaultFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (DEBUG) {
                Log.d(TAG, "start filtering. constraint: " + constraint + ", thread:"
                        + Thread.currentThread());
            }

            if (constraint == null) {
                return new FilterResults();
            }

            final FilterResults results = new FilterResults();
            Cursor defaultDirectoryCursor = null;
            Cursor directoryCursor = null;
            boolean limitResults = true;

            if (TextUtils.isEmpty(constraint)) {
                limitResults = false;
            }

            try {
                defaultDirectoryCursor = doQuery(
                        constraint,
                        limitResults ? mPreferredMaxResultCount : -1);

                if (defaultDirectoryCursor == null) {
                    if (DEBUG) {
                        Log.w(TAG, "null cursor returned");
                    }
                } else {
                    // These variables will become mResults, mResultMap, mNonAggregatedResults, and
                    // mExistingDestinations. Here we shouldn't use those member variables directly
                    // since this method is run outside the UI thread.
                    final LinkedHashMap<String, List<SearchResult>> searchResultMap = new LinkedHashMap<>();
                    final List<SearchResult> nonAggregatedResults = new ArrayList<>();
                    final Set<String> existingDestinations = new HashSet<>();

                    while (defaultDirectoryCursor.moveToNext()) {
                        // Note: At this point each searchResult doesn't contain any photo
                        // (thus getPhotoBytes() returns null).
                        putOneResult(
                                new TemporaryResult(defaultDirectoryCursor),
                                searchResultMap,
                                existingDestinations);
                    }

                    // We'll copy this result to mResult in publicResults() (run in the UX thread).
                    final List<SearchResult> entries = constructResultList(
                            searchResultMap, nonAggregatedResults);

                    // After having local results, check the size of results. If the results are
                    // not enough, we search remote directories, which will take longer time.
                    final int limit = mPreferredMaxResultCount - existingDestinations.size();
                    final List<DirectorySearchParams> paramsList;
                    if (limit > 0 && limitResults) {
                        if (DEBUG) {
                            Log.d(TAG, "More entries should be needed (current: "
                                    + existingDestinations.size()
                                    + ", remaining limit: " + limit + ") ");
                        }
                        directoryCursor = mContentResolver.query(
                                DirectoryListQuery.URI, DirectoryListQuery.PROJECTION,
                                null, null, null);
                        paramsList = setupOtherDirectories(mContext, directoryCursor, mAccount);
                    } else {
                        // We don't need to search other directories.
                        paramsList = null;
                    }

                    results.values = new DefaultFilterResult(
                            entries, searchResultMap, nonAggregatedResults,
                            existingDestinations, paramsList);
                    results.count = 1;
                }
            } finally {
                if (defaultDirectoryCursor != null) {
                    defaultDirectoryCursor.close();
                }
                if (directoryCursor != null) {
                    directoryCursor.close();
                }
            }
            return results;
        }

        @Override
        protected void publishResults(final CharSequence constraint, FilterResults results) {
            // If a user types a string very quickly and database is slow, "constraint" refers to
            // an older text which shows inconsistent results for users obsolete (b/4998713).
            // TODO: Fix it.
            mCurrentConstraint = constraint;

            clearTempResults();

            if (results.values != null) {
                DefaultFilterResult defaultFilterResult = (DefaultFilterResult) results.values;
                mResultMap = defaultFilterResult.searchResultMap;
                mNonAggregatedResults = defaultFilterResult.nonAggregatedResults;
                mExistingDestinations = defaultFilterResult.existingDestinations;

                // If there are no local results, in the new result set, cache off what had been
                // shown to the user for use until the first directory result is returned
                if (defaultFilterResult.entries.size() == 0 &&
                        defaultFilterResult.paramsList != null) {
                    cacheCurrentResults();
                }

                updateResults(defaultFilterResult.entries);

                // We need to search other remote directories, doing other Filter requests.
                if (defaultFilterResult.paramsList != null) {
                    final int limit = mPreferredMaxResultCount -
                            defaultFilterResult.existingDestinations.size();
                    startSearchOtherDirectories(constraint, defaultFilterResult.paramsList, limit);
                }
            }

        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            final SearchResult searchResult = (SearchResult)resultValue;
            final String displayName = searchResult.getName();
            final String emailAddress = searchResult.getNumber();
            if (TextUtils.isEmpty(displayName) || TextUtils.equals(displayName, emailAddress)) {
                 return emailAddress;
            } else {
                return new Rfc822Token(displayName, emailAddress, null).toString();
            }
        }
    }

    /**
     * An asynchronous filter that performs search in a particular directory.
     */
    protected class DirectoryFilter extends Filter {
        private final DirectorySearchParams mParams;
        private int mLimit;

        public DirectoryFilter(DirectorySearchParams params) {
            mParams = params;
        }

        public synchronized void setLimit(int limit) {
            this.mLimit = limit;
        }

        public synchronized int getLimit() {
            return this.mLimit;
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (DEBUG) {
                Log.d(TAG, "DirectoryFilter#performFiltering. directoryId: " + mParams.directoryId
                        + ", constraint: " + constraint + ", thread: " + Thread.currentThread());
            }
            final FilterResults results = new FilterResults();
            results.values = null;
            results.count = 0;

            if (!TextUtils.isEmpty(constraint)) {
                final ArrayList<TemporaryResult> tempResults = new ArrayList<TemporaryResult>();

                Cursor cursor = null;
                try {
                    // We don't want to pass this Cursor object to UI thread (b/5017608).
                    // Assuming the result should contain fairly small results (at most ~10),
                    // We just copy everything to local structure.
                    cursor = doQuery(constraint, getLimit());

                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            tempResults.add(new TemporaryResult(cursor));
                        }
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                if (!tempResults.isEmpty()) {
                    results.values = tempResults;
                    results.count = 1;
                }
            }

            if (DEBUG) {
                Log.v(TAG, "finished loading directory \"" + mParams.displayName + "\"" +
                        " with query " + constraint);
            }

            return results;
        }

        @Override
        protected void publishResults(final CharSequence constraint, FilterResults results) {
            if (DEBUG) {
                Log.d(TAG, "DirectoryFilter#publishResult. constraint: " + constraint
                        + ", mCurrentConstraint: " + mCurrentConstraint);
            }
            mDelayedMessageHandler.removeDelayedLoadMessage();
            // Check if the received result matches the current constraint
            // If not - the user must have continued typing after the request was issued, which
            // means several member variables (like mRemainingDirectoryLoad) are already
            // overwritten so shouldn't be touched here anymore.
            if (TextUtils.equals(constraint, mCurrentConstraint)) {
                if (results.count > 0) {
                    @SuppressWarnings("unchecked")
                    final ArrayList<TemporaryResult> tempResults =
                            (ArrayList<TemporaryResult>) results.values;

                    for (TemporaryResult tempResult : tempResults) {
                        putOneResult(tempResult, mResultMap, mExistingDestinations);
                    }
                }

                // If there are remaining directories, set up delayed message again.
                mRemainingDirectoryCount--;
                if (mRemainingDirectoryCount > 0) {
                    if (DEBUG) {
                        Log.d(TAG, "Resend delayed load message. Current mRemainingDirectoryLoad: "
                                + mRemainingDirectoryCount);
                    }
                    mDelayedMessageHandler.sendDelayedLoadMessage();
                }

                // If this directory result has some items, or there are no more directories that
                // we are waiting for, clear the temp results
                if (results.count > 0 || mRemainingDirectoryCount == 0) {
                    // Clear the temp entries
                    clearTempResults();
                }
            }

            // Show the list again without "waiting" message.
            updateResults(constructResultList(mResultMap, mNonAggregatedResults));
        }
    }

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final LayoutInflater mInflater;
    private Account mAccount;
    private final int mPreferredMaxResultCount;
    private DropdownLayoutAdapter mDropdownLayoutAdapter;

    /**
     * {@link #mResults} is responsible for showing every result for this Adapter. To
     * construct it, we use {@link #mResultMap}, {@link #mNonAggregatedResults}, and
     * {@link #mExistingDestinations}.
     *
     * First, each destination (an email address or a phone number) with a valid contactId is
     * inserted into {@link #mResultMap} and grouped by the contactId. Destinations without valid
     * contactId (possible if they aren't in local storage) are stored in
     * {@link #mNonAggregatedResults}.
     * Duplicates are removed using {@link #mExistingDestinations}.
     *
     * After having all results from Cursor objects, all destinations in mResultMap are copied to
     * {@link #mResults}. If the number of destinations is not enough (i.e. less than
     * {@link #mPreferredMaxResultCount}), destinations in mNonAggregatedResults are also used.
     *
     * These variables are only used in UI thread, thus should not be touched in
     * performFiltering() methods.
     */
    private LinkedHashMap<String, List<SearchResult>> mResultMap;
    private List<SearchResult> mNonAggregatedResults;
    private Set<String> mExistingDestinations;
    /** Note: use {@link #updateResults(List)} to update this variable. */
    private List<SearchResult> mResults;
    private List<SearchResult> mTempResults;

    /** The number of directories this adapter is waiting for results. */
    private int mRemainingDirectoryCount;

    /**
     * Used to ignore asynchronous queries with a different constraint, which may happen when
     * users type characters quickly.
     */
    private CharSequence mCurrentConstraint;

    private static LruCache<Uri, byte[]> mPhotoCacheMap;

    /**
     * Handler specific for maintaining "Waiting for more contacts" message, which will be shown
     * when:
     * - there are directories to be searched
     * - results from directories are slow to come
     */
    private final class DelayedMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (mRemainingDirectoryCount > 0) {
                updateResults(constructResultList(mResultMap, mNonAggregatedResults));
            }
        }

        public void sendDelayedLoadMessage() {
            sendMessageDelayed(obtainMessage(MESSAGE_SEARCH_PENDING, 0, 0, null),
                    MESSAGE_SEARCH_PENDING_DELAY);
        }

        public void removeDelayedLoadMessage() {
            removeMessages(MESSAGE_SEARCH_PENDING);
        }
    }

    private final DelayedMessageHandler mDelayedMessageHandler = new DelayedMessageHandler();

    private ResultsUpdatedObserver mResultsUpdatedObserver;

    /**
     * Constructor for email queries.
     */
    public SearchAdapter(Context context) {
        this(context, DEFAULT_PREFERRED_MAX_RESULT_COUNT, QUERY_TYPE_EMAIL);
    }

    public SearchAdapter(Context context, int preferredMaxResultCount) {
        this(context, preferredMaxResultCount, QUERY_TYPE_EMAIL);
    }

    public SearchAdapter(int queryMode, Context context) {
        this(context, DEFAULT_PREFERRED_MAX_RESULT_COUNT, queryMode);
    }

    public SearchAdapter(int queryMode, Context context, int preferredMaxResultCount) {
        this(context, preferredMaxResultCount, queryMode);
    }

    public SearchAdapter(Context context, int preferredMaxResultCount, int queryMode) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mInflater = LayoutInflater.from(context);
        mPreferredMaxResultCount = preferredMaxResultCount;
        if (mPhotoCacheMap == null) {
            mPhotoCacheMap = new LruCache<Uri, byte[]>(PHOTO_CACHE_SIZE);
        }
        mQueryType = queryMode;

        mQuery = Queries.PHONE;
    }

    public Context getContext() {
        return mContext;
    }

    public int getQueryType() {
        return mQueryType;
    }

    public void setDropdownChipLayouter(DropdownLayoutAdapter dropdownLayoutAdapter) {
        mDropdownLayoutAdapter = dropdownLayoutAdapter;
    }

    public DropdownLayoutAdapter getDropdownChipLayouter() {
        return mDropdownLayoutAdapter;
    }

    /** Will be called from {@link AutoCompleteTextView} to prepare auto-complete list. */
    @Override
    public Filter getFilter() {
        return new DefaultFilter();
    }

    public static List<DirectorySearchParams> setupOtherDirectories(Context context,
            Cursor directoryCursor, Account account) {
        final PackageManager packageManager = context.getPackageManager();
        final List<DirectorySearchParams> paramsList = new ArrayList<DirectorySearchParams>();
        DirectorySearchParams preferredDirectory = null;
        while (directoryCursor.moveToNext()) {
            final long id = directoryCursor.getLong(DirectoryListQuery.ID);

            // Skip the local invisible directory, because the default directory already includes
            // all local results.
            if (id == Directory.LOCAL_INVISIBLE) {
                continue;
            }

            final DirectorySearchParams params = new DirectorySearchParams();
            final String packageName = directoryCursor.getString(DirectoryListQuery.PACKAGE_NAME);
            final int resourceId = directoryCursor.getInt(DirectoryListQuery.TYPE_RESOURCE_ID);
            params.directoryId = id;
            params.displayName = directoryCursor.getString(DirectoryListQuery.DISPLAY_NAME);
            params.accountName = directoryCursor.getString(DirectoryListQuery.ACCOUNT_NAME);
            params.accountType = directoryCursor.getString(DirectoryListQuery.ACCOUNT_TYPE);
            if (packageName != null && resourceId != 0) {
                try {
                    final Resources resources =
                            packageManager.getResourcesForApplication(packageName);
                    params.directoryType = resources.getString(resourceId);
                    if (params.directoryType == null) {
                        Log.e(TAG, "Cannot resolve directory name: "
                                + resourceId + "@" + packageName);
                    }
                } catch (NameNotFoundException e) {
                    Log.e(TAG, "Cannot resolve directory name: "
                            + resourceId + "@" + packageName, e);
                }
            }

            // If an account has been provided and we found a directory that
            // corresponds to that account, place that directory second, directly
            // underneath the local contacts.
            if (account != null && account.name.equals(params.accountName) &&
                    account.type.equals(params.accountType)) {
                preferredDirectory = params;
            } else {
                paramsList.add(params);
            }
        }

        if (preferredDirectory != null) {
            paramsList.add(1, preferredDirectory);
        }

        return paramsList;
    }

    /**
     * Starts search in other directories using {@link Filter}. Results will be handled in
     * {@link DirectoryFilter}.
     */
    protected void startSearchOtherDirectories(
            CharSequence constraint, List<DirectorySearchParams> paramsList, int limit) {
        final int count = paramsList.size();
        // Note: skipping the default partition (index 0), which has already been loaded
        for (int i = 1; i < count; i++) {
            final DirectorySearchParams params = paramsList.get(i);
            params.constraint = constraint;
            if (params.filter == null) {
                params.filter = new DirectoryFilter(params);
            }
            params.filter.setLimit(limit);
            params.filter.filter(constraint);
        }

        // Directory search started. We may show "waiting" message if directory results are slow
        // enough.
        mRemainingDirectoryCount = count - 1;
        mDelayedMessageHandler.sendDelayedLoadMessage();
    }

    private static void putOneResult(
            TemporaryResult searchResult,
            LinkedHashMap<String, List<SearchResult>> searchResultMap,
            Set<String> existingDestinations) {
        if (existingDestinations.contains(searchResult.mNumber)) {
            return;
        }

        existingDestinations.add(searchResult.mNumber);

        final List<SearchResult> searchResultList = new ArrayList<>();
            searchResultList.add(SearchResult.constructResult(
                    searchResult.mContactId,
                    searchResult.mName,
                    searchResult.mNumber,
                    searchResult.mPhoto));

            searchResultMap.put(searchResult.mContactId, searchResultList);
    }

    /**
     * Constructs an actual list for this Adapter using {@link #mResultMap}. Also tries to
     * fetch a cached photo for each contact searchResult (other than separators), or request another
     * thread to get one from directories.
     */
    private List<SearchResult> constructResultList(
            LinkedHashMap<String, List<SearchResult>> searchResultMap,
            List<SearchResult> nonAggregatedResults) {
        final List<SearchResult> entries = new ArrayList<SearchResult>();
        int validResultCount = 0;
        for (Map.Entry<String, List<SearchResult>> mapResult : searchResultMap.entrySet()) {
            final List<SearchResult> searchResultList = mapResult.getValue();
            final int size = searchResultList.size();
            for (int i = 0; i < size; i++) {
                SearchResult searchResult = searchResultList.get(i);
                entries.add(searchResult);
                validResultCount++;
            }
//            if (validResultCount > mPreferredMaxResultCount) {
//                break;
//            }
        }
        if (validResultCount <= mPreferredMaxResultCount) {
            for (int i = 0; i < nonAggregatedResults.size(); i++) {
                SearchResult searchResult = nonAggregatedResults.get(i);
//                if (validResultCount > mPreferredMaxResultCount) {
//                    break;
//                }
                entries.add(searchResult);

                validResultCount++;
            }
        }

        return entries;
    }


    public interface ResultsUpdatedObserver {
        void onChanged(List<SearchResult> entries);
    }


    public void registerUpdateObserver(ResultsUpdatedObserver observer) {
        mResultsUpdatedObserver = observer;
    }

    /** Resets {@link #mResults} and notify the event to its parent ListView. */
    private void updateResults(List<SearchResult> newResults) {
        mResults = newResults;
        mResultsUpdatedObserver.onChanged(newResults);
        notifyDataSetChanged();
    }

    private void cacheCurrentResults() {
        mTempResults = mResults;
    }

    private void clearTempResults() {
        mTempResults = null;
    }

    protected List<SearchResult> getResults() {
        return mTempResults != null ? mTempResults : mResults;
    }

    private Cursor doQuery(CharSequence constraint, int limit) {
        final Uri.Builder builder = mQuery.getContentFilterUri().buildUpon();
        builder.appendPath(constraint.toString());
        builder.appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                String.valueOf(limit + ALLOWANCE_FOR_DUPLICATES));

        if (mAccount != null) {
            builder.appendQueryParameter(PRIMARY_ACCOUNT_NAME, mAccount.name);
            builder.appendQueryParameter(PRIMARY_ACCOUNT_TYPE, mAccount.type);
        }
        String where = (showMobileOnly && mQueryType == QUERY_TYPE_PHONE) ?
                ContactsContract.CommonDataKinds.Phone.TYPE + "=" + ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE : null;
        final long start = System.currentTimeMillis();
        final Cursor cursor = mContentResolver.query(
                limit == -1 ? mQuery.getContentUri() : builder.build(), mQuery.getProjection(),
                where, null,
                limit == -1 ? ContactsContract.Contacts.DISPLAY_NAME + " ASC" : null);
        final long end = System.currentTimeMillis();
        if (DEBUG) {
            Log.d(TAG, "Time for autocomplete (query: " + constraint
                    + ", num_of_results: "
                    + (cursor != null ? cursor.getCount() : "null") + "): "
                    + (end - start) + " ms");
        }
        return cursor;
    }

    @Override
    public int getCount() {
        final List<SearchResult> entries = getResults();
        return entries != null ? entries.size() : 0;
    }

    @Override
    public SearchResult getItem(int position) {
        return getResults().get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final SearchResult searchResult = getResults().get(position);

        return mDropdownLayoutAdapter.bindView(convertView, parent, searchResult);
    }
}
