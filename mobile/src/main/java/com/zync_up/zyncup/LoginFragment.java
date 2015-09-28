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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;

public class LoginFragment extends Fragment {
    //TODO: Handle each individual error appropriately so that the use knows how to respond

    //LoginActivity instance
    private Activity mActivity;

    //The login form
    private CardView mLoginFormView;

    //User input fields in the login form
    private EditText mUsernameView;
    private EditText mPasswordView;

    //Keyboard manager
    private InputMethodManager mInputMethodManager;

    //Keep track of login task so we can cancel it if needed
    private UserLoginTask mAuthTask = null;

    //Spinning progressbar for login task
    private View mProgressView;

    //Empty constructor
    public LoginFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    /**
     * Inflates the main view and instantiates UI elements.  Also listens
     * for touches on the login form and background.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //Assign the LoginActivity instance
        mActivity = getActivity();
        mActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        //Assign the soft keyboard
        mInputMethodManager =
                (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);

        //Assign the layout from fragment_login
        View rootView = inflater.inflate(R.layout.fragment_login, container, false);

        //Assign input fields from the login form
        mUsernameView = (EditText) rootView.findViewById(R.id.login_username);
        mPasswordView = (EditText) rootView.findViewById(R.id.login_password);

        //Assign the progressbar
        mProgressView = rootView.findViewById(R.id.login_progress);

        //Assign the login form and begins listening for touches to the form
        mLoginFormView = (CardView) rootView.findViewById(R.id.login_form);
        mLoginFormView.setOnTouchListener(new CardTouchListener());

        //Listen for touches to the screen and move the login form
        FrameLayout swipeView = (FrameLayout) rootView.findViewById(R.id.login_swipe);
        swipeView.setOnTouchListener(new CardTouchListener());

        //Listens for login button clicks
        Button button_login = (Button) rootView.findViewById(R.id.login_button);
        button_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        //Set and start the fade out animation for the splash screen tagline
        Animation animation = AnimationUtils.loadAnimation(mActivity, android.R.anim.fade_out);
        TextView tagline = (TextView) rootView.findViewById(R.id.textview_tagline);
        animation.setFillAfter(true);
        tagline.startAnimation(animation);

        // Inflate the layout for this fragment
        return rootView;
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid username, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    public void attemptLogin() {

        //Cancel and set error message if the form is invalid in any way
        boolean cancel = false;

        //View to focus on when there is an error or invalid form
        View focusView = null;

        //Hide keyboard
        mInputMethodManager.hideSoftInputFromWindow(mLoginFormView.getWindowToken(), 0);

        //If login task is running, don't attempt login
        if (mAuthTask != null) {
            return;
        }

        // Reset errors
        mUsernameView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt
        String username = mUsernameView.getText().toString();
        String password = mPasswordView.getText().toString();

        // Check for a valid password
        if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_field_required));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid username
        if (TextUtils.isEmpty(username)) {
            mUsernameView.setError(getString(R.string.error_field_required));
            focusView = mUsernameView;
            cancel = true;
        }

        // If form is invalid, don't attempt login and focus on the error
        // Otherwise, show the progressbar and start the server call
        if (cancel) {
            focusView.requestFocus();
        } else {
            showProgress(true);
            mAuthTask = new UserLoginTask(username, password);
            mAuthTask.execute();
        }
    }


    /**
     * Shows ProgressBar instead of login form while mAuthTask
     * runs in the background.
     */
    public void showProgress(final boolean show) {

        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
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

        //Variables to store the Y location of the login form
        int prevY;
        int check = 0;
        int prevTop;

        //Minimum distance the login form has to travel downwards
        final int MIN_DISTANCE = 200;

        //Duration of the collapse animation
        final int ANIMATION_DURATION = 200;

        //Assign layout params for the login form
        final CardView.LayoutParams params =
                (CardView.LayoutParams) mLoginFormView.getLayoutParams();


        //Handles touch to the login form or screen underneath. If the user
        //moves the card below 200px
        @Override
        public boolean onTouch(final View view, final MotionEvent event) {

            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    params.topMargin = (int) (prevTop + (event.getRawY() - prevY));
                    mLoginFormView.setLayoutParams(params);
                    break;

                case MotionEvent.ACTION_UP:
                    check = params.topMargin - prevTop;
                    if (check > MIN_DISTANCE) {
                        getFragmentManager().popBackStack();
                        break;
                    } else {
                        mLoginFormView.animate()
                                .translationYBy(-check)
                                .setDuration(ANIMATION_DURATION)
                                .setInterpolator(new OvershootInterpolator());
                    }
                    break;

                case MotionEvent.ACTION_DOWN:
                    prevTop = params.topMargin;
                    prevY = (int) event.getRawY();
                    mInputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    break;

            }
            return true;
        }
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user and login.
     */
    private class UserLoginTask extends AsyncTask<String, Void, Boolean> {

        //Strings containing the variables passed from attemptLogin
        private final String mUsername;
        private final String mPassword;

        //Tags for the server JSON and shared preferences
        private final String TAG_USERNAME = "username";
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

        //Assign shared preferences editor to store JSON values from server
        SharedPreferences.Editor editor = mActivity.getSharedPreferences(
                "PREFS", Activity.MODE_PRIVATE).edit();

        //Constructor that assigns variables passed from attemptLogin
        UserLoginTask(String email, String password) {
            mUsername = email;
            mPassword = password;
        }

        @Override
        protected Boolean doInBackground(String... params) {

            JSONParser parser = new JSONParser();

            //Set HTTP request type and Basic Auth header
            String requestType = "GET";
            String userCredentials = mUsername + ":" + mPassword;
            String basicAuth = "Basic " + new String(
                    Base64.encode(userCredentials.getBytes(), Base64.NO_WRAP));

            try {
                //Login API
                URL url = new URL("http://mfebseast.elasticbeanstalk.com/api/v2.0/users/login/");

                //Get the JSON object from the server
                JSONObject loginObject = parser.getLoginObject(
                        mActivity, url, requestType, basicAuth);

                //If the JSON is not null, then store each of the objects in shared prefs
                return loginObject !=null && storePreferences(loginObject);
                
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        private boolean storePreferences(JSONObject loginObject) {

            try {
                //We use the logged_in object as a login check in MainActivity
                //Need to treat this object as boolean
                if (loginObject.getBoolean(TAG_LOGGED_IN)) {
                    editor.putBoolean(TAG_LOGGED_IN, loginObject.getBoolean(TAG_LOGGED_IN));
                } else {
                    return false;
                }

            } catch (JSONException e) {
                e.printStackTrace();
                return false;
            }

            //Store each JSON object in shared preferences
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
                e.printStackTrace();
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {

            //Authentication is complete
            mAuthTask = null;

            //If everything goes according to plan, then start MainActivity
            //Otherwise, show error message on the password field
            if (success) {
                Intent intent = new Intent(mActivity, MainActivity.class);
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

            //User has pressed back or otherwise cancelled the login task
            mAuthTask = null;
            showProgress(false);
        }
    }

}
