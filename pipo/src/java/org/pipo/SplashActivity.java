package org.pipo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.util.DisplayMetrics;
import neko.App;

import org.pipo.R;

public class SplashActivity extends Activity {

    private static boolean firstLaunch = true;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        Log.i("pipo", "dpi: " + metrics.densityDpi);

        if (firstLaunch) {
            firstLaunch = false;
            setupSplash();
            App.loadAsynchronously("org.pipo.MyActivity",
                                   new Runnable() {
                                       @Override
                                       public void run() {
                                           proceed();
                                       }});
        } else {
            proceed();
        }
    }

    public void setupSplash() {
        Log.i("pipo", "splashscreen id: " + R.layout.splashscreen);
        setContentView(R.layout.splashscreen);

        TextView appNameView = (TextView)findViewById(R.id.splash_app_name);
        if (appNameView == null) {
            Toast.makeText(this, "appNameView == null", Toast.LENGTH_SHORT).show();
            Log.i("pipo", "appNameView == null");
        } else {
            Toast.makeText(this, "appNameView != null", Toast.LENGTH_SHORT).show();
            Log.i("pipo", "appNameView != null");
        }
        appNameView.setText(R.string.app_name);

        Animation rotation = AnimationUtils.loadAnimation(this, R.anim.splash_rotation);
        ImageView circleView = (ImageView)findViewById(R.id.splash_circles);
        circleView.startAnimation(rotation);
    }

    public void proceed() {
        startActivity(new Intent("org.pipo.MAIN"));
        finish();
    }

}
