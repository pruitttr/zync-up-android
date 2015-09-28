package com.zync_up.zyncup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.zync_up.zyncup.HamburgerDrawable.IconState;

import java.util.List;

public class SearchBar extends RelativeLayout {

    public static final int VOICE_RECOGNITION_CODE = 1234;

    private HamburgerView hamburgerView;
    private TextView textview_search_hint;
    private SearchEditText edittext_search;
    private Context mContext;
    private boolean searchOpen;
    private boolean isMic;
    private ImageView mic;
    private SearchListener mSearchListener;
    private MenuListener mMenuListener;

    private boolean isVoiceRecognitionIntentSupported;
    private Activity mContainerActivity;

    /**
     * Create a new searchbox
     * @param context Context
     */
    public SearchBar(Context context) {
        this(context, null);
    }

    /**
     * Create a searchbox with params
     * @param context Context
     * @param attrs Attributes
     */
    public SearchBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Create a searchbox with params and a style
     * @param context Context
     * @param attrs Attributes
     * @param defStyle Style
     */
    public SearchBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inflate(context, R.layout.searchbar, this);

        mContext = context;

        searchOpen = false;
        isMic = true;
        isVoiceRecognitionIntentSupported = isIntentAvailable(context,
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH));

        hamburgerView = (HamburgerView) findViewById(R.id.hamburgerview);
        textview_search_hint = (TextView) findViewById(R.id.textview_hint);
        mic = (ImageView) findViewById(R.id.imageview_mic);

        edittext_search = (SearchEditText) findViewById(R.id.edittext_search);
        edittext_search.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        com.zync_up.zyncup.SearchAdapter adapter = new com.zync_up.zyncup.SearchAdapter(
                com.zync_up.zyncup.SearchAdapter.QUERY_TYPE_PHONE, mContext);
        edittext_search.setAdapter(adapter);

        hamburgerView.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                switch(hamburgerView.getState()) {
                    case BURGER:
                        if (mMenuListener != null)
                            mMenuListener.onMenuClick();
                        break;
                    case ARROW:
                        toggleSearch();
                        break;
                    case X:
                        edittext_search.setText("");
                }
            }

        });

        textview_search_hint.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                toggleSearch();
            }
        });

        micStateChanged();

        mic.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startVoiceRecognition();
            }
        });

    }

    private static boolean isIntentAvailable(Context context, Intent intent) {
        PackageManager mgr = context.getPackageManager();
        if (mgr != null) {
            List<ResolveInfo> list = mgr.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            return list.size() > 0;
        }
        return false;
    }

    /***
     * Toggle the searchbox's open/closed state manually
     */
    public void toggleSearch() {
        if (searchOpen) {
            closeSearch();
        } else {
            openSearch(true);
        }
    }

    /***
     * Start the voice input activity manually
     */
    public void startVoiceRecognition() {
        if (isMicEnabled()) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                    mContext.getString(R.string.speak_now));
            if (mContainerActivity != null)
                mContainerActivity.startActivityForResult(intent, VOICE_RECOGNITION_CODE);
        }
    }

    public void enableVoiceRecognition(Activity context) {
        mContainerActivity = context;
        micStateChanged();
    }

    private boolean isMicEnabled() {
        return isVoiceRecognitionIntentSupported && mContainerActivity != null;
    }

    private void micStateChanged() {
        mic.setVisibility((!isMic || isMicEnabled()) ? VISIBLE : INVISIBLE);
    }

    /***
     * Set the menu listener
     * @param menuListener MenuListener
     */
    public void setMenuListener(MenuListener menuListener) {
        this.mMenuListener = menuListener;
    }

    /***
     * Set the search listener
     * @param listener SearchListener
     */
    public void setSearchListener(SearchListener listener) {
        this.mSearchListener = listener;
    }

    private void openSearch(Boolean openKeyboard) {
        hamburgerView.animateState(IconState.ARROW);
        mic.setVisibility(GONE);

        textview_search_hint.setVisibility(View.GONE);
        edittext_search.setVisibility(View.VISIBLE);
        edittext_search.requestFocus();

        edittext_search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //TODO: Fix bug that doesn't animate the X --> Arrow after pressing the Arrow.
                if (charSequence.length() > 0 && hamburgerView.getState() == IconState.ARROW) {
                    hamburgerView.animateState(IconState.X);
                } else if (charSequence.length() == 0 && hamburgerView.getState() == IconState.X) {
                    hamburgerView.animateState(IconState.ARROW);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });


        if (mSearchListener != null)
            mSearchListener.onSearchOpened();

        if (openKeyboard) {
            InputMethodManager inputMethodManager = (InputMethodManager) mContext
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.toggleSoftInputFromWindow(
                    getApplicationWindowToken(),
                    InputMethodManager.SHOW_FORCED, 0);
        }
        searchOpen = true;

    }

    private void closeSearch() {
        this.hamburgerView.animateState(IconState.BURGER);
        mic.setVisibility(VISIBLE);

        this.textview_search_hint.setVisibility(View.VISIBLE);
        this.edittext_search.setVisibility(View.GONE);
        //mResultsListView.setVisibility(View.GONE);
        if (mSearchListener != null)
            mSearchListener.onSearchClosed();
        InputMethodManager inputMethodManager = (InputMethodManager) mContext
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(getApplicationWindowToken(),
                0);
        searchOpen = false;
    }


    public interface SearchListener {
        /**
         * Called when the searchbox is opened
         */
        void onSearchOpened();

        /**
         * Called when the clear button is pressed
         */
        void onSearchCleared();

        /**
         * Called when the searchbox is closed
         */
        void onSearchClosed();

        /**
         * Called when the searchbox's edittext changes
         */
        void onSearchTermChanged(String term);

        /**
         * Called when a search happens, with a result
         * @param result
         */
        void onSearch(String result);
    }

    public interface MenuListener {
        /**
         * Called when the menu button is pressed
         */
        void onMenuClick();
    }
}
