package com.zync_up.zyncup;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;

public class RegisterFragment extends Fragment {
    //TODO: Comment & Javadoc

    private Activity mActivity;

    private CardView mRegisterFormView;

    private EditText mUsernameView;
    private EditText mPasswordView;
    private EditText mPhoneView;
    private EditText mFirstNameView;
    private EditText mLastNameView;

    private InputMethodManager mInputMethodManager;

    /**
     * Keep track of the register task to ensure we can cancel it if requested.
     */
    private UserRegisterTask mAuthTask = null;

    private View mProgressView;

    public RegisterFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mActivity = getActivity();
        mActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        View rootView = inflater.inflate(R.layout.fragment_register, container, false);

        mFirstNameView = (EditText) rootView.findViewById(R.id.register_first_name);

        mLastNameView = (EditText) rootView.findViewById(R.id.register_last_name);

        mUsernameView = (EditText) rootView.findViewById(R.id.register_username);

        mPasswordView = (EditText) rootView.findViewById(R.id.register_password);

        mPhoneView = (EditText) rootView.findViewById(R.id.register_phone);

        mProgressView = rootView.findViewById(R.id.register_progress);

        mInputMethodManager =
                (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);

        mRegisterFormView = (CardView) rootView.findViewById(R.id.register_form);
        mRegisterFormView.setOnTouchListener(new CardTouchListener());

        FrameLayout swipeView = (FrameLayout) rootView.findViewById(R.id.register_swipe);
        swipeView.setOnTouchListener(new CardTouchListener());

