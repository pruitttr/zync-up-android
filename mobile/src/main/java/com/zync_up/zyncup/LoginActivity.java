package com.zync_up.zyncup;


import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.transition.TransitionInflater;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class LoginActivity extends Activity {
    //TODO: Comment & Javadoc

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        SharedPreferences sharedPreferences = getSharedPreferences("PREFS", Activity.MODE_PRIVATE);
        sharedPreferences.edit().putBoolean("isFirstOpen", true).apply();

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.container_login, new SplashFragment())
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onWindowFocusChanged (boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        // Be sure to call the superclass implementation
        return super.onTouchEvent(event);
    }

    public static class SplashFragment extends Fragment {

        public SplashFragment() {

        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_splash, container, false);

            CardView loginFormView = (CardView) rootView.findViewById(R.id.login_form);
            loginFormView.setOnTouchListener(new loginTouchListener(loginFormView));

            CardView registerFormView = (CardView) rootView.findViewById(R.id.register_form);
            registerFormView.setOnTouchListener(new registerTouchListener(registerFormView));

            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("PREFS", Activity.MODE_PRIVATE);

            if (sharedPreferences.getBoolean("isFirstOpen", true)) {
                Typewriter tagline = (Typewriter) rootView.findViewById(R.id.textview_tagline);
                tagline.setCharacterDelay(100);
                tagline.animateText(tagline.getText());
                sharedPreferences.edit().putBoolean("isFirstOpen", false).apply();
            }

            // Inflate the layout for this fragment
            return rootView;
        }

        private class loginTouchListener implements View.OnTouchListener {

            CardView mCardView;

            private loginTouchListener(CardView cardView) {
                this.mCardView = cardView;
            }

            @Override
            public boolean onTouch(final View view, final MotionEvent event) {

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:

                        break;

                    case MotionEvent.ACTION_UP:

                        break;

                    case MotionEvent.ACTION_DOWN:
                        Fragment fragment = new LoginFragment();
                        TransitionInflater transitionInflater = TransitionInflater.from(getActivity());
                        fragment.setSharedElementEnterTransition(transitionInflater.inflateTransition(android.R.transition.move));

                        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                        TextView formTitleText = (TextView) getActivity().findViewById(R.id.login_form_title);
                        fragmentTransaction.replace(R.id.container_login, fragment)
                                .addSharedElement(view, "login_form")
                                .addSharedElement(formTitleText, "login_form_title")
                                .addToBackStack("login_transaction");
                        fragmentTransaction.commit();
                        break;
                }
                return true;
            }
        }

        private class registerTouchListener implements View.OnTouchListener {

            CardView mCardView;


            private registerTouchListener(CardView cardView) {
                this.mCardView = cardView;
            }

            @Override
            public boolean onTouch(final View view, final MotionEvent event) {

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:

                        break;

                    case MotionEvent.ACTION_UP:

                        break;

                    case MotionEvent.ACTION_DOWN:
                        Fragment fragment = new RegisterFragment();
                        TransitionInflater transitionInflater = TransitionInflater.from(getActivity());
                        fragment.setSharedElementEnterTransition(transitionInflater.inflateTransition(android.R.transition.move));

                        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                        TextView formTitleText = (TextView) getActivity().findViewById(R.id.register_form_title);
                        fragmentTransaction.replace(R.id.container_login, fragment)
                                .addSharedElement(view, "register_form")
                                .addSharedElement(formTitleText, "register_form_title")
                                .addToBackStack("register_transaction");

                        fragmentTransaction.commit();
                        break;
                }
                return true;
            }
        }
    }
}
