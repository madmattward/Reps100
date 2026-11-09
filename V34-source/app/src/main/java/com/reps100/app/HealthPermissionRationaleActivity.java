package com.reps100.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

public class HealthPermissionRationaleActivity extends Activity {
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(40, 60, 40, 40);
    TextView message = new TextView(this);
    message.setText("Reps100 uses Health Connect write permissions to save completed strength-training workouts and estimated calories. You can disable future exports in your Reps100 profile or revoke permissions in Android settings.");
    message.setTextSize(18);
    layout.addView(message);
    setContentView(layout);
  }
}