        Button mRegisterButton = (Button) rootView.findViewById(R.id.register_button);
        mRegisterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptRegister();
            }
        });

        // Inflate the layout for this fragment
        return rootView;
    }


    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    public void attemptRegister() {

        mInputMethodManager.hideSoftInputFromWindow(mRegisterFormView.getWindowToken(), 0);

        boolean cancel = false;
        View focusView = null;

        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mFirstNameView.setError(null);
        mLastNameView.setError(null);
        mUsernameView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String firstName = mFirstNameView.getText().toString();
        String lastName = mLastNameView.getText().toString();
        String username = mUsernameView.getText().toString();
        String password = mPasswordView.getText().toString();
        String phone = mPhoneView.getText().toString();

        //Check for a valid first name
        if (TextUtils.isEmpty(firstName)) {
            mFirstNameView.setError(getString(R.string.error_field_required));
            focusView = mFirstNameView;
            cancel = true;
        }

        //Check for a valid last name
        if (TextUtils.isEmpty(lastName)) {
            mLastNameView.setError(getString(R.string.error_field_required));
            focusView = mFirstNameView;
            cancel = true;
        }

        // Check for a valid username, if the user entered one.
        if (!TextUtils.isEmpty(username) && !isValidLength(username)) {
            mUsernameView.setError(getString(R.string.error_invalid_username));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid password.
        if (!TextUtils.isEmpty(password) && !isValidLength(password)) {
            mPasswordView.setError(getString(R.string.error_field_required));
            focusView = mPasswordView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserRegisterTask(firstName, lastName, username, password, phone);
            mAuthTask.execute();
        }
    }

    private boolean isValidLength(String string) {
        return string.length() > 4;
    }

    public void showProgress(final boolean show) {

        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mRegisterFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        mRegisterFormView.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mRegisterFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        });

        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    /**
     * Handles user touch on the login form. The card should move up
     * and down, and collapse when brought below the threshold.
     */
    private class CardTouchListener implements View.OnTouchListener {

        int prevY;
        int check = 0;
        int prevTop;

        final CardView.LayoutParams params =
                (CardView.LayoutParams) mRegisterFormView.getLayoutParams();


        @Override
        public boolean onTouch(final View view, final MotionEvent event) {

            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    params.topMargin = (int) (prevTop + (event.getRawY() - prevY));
                    mRegisterFormView.setLayoutParams(params);
                    break;

                case MotionEvent.ACTION_UP:
                    check = params.topMargin - prevTop;
                    if (check > 200) {
                        getFragmentManager().popBackStack();
                        break;
                    } else {
                        mRegisterFormView.animate()
                                .translationYBy(-check)
                                .setDuration(600)
                                .setInterpolator(new OvershootInterpolator());
                    }
                    break;

                case MotionEvent.ACTION_DOWN:
                    prevTop = params.topMargin;
                    prevY = (int) event.getRawY();
                    mInputMethodManager.hideSoftInputFromWindow(
                            view.getWindowToken(), 0);
                    break;

            }
            return true;
        }
    }



    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    private class UserRegisterTask extends AsyncTask<String, Void, Boolean> {

        private final String mFirstName;
        private final String mLastName;
        private final String mUsername;
        private final String mPassword;
        private final String mPhone;

        private final String LOG_TAG = UserRegisterTask.class.getSimpleName();

        private final String TAG_USERNAME = "username";
        private final String TAG_PASSWORD = "password";
        private final String TAG_LAST_NAME = "last_name";
        private final String TAG_AUTH_TOKEN = "auth_token";
        private final String TAG_PHONE = "phone";
        private final String TAG_VERTICAL_ACCURACY = "vertical_accuracy";
        private final String TAG_USER_ID = "user_id";
        private final String TAG_FIRST_NAME = "first_name";
        private final String TAG_UDID = "udid";
        private final String TAG_HORIZONTAL_ACCURACY = "horizontal_accuracy";
        private final String TAG_LONGITUDE = "longitude";
        private final String TAG_LATITUDE = "latitude";
        private final String TAG_LOGGED_IN = "logged_in";
        private final String TAG_IMAGE_URL = "image_url";
        private final String TAG_EMAIL = "email";

        SharedPreferences.Editor editor = mActivity.getSharedPreferences(
                "PREFS", Activity.MODE_PRIVATE).edit();

        UserRegisterTask(String firstName, String lastName, String username, String password, String phone) {
            mFirstName = firstName;
            mLastName = lastName;
            mUsername = username;
            mPassword = password;
            mPhone = phone;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            // TODO: attempt authentication against a network service.

            JSONParser parser = new JSONParser();

            String TAG_REQUEST_TYPE = "POST";

            try {

                //Constructs the URL for the call.
                URL url = new URL("http://mfebseast.elasticbeanstalk.com/api/v2.0/users/");

                String userCredentials = mUsername + ":" + mPassword;
                String basicAuth = "Basic " + new String(Base64.encode(userCredentials.getBytes(),
                        Base64.NO_WRAP));

                JSONObject bodyObject = new JSONObject();
                try {
                    bodyObject.put(TAG_FIRST_NAME, mFirstName);
                    bodyObject.put(TAG_LAST_NAME, mLastName);
                    bodyObject.put(TAG_USERNAME, mUsername);
                    bodyObject.put(TAG_PASSWORD, mPassword);
                    if (!mPhone.equals(""))
                        bodyObject.put(TAG_PHONE, mPhone);
                    //TODO: Figure this out
                    bodyObject.put(TAG_UDID, "0123456789012345678901234567890123456789");

                } catch (JSONException e) {
                    Log.e(LOG_TAG, e.toString());
                }

                JSONObject loginObject = parser.getRegisterObject(mActivity, url, TAG_REQUEST_TYPE,
                        basicAuth, bodyObject);

                return loginObject !=null && storePreferences(loginObject);

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                return false;
            }
        }

        private boolean storePreferences(JSONObject loginObject) {

            try {
                if (loginObject.getBoolean(TAG_LOGGED_IN)) {
                    editor.putBoolean(TAG_LOGGED_IN, loginObject.getBoolean(TAG_LOGGED_IN));
                } else {
                    return false;
                }

            } catch (JSONException e) {
                Log.e(LOG_TAG, e.toString());
                return false;
            }
            storePreference(loginObject, TAG_FIRST_NAME);
            storePreference(loginObject, TAG_LAST_NAME);
            storePreference(loginObject, TAG_USERNAME);
            storePreference(loginObject, TAG_AUTH_TOKEN);
            storePreference(loginObject, TAG_PHONE);
            storePreference(loginObject, TAG_VERTICAL_ACCURACY);
            storePreference(loginObject, TAG_HORIZONTAL_ACCURACY);
            storePreference(loginObject, TAG_USER_ID);
            storePreference(loginObject, TAG_UDID);
            storePreference(loginObject, TAG_LONGITUDE);
            storePreference(loginObject, TAG_LATITUDE);
            storePreference(loginObject, TAG_IMAGE_URL);
            storePreference(loginObject, TAG_EMAIL);

            editor.apply();

            return true;
        }

        private void storePreference(JSONObject object, String tag) {
            try {
                if (object.getString(tag) != null) {
                    editor.putString(tag, object.getString(tag));
                }
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.toString());
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            mAuthTask = null;

            if (success) {
                Intent intent = new Intent(mActivity, GetStartedActivity.class);
                startActivity(intent);
                mActivity.finish();
            } else {
                showProgress(false);
                mPasswordView.setError(getString(R.string.error_login));
                mPasswordView.requestFocus();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }

}
