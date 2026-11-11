package com.reps100.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.media.*;
import android.text.*;
import android.text.method.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.*;
import java.util.*;
import org.json.*;

public class MainActivity extends androidx.activity.ComponentActivity {
    // V30: independent three-column tiles with consistent gutters.
    private void addMenuRow3(LinearLayout parent, View first, View second, View third) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        View[] items = {first, second, third};
        for (int i = 0; i < items.length; i++) {
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(142), 1f);
            params.setMargins(dp(i == 0 ? 5 : 3), dp(6),
                    dp(i == 2 ? 5 : 3), dp(6));
            row.addView(items[i], params);
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(154)));
    }


    // V20: parent container that can claim a horizontal swipe even when
    // the initial ACTION_DOWN landed on a child view inside the card.
    class SwipeDeleteLayout extends FrameLayout {
        float downX, downY;
        boolean interceptingSwipe;
        boolean reordering;
        final int touchSlop;

        SwipeDeleteLayout(Context context) {
            super(context);
            setClickable(true);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (reordering) return false;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    interceptingSwipe = false;
                    // Allow the parent to own vertical card scrolling, as in Create Routine.
                    return false;

                case MotionEvent.ACTION_MOVE:
                    // Once a horizontal swipe has been claimed, keep ownership
                    // until ACTION_UP/ACTION_CANCEL even if the finger drifts vertically.
                    if (interceptingSwipe) {
                        ScrollView activeScroll = "daily-reorder-card".equals(getTag()) ? daily100ScrollView : routineScrollView;
                        if (activeScroll != null) activeScroll.requestDisallowInterceptTouchEvent(true);
                        return true;
                    }

                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;

                    // Right-swipe only. Vertical movement remains ScrollView territory.
                    if (dx > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        interceptingSwipe = true;
                        ScrollView activeScroll = "daily-reorder-card".equals(getTag()) ? daily100ScrollView : routineScrollView;
                        if (activeScroll != null) activeScroll.requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    interceptingSwipe = false;
                    break;
            }
            return false;
        }
    }


  // ---------- Theme ----------
  final int BG = Color.rgb(5, 15, 28);
  final int BAR = Color.rgb(9, 28, 47);
  final int CARD = Color.rgb(16, 39, 64);
  final int CARD_ALT = Color.rgb(29, 58, 88);
  final int TEXT = Color.rgb(244, 248, 255);
  final int MUTED = Color.rgb(184, 199, 217);
  final int GREEN = Color.rgb(92, 166, 255);
  final int GREEN_DARK = Color.rgb(30, 104, 211);
  final int GREEN_SOFT = Color.rgb(205, 228, 255);
  final int BLUE = Color.rgb(52, 125, 230);
  final int RED = Color.rgb(245, 122, 135);
  final int GRID_CARD = Color.rgb(13, 35, 58);

  // ---------- State ----------
  FrameLayout windowRoot;
  String currentScreen = "";
  ArrayDeque<String> navigation = new ArrayDeque<>();
  ExerciseData.Exercise detailExercise;
  boolean selectingForRoutine = false;
  boolean selectingForDaily100 = false;
  int libraryScrollY = 0;
  ScrollView routineScrollView;
  ScrollView daily100ScrollView;
  String dailyActiveName = null;
  int dailyPhase = 0; // 0 ready, 1 timing, 2 stopped, 3 recorded
  long dailySetStart = 0;
  long dailySetElapsed = 0;
  final android.os.Handler dailyTimerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
  Runnable dailyTimerTick;
  void stopDailyTimer() {
    if (dailyTimerTick != null) dailyTimerHandler.removeCallbacks(dailyTimerTick);
    dailyTimerTick = null;
  }

  int routineScrollBeforeKeyboard = 0;
  boolean routineKeyboardVisible = false;
  String libraryQuery = "";
  String libraryEquipment = "All";
  String libraryMuscle = "All";

  ArrayList<ExerciseData.Exercise> routine = new ArrayList<>();
  HashMap<String, Double> routineWeights = new HashMap<>();
  HashMap<String, String> routineWeightUnits = new HashMap<>();

  // V12: load data belongs to each routine occurrence, not the exercise name.
  // This allows the same exercise to appear multiple times with different loads.
  ArrayList<Double> routineOccurrenceWeights = new ArrayList<>();
  ArrayList<String> routineOccurrenceUnits = new ArrayList<>();

  int selectedSets = 1;
  int minReps = 1;
  int maxReps = 13;

  int currentExercise = 0;
  int currentSet = 0;
  ArrayList<Integer> currentSplit = new ArrayList<>();
  ArrayList<ArrayList<Integer>> workoutSplits = new ArrayList<>();

  // V21: measured elapsed interval for every randomised set.
  ArrayList<ArrayList<Double>> workoutSetDurations = new ArrayList<>();
  long workoutStarted = 0;
  long workoutSetStarted = 0;
  long workoutLastStop = 0;
  int workoutPhase = 0; // 0 ready, 1 timing, 2 finished set
  final ArrayList<Double> workoutRestIntervals = new ArrayList<>();
  final android.os.Handler workoutTimerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
  Runnable workoutTimerUpdate;


  SharedPreferences prefs;
  SoundPool soundPool;
  int clickSound=0;
  int scrollSound=0;
  boolean clickSoundReady=false;
  boolean scrollSoundReady=false;
  long lastScrollClick=0;
  long lastScrollHaptic=0;
  int lastScrollStep=-1;
  SimpleDateFormat dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
  SimpleDateFormat dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
  SimpleDateFormat dayLabel = new SimpleDateFormat("EEE", Locale.US);
  SimpleDateFormat shortDate = new SimpleDateFormat("d MMM", Locale.US);

  int dp(float v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  private final androidx.activity.result.ActivityResultLauncher<java.util.Set<String>> healthPermissionLauncher =
      registerForActivityResult(
          androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
          grantedPermissions -> {
            HealthConnectBridge.INSTANCE.hasPermissions(this, granted -> {
              if (granted) prefs.edit().putBoolean("healthConnectEnabled", true).apply();
              Toast.makeText(this,
                  granted ? "Health Connect export enabled." : "Health Connect permissions were not granted. Please allow both permissions.",
                  granted ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
              showProfile(false);
              return kotlin.Unit.INSTANCE;
            });
          });

  @Override
  protected void onResume() {
    super.onResume();
    if (prefs != null && prefs.getBoolean("healthConnectEnabled", false)
        && HealthConnectBridge.INSTANCE.isAvailable(this)) {
      HealthConnectBridge.INSTANCE.hasPermissions(this, granted -> {
        if (!granted) prefs.edit().putBoolean("healthConnectEnabled", false).apply();
        return kotlin.Unit.INSTANCE;
      });
    }
  }

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(BAR);
    getWindow().setNavigationBarColor(Color.BLACK);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    if (Build.VERSION.SDK_INT >= 30) getWindow().setStatusBarColor(BAR);

    AudioAttributes aa=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    soundPool=new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(aa).build();
    soundPool.setOnLoadCompleteListener((pool,sampleId,status) -> {
      if(sampleId==clickSound && status==0) clickSoundReady=true;
      if(sampleId==scrollSound && status==0) scrollSoundReady=true;
    });
    clickSound=soundPool.load(this,R.raw.soft_ui_tap,1);
    scrollSound=soundPool.load(this,R.raw.soft_ui_scroll,1);
    prefs = getSharedPreferences("reps100", 0);
    selectedSets = prefs.getInt("sets", 1);
    minReps = prefs.getInt("minReps", 1);
    maxReps = prefs.getInt("maxReps", 13);

    if (!prefs.contains("installDate")) {
      long firstInstall = System.currentTimeMillis();
      try {
        firstInstall = getPackageManager().getPackageInfo(getPackageName(), 0).firstInstallTime;
      } catch (Exception ignored) {}
      prefs.edit().putString("installDate", dateKey.format(new Date(firstInstall))).apply();
    }

    if (Build.VERSION.SDK_INT >= 33) {
      getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
          android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBackWithSound);
    }

    installWakeUpRoutineOnce();
    if (safetyAccepted()) showHome(false);
    else showSafetyAgreement(true);
  }

  @Override protected void onDestroy() {
    if(soundPool!=null){
      soundPool.release();
      soundPool=null;
    }
    super.onDestroy();
  }

  @SuppressWarnings("MissingSuperCall")

  @Override public void onBackPressed() { goBackWithSound(); }

  void goBackWithSound() {
    playClick();
    goBack();
  }

  void goBack() {
    if (!safetyAccepted()) { finish(); return; }
    if (!navigation.isEmpty()) {
      String target = navigation.pop();
      renderScreen(target);
      return;
    }
    if (!"home".equals(currentScreen)) {
      showHome(false);
    } else {
      finish();
    }
  }

  void pushScreen(String screen) {
    if (currentScreen != null && currentScreen.length() > 0 && !currentScreen.equals(screen)) {
      navigation.push(currentScreen);
    }
  }

  void returnToScreen(String target) {
    while (!navigation.isEmpty()) {
      String s = navigation.pop();
      if (target.equals(s)) {
        renderScreen(target);
        return;
      }
    }
    renderScreen(target);
  }

  void renderScreen(String screen) {
    if (!safetyAccepted()) { showSafetyAgreement(true); return; }
    switch (screen) {
      case "home": showHome(false); break;
      case "routine": showRoutine(false); break;
      case "sets": showSetsManager(false); break;
      case "library": showLibrary(false); break;
      case "detail":
        if (detailExercise != null) showDetail(detailExercise, false);
        else showLibrary(false);
        break;
      case "routines": showRoutineList(false); break;
      case "history": showHistory(false); break;
      case "profile": showProfile(false); break;
      case "daily100": stopDailyTimer(); showDaily100(false); break;
      case "dailySet": showDailySet(false); break;
      case "challenges": showChallenges(false); break;
      case "achievements": showAchievements(false); break;
      case "trophies": showTrophyCabinet(false); break;
      case "settings": showSettings(false); break;
      case "safety": showSafetyAgreement(false); break;
      case "community": showCommunity(false); break;
      case "workout": showWorkout(false); break;
      case "preWorkout": showPreWorkout(false); break;
      default: showHome(false);
    }
  }

  void display(String screen, View content, boolean push) {
    if (push) pushScreen(screen);
    currentScreen = screen;

    windowRoot = new FrameLayout(this);
    windowRoot.setBackgroundColor(BG);

    // Android 15 edge-to-edge is handled by applying system-bar insets ourselves.
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      windowRoot.setOnApplyWindowInsetsListener((v, insets) -> {
        android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
        return insets;
      });
    } else {
      windowRoot.setFitsSystemWindows(true);
    }

    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
    windowRoot.addView(content, lp);
    setContentView(windowRoot);
    windowRoot.requestApplyInsets();
  }

  // Versioned, offline-first acknowledgement. Future account sync must be opt-in and authenticated.
  static final String SAFETY_VERSION = "1.0";
  static final String SAFETY_KEY = "safetyAcknowledgedVersion";
  static final String SAFETY_TEXT =
      "Reps100 Health & Safety Disclaimer, Risk Acknowledgement and Terms of Participation\nVersion 1.0\n\n" +
      "IMPORTANT: READ BEFORE USING REPS100.\n\n" +
      "Reps100 supports resistance, bodyweight and high-repetition exercise. Exercise carries inherent risks, including injury, illness and occasionally serious medical complications.\n\n" +
      "1. MEDICAL ADVICE AND FITNESS TO EXERCISE\nConsult a qualified healthcare professional before starting strenuous exercise if you have health concerns, a pre-existing condition, cardiovascular or respiratory problems, high blood pressure, joint problems, recent injury or surgery, are pregnant or recovering from childbirth, or use medication affecting exercise safety. Reps100 does not certify fitness to exercise.\n\n" +
      "2. NATURE AND INTENSITY\nHigh-volume training can be demanding and is not suitable for everyone. Select suitable movements, weights, repetitions, intensity and recovery. Never sacrifice correct technique to reach a repetition target.\n\n" +
      "3. EXERCISE GUIDANCE\nExercise descriptions, photographs and illustrations are general information, not personalised coaching or medical advice. Seek qualified professional instruction if uncertain about technique or safe execution. Do not attempt unsuitable exercises.\n\n" +
      "4. WARNING SIGNS\nStop immediately for sharp or sudden pain, chest discomfort, dizziness, faintness, unusual breathlessness, loss of coordination or other concerning symptoms. Seek urgent medical assistance when appropriate. Delayed onset muscle soreness (DOMS) is not the same as acute injury or severe or persistent pain. Do not train through significant pain.\n\n" +
      "5. RECOVERY\nAllow adequate recovery between workouts. Adjust frequency and intensity to fitness, experience, sleep, nutrition, fatigue and physical condition. Inadequate recovery can increase injury and illness risks.\n\n" +
      "6. EQUIPMENT AND ENVIRONMENT\nCheck that equipment is appropriate, correctly assembled and maintained; follow manufacturer instructions. Use adequate space, safe flooring and suitable footwear. Arrange spotting or supervision when needed.\n\n" +
      "7. HEALTH AND PERFORMANCE ESTIMATES\nCalories, BMI, body composition, workout duration and other figures are estimates, not diagnoses or medical assessments. Actual results vary. No fitness or health outcome is guaranteed.\n\n" +
      "8. VOLUNTARY PARTICIPATION AND INHERENT RISKS\nParticipation is voluntary. Risks include strains, joint injuries, falls and cardiovascular complications. Exercise reasonable care and remain aware of personal limitations. No exercise programme guarantees freedom from injury.\n\n" +
      "9. RESPONSIBILITY AND LIABILITY\nReps100 and its developer offer general exercise guidance, not personalised medical or professional training services. To the fullest extent permitted by law, they do not accept responsibility for harm arising from disregarding medical advice, incorrect technique, misuse of equipment, failure to follow safety precautions or exercise beyond individual capabilities. Nothing excludes or restricts liability for death or personal injury caused by negligence, fraud, or other liability that cannot lawfully be excluded. Statutory consumer rights remain unaffected.\n\n" +
      "10. AGE REQUIREMENT\nReps100 is intended for adults aged 18 or over.\n\n" +
      "11. ACKNOWLEDGEMENT\nBy choosing I Understand and Agree, you confirm you have read and understood these warnings, are at least 18, understand the need for medical and professional advice when appropriate, will train within your capabilities, stop for concerning symptoms, allow recovery, and acknowledge that acceptance will be recorded locally. This acknowledgement does not waive rights that cannot lawfully be waived.";

  boolean safetyAccepted() {
    return SAFETY_VERSION.equals(prefs.getString(SAFETY_KEY, ""));
  }

  void showSafetyAgreement(boolean required) {
    LinearLayout page = pageShell(required ? "Before You Begin" : "Health & Safety Agreement");
    LinearLayout body = contentColumn();
    body.addView(sectionTitle("Reps100 · Health & Safety"));
    body.addView(text(required ? "Please read the entire agreement. Acceptance is required before using Reps100." : "Original agreement and locally stored electronic acknowledgement. If no saved copy exists, the Version 1.0 agreement is shown for reference.", 15, MUTED));
    ScrollView agreementScroll = new ScrollView(this);
    String recordedText = !required ? prefs.getString("safetyAcknowledgedText", "") : "";
    TextView terms = text(!required && !recordedText.isEmpty() ? recordedText : SAFETY_TEXT, 15, TEXT);
    terms.setPadding(dp(12),dp(12),dp(12),dp(16));
    agreementScroll.addView(terms);
    agreementScroll.setBackground(shape(CARD, 14));
    body.addView(agreementScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    CheckBox read = new CheckBox(this);
    read.setText("I have read the full agreement"); read.setTextColor(TEXT);
    CheckBox adult = new CheckBox(this);
    adult.setText("I confirm I am 18 or older"); adult.setTextColor(TEXT);
    CheckBox risks = new CheckBox(this);
    risks.setText("I understand and acknowledge the risks"); risks.setTextColor(TEXT);
    EditText name = field("Full name (electronic acknowledgement)", "", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    Button accept = button("I Understand and Agree", BLUE);
    accept.setEnabled(false);
    final boolean[] reachedEnd = {false};
    agreementScroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v, sx, sy, osx, osy) -> {
      if (!agreementScroll.canScrollVertically(1)) reachedEnd[0] = true;
      accept.setEnabled(reachedEnd[0] && read.isChecked() && adult.isChecked() && risks.isChecked() && name.getText().toString().trim().length() > 1);
    });
    Runnable refresh = () -> accept.setEnabled(reachedEnd[0] && read.isChecked() && adult.isChecked() && risks.isChecked() && name.getText().toString().trim().length() > 1);
    read.setOnCheckedChangeListener((b, checked)->refresh.run());
    adult.setOnCheckedChangeListener((b, checked)->refresh.run());
    risks.setOnCheckedChangeListener((b, checked)->refresh.run());
    name.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence a,int b,int c,int d) {}
      public void onTextChanged(CharSequence a,int b,int c,int d) { refresh.run(); }
      public void afterTextChanged(Editable e) {}
    });
    if (required) {
      body.addView(name);
      body.addView(read); body.addView(adult); body.addView(risks);
      body.addView(accept, margins(-1, dp(54), 0, 8, 0, 8));
      Button decline = button("Decline and Exit", CARD);
      decline.setOnClickListener(v -> finish());
      body.addView(decline, margins(-1, dp(48), 0, 4, 0, 8));
      accept.setOnClickListener(v -> {
        if (!reachedEnd[0] || !read.isChecked() || !adult.isChecked() || !risks.isChecked() || name.getText().toString().trim().length() < 2) return;
        prefs.edit().putString(SAFETY_KEY, SAFETY_VERSION)
            .putLong("safetyAcknowledgedAt", System.currentTimeMillis())
            .putString("safetyAcknowledgedName", name.getText().toString().trim())
            .putString("safetyAcknowledgementMethod", "affirmative-checkbox-and-button")
            .putString("safetyAcknowledgedText", SAFETY_TEXT).apply();
        navigation.clear(); showHome(false);
      });
    } else {
      String acceptedVersion = prefs.getString(SAFETY_KEY, "Not yet accepted");
      long acceptedAt = prefs.getLong("safetyAcknowledgedAt", 0L);
      body.addView(text("Acknowledged version: " + acceptedVersion +
          "\nName: " + prefs.getString("safetyAcknowledgedName", "—") +
          "\nDate: " + (acceptedAt > 0 ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.getDefault()).format(new java.util.Date(acceptedAt)) : "—") +
           "\nAcceptance method: " + prefs.getString("safetyAcknowledgementMethod", "Not recorded"), 14, MUTED));
    }
    page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    display("safety", page, !required);
  }


  // ---------- UI helpers ----------
  GradientDrawable shape(int color, float radiusDp) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(radiusDp));
    return g;
  }

  GradientDrawable outline(int color, int stroke, float radiusDp) {
    GradientDrawable g = shape(Color.TRANSPARENT, radiusDp);
    g.setStroke(dp(stroke), color);
    return g;
  }

  TextView text(String s, float sp, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(sp);
    t.setTextColor(color);
    t.setGravity(Gravity.CENTER_VERTICAL);
    t.setPadding(dp(8), dp(6), dp(8), dp(6));
    t.setLineSpacing(0, 1.08f);
    t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    return t;
  }

  TextView bold(String s, float sp) {
    TextView t = text(s, sp, TEXT);
    t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    return t;
  }

  int lighten(int color,int amount){return Color.rgb(Math.min(255,Color.red(color)+amount),Math.min(255,Color.green(color)+amount),Math.min(255,Color.blue(color)+amount));}
  int darken(int color,int amount){return Color.rgb(Math.max(0,Color.red(color)-amount),Math.max(0,Color.green(color)-amount),Math.max(0,Color.blue(color)-amount));}

  Button button(String label, int color) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextColor(TEXT);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    int faceColor = (color == CARD || color == CARD_ALT) ? Color.rgb(25,53,80) : color;
    GradientDrawable face=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{lighten(faceColor,34),faceColor,darken(faceColor,24)}); face.setCornerRadius(dp(18)); face.setStroke(dp(1),lighten(color,65)); b.setBackground(face); b.setElevation(dp(6));
    b.setPadding(dp(10), 0, dp(10), 0); b.setMinHeight(0); b.setMinimumHeight(0);
    // Preserve V31 appearance: haptics on touch-down, no visual press changes.
    b.setOnTouchListener((v,e)->{
      if(e.getActionMasked()==MotionEvent.ACTION_DOWN) playClick(v);
      return false;
    });
    return b;
  }

  void setWorkoutActionFace(Button button, int color) {
    GradientDrawable face = new GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        new int[]{lighten(color, 34), color, darken(color, 24)});
    face.setCornerRadius(dp(18));
    face.setStroke(dp(1), lighten(color, 65));
    button.setBackground(face);
  }

  Button outlineButton(String label) {
    Button b = button(label, Color.TRANSPARENT);
    b.setBackground(outline(CARD_ALT, 1, 18));
    return b;
  }

  void playClick() { playClick(getWindow().getDecorView()); }

  void playClick(View source) {
    if(prefs.getBoolean("hapticsEnabled",true)) {
      View target=source!=null ? source : getWindow().getDecorView();
      target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
          HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
    }
    if(!prefs.getBoolean("soundsEnabled",true)) return;
    if(soundPool!=null && clickSound!=0 && clickSoundReady)
      soundPool.play(clickSound,0.65f,0.65f,1,0,1f);
  }

  void deletionHaptic(View source) {
    if(!prefs.getBoolean("hapticsEnabled",true)) return;
    View target=source!=null ? source : getWindow().getDecorView();
    target.performHapticFeedback(HapticFeedbackConstants.CONFIRM,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
  }

  void playScrollClick(int scrollY) {
    long hapticNow=android.os.SystemClock.uptimeMillis();
    if(prefs.getBoolean("hapticsEnabled",true) && hapticNow-lastScrollHaptic>=115) {
      lastScrollHaptic=hapticNow;
      getWindow().getDecorView().performHapticFeedback(
          android.view.HapticFeedbackConstants.CLOCK_TICK);
    }
    if(!prefs.getBoolean("soundsEnabled",true)) return;
    // Emit a restrained tick for continuing motion, including the last few
    // pixels of a decelerating fling. Never rely on crossing a 72dp boundary.
    long now=android.os.SystemClock.uptimeMillis();
    if(now-lastScrollClick>=65){
      lastScrollClick=now;
      if(soundPool!=null && scrollSound!=0 && scrollSoundReady)
        soundPool.play(scrollSound,0.24f,0.24f,1,0,1f);
    }
  }

  void resetScrollClickState(int scrollY) {
    lastScrollClick=android.os.SystemClock.uptimeMillis();
  }

  LinearLayout vbox() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setBackgroundColor(BG);
    return l;
  }

  LinearLayout contentColumn() {
    LinearLayout l = vbox();
    l.setPadding(dp(18), dp(14), dp(18), dp(22));
    return l;
  }

  ScrollView scroll(View child) {
    ScrollView s = new ScrollView(this);
    s.setFillViewport(true);
    s.setClipToPadding(false);
    s.addView(child, new ScrollView.LayoutParams(-1, -2));
    return s;
  }

  Space space(int h) {
    Space s = new Space(this);
    s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
    return s;
  }

  LinearLayout.LayoutParams margins(int width, int height, int l, int t, int r, int b) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
    lp.setMargins(dp(l), dp(t), dp(r), dp(b));
    return lp;
  }

  LinearLayout titleBar(String title) {
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(dp(6), dp(4), dp(6), dp(4));
    bar.setBackgroundColor(BAR);

    Button back = new Button(this);
    back.setText("‹");
    back.setTextSize(34);
    back.setTextColor(TEXT);
    back.setBackgroundColor(Color.TRANSPARENT);
    back.setPadding(0, 0, 0, dp(3));
    back.setMinHeight(0);
    back.setMinimumHeight(0);
    back.setContentDescription("Back");
    back.setOnClickListener(v -> goBackWithSound());
    bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(82)));

    int titleArt = 0;
    switch (title) {
      case "Create New Routine":
      case "Create Routine": titleArt = R.drawable.v31_title_create_routine; break;
      case "Sets and Reps Manager":
      case "Sets & Reps": titleArt = R.drawable.v31_title_sets_reps; break;
      case "Routines Library":
      case "Routines": titleArt = R.drawable.v31_title_routines; break;
      case "Exercise List":
      case "Exercise Library":
      case "Exercises": titleArt = R.drawable.v31_title_exercises; break;
      case "Add Exercise": titleArt = R.drawable.v31_title_add_exercise; break;
      case "Completed Workouts":
      case "Completed": titleArt = R.drawable.v31_title_completed; break;
      case "Personal Profile":
      case "Profile": titleArt = R.drawable.v31_title_profile; break;
      case "Daily 100": titleArt = R.drawable.v31_title_daily_100; break;
      case "Challenges": titleArt = R.drawable.v31_title_challenges; break;
      case "Achievements": titleArt = R.drawable.v31_title_achievements; break;
      case "Community": titleArt = R.drawable.v31_title_community; break;
      case "Settings": titleArt = R.drawable.v31_title_settings; break;
    }
    if (titleArt != 0) {
      ImageView headingArt = new ImageView(this);
      headingArt.setImageResource(titleArt);
      headingArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
      headingArt.setAdjustViewBounds(true);
      headingArt.setContentDescription(title);
      bar.addView(headingArt, new LinearLayout.LayoutParams(0, dp(82), 1));
    } else {
      TextView h = text(title, title.length() > 20 ? 19 : 24, TEXT);
      h.setGravity(Gravity.CENTER);
      h.setSingleLine(false);
      bar.addView(h, new LinearLayout.LayoutParams(0, dp(82), 1));
    }

    Space balance = new Space(this);
    bar.addView(balance, new LinearLayout.LayoutParams(dp(52), dp(82)));
    return bar;
  }

  LinearLayout pageShell(String title) {
    LinearLayout page = vbox();
    page.addView(titleBar(title), new LinearLayout.LayoutParams(-1, dp(92)));
    return page;
  }

  TextView sectionTitle(String s) {
    TextView t = bold(s, 20);
    t.setPadding(dp(2), dp(14), dp(2), dp(8));
    return t;
  }

  EditText field(String hint, String value, int inputType) {
    EditText e = new EditText(this);
    e.setHint(hint);
    e.setText(value);
    e.setTextColor(TEXT);
    e.setHintTextColor(MUTED);
    e.setSingleLine();
    e.setTextSize(17);
    e.setInputType(inputType);
    e.setPadding(dp(16), 0, dp(16), 0);
    e.setBackground(outline(CARD_ALT, 1, 20));
    e.setSelectAllOnFocus(false);
    e.setLayoutParams(margins(-1, dp(58), 0, 5, 0, 5));
    return e;
  }

  void hideKeyboard(View v) {
    try {
      ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
          .hideSoftInputFromWindow(v.getWindowToken(), 0);
    } catch (Exception ignored) {}
  }

  // ---------- Home ----------
  void showHome(boolean push) {
    if (!push) navigation.clear();

    LinearLayout all = contentColumn();

    LinearLayout heading = new LinearLayout(this);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    heading.setOrientation(LinearLayout.HORIZONTAL);
    heading.addView(headerIcon("community", v -> showCommunity(true)), new LinearLayout.LayoutParams(dp(58),dp(58)));
    ImageView brand = new ImageView(this);
    brand.setImageResource(prefs.getBoolean("centurianAchievement",false)
        ? R.drawable.centurian_title : R.drawable.v31_title_reps100);
    brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
    brand.setContentDescription("REPS100");
    heading.addView(brand,new LinearLayout.LayoutParams(0,dp(68),1));
    heading.addView(headerIcon("settings", v -> showSettings(true)), new LinearLayout.LayoutParams(dp(58),dp(58)));
    all.addView(heading);

    TextView strap = text("100 reps to a more conditioned body. With a unique rep randomiser that keeps your sets interesting, which you can tailor to match your level.", 14, GREEN);
    strap.setGravity(Gravity.CENTER);
    strap.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    strap.setPadding(dp(18), dp(6), dp(18), dp(10));
    all.addView(strap);

    addStreakCard(all);
    all.addView(space(8));

    addMenuRow3(all,
        menuCard("plus", "Create Routine", v -> showRoutine(true)),
        menuCard("sliders", "Sets & Reps", v -> showSetsManager(true)),
        menuCard("list", "Routines", v -> showRoutineList(true)));

    addMenuRow3(all,
        menuCard("dumbbell", "Exercises", v -> { selectingForRoutine = false; selectingForDaily100 = false; showLibrary(true); }),
        menuCard("check", "Completed", v -> showHistory(true)),
        menuCard("smile", "Profile", v -> showProfile(true)));

    addMenuRow3(all,
        menuCard("daily100", "Daily 100", v -> showDaily100(true)),
        menuCard("challenges", "Challenges", v -> showChallenges(true)),
        menuCard("achievements", "Achievements", v -> showAchievements(true)));

    TextView offline = text("OFFLINE FIRST  •  YOUR DATA STAYS ON DEVICE", 12, MUTED);
    offline.setGravity(Gravity.CENTER);
    offline.setPadding(dp(8), dp(18), dp(8), dp(8));
    all.addView(offline);

    display("home", scroll(all), push);
  }

  int v26ButtonImage(String type, boolean face) {
    switch(type) {
      case "plus": return face ? R.drawable.v26_plus_face : R.drawable.v26_plus_ring;
      case "sliders": return face ? R.drawable.v26_sliders_face : R.drawable.v26_sliders_ring;
      case "list": return face ? R.drawable.v26_list_face : R.drawable.v26_list_ring;
      case "dumbbell": return face ? R.drawable.v26_dumbbell_face : R.drawable.v26_dumbbell_ring;
      case "check": return face ? R.drawable.v26_check_face : R.drawable.v26_check_ring;
      case "smile": return face ? R.drawable.v26_smile_face : R.drawable.v26_smile_ring;
      case "community": return face ? R.drawable.v26_community_face : R.drawable.v26_community_ring;
      default: return face ? R.drawable.v26_settings_face : R.drawable.v26_settings_ring;
    }
  }

  View v26MetalButton(String type, int size, View.OnClickListener action) {
    FrameLayout button = new FrameLayout(this);
    FrameLayout faceLayer = new FrameLayout(this);
    ImageView face = new ImageView(this);
    face.setScaleType(ImageView.ScaleType.FIT_CENTER);
    if (isV28Icon(type)) {
      // V29: use the supplied original button artwork, including its silver ring.
      int artwork = "daily100".equals(type) ? R.drawable.v29_daily100
          : "challenges".equals(type) ? R.drawable.v29_challenges
          : R.drawable.v29_achievements;
      face.setImageResource(artwork);
      faceLayer.addView(face,new FrameLayout.LayoutParams(-1,-1));
    } else {
      ImageView ring = new ImageView(this);
      ring.setImageResource(v26ButtonImage(type,false));
      ring.setScaleType(ImageView.ScaleType.FIT_CENTER);
      button.addView(ring,new FrameLayout.LayoutParams(-1,-1));
      face.setImageResource(v26ButtonImage(type,true));
      faceLayer.addView(face,new FrameLayout.LayoutParams(-1,-1));
    }
    button.addView(faceLayer,new FrameLayout.LayoutParams(-1,-1));
    button.setClickable(true);
    button.setFocusable(true);
    button.setContentDescription(type);
    button.setOnClickListener(action);
    button.setOnTouchListener((v,e)->{
      if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
        playClick(v);
        faceLayer.animate().cancel();
        faceLayer.animate().scaleX(.94f).scaleY(.94f).translationY(dp(2))
          .alpha(.86f).setDuration(75).start();
      } else if(e.getActionMasked()==MotionEvent.ACTION_MOVE) {
        boolean inside=e.getX()>=0 && e.getX()<v.getWidth() && e.getY()>=0 && e.getY()<v.getHeight();
        faceLayer.animate().cancel();
        faceLayer.animate().scaleX(inside ? .94f : 1f).scaleY(inside ? .94f : 1f)
          .translationY(inside ? dp(2) : 0).alpha(inside ? .86f : 1f).setDuration(75).start();
      } else if(e.getActionMasked()==MotionEvent.ACTION_UP || e.getActionMasked()==MotionEvent.ACTION_CANCEL) {
        faceLayer.animate().cancel();
        faceLayer.animate().scaleX(1f).scaleY(1f).translationY(0)
          .alpha(1f).setDuration(130).start();
      }
      return false;
    });
    return button;
  }

  boolean isV28Icon(String icon) {
    return "daily100".equals(icon) || "challenges".equals(icon) || "achievements".equals(icon);
  }

  View menuCard(String icon, String label, View.OnClickListener listener) {
    LinearLayout c=vbox();
    c.setGravity(Gravity.CENTER);
    c.setPadding(dp(8),dp(8),dp(8),dp(8));
    c.setBackground(shape(GRID_CARD,20));
    c.addView(centurianHomeButton(icon,70,listener),new LinearLayout.LayoutParams(dp(68),dp(68)));
    TextView lab=bold(label,11);
    lab.setGravity(Gravity.CENTER);
    lab.setPadding(dp(4),dp(7),dp(4),0);
    c.addView(lab,new LinearLayout.LayoutParams(-1,dp(38)));
    return c;
  }

  void addMenuRow(LinearLayout parent, View a, View b) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams ca = new LinearLayout.LayoutParams(0, dp(142), 1);
    ca.setMargins(dp(5), dp(6), dp(6), dp(6));
    LinearLayout.LayoutParams cb = new LinearLayout.LayoutParams(0, dp(142), 1);
    cb.setMargins(dp(6), dp(6), dp(5), dp(6));
    row.addView(a, ca);
    row.addView(b, cb);
    parent.addView(row, new LinearLayout.LayoutParams(-1, dp(154)));
  }

  class MenuIconView extends View {
    final String type;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    MenuIconView(Context c, String type) { super(c); this.type = type; }
    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float w=getWidth(), h=getHeight(), cx=w/2f, cy=h/2f;
      paint.setColor(isV28Icon(type) ? Color.WHITE : GREEN_SOFT); paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(3)); paint.setStrokeCap(Paint.Cap.ROUND);
      if ("daily100".equals(type)) {
        paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        paint.setTextSize(Math.min(w,h)*.34f);
        canvas.drawText("100",cx,cy+Math.min(w,h)*.12f,paint);
      } else if ("challenges".equals(type)) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(cx,cy,dp(18),paint);
        canvas.drawCircle(cx,cy,dp(10),paint);
        paint.setStyle(Paint.Style.FILL); canvas.drawCircle(cx,cy,dp(4),paint);
      } else if ("achievements".equals(type)) {
        paint.setStyle(Paint.Style.FILL);
        Path star=new Path();
        for(int i=0;i<10;i++) {double angle=-Math.PI/2+i*Math.PI/5; float r=dp(i%2==0?20:9);
          float x=cx+(float)Math.cos(angle)*r,y=cy+(float)Math.sin(angle)*r;
          if(i==0)star.moveTo(x,y);else star.lineTo(x,y);
        }
        star.close(); canvas.drawPath(star,paint);
      } else if ("plus".equals(type)) {
        canvas.drawLine(cx-dp(15),cy,cx+dp(15),cy,paint);
        canvas.drawLine(cx,cy-dp(15),cx,cy+dp(15),paint);
      } else if ("sliders".equals(type)) {
        for(int i=-1;i<=1;i++){float y=cy+i*dp(12); canvas.drawLine(cx-dp(18),y,cx+dp(18),y,paint);}
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx-dp(7),cy-dp(12),dp(4),paint);
        canvas.drawCircle(cx+dp(8),cy,dp(4),paint);
        canvas.drawCircle(cx-dp(2),cy+dp(12),dp(4),paint);
      } else if ("list".equals(type)) {
        for(int i=-1;i<=1;i++){float y=cy+i*dp(12); paint.setStyle(Paint.Style.FILL);canvas.drawCircle(cx-dp(16),y,dp(2.5f),paint);paint.setStyle(Paint.Style.STROKE);canvas.drawLine(cx-dp(8),y,cx+dp(18),y,paint);}
      } else if ("dumbbell".equals(type)) {
        canvas.drawLine(cx-dp(14),cy,cx+dp(14),cy,paint);
        canvas.drawLine(cx-dp(14),cy-dp(9),cx-dp(14),cy+dp(9),paint);
        canvas.drawLine(cx-dp(19),cy-dp(6),cx-dp(19),cy+dp(6),paint);
        canvas.drawLine(cx+dp(14),cy-dp(9),cx+dp(14),cy+dp(9),paint);
        canvas.drawLine(cx+dp(19),cy-dp(6),cx+dp(19),cy+dp(6),paint);
      } else if ("community".equals(type)) {
        // V24: reference community symbol, scaled to the existing 43dp header circle.
        float u=Math.min(w,h)/100f;
        canvas.save(); canvas.translate(cx,cy); canvas.scale(u,u);
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        canvas.drawCircle(0,0,43,paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(-18,-14,7,paint);
        canvas.drawCircle(18,-14,7,paint);
        canvas.drawCircle(0,-7,9,paint);
        canvas.drawRoundRect(new RectF(-33,-3,-12,22),5,5,paint);
        canvas.drawRoundRect(new RectF(12,-3,33,22),5,5,paint);
        // Blue separation around the foreground member matches the supplied graphic.
        paint.setColor(GREEN_DARK);
        canvas.drawRoundRect(new RectF(-19,0,19,29),8,8,paint);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(-16,3,16,26),6,6,paint);
        canvas.restore();
      } else if ("settings".equals(type)) {
        // V24: solid eight-tooth gear rather than the former radial target symbol.
        float u=Math.min(w,h)/100f;
        canvas.save(); canvas.translate(cx,cy); canvas.scale(u,u);
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.FILL);
        Path gear=new Path();
        for(int i=0;i<32;i++) {
          double theta=2*Math.PI*i/32-Math.PI/2;
          // Four points per tooth: alternating tip, tip, root, root.
          float r=(i%4==0 || i%4==1)?43f:33f;
          float x=(float)(Math.cos(theta)*r), y=(float)(Math.sin(theta)*r);
          if(i==0) gear.moveTo(x,y); else gear.lineTo(x,y);
        }
        gear.close(); canvas.drawPath(gear,paint);
        paint.setColor(GREEN_DARK);
        canvas.drawCircle(0,0,23,paint);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(6f);
        canvas.drawCircle(0,0,15,paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
      } else if ("check".equals(type)) {
        canvas.drawCircle(cx,cy,dp(20),paint);
        Path q=new Path();q.moveTo(cx-dp(10),cy);q.lineTo(cx-dp(3),cy+dp(8));q.lineTo(cx+dp(12),cy-dp(10));canvas.drawPath(q,paint);
      } else {
        canvas.drawCircle(cx,cy,dp(20),paint);
        paint.setStyle(Paint.Style.FILL);canvas.drawCircle(cx-dp(7),cy-dp(5),dp(2.5f),paint);canvas.drawCircle(cx+dp(7),cy-dp(5),dp(2.5f),paint);
        paint.setStyle(Paint.Style.STROKE);RectF r=new RectF(cx-dp(10),cy-dp(2),cx+dp(10),cy+dp(11));canvas.drawArc(r,15,150,false,paint);
      }
    }
  }

  // V33: Centurian home artwork only; existing home menu geometry is unchanged.
  View centurianHomeButton(String type, int size, View.OnClickListener action) {
    View original = v26MetalButton(type,size,action);
    if (!prefs.getBoolean("centurianAchievement",false)) return original;
    FrameLayout decorated = new FrameLayout(this);
    ImageView laurels = new ImageView(this);
    laurels.setImageResource(R.drawable.centurian_laurels);
    laurels.setScaleType(ImageView.ScaleType.FIT_CENTER);
    decorated.addView(laurels,new FrameLayout.LayoutParams(-1,-1));
    FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(-1,-1);
    int inset = dp(size>=65 ? 8 : 6);
    iconParams.setMargins(inset,inset,inset,inset);
    decorated.addView(original,iconParams);
    return decorated;
  }

  View headerIcon(String type, View.OnClickListener action) {
    return centurianHomeButton(type,58,action);
  }

  void settingsSection(LinearLayout body,String title,String content) {
    body.addView(sectionTitle(title));
    TextView desc=text(content,14,MUTED);
    desc.setPadding(dp(14),dp(14),dp(14),dp(14));
    desc.setBackground(shape(CARD,16));
    body.addView(desc,margins(-1,-2,0,5,0,10));
  }

  void showSettings(boolean push) {
    LinearLayout page=pageShell("Settings");
    LinearLayout body=contentColumn();
    settingsSection(body,"Account","Email sign-up and secure account backup are planned for a future update. No account is created or data uploaded in V24.");
    settingsSection(body,"Notifications · Coming soon","Daily workout reminders and streak notifications will be available in a future release. Notifications are not enabled yet.");
    body.addView(sectionTitle("Sound"));
    LinearLayout soundRow = new LinearLayout(this);
    soundRow.setOrientation(LinearLayout.HORIZONTAL);
    soundRow.setGravity(Gravity.CENTER_VERTICAL);
    soundRow.setPadding(dp(14),dp(10),dp(14),dp(10));
    soundRow.setBackground(shape(CARD,16));
    TextView soundLabel = text("Button and scrolling sounds",15,Color.WHITE);
    soundRow.addView(soundLabel,new LinearLayout.LayoutParams(0,-2,1));
    android.widget.Switch soundToggle = new android.widget.Switch(this);
    soundToggle.setContentDescription("Sound on or off");
    soundToggle.setChecked(prefs.getBoolean("soundsEnabled",true));
    soundToggle.setOnCheckedChangeListener((control,enabled) -> {
      prefs.edit().putBoolean("soundsEnabled",enabled).apply();
    });
    soundRow.addView(soundToggle);
    body.addView(soundRow,margins(-1,-2,0,5,0,10));
    body.addView(sectionTitle("Vibration / Haptic feedback"));
    LinearLayout hapticRow = new LinearLayout(this);
    hapticRow.setOrientation(LinearLayout.HORIZONTAL);
    hapticRow.setGravity(Gravity.CENTER_VERTICAL);
    hapticRow.setPadding(dp(14),dp(10),dp(14),dp(10));
    hapticRow.setBackground(shape(CARD,16));
    TextView hapticLabel = text("Button and scrolling vibration",15,Color.WHITE);
    hapticRow.addView(hapticLabel,new LinearLayout.LayoutParams(0,-2,1));
    android.widget.Switch hapticToggle = new android.widget.Switch(this);
    hapticToggle.setContentDescription("Haptic feedback on or off");
    hapticToggle.setChecked(prefs.getBoolean("hapticsEnabled",true));
    hapticToggle.setOnCheckedChangeListener((control,enabled) ->
        prefs.edit().putBoolean("hapticsEnabled",enabled).apply());
    hapticRow.addView(hapticToggle);
    body.addView(hapticRow,margins(-1,-2,0,5,0,10));
    body.addView(sectionTitle("Centurian Achievement"));
    LinearLayout centurianRow = new LinearLayout(this);
    centurianRow.setOrientation(LinearLayout.HORIZONTAL);
    centurianRow.setGravity(Gravity.CENTER_VERTICAL);
    centurianRow.setPadding(dp(14),dp(10),dp(14),dp(10));
    centurianRow.setBackground(shape(CARD,16));
    TextView centurianLabel = text("Centurian Achievement theme",15,Color.WHITE);
    centurianRow.addView(centurianLabel,new LinearLayout.LayoutParams(0,-2,1));
    android.widget.Switch centurianToggle = new android.widget.Switch(this);
    centurianToggle.setContentDescription("Centurian Achievement theme on or off");
    centurianToggle.setChecked(prefs.getBoolean("centurianAchievement",false));
    centurianToggle.setOnCheckedChangeListener((control,enabled) ->
        prefs.edit().putBoolean("centurianAchievement",enabled).apply());
    centurianRow.addView(centurianToggle);
    body.addView(centurianRow,margins(-1,-2,0,5,0,10));
    body.addView(sectionTitle("Health & Safety"));
    Button safetyButton = button("View signed Health & Safety Agreement", CARD);
    safetyButton.setOnClickListener(v -> showSafetyAgreement(false));
    body.addView(safetyButton,margins(-1,dp(52),0,5,0,10));
    settingsSection(body,"About Reps100","Reps100 helps you create routines, track workouts, and build consistency with a flexible 100-repetition workout approach.");
    settingsSection(body,"Tips","• Keep your body measurements up to date for more relevant estimates.\n• Build good habits through regular workouts.\n• Keep building your workout streak.\n• Try the Wake Up Workout example in your saved routines and build from there.");
    settingsSection(body,"Calorie estimates","Potential calories burned and workout times are approximations, not accurate measurements. They depend on assumed repetition pace, rest time, body weight and exercise intensity. Actual expenditure varies with effort, technique, fitness and individual physiology. Do not use these figures for medical decisions.");
    page.addView(scroll(body),new LinearLayout.LayoutParams(-1,0,1));
    display("settings",page,push);
  }

  void showCommunity(boolean push) {
    LinearLayout page=pageShell("Community");
    LinearLayout body=contentColumn();
    LinearLayout panel=vbox();panel.setGravity(Gravity.CENTER);
    MenuIconView icon=new MenuIconView(this,"community");
    panel.addView(icon,new LinearLayout.LayoutParams(dp(95),dp(95)));
    TextView heading=bold("Reps100 Community",23);heading.setGravity(Gravity.CENTER);
    panel.addView(heading);
    TextView msg=text("Community features are coming in a future update. In V24, workouts and personal data stay on your device; nothing is shared.",15,MUTED);
    msg.setGravity(Gravity.CENTER);msg.setPadding(dp(14),dp(14),dp(14),dp(14));
    panel.addView(msg);
    panel.setBackground(shape(CARD,20));
    body.addView(panel,margins(-1,-2,0,20,0,10));
    page.addView(scroll(body),new LinearLayout.LayoutParams(-1,0,1));
    display("community",page,push);
  }

  void installWakeUpRoutineOnce() {
    if(prefs.getBoolean("wakeUpRoutineSeededV23",false)) return;
    JSONArray old=savedRoutines();
    for(int i=0;i<old.length();i++) {
      JSONObject existing=old.optJSONObject(i);
      if(existing!=null && "Wake Up Workout".equalsIgnoreCase(existing.optString("name"))) {
        prefs.edit().putBoolean("wakeUpRoutineSeededV23",true).apply();return;
      }
    }
    try {
      JSONObject example=new JSONObject();
      example.put("name","Wake Up Workout");
      example.put("sets",6);example.put("min",1);example.put("max",30);
      JSONArray exercises=new JSONArray();
      String[] names={"Push-Up","Sit-Up","Bodyweight Squat","V Push Ups","Sitting Knee Tucks","Jumping Jack"};
      for(String name:names) {
        if(findExercise(name)==null) continue;
        JSONObject e=new JSONObject();e.put("name",name);e.put("weight",0);e.put("unit","kg");exercises.put(e);
      }
      example.put("exercises",exercises);
      if(exercises.length()>0) old.put(example);
      prefs.edit().putString("savedRoutines",old.toString()).putBoolean("wakeUpRoutineSeededV23",true).apply();
    } catch(JSONException ignored) {}
  }

  // V26: use the same hybrid engine for routine planning and completed sessions.
  // Planning assumes a plausible pace because no actual set times exist yet.
  // This is a scenario range, not a guaranteed minimum or maximum burn.
  String potentialEstimate(JSONArray exercises,int sets,int min,int max) {
    if(exercises==null || exercises.length()==0)
      return "Potential Calories Burned: —  •  Estimated Time: —";

    double low=0.0, high=0.0, activeMin=0.0, activeMax=0.0;
    int count=0;
    for(int i=0;i<exercises.length();i++) {
      JSONObject o=exercises.optJSONObject(i);
      if(o==null) continue;
      ExerciseData.Exercise e=findExercise(o.optString("name"));
      if(e==null) continue;
      count++;

      // Reps100 routines allocate 100 reps per exercise, split across sets.
      int reps=100;
      double weight=o.optDouble("weight",0.0);
      String unit=o.optString("unit","kg");
      double expectedSeconds=reps*secondsPerRep(e);
      // Show a realistic planning range around the exercise's default pace.
      double quickSeconds=expectedSeconds*0.8;
      double slowSeconds=expectedSeconds*1.5;

      // Same 75/25 measured-time/rep-assumed-time hybrid as workout history.
      // Both estimates cover the same work: never add them together.
      double repEstimate=estimateCaloriesSet(e,reps,weight,unit,expectedSeconds);
      low+=0.75*estimateCaloriesSet(e,reps,weight,unit,quickSeconds)
          +0.25*repEstimate;
      high+=0.75*estimateCaloriesSet(e,reps,weight,unit,slowSeconds)
          +0.25*repEstimate;
      activeMin+=quickSeconds/60.0;
      activeMax+=slowSeconds/60.0;
    }
    if(count==0)
      return "Potential Calories Burned: —  •  Estimated Time: —";

    // Rest assumptions: 30–90 seconds between sets; net resting expenditure.
    int rests=count*Math.max(0,sets-1);
    double kg=Math.max(1.0,profileWeightKg());
    double restNetMet=Math.max(0.0,correctedMet(1.5)-correctedMet(1.0));
    low+=kg*restNetMet*(rests*30.0/3600.0);
    high+=kg*restNetMet*(rests*90.0/3600.0);
    double durationMin=activeMin+rests*0.5;
    double durationMax=activeMax+rests*1.5;

    return "Potential Calories Burned: "+Math.round(low)+"–"+Math.round(high)
        +" kcal\\nEstimated Workout Time: "+Math.round(durationMin)+"–"
        +Math.round(durationMax)+" min";
  }

  String draftPotentialEstimate() {
    JSONArray ex=new JSONArray();
    for(ExerciseData.Exercise e:routine) {
      try {JSONObject o=new JSONObject();o.put("name",e.name);int i=ex.length();o.put("weight",occurrenceWeight(i));o.put("unit",occurrenceUnit(i));ex.put(o);}catch(JSONException ignored){}
    }
    return potentialEstimate(ex,selectedSets,minReps,maxReps);
  }

  void addStreakCard(LinearLayout p) {
    int current = currentStreak();
    int best = bestStreak();
    LinearLayout card = vbox();
    card.setPadding(dp(16), dp(12), dp(16), dp(12));
    GradientDrawable streakFace=shape(GREEN_DARK,18);
    streakFace.setStroke(dp(2),lighten(GREEN_DARK,55));
    card.setBackground(streakFace);

    String headline = current > 0 ? "🔥  " + current + " day streak" : "Start a new streak today";
    TextView h = bold(headline, 18);
    h.setGravity(Gravity.CENTER);
    h.setSingleLine(false);
    card.addView(h);

    double currentCalories = currentStreakCalories();
    double recordCalories = bestStreakCalories();
    TextView stats = text("Current: " + current + " days   •   " + String.format(Locale.US, "%.1f kcal", currentCalories)
        + "\nRecord: " + best + " days   •   " + String.format(Locale.US, "%.1f kcal", recordCalories), 13, TEXT);
    stats.setGravity(Gravity.CENTER);
    stats.setSingleLine(false);
    card.addView(stats);

    String msg = hasWorkoutOn(dateKey.format(new Date()))
        ? "Today's routine is complete."
        : "Complete a routine today to build your streak.";
    TextView m = text(msg, 13, TEXT);
    m.setGravity(Gravity.CENTER);
    card.addView(m);

    // V35: compact trophy shortcut inside the existing streak card.
    Button trophies=button("🏆  Trophy Cabinet",darken(BLUE,40));
    trophies.setTextSize(13);
    trophies.setOnClickListener(v -> showTrophyCabinet(true));
    LinearLayout.LayoutParams trophyParams=margins(-2,dp(44),0,10,0,0);
    trophyParams.gravity=Gravity.CENTER_HORIZONTAL;
    card.addView(trophies,trophyParams);
    p.addView(card, margins(-1, -2, 0, 14, 0, 4));
  }

  // ---------- V28 Daily 100 and local challenges ----------
  String dailyKey() { return dateKey.format(new Date()); }

  // V33: keep Daily100's historic exercise records for one rolling calendar month.
  // Completed workout entries are stored separately and are not changed here.
  JSONObject dailyRecords() {
    JSONObject all;
    try { all = new JSONObject(prefs.getString("v28DailyRecords", "{}")); }
    catch (Exception ignored) { return new JSONObject(); }
    Calendar cutoff = Calendar.getInstance();
    cutoff.add(Calendar.MONTH, -1);
    String oldestAllowed = dateKey.format(cutoff.getTime());
    ArrayList<String> expired = new ArrayList<>();
    Iterator<String> keys = all.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (key.matches("\\d{4}-\\d{2}-\\d{2}") && key.compareTo(oldestAllowed) < 0)
        expired.add(key);
    }
    if (!expired.isEmpty()) {
      for (String key : expired) all.remove(key);
      prefs.edit().putString("v28DailyRecords", all.toString()).apply();
    }
    return all;
  }

  void saveDailyRecords(JSONObject all) {
    prefs.edit().putString("v28DailyRecords", all.toString()).apply();
  }

  JSONObject todayDaily() {
    return dailyRecords().optJSONObject(dailyKey());
  }

  int dailyTotal(JSONObject record) {
    JSONArray sets = record == null ? null : record.optJSONArray("sets");
    int sum = 0;
    if (sets != null) for (int i=0;i<sets.length();i++) sum += Math.max(0,sets.optInt(i));
    return sum;
  }

  // Rep-only Daily 100 calorie estimate; no rest-time assumption.
  double dailyCalories(String name, int reps) {
    ExerciseData.Exercise exercise=findExercise(name);
    if(exercise==null || reps<=0)return 0.0;
    return estimateCalories(exercise,reps,0.0,"kg");
  }

  // A completed Daily 100 result is frozen at the moment the 100th rep is saved.
  // Older completed records without a snapshot retain the legacy estimate.
  double dailyRecordedCalories(String name, JSONObject record) {
    if (record != null && record.has("completedKcal"))
      return record.optDouble("completedKcal", 0.0);
    return dailyCalories(name, dailyTotal(record));
  }

  int completedDailyOn(String date) {
    JSONObject day = dailyRecords().optJSONObject(date);
    if (day == null) return 0;
    int completed = 0;
    Iterator<String> names = day.keys();
    while (names.hasNext()) if (dailyTotal(day.optJSONObject(names.next())) >= 100) completed++;
    return completed;
  }

  int dailyCompletedAllTime() {
    JSONObject all = dailyRecords();
    Iterator<String> days=all.keys(); int count=0;
    while(days.hasNext()) count += completedDailyOn(days.next());
    return count;
  }

  int dailyCompletedDays() {
    JSONObject all = dailyRecords();
    Iterator<String> days=all.keys(); int count=0;
    while(days.hasNext()) if(completedDailyOn(days.next())>0) count++;
    return count;
  }

  int completedDailyThisWeek() {
    Calendar c=Calendar.getInstance();
    c.setFirstDayOfWeek(Calendar.MONDAY);
    int offset=(c.get(Calendar.DAY_OF_WEEK)+5)%7;
    c.add(Calendar.DAY_OF_YEAR,-offset);
    int count=0;
    for(int i=0;i<7;i++) {count+=completedDailyOn(dateKey.format(c.getTime()));c.add(Calendar.DAY_OF_YEAR,1);}
    return count;
  }

  ArrayList<String> dailyOrder(JSONObject day) {
    return dailyOrder(day, dailyKey());
  }

  ArrayList<String> dailyOrder(JSONObject day, String date) {
    ArrayList<String> names = new ArrayList<>();
    if (day == null) return names;
    String saved = prefs.getString("dailyOrder_" + date, "");
    try {
      JSONArray order = new JSONArray(saved);
      for (int i=0;i<order.length();i++) {
        String name=order.optString(i);
        if(day.has(name) && !names.contains(name)) names.add(name);
      }
    } catch(Exception ignored) {}
    Iterator<String> it=day.keys();
    while(it.hasNext()) { String name=it.next(); if(!names.contains(name))names.add(name); }
    return names;
  }

  void saveDailyOrder(ArrayList<String> names) {
    saveDailyOrder(names, dailyKey());
  }

  void saveDailyOrder(ArrayList<String> names, String date) {
    JSONArray array=new JSONArray(); for(String name:names)array.put(name);
    prefs.edit().putString("dailyOrder_"+date,array.toString()).apply();
  }

  void removeDailyExercise(String name) { removeDailyExercise(name, dailyKey()); }

  void removeDailyExercise(String name, String date) {
    JSONObject all=dailyRecords(); JSONObject day=all.optJSONObject(date);
    if(day==null)return;
    day.remove(name);
    if(day.length()==0)all.remove(date);
    else try {all.put(date,day);}catch(JSONException ignored){}
    saveDailyRecords(all);
    ArrayList<String> names=dailyOrder(day,date);names.remove(name);saveDailyOrder(names,date);
    showDaily100(false, true);
  }

  void dailyCard(LinearLayout parent,String name,JSONObject record) {
    dailyCard(parent,name,record,dailyKey());
  }

  void dailyCard(LinearLayout parent,String name,JSONObject record,String date) {
    final boolean historical=!date.equals(dailyKey());
    LinearLayout card=vbox(); card.setPadding(dp(14),dp(10),dp(14),dp(12));
    card.setBackground(shape(CARD,18));card.setElevation(dp(5));
    int done=dailyTotal(record),left=Math.max(0,100-done);
    LinearLayout heading=new LinearLayout(this);
    heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(Gravity.CENTER_VERTICAL);
    TextView handle=bold("☰",24);handle.setTextColor(MUTED);handle.setGravity(Gravity.CENTER);
    handle.setContentDescription("Hold and drag to reorder exercise");
    heading.addView(handle,new LinearLayout.LayoutParams(dp(44),dp(64)));
    LinearLayout details=vbox();details.addView(bold(name,17));
    ExerciseData.Exercise exercise=null;
    for(ExerciseData.Exercise e:ExerciseData.ALL)if(e.name.equals(name)){exercise=e;break;}
    if(exercise!=null)details.addView(text(exercise.muscle+"  •  "+exercise.movement+"  •  "+exercise.equipment,12,MUTED));
    heading.addView(details,new LinearLayout.LayoutParams(0,-2,1));card.addView(heading);
    card.addView(text(done+" / 100 reps  •  "+(left==0?"Completed!":left+" remaining"),14,left==0?GREEN:MUTED));
    card.addView(text("Estimated calories burned: "+String.format(Locale.US,"%.1f kcal",(historical && done>=100 ? dailyRecordedCalories(name,record) : dailyCalories(name,done))),14,GREEN));
    JSONArray sets=record.optJSONArray("sets");
    if(sets!=null && sets.length()>0) {
      StringBuilder history=new StringBuilder("Sets: ");
      for(int i=0;i<sets.length();i++){if(i>0)history.append(" + ");history.append(sets.optInt(i));}
      card.addView(text(history.toString(),12,MUTED));
    }
    if(left>0 && !historical){
      Button add=button("Record a set",darken(BLUE,55));
      add.setOnClickListener(v->openDailySet(name));
      card.addView(add,margins(-1,dp(52),0,6,0,2));
    }
    // Match Create Routine: icon-only delete reveal, threshold colour, and
    // handle-only bounded drag with live gap animation and edge scrolling.
    final int liftedCardColor=Color.rgb(Math.min(255,Color.red(CARD)+18),
        Math.min(255,Color.green(CARD)+18),Math.min(255,Color.blue(CARD)+18));
    TextView deleteBg=bold("🗑",24);
    deleteBg.setGravity(Gravity.CENTER_VERTICAL);
    deleteBg.setPadding(dp(22),0,0,0);
    deleteBg.setTextColor(0xFFE8E8E8);
    deleteBg.setBackground(shape(0xFF641E24,18));
    SwipeDeleteLayout wrapper=new SwipeDeleteLayout(this);
    wrapper.addView(deleteBg,new FrameLayout.LayoutParams(-1,-1));
    wrapper.addView(card,new FrameLayout.LayoutParams(-1,-2));
    wrapper.setTag("daily-reorder-card");
    parent.addView(wrapper,margins(-1,-2,0,6,0,8));
    wrapper.setOnTouchListener((v,ev)->{
      switch(ev.getActionMasked()){
        case MotionEvent.ACTION_MOVE: {
          float dx=ev.getX()-wrapper.downX;
          if(dx>0){
            float swipeX=Math.min(dx,card.getWidth());
            card.setTranslationX(swipeX);
            deleteBg.setBackground(shape(swipeX>card.getWidth()*.50f?0xFFD9364A:0xFF641E24,18));
          }
          return true;
        }
        case MotionEvent.ACTION_UP: {
          boolean armed=card.getTranslationX()>card.getWidth()*.50f;
          wrapper.interceptingSwipe=false;
          if(daily100ScrollView!=null)daily100ScrollView.requestDisallowInterceptTouchEvent(false);
          deleteBg.setBackground(shape(0xFF641E24,18));
          card.animate().translationX(0).setDuration(150).start();
          if(armed)new AlertDialog.Builder(this).setTitle("Remove exercise?")
              .setMessage("Remove "+name+" from the Daily 100 challenge on "+date+"?"+(done>0?" Its recorded reps will also be deleted.":""))
              .setNegativeButton("Cancel",null)
              .setPositiveButton("Remove",(d,w)->{
                deletionHaptic(wrapper);
                removeDailyExercise(name,date);
              }).show();
          return true;
        }
        case MotionEvent.ACTION_CANCEL:
          wrapper.interceptingSwipe=false;
          if(daily100ScrollView!=null)daily100ScrollView.requestDisallowInterceptTouchEvent(false);
          deleteBg.setBackground(shape(0xFF641E24,18));
          card.animate().translationX(0).setDuration(150).start();
          return true;
      }
      return true;
    });
    handle.setOnTouchListener(new View.OnTouchListener(){
      final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
      final int slop=ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
      Runnable hold;
      boolean dragging=false,cancelledHold=false;
      float downRawX,downRawY,dragOffsetY=0;
      final float[] lastFingerY={0};
      int originalIndex=-1,targetIndex=-1;
      ArrayList<String> order;
      int firstCard(){for(int i=0;i<parent.getChildCount();i++)if("daily-reorder-card".equals(parent.getChildAt(i).getTag()))return i;return -1;}
      int lastCard(){for(int i=parent.getChildCount()-1;i>=0;i--)if("daily-reorder-card".equals(parent.getChildAt(i).getTag()))return i;return -1;}
      void positionCard(){
        if(daily100ScrollView==null)return;
        int[] scrollLoc=new int[2];daily100ScrollView.getLocationOnScreen(scrollLoc);
        float minTop=scrollLoc[1],maxBottom=scrollLoc[1]+daily100ScrollView.getHeight();
        int first=firstCard(),last=lastCard();
        if(first<0||last<0)return;
        View topCard=parent.getChildAt(first),bottomCard=parent.getChildAt(last);
        int[] topLoc=new int[2],bottomLoc=new int[2];
        topCard.getLocationOnScreen(topLoc);bottomCard.getLocationOnScreen(bottomLoc);
        minTop=Math.max(minTop,topLoc[1]-topCard.getTranslationY());
        maxBottom=Math.min(maxBottom,bottomLoc[1]-bottomCard.getTranslationY()+bottomCard.getHeight());
        int[] ownLoc=new int[2];wrapper.getLocationOnScreen(ownLoc);
        float originalTop=ownLoc[1]-wrapper.getTranslationY();
        float desiredTop=originalTop+lastFingerY[0]-downRawY+dragOffsetY;
        float upper=Math.max(minTop,maxBottom-wrapper.getHeight());
        wrapper.setTranslationY(Math.max(minTop,Math.min(upper,desiredTop))-originalTop);
      }
      void updateTarget(){
        if(order==null||originalIndex<0)return;
        int[] draggedLoc=new int[2];wrapper.getLocationOnScreen(draggedLoc);
        float top=draggedLoc[1],bottom=top+wrapper.getHeight();
        int next=originalIndex,count=0;
        for(int j=0;j<parent.getChildCount();j++){
          View other=parent.getChildAt(j);
          if(!"daily-reorder-card".equals(other.getTag()))continue;
          int itemIndex=count++;
          if(other==wrapper)continue;
          int[] loc=new int[2];other.getLocationOnScreen(loc);
          float mid=loc[1]-other.getTranslationY()+other.getHeight()/2f;
          if(itemIndex>originalIndex&&bottom>mid)next++;
          if(itemIndex<originalIndex&&top<mid)next--;
        }
        next=Math.max(0,Math.min(order.size()-1,next));
        if(next==targetIndex)return;
        targetIndex=next;count=0;
        for(int j=0;j<parent.getChildCount();j++){
          View other=parent.getChildAt(j);
          if(!"daily-reorder-card".equals(other.getTag()))continue;
          int itemIndex=count++;
          if(other==wrapper)continue;
          float shift=0;
          if(targetIndex>originalIndex&&itemIndex>originalIndex&&itemIndex<=targetIndex)shift=-wrapper.getHeight();
          if(targetIndex<originalIndex&&itemIndex>=targetIndex&&itemIndex<originalIndex)shift=wrapper.getHeight();
          other.animate().cancel();other.animate().translationY(shift).setDuration(100).start();
        }
      }
      final Runnable edgeScroll=new Runnable(){@Override public void run(){
        if(!dragging||daily100ScrollView==null)return;
        int[] loc=new int[2];daily100ScrollView.getLocationOnScreen(loc);
        float fingerY=lastFingerY[0]-loc[1];int step=0;
        if(fingerY<dp(85))step=-dp(13);
        else if(fingerY>daily100ScrollView.getHeight()-dp(85))step=dp(13);
        if(step!=0){
          int before=daily100ScrollView.getScrollY();daily100ScrollView.scrollBy(0,step);
          dragOffsetY+=daily100ScrollView.getScrollY()-before;
          positionCard();updateTarget();
        }
        handler.postDelayed(this,25);
      }};
      @Override public boolean onTouch(View v,MotionEvent ev){
        switch(ev.getActionMasked()){
          case MotionEvent.ACTION_DOWN:
            downRawX=ev.getRawX();downRawY=lastFingerY[0]=ev.getRawY();
            dragging=false;wrapper.reordering=false;cancelledHold=false;dragOffsetY=0;
            order=dailyOrder(dailyRecords().optJSONObject(date),date);originalIndex=order.indexOf(name);targetIndex=originalIndex;
            hold=()->{
              if(cancelledHold||originalIndex<0)return;
              dragging=true;wrapper.reordering=true;
              wrapper.setTranslationZ(dp(20));card.setElevation(dp(18));
              card.setBackground(shape(liftedCardColor,18));
              card.animate().scaleX(1.025f).scaleY(1.025f).setDuration(120).start();
              v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
              if(daily100ScrollView!=null)daily100ScrollView.requestDisallowInterceptTouchEvent(true);
              handler.post(edgeScroll);
            };
            handler.postDelayed(hold,ViewConfiguration.getLongPressTimeout());return true;
          case MotionEvent.ACTION_MOVE:
            lastFingerY[0]=ev.getRawY();
            // Identical to Create Routine: movement cancels pending long-press;
            // the shared SwipeDeleteLayout owns horizontal swipe detection.
            if(!dragging && (Math.abs(ev.getRawY()-downRawY)>slop || Math.abs(ev.getRawX()-downRawX)>slop)) {
              cancelledHold=true;handler.removeCallbacks(hold);
            }
            if(dragging){positionCard();updateTarget();}
            return true;
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL:
            cancelledHold=true;if(hold!=null)handler.removeCallbacks(hold);
            handler.removeCallbacks(edgeScroll);
            // Match Create Routine handle: release interception on the wrapper.
            wrapper.requestDisallowInterceptTouchEvent(false);
            if(dragging){
              if(ev.getActionMasked()==MotionEvent.ACTION_UP&&targetIndex!=originalIndex&&originalIndex>=0){
                order.remove(originalIndex);order.add(targetIndex,name);saveDailyOrder(order,date);
              }
              dragging=false;wrapper.reordering=false;
              card.setBackground(shape(CARD,18));
              wrapper.setTranslationY(0);wrapper.setTranslationZ(0);
              card.setElevation(dp(5));card.setScaleX(1);card.setScaleY(1);
              showDaily100(false,true);
            }
            return true;
        }
        return true;
      }
    });
  }

  // V32: Weightlifting loads can be corrected while the workout is in progress.
  boolean usesAdjustableLoad(ExerciseData.Exercise exercise) {
    if (exercise == null || isBodyweightExercise(exercise)) return false;
    String eq = exercise.equipment == null ? "" : exercise.equipment.toLowerCase(Locale.US);
    return !eq.contains("cardio") && !eq.contains("none");
  }

  String loadDescription(ExerciseData.Exercise exercise, double weight, String unit) {
    String label = exercise != null && exercise.equipment.toLowerCase(Locale.US).contains("dumbbell")
        ? "Weight per dumbbell" : "Weight used";
    return label + ": " + formatNumber(weight) + " " + unit;
  }

  void editWorkoutLoad(ExerciseData.Exercise exercise, double initial, String initialUnit,
                       java.util.function.BiConsumer<Double,String> onSave) {
    LinearLayout fields = vbox();
    fields.setPadding(dp(20), dp(8), dp(20), dp(8));
    fields.addView(text("Change the load for this exercise. The new value applies to the next sets.", 14, MUTED));
    EditText value = field("Weight", formatNumber(initial),
        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    fields.addView(value);
    Spinner unit = new Spinner(this);
    String[] choices = {"kg", "lb"};
    unit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices));
    unit.setSelection("lb".equals(initialUnit) ? 1 : 0);
    fields.addView(unit);
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("Adjust weight — " + exercise.name).setView(fields)
        .setNegativeButton("Cancel", null).setPositiveButton("Save weight", null).create();
    dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      double weight;
      try { weight = Double.parseDouble(value.getText().toString().trim()); }
      catch (Exception ex) { value.setError("Enter a valid weight"); return; }
      if (Double.isNaN(weight) || Double.isInfinite(weight) || weight < 0 || weight > 2000) {
        value.setError("Enter a weight between 0 and 2000"); return;
      }
      onSave.accept(weight, choices[unit.getSelectedItemPosition()]);
      dialog.dismiss();
    }));
    dialog.show();
  }

  void openDailySet(String name) {
    dailyActiveName = name;
    dailyPhase = 0;
    dailySetElapsed = 0;
    showDailySet(true);
  }

  void showDailySet(boolean push) {
    if (dailyActiveName == null) { showDaily100(false); return; }
    JSONObject rec = todayDaily().optJSONObject(dailyActiveName);
    if (rec == null || dailyTotal(rec) >= 100) { stopDailyTimer(); showDaily100(false); return; }
    final String name = dailyActiveName;
    final int done = dailyTotal(rec);
    ExerciseData.Exercise exercise = findExercise(name);
    LinearLayout page = pageShell("Daily 100");
    LinearLayout body = contentColumn();
    TextView progress = text(done + " / 100 reps completed  •  " + (100-done) + " remaining",14,BLUE);
    progress.setGravity(Gravity.CENTER); body.addView(progress);
    TextView heading = bold(name,28); heading.setGravity(Gravity.CENTER); body.addView(heading);
    if (exercise != null) {
      ImageView picture = new ImageView(this);
      picture.setImageResource(exerciseDrawable(exercise,false));
      picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
      picture.setAdjustViewBounds(true);
      body.addView(picture,margins(-1,dp(180),0,4,0,4));
    }
    LinearLayout stats = new LinearLayout(this);
    stats.setGravity(Gravity.CENTER);
    LinearLayout repsColumn=vbox();
    TextView count=bold(String.valueOf(done),60);count.setGravity(Gravity.CENTER);repsColumn.addView(count);
    TextView countLabel=text("REPS COMPLETED",13,MUTED);countLabel.setGravity(Gravity.CENTER);repsColumn.addView(countLabel);
    stats.addView(repsColumn,new LinearLayout.LayoutParams(0,-2,0.35f));
    LinearLayout timerColumn=vbox();
    TextView timer=bold(elapsedClock(dailySetElapsed),46);
    timer.setSingleLine(true);timer.setMaxLines(1);timer.setHorizontallyScrolling(true);timer.setGravity(Gravity.CENTER);
    timer.setAutoSizeTextTypeUniformWithConfiguration(30,46,1,android.util.TypedValue.COMPLEX_UNIT_SP);
    timerColumn.addView(timer);
    TextView timerLabel=text("SET TIMER",13,MUTED);timerLabel.setGravity(Gravity.CENTER);timerColumn.addView(timerLabel);
    stats.addView(timerColumn,new LinearLayout.LayoutParams(0,-2,0.65f));body.addView(stats);
    if (usesAdjustableLoad(exercise)) {
      final ExerciseData.Exercise loadExercise = exercise;
      double weight = rec.optDouble("weight", 0);
      String unit = rec.optString("unit", prefs.getString("profileWeightUnit", "kg"));
      Button adjust = outlineButton(loadDescription(exercise, weight, unit) + "   ✎ Edit");
      adjust.setOnClickListener(v -> editWorkoutLoad(loadExercise, weight, unit, (newWeight, newUnit) -> {
        JSONObject all = dailyRecords();
        JSONObject today = all.optJSONObject(dailyKey());
        JSONObject saved = today == null ? null : today.optJSONObject(name);
        if (saved == null) return;
        try { saved.put("weight", newWeight); saved.put("unit", newUnit);
          today.put(name, saved); all.put(dailyKey(), today); }
        catch (JSONException ex) { Toast.makeText(this,"Unable to save weight",Toast.LENGTH_SHORT).show(); return; }
        saveDailyRecords(all);
        adjust.setText(loadDescription(loadExercise, newWeight, newUnit) + "   ✎ Edit");
      }));
      body.addView(adjust, margins(-1, dp(52), 0, 8, 0, 8));
    }
    Button action=button(dailyPhase==0?"Start exercise":dailyPhase==1?"Stop exercise":dailyPhase==2?"Record the set":"Next",BLUE);
    final int activeGreen=Color.rgb(133,205,155);
    action.setTag(dailyPhase==1?activeGreen:BLUE);
    setWorkoutActionFace(action,dailyPhase==1?activeGreen:BLUE);
    action.setOnClickListener(v->{
      if (dailyPhase==0) {
        dailyPhase=1;dailySetStart=System.currentTimeMillis();
        action.setText("Stop exercise");action.setTag(activeGreen);setWorkoutActionFace(action,activeGreen);
        stopDailyTimer();
        dailyTimerTick=new Runnable(){@Override public void run(){
          if(dailyPhase!=1)return;
          timer.setText(elapsedClock(System.currentTimeMillis()-dailySetStart));
          dailyTimerHandler.postDelayed(this,200);
        }};
        dailyTimerHandler.post(dailyTimerTick);
      } else if(dailyPhase==1) {
        dailySetElapsed=Math.max(100, System.currentTimeMillis()-dailySetStart);
        dailyPhase=2;stopDailyTimer();timer.setText(elapsedClock(dailySetElapsed));
        action.setText("Record the set");action.setTag(BLUE);setWorkoutActionFace(action,BLUE);
      } else if(dailyPhase==2) {
        recordTimedDailySet(name,100-done,exercise,action,progress);
      } else {
        openNextDailySet(name);
      }
    });
    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
    Button back=button("Back",CARD_ALT);
    back.setEnabled(dailyPhase!=1);
    back.setOnClickListener(v->{if(dailyPhase!=1){stopDailyTimer();showDaily100(false);}});
    row.addView(back,new LinearLayout.LayoutParams(dp(92),dp(54)));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);lp.setMargins(dp(8),0,0,0);
    row.addView(action,lp);body.addView(row,margins(-1,dp(54),0,16,0,8));
    Button cancel=outlineButton("Cancel");cancel.setTextColor(RED);
    cancel.setOnClickListener(v->{stopDailyTimer();dailyPhase=0;showDaily100(false);});
    body.addView(cancel,margins(-1,dp(56),0,0,0,8));
    page.addView(scroll(body),new LinearLayout.LayoutParams(-1,0,1));
    display("dailySet",page,push);
  }

  void openNextDailySet(String name) {
    stopDailyTimer();
    JSONObject day=todayDaily();
    ArrayList<String> names=dailyOrder(day);
    int index=names.indexOf(name);
    for(int i=index+1;i<names.size();i++) {
      JSONObject record=day.optJSONObject(names.get(i));
      if(record!=null && dailyTotal(record)<100) {
        dailyActiveName=names.get(i);dailyPhase=0;dailySetElapsed=0;
        showDailySet(false);return;
      }
    }
    showDaily100(false);
  }

  void recordTimedDailySet(String name,int remaining,ExerciseData.Exercise exercise,Button action,TextView progress) {
    EditText entry=field("Repetitions completed","",InputType.TYPE_CLASS_NUMBER);
    entry.setSingleLine(true);
    LinearLayout wrap=vbox();wrap.setPadding(dp(20),dp(8),dp(20),0);
    wrap.addView(text("Enter 1 to "+remaining+" repetitions for this timed set.",14,MUTED));wrap.addView(entry);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Repetitions completed")
      .setView(wrap).setNegativeButton("Cancel",null).setPositiveButton("Save set",null).create();
    dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      int reps=0;try{reps=Integer.parseInt(entry.getText().toString().trim());}catch(Exception ignoredNumber){}
      if(reps<1||reps>remaining){entry.setError("Enter 1 to "+remaining);return;}
      if(exercise!=null && dailySetElapsed/1000.0/reps < minimumSecondsPerRep(exercise)) {
        new AlertDialog.Builder(this).setTitle("Unrealistic set duration")
          .setMessage("This set was completed faster than the plausible repetition pace. It will not be recorded. Please repeat the timed set.")
          .setPositiveButton("OK",(d,w)->{dialog.dismiss();dailyPhase=0;dailySetElapsed=0;showDailySet(false);}).show();
        return;
      }
      JSONObject all=dailyRecords();JSONObject day=all.optJSONObject(dailyKey());
      if(day==null || !day.has(name)){dialog.dismiss();showDaily100(false);return;}
      JSONObject rec=day.optJSONObject(name);if(rec==null)return;
      JSONArray sets=rec.optJSONArray("sets");if(sets==null)sets=new JSONArray();
      JSONArray times=rec.optJSONArray("setDurations");if(times==null)times=new JSONArray();
      int before=dailyTotal(rec);sets.put(reps);times.put(Double.valueOf(dailySetElapsed/1000.0));
      try {
        rec.put("sets",sets);rec.put("setDurations",times);
        if(before<100 && dailyTotal(rec)>=100){
          rec.put("completedKcal",dailyCalories(name,dailyTotal(rec)));
          rec.put("completedAt",System.currentTimeMillis());
        }
        day.put(name,rec);all.put(dailyKey(),day);
      }catch(JSONException ex){Toast.makeText(this,"Unable to save set",Toast.LENGTH_LONG).show();return;}
      saveDailyRecords(all);dialog.dismiss();dailyPhase=3;
      int total=dailyTotal(rec);
      if(total>=100) captureFitnessDailyCompletion(dailyKey(),name,rec);
      progress.setText(total+" / 100 reps completed  •  "+Math.max(0,100-total)+" remaining");
      action.setText(total>=100?"Daily100 achieved":"Next");
      if(total>=100)Toast.makeText(this,"Daily 100 complete: "+name+"!",Toast.LENGTH_LONG).show();
    }));
    dialog.show();
  }

  // Reuse the same searchable, filtered exercise library as Create Routine.
  void chooseDailyExercise() {
    selectingForRoutine = false;
    selectingForDaily100 = true;
    libraryScrollY = 0;
    showLibrary(true);
  }

  void addDailyExerciseFromLibrary(ExerciseData.Exercise e) {
    JSONObject all = dailyRecords();
    JSONObject day = all.optJSONObject(dailyKey());
    if (day == null) day = new JSONObject();
    if (day.length() >= 5 && !day.has(e.name)) {
      Toast.makeText(this, "Daily 100 allows a maximum of 5 exercises per day", Toast.LENGTH_LONG).show();
      return;
    }
    if (day.has(e.name)) {
      Toast.makeText(this, e.name + " is already in today's challenge", Toast.LENGTH_SHORT).show();
      return;
    }
    try {
      JSONObject rec = new JSONObject();
      rec.put("sets", new JSONArray());
      rec.put("weight", 0.0);
      rec.put("unit", prefs.getString("profileWeightUnit", "kg"));
      day.put(e.name, rec);
      all.put(dailyKey(), day);
    } catch (JSONException ex) {
      Toast.makeText(this, "Unable to add exercise", Toast.LENGTH_SHORT).show();
      return;
    }
    saveDailyRecords(all);
    selectingForDaily100 = false;
    libraryScrollY = 0;
    Toast.makeText(this, e.name + " added", Toast.LENGTH_SHORT).show();
    returnToScreen("daily100");
  }

  // Display only: preserve ISO yyyy-MM-dd keys for storage and chronological sorting.
  String ukDailyDate(String isoDate) {
    if (isoDate != null && isoDate.matches("\\d{4}-\\d{2}-\\d{2}"))
      return isoDate.substring(8,10)+"/"+isoDate.substring(5,7)+"/"+isoDate.substring(0,4);
    return isoDate;
  }

  void showDaily100(boolean push) {
    showDaily100(push, false);
  }

  void showDaily100(boolean push, boolean refreshInPlace) {
    ScrollView existingScroll = refreshInPlace ? daily100ScrollView : null;
    LinearLayout page=pageShell("Daily 100");LinearLayout body=contentColumn();
    body.addView(text("Choose any number of exercises to perform throughout today, and record sets whenever you have time. Each exercise counts as complete only when it reaches 100 reps. It will then count towards your achievements and daily calories burned.",14,MUTED));
    body.addView(text("Daily 100 is limited to a maximum of 5 exercises per day.",14,MUTED));
    body.addView(bold("Today • "+ukDailyDate(dailyKey()),18));
    Button choose=button("＋  Add exercise",darken(BLUE,55));
    JSONObject currentDayForLimit = todayDaily();
    boolean atDailyLimit = currentDayForLimit != null && currentDayForLimit.length() >= 5;
    choose.setEnabled(!atDailyLimit);
    choose.setOnClickListener(v->chooseDailyExercise());
    if (atDailyLimit) body.addView(text("Daily limit reached: 5 exercises.", 12, MUTED));
    body.addView(choose,margins(-1,dp(52),0,6,0,12));
    JSONObject day=todayDaily();
    if(day==null||day.length()==0)body.addView(text("No Daily 100 exercises started yet.",14,MUTED));
    else {
      body.addView(sectionTitle("Exercises in today's challenge ("+day.length()+")"));
      body.addView(text("Hold ☰ to reorder • Swipe right to delete (including completed exercises)",12,MUTED));
      for(String name:dailyOrder(day))dailyCard(body,name,day.optJSONObject(name));
    }
    // Previous days are retained in the same on-device records, and displayed
    // below today's challenge instead of disappearing at midnight.
    JSONObject allDays=dailyRecords();
    ArrayList<String> historicalDates=new ArrayList<>();
    Iterator<String> dayKeys=allDays.keys();
    while(dayKeys.hasNext()) {
      String date=dayKeys.next();
      if(!date.equals(dailyKey()) && date.matches("\\d{4}-\\d{2}-\\d{2}"))
        historicalDates.add(date);
    }
    Collections.sort(historicalDates,Collections.reverseOrder());
    body.addView(sectionTitle("Historic exercises"));
    body.addView(text("Rolling one-month archive: exercises older than one month are automatically removed.",12,MUTED));
    if(historicalDates.isEmpty())body.addView(text("Previous days' exercises will appear here after midnight.",13,MUTED));
    for(String date:historicalDates) {
      JSONObject past=allDays.optJSONObject(date);
      if(past==null || past.length()==0)continue;
      body.addView(bold(ukDailyDate(date)+"  •  "+past.length()+" exercises",16));
      body.addView(text("Hold ☰ to reorder • Swipe right to delete",12,MUTED));
      // A separate list per day prevents dragging exercises across dates.
      LinearLayout historicList=vbox();
      body.addView(historicList,margins(-1,-2,0,4,0,10));
      for(String name:dailyOrder(past,date)) {
        JSONObject record=past.optJSONObject(name);
        if(record!=null)dailyCard(historicList,name,record,date);
      }
    }
    body.addView(text("Progress is stored on this device. At midnight, the days challenges come to an end, and you can start a new day's challenges. Completed workouts remain in the Completed page.",12,MUTED));
    if (existingScroll != null && "daily100".equals(currentScreen)
        && existingScroll.getParent() != null) {
      // Reuse the visible ScrollView. Never replace the Activity window on
      // a drag/drop or swipe/delete refresh, preventing a top-of-page flash.
      final int preservedY = existingScroll.getScrollY();
      final int oldFocusability = existingScroll.getDescendantFocusability();
      existingScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
      existingScroll.removeAllViews();
      existingScroll.addView(body);
      existingScroll.scrollTo(0, preservedY);
      final ScrollView retainedScroll = existingScroll;
      retainedScroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw() {
          if (retainedScroll.getViewTreeObserver().isAlive())
            retainedScroll.getViewTreeObserver().removeOnPreDrawListener(this);
          int maxY = Math.max(0, body.getHeight() - retainedScroll.getHeight());
          retainedScroll.scrollTo(0, Math.min(preservedY, maxY));
          retainedScroll.setDescendantFocusability(oldFocusability);
          return true;
        }
      });
    } else {
      daily100ScrollView = scroll(body);
      page.addView(daily100ScrollView,new LinearLayout.LayoutParams(-1,0,1));
      display("daily100",page,push);
    }
  }

  void challengeProgress(LinearLayout body,String title,String description,int value,int target) {
    LinearLayout card=vbox();card.setPadding(dp(15),dp(12),dp(15),dp(12));card.setBackground(shape(CARD,18));
    card.addView(bold(title,17));card.addView(text(description,13,MUTED));
    card.addView(text(Math.min(value,target)+" / "+target+(value>=target?"  •  Completed":""),14,value>=target?GREEN:TEXT));
    body.addView(card,margins(-1,-2,0,5,0,7));
  }

  // V34 Standard Fitness: use verified completed routines plus Daily100 records.
  // Daily100's rolling history is archived as a compact completion ledger below.
  JSONArray fitnessLedger() {
    try { return new JSONArray(prefs.getString("standardFitnessDailyLedger", "[]")); }
    catch (Exception ignored) { return new JSONArray(); }
  }

  void syncFitnessLedger() {
    JSONArray ledger=fitnessLedger();
    HashSet<String> seen=new HashSet<>();
    for(int i=0;i<ledger.length();i++) {
      JSONObject o=ledger.optJSONObject(i);
      if(o!=null)seen.add(o.optString("id"));
    }
    JSONObject days=dailyRecords();
    Iterator<String> dates=days.keys();
    boolean changed=false;
    while(dates.hasNext()) {
      String date=dates.next();
      JSONObject day=days.optJSONObject(date);
      if(day==null)continue;
      Iterator<String> names=day.keys();
      while(names.hasNext()) {
        String name=names.next();
        JSONObject r=day.optJSONObject(name);
        if(dailyTotal(r)<100)continue;
        String id=date+"|"+name;
        if(!seen.add(id))continue;
        JSONObject entry=new JSONObject();
        try {
          entry.put("id",id);entry.put("date",date);
          entry.put("name",name);entry.put("reps",dailyTotal(r));
          entry.put("timestamp",r.optLong("completedAt",0));
          ledger.put(entry);changed=true;
        } catch(Exception ignored) {}
      }
    }
    if(changed)prefs.edit().putString("standardFitnessDailyLedger",ledger.toString()).apply();
  }

  // Daily100 records are captured at completion so they survive the rolling
  // month cleanup. No duplicate credit when a page is revisited.
  void captureFitnessDailyCompletion(String date,String name,JSONObject record) {
    if(record==null||dailyTotal(record)<100)return;
    JSONArray ledger=fitnessLedger();String id=date+"|"+name;
    for(int i=0;i<ledger.length();i++)if(id.equals(ledger.optJSONObject(i)==null?"":ledger.optJSONObject(i).optString("id")))return;
    JSONObject entry=new JSONObject();
    try {
      entry.put("id",id);entry.put("date",date);entry.put("name",name);
      entry.put("reps",dailyTotal(record));entry.put("timestamp",record.optLong("completedAt",System.currentTimeMillis()));
      ledger.put(entry);
      prefs.edit().putString("standardFitnessDailyLedger",ledger.toString()).apply();
    } catch(Exception ignored) {}
  }

  class FitnessStats {
    int workouts,reps,days,distinctExercises,bestWeeklyReps,weeklyReps,weeklyWorkouts;
    int previousWeeklyReps,previousWeeklyWorkouts;
    HashSet<String> names=new HashSet<>(),dates=new HashSet<>();
  }

  String weekStart(String date) {
    try {
      Calendar c=Calendar.getInstance();c.setTime(dateKey.parse(date));
      c.add(Calendar.DAY_OF_YEAR,-((c.get(Calendar.DAY_OF_WEEK)+5)%7));
      return dateKey.format(c.getTime());
    }catch(Exception ignored){return "";}
  }

  FitnessStats fitnessStats() {
    syncFitnessLedger();FitnessStats f=new FitnessStats();
    HashMap<String,Integer> weekly=new HashMap<>();
    String thisWeek=weekStart(dailyKey());
    Calendar prev=Calendar.getInstance();prev.add(Calendar.DAY_OF_YEAR,-7);
    String lastWeek=weekStart(dateKey.format(prev.getTime()));
    JSONArray records=loadRecords();
    for(int i=0;i<records.length();i++) {
      JSONObject r=records.optJSONObject(i);if(r==null)continue;
      String date=r.optString("date","");if(date.isEmpty())continue;
      f.workouts++;f.dates.add(date);
      JSONArray exercises=r.optJSONArray("exercises");int reps=0;
      if(exercises!=null)for(int j=0;j<exercises.length();j++) {
        JSONObject e=exercises.optJSONObject(j);if(e==null)continue;
        reps+=Math.max(0,e.optInt("reps",0));
        if(!e.optString("name","").isEmpty())f.names.add(e.optString("name"));
      }
      f.reps+=reps;String week=weekStart(date);
      weekly.put(week,weekly.containsKey(week)?weekly.get(week)+reps:reps);
      if(week.equals(thisWeek))f.weeklyWorkouts++;
      if(week.equals(lastWeek))f.previousWeeklyWorkouts++;
    }
    JSONArray ledger=fitnessLedger();
    for(int i=0;i<ledger.length();i++) {
      JSONObject e=ledger.optJSONObject(i);if(e==null)continue;
      String date=e.optString("date","");if(date.isEmpty())continue;
      f.workouts++;f.dates.add(date);
      String name=e.optString("name","");if(!name.isEmpty())f.names.add(name);
      int reps=Math.max(0,e.optInt("reps",100));f.reps+=reps;
      String week=weekStart(date);
      weekly.put(week,weekly.containsKey(week)?weekly.get(week)+reps:reps);
      if(week.equals(thisWeek))f.weeklyWorkouts++;
      if(week.equals(lastWeek))f.previousWeeklyWorkouts++;
    }
    f.days=f.dates.size();f.distinctExercises=f.names.size();
    f.weeklyReps=weekly.containsKey(thisWeek)?weekly.get(thisWeek):0;
    f.previousWeeklyReps=weekly.containsKey(lastWeek)?weekly.get(lastWeek):0;
    for(Integer count:weekly.values())f.bestWeeklyReps=Math.max(f.bestWeeklyReps,count);
    return f;
  }

  // V35: All badges are derived from V34's completed workout and Daily100 ledgers.
  // Earned badges have their own permanent unlock dates, independent of the theme toggle.
  static final String[] V35_CATEGORIES={"Strength","Endurance","Consistency","Volume","Workout Dedication","Exercise Variety","Personal Best","Challenge Champion","Calorie Milestones","Daily100 Mastery"};
  static final String[] V35_IDS={"strength","endurance","consistency","volume","dedication","variety","personalbest","champion","calories","daily100"};
  static final String[] V35_TIERS={"Bronze","Silver","Gold","Platinum"};
  static final int[][] V35_TARGETS={
    {500,2500,10000,25000}, // resistance equipment repetitions
    {500,2500,10000,25000}, // bodyweight repetitions
    {3,7,30,100},            // distinct training dates
    {1000,5000,20000,50000},// total repetitions
    {5,25,100,250},          // completed workouts
    {5,10,20,40},            // distinct exercises
    {1,3,6,12},              // new weekly repetition records
    {1,4,12,25},             // calendar weeks with >=3 workouts
    {500,2500,10000,25000}, // estimated calories in saved workouts
    {5,20,50,100}           // Daily100 completions
  };
  static final String[] V35_DESCRIPTIONS={
    "Resistance-equipment repetitions","Bodyweight exercise repetitions","Different training days",
    "All completed repetitions","Completed workouts and Daily100 exercises","Different completed exercises",
    "Weekly repetition personal records","Weeks with at least three completed sessions",
    "Estimated calories in saved workouts","Completed Daily100 exercises"
  };
  int[] v35Totals() {
    FitnessStats f=fitnessStats();int[] v=new int[10];
    v[2]=f.days;v[3]=f.reps;v[4]=f.workouts;v[5]=f.distinctExercises;
    HashMap<String,Integer> weeks=new HashMap<>(),weekSessions=new HashMap<>();
    JSONArray records=loadRecords();
    for(int i=0;i<records.length();i++){
      JSONObject r=records.optJSONObject(i);if(r==null)continue;
      String date=r.optString("date","");if(date.isEmpty())continue;
      String wk=weekStart(date);if(wk.isEmpty())continue;
      int reps=0;JSONArray ex=r.optJSONArray("exercises");
      if(ex!=null)for(int j=0;j<ex.length();j++){
        JSONObject e=ex.optJSONObject(j);if(e==null)continue;
        int n=Math.max(0,e.optInt("reps",0));reps+=n;
        String name=e.optString("name","");
        for(ExerciseData.Exercise def:ExerciseData.ALL)if(def.name.equalsIgnoreCase(name)){
          if("Bodyweight".equalsIgnoreCase(def.equipment))v[1]+=n;
          else v[0]+=n;
          break;
        }
      }
      v[8]+=(int)Math.max(0,Math.round(r.optDouble("calories",0)));
      weeks.put(wk,weeks.containsKey(wk)?weeks.get(wk)+reps:reps);
      weekSessions.put(wk,weekSessions.containsKey(wk)?weekSessions.get(wk)+1:1);
    }
    JSONArray ledger=fitnessLedger();
    HashSet<String> dailyIds=new HashSet<>();
    for(int i=0;i<ledger.length();i++){
      JSONObject e=ledger.optJSONObject(i);if(e==null)continue;
      String id=e.optString("id","");if(!dailyIds.add(id))continue;
      v[9]++;
      String date=e.optString("date","");String wk=weekStart(date);
      if(!wk.isEmpty()){
        int reps=Math.max(0,e.optInt("reps",100));
        weeks.put(wk,weeks.containsKey(wk)?weeks.get(wk)+reps:reps);
        weekSessions.put(wk,weekSessions.containsKey(wk)?weekSessions.get(wk)+1:1);
      }
      String name=e.optString("name","");
      int reps=Math.max(0,e.optInt("reps",100));
      for(ExerciseData.Exercise def:ExerciseData.ALL)if(def.name.equalsIgnoreCase(name)){
        if("Bodyweight".equalsIgnoreCase(def.equipment))v[1]+=reps;else v[0]+=reps;
        break;
      }
    }
    ArrayList<String> sortedWeeks=new ArrayList<>(weeks.keySet());
    java.util.Collections.sort(sortedWeeks);
    int record=0;
    for(String wk:sortedWeeks){
      int n=weeks.get(wk);
      if(n>record && n>0){v[6]++;record=n;}
      if(weekSessions.containsKey(wk)&&weekSessions.get(wk)>=3)v[7]++;
    }
    return v;
  }
  String v35UnlockKey(int category,int tier){return "v35StandardBadge_"+V35_IDS[category]+"_"+tier;}
  void v35RecordUnlocks(int[] totals){
    android.content.SharedPreferences.Editor edit=prefs.edit();boolean changed=false;
    for(int i=0;i<10;i++)for(int t=0;t<4;t++){
      String key=v35UnlockKey(i,t);
      if(totals[i]>=V35_TARGETS[i][t]&&!prefs.contains(key)){
        edit.putLong(key,System.currentTimeMillis());changed=true;
      }
    }
    if(changed)edit.apply();
  }
  void v35BadgeCard(LinearLayout body,int category,int tier,int value){
    int target=V35_TARGETS[category][tier];
    boolean unlocked=prefs.contains(v35UnlockKey(category,tier));
    LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);
    card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(8),dp(8),dp(8),dp(8));
    card.setBackground(shape(CARD,14));
    ImageView badge=new ImageView(this);
    int imageId=getResources().getIdentifier("v35_badge_"+V35_IDS[category]+"_"+tier,"drawable",getPackageName());
    if(imageId!=0)badge.setImageResource(imageId);
    badge.setScaleType(ImageView.ScaleType.FIT_CENTER);badge.setAlpha(unlocked?1f:0.27f);
    badge.setContentDescription(V35_CATEGORIES[category]+" "+V35_TIERS[tier]+(unlocked?" unlocked":" locked"));
    card.addView(badge,new LinearLayout.LayoutParams(dp(112),dp(86)));
    LinearLayout info=vbox();info.setPadding(dp(8),0,0,0);
    info.addView(bold(V35_TIERS[tier]+(unlocked?"  ✓":"  •  Locked"),14));
    info.addView(text(V35_DESCRIPTIONS[category],11,MUTED));
    info.addView(text(Math.min(value,target)+" / "+target,12,unlocked?GREEN:BLUE));
    if(unlocked){long when=prefs.getLong(v35UnlockKey(category,tier),0);
      info.addView(text("Earned: "+new java.text.SimpleDateFormat("dd MMM yyyy",Locale.getDefault()).format(new Date(when)),11,GREEN));}
    ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    bar.setMax(Math.max(1,target));bar.setProgress(Math.min(value,target));
    info.addView(bar,margins(-1,dp(4),0,5,0,0));
    card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
    body.addView(card,margins(-1,-2,0,4,0,5));
  }
  void showTrophyCabinet(boolean push){
    LinearLayout page=pageShell("Trophy Cabinet"),body=contentColumn();
    int[] totals=v35Totals();v35RecordUnlocks(totals);
    int unlocked=0;for(int i=0;i<10;i++)for(int t=0;t<4;t++)if(prefs.contains(v35UnlockKey(i,t)))unlocked++;
    body.addView(text("STANDARD ACHIEVEMENT COLLECTION",17,BLUE));
    body.addView(bold(unlocked+" / 40 BADGES COLLECTED",18));
    body.addView(text("Your earned badges are kept when switching to Centurian Achievement. Locked badges show progress from completed training history.",12,MUTED));
    for(int i=0;i<10;i++){
      body.addView(sectionTitle(V35_CATEGORIES[i]));
      for(int t=0;t<4;t++)v35BadgeCard(body,i,t,totals[i]);
    }
    page.addView(scroll(body),new LinearLayout.LayoutParams(-1,0,1));
    display("trophies",page,push);
  }

  void fitnessBadge(LinearLayout body,String glyph,String title,String description,int value,int target,int tier) {
    LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);
    card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(12),dp(12),dp(12),dp(12));
    card.setBackground(shape(CARD,18));
    int[] tierColors={Color.rgb(173,112,70),Color.rgb(177,193,214),Color.rgb(236,184,64),Color.rgb(70,175,255)};
    int color=tierColors[Math.max(0,Math.min(3,tier))];
    TextView emblem=text(glyph,27,value>=target?Color.WHITE:MUTED);
    emblem.setGravity(Gravity.CENTER);emblem.setTypeface(null,android.graphics.Typeface.BOLD);
    GradientDrawable ring=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
        value>=target?new int[]{color,Color.rgb(5,65,141),color}:new int[]{Color.rgb(39,52,70),Color.rgb(13,28,46)});
    ring.setCornerRadius(dp(40));ring.setStroke(dp(2),value>=target?color:Color.rgb(62,81,107));
    emblem.setBackground(ring);
    card.addView(emblem,new LinearLayout.LayoutParams(dp(58),dp(58)));
    LinearLayout info=vbox();info.setPadding(dp(12),0,0,0);
    info.addView(bold(title,16));info.addView(text(description,12,MUTED));
    info.addView(text(value>=target?"UNLOCKED":Math.min(value,target)+" / "+target,13,value>=target?GREEN:BLUE));
    ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    bar.setMax(Math.max(1,target));bar.setProgress(Math.min(value,target));
    bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
    info.addView(bar,margins(-1,dp(5),0,6,0,0));
    card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
    body.addView(card,margins(-1,-2,0,5,0,9));
  }

  void showChallenges(boolean push) {
    LinearLayout page=pageShell("Challenges"),body=contentColumn();
    FitnessStats f=fitnessStats();
    body.addView(text("PERSONALISED FITNESS CHALLENGES",16,BLUE));
    body.addView(text("Targets adapt to your verified workout history. Only completed workouts count; challenges reset every Monday.",13,MUTED));
    String[] difficulties={"Easy","Balanced","Ambitious"};
    int difficulty=Math.max(0,Math.min(2,prefs.getInt("standardChallengeDifficulty",1)));
    body.addView(sectionTitle("Challenge difficulty"));
    LinearLayout selector=new LinearLayout(this);selector.setOrientation(LinearLayout.HORIZONTAL);
    for(int i=0;i<3;i++) {
      final int choice=i;
      Button b=button(difficulties[i],i==difficulty?darken(BLUE,35):CARD);
      b.setTextSize(12);
      selector.addView(b,new LinearLayout.LayoutParams(0,dp(49),1));
      b.setOnClickListener(v->{prefs.edit().putInt("standardChallengeDifficulty",choice).apply();showChallenges(false);});
    }
    body.addView(selector,margins(-1,-2,0,5,0,12));
    double multiplier=difficulty==0?0.85:difficulty==2?1.25:1.05;
    int workoutTarget=Math.max(1,(int)Math.ceil(Math.max(1,f.previousWeeklyWorkouts)*multiplier));
    int repTarget=Math.max(100,(int)(Math.ceil(Math.max(100,f.previousWeeklyReps)*multiplier/100.0)*100));
    // Targets are frozen at the start of a week so they cannot move mid-challenge.
    String key="standardTargets_"+weekStart(dailyKey())+"_"+difficulty;
    String[] saved=prefs.getString(key,"").split(",");
    if(saved.length==2)try {workoutTarget=Integer.parseInt(saved[0]);repTarget=Integer.parseInt(saved[1]);}
    catch(Exception ignored){}
    else prefs.edit().putString(key,workoutTarget+","+repTarget).apply();
    fitnessBadge(body,"✓","Weekly consistency","Complete your personalised workout target",f.weeklyWorkouts,workoutTarget,1);
    fitnessBadge(body,"+","Weekly volume","Complete your personalised repetition target",f.weeklyReps,repTarget,2);
    fitnessBadge(body,"★","Personal best","Beat last week's completed repetition total",f.weeklyReps,Math.max(100,f.previousWeeklyReps+1),3);
    body.addView(sectionTitle("Classic Daily 100 challenges"));
    challengeProgress(body,"Daily Double","Complete two Daily 100 exercises today.",completedDailyOn(dailyKey()),2);
    challengeProgress(body,"Daily Triple","Complete three Daily 100 exercises today.",completedDailyOn(dailyKey()),3);
    Button daily=button("Open Daily 100",darken(BLUE,55));
    daily.setOnClickListener(v->showDaily100(true));
    body.addView(daily,margins(-1,dp(52),0,6,0,12));
    page.addView(scroll(body),new LinearLayout.LayoutParams(-1,0,1));display("challenges",page,push);
  }

  void showAchievements(boolean push) { showTrophyCabinet(push); }

  // ---------- Sets and reps ----------
  void showSetsManager(boolean push) {
    LinearLayout page = pageShell("Sets & Reps");
    LinearLayout body = contentColumn();

    TextView intro = text("Choose the parameters of how the 100 reps are randomly distributed across each exercise, or select one of the preset options below. Press Save before creating your routine.", 15, MUTED);
    intro.setGravity(Gravity.CENTER);
    body.addView(intro);

    String[] presetNames={"Centurion Set","10 x 10 GVT","4 x 25 Quatro","5 x 20 Venti Set","8 x Random 20","6 x Random 30","Set my own parameters"};
    int[][] presetValues={{1,100,100},{10,10,10},{4,25,25},{5,20,20},{8,1,20},{6,1,30},{1,1,1}};
    LinearLayout presets=vbox();

    for(int row=0;row<3;row++){
      LinearLayout presetRow=new LinearLayout(this);
      presetRow.setOrientation(LinearLayout.HORIZONTAL);

      for(int col=0;col<2;col++){
        final int pi=row*2+col;
        Button pb=button(presetNames[pi],BLUE);
                setChipSelected(pb, selectedSets==presetValues[pi][0] && minReps==presetValues[pi][1] && maxReps==presetValues[pi][2]);
        pb.setAllCaps(false);
        pb.setTextSize(12);
        pb.setOnClickListener(v->{
          selectedSets=presetValues[pi][0];
          minReps=presetValues[pi][1];
          maxReps=presetValues[pi][2];
          showSetsManager(false);
        });

        LinearLayout.LayoutParams pp=
            new LinearLayout.LayoutParams(0,dp(54),1);
        pp.setMargins(
            col==0 ? 0 : dp(4),
            dp(4),
            col==0 ? dp(4) : 0,
            dp(4));
        presetRow.addView(pb,pp);
      }

      presets.addView(
          presetRow,
          new LinearLayout.LayoutParams(-1,dp(62)));
    }

    final int customIndex=6;
    Button custom=button(presetNames[customIndex],BLUE);
        setChipSelected(custom, selectedSets==presetValues[customIndex][0] && minReps==presetValues[customIndex][1] && maxReps==presetValues[customIndex][2]);
    custom.setAllCaps(false);
    custom.setOnClickListener(v->{
      selectedSets=presetValues[customIndex][0];
      minReps=presetValues[customIndex][1];
      maxReps=presetValues[customIndex][2];
      showSetsManager(false);
    });
    presets.addView(custom,margins(-1,dp(48),0,4,0,4));

    body.addView(presets,margins(-1,-2,0,8,0,10));

    final TextView summary = text("", 15, TEXT);
    summary.setPadding(dp(18), dp(16), dp(18), dp(16));
    summary.setBackground(shape(GREEN_DARK, 20));

    addSeekCard(body, "Number of sets", selectedSets, 1, 10, value -> {
      selectedSets = value;
      updateRepSummary(summary);
    });
    addSeekCard(body, "Minimum reps per set", minReps, 1, 50, value -> {
      minReps = value;
      if (minReps > maxReps) maxReps = minReps;
      updateRepSummary(summary);
    });
    addSeekCard(body, "Maximum reps per set", maxReps, 1, 100, value -> {
      maxReps = value;
      if (maxReps < minReps) minReps = maxReps;
      updateRepSummary(summary);
    });

    updateRepSummary(summary);
    body.addView(summary, margins(-1, -2, 0, 10, 0, 16));

    Button save = button("Save", darken(BLUE,55));
    save.setTextColor(TEXT);
    save.setOnClickListener(v -> {
      if (!validRepRange()) {
        Toast.makeText(this,
            "Those settings cannot make exactly 100 reps. Adjust the set or rep limits.",
            Toast.LENGTH_LONG).show();
        return;
      }
      prefs.edit()
          .putInt("sets", selectedSets)
          .putInt("minReps", minReps)
          .putInt("maxReps", maxReps)
          .apply();
      goBack();
    });
    body.addView(save, margins(-1, dp(54), 0, 8, 0, 12));

    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
    display("sets", page, push);
  }

  interface IntSetter { void set(int value); }

  void addSeekCard(LinearLayout body, String label, int value, int min, int max, IntSetter setter) {
    LinearLayout card = vbox();
    card.setPadding(dp(18), dp(14), dp(18), dp(14));
    card.setBackground(shape(CARD_ALT, 22));

    LinearLayout top = new LinearLayout(this);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    TextView lab = bold(label, 17);
    TextView val = bold(String.valueOf(value), 18);
    val.setTextColor(GREEN);
    val.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    top.addView(lab, new LinearLayout.LayoutParams(0, dp(44), 1));
    top.addView(val, new LinearLayout.LayoutParams(dp(70), dp(44)));
    card.addView(top);

    SeekBar seek = new SeekBar(this);
    seek.setMax(max - min);
    seek.setProgress(value - min);
    seek.setPadding(dp(8), 0, dp(8), 0);
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onStartTrackingTouch(SeekBar s) {}
      public void onStopTrackingTouch(SeekBar s) {}
      public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
        int v = min + progress;
        val.setText(String.valueOf(v));
        setter.set(v);
      }
    });
    LinearLayout seekRow=new LinearLayout(this); seekRow.setOrientation(LinearLayout.HORIZONTAL); seekRow.setGravity(Gravity.CENTER_VERTICAL);
    Button dec=button("▼",CARD); Button inc=button("▲",CARD); dec.setContentDescription("Decrease "+label); inc.setContentDescription("Increase "+label);
    dec.setOnClickListener(v->{ if(seek.getProgress()>0) seek.setProgress(seek.getProgress()-1); });
    inc.setOnClickListener(v->{ if(seek.getProgress()<seek.getMax()) seek.setProgress(seek.getProgress()+1); });
    seekRow.addView(dec,new LinearLayout.LayoutParams(dp(48),dp(48))); seekRow.addView(seek,new LinearLayout.LayoutParams(0,dp(54),1)); seekRow.addView(inc,new LinearLayout.LayoutParams(dp(48),dp(48)));
    card.addView(seekRow);
    body.addView(card, margins(-1, -2, 0, 10, 0, 10));
  }

  void updateRepSummary(TextView t) {
    String feasibility = validRepRange() ? "" : "\n⚠ Adjust these limits so 100 reps are possible.";
    t.setText("Each exercise will total exactly 100 reps, spread across "
        + selectedSets + " sets, with each set between " + minReps + " and "
        + maxReps + " reps." + feasibility);
  }

  boolean validRepRange() {
    return selectedSets >= 1 && selectedSets <= 10
        && minReps <= maxReps
        && selectedSets * minReps <= 100
        && selectedSets * maxReps >= 100;
  }

  ArrayList<Integer> randomSplit(int sets, int min, int max) {
    ArrayList<Integer> split = new ArrayList<>();
    int remaining = 100;
    Random r = new Random();
    for (int i = 0; i < sets; i++) {
      int left = sets - i - 1;
      int low = Math.max(min, remaining - left * max);
      int high = Math.min(max, remaining - left * min);
      int v = low + (high > low ? r.nextInt(high - low + 1) : 0);
      split.add(v);
      remaining -= v;
    }
    return split;
  }

  // ---------- Create routine ----------
  void showRoutine(boolean push) {
    showRoutine(push, false);
  }

  void showRoutine(boolean push, boolean refreshInPlace) {
    ScrollView existingScroll = refreshInPlace ? routineScrollView : null;
    LinearLayout page = pageShell("Create Routine");
    LinearLayout body = contentColumn();

    EditText name = field("Routine name", prefs.getString("draftRoutineName", ""),
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    body.addView(name);

    LinearLayout setCard = new LinearLayout(this);
    setCard.setOrientation(LinearLayout.HORIZONTAL);
    setCard.setGravity(Gravity.CENTER_VERTICAL);
    setCard.setPadding(dp(14), dp(8), dp(10), dp(8));
    setCard.setBackground(shape(CARD, 18));
    TextView setSummary = text(selectedSets + " sets  •  " + minReps + "–" + maxReps
        + " reps per set  •  100 total", 14, TEXT);
    setCard.addView(setSummary, new LinearLayout.LayoutParams(0, dp(52), 1));
    Button editSets = button("Edit", GREEN_DARK);
    editSets.setOnClickListener(v -> {
      prefs.edit().putString("draftRoutineName", name.getText().toString()).apply();
      showSetsManager(true);
    });
    setCard.addView(editSets, new LinearLayout.LayoutParams(dp(82), dp(46)));
    body.addView(setCard, margins(-1, -2, 0, 8, 0, 12));

    Button add = button("＋  Add Exercise", darken(BLUE,55));
    add.setTextColor(TEXT);
    add.setOnClickListener(v -> {
      prefs.edit().putString("draftRoutineName", name.getText().toString()).apply();
      selectingForDaily100 = false;
      selectingForRoutine = true;
      showLibrary(true);
    });
    body.addView(add, margins(-1, dp(52), 0, 6, 0, 12));

    body.addView(sectionTitle("Exercises in this routine (" + routine.size() + ")"));
    body.addView(text("Hold ☰ to reorder • Swipe right on an exercise to delete it",12,MUTED));
    TextView potential=text(draftPotentialEstimate(),14,GREEN);
    potential.setPadding(dp(12),dp(12),dp(12),dp(12));
    potential.setBackground(shape(CARD,14));
    body.addView(potential,margins(-1,-2,0,10,0,12));

    if (routine.isEmpty()) {
      TextView empty = text("No exercises added yet. Tap “Add Exercise” to build your routine.",
          15, TEXT);
      empty.setPadding(dp(18), dp(16), dp(18), dp(16));
      empty.setBackground(shape(CARD_ALT, 18));
      body.addView(empty, margins(-1, -2, 0, 4, 0, 14));
    } else {
      for (int i = 0; i < routine.size(); i++) {
        addRoutineExerciseCard(body, routine.get(i), i);
      }
    }

    Button save = button("Save Routine", darken(BLUE,55));
    save.setTextColor(TEXT);
    save.setOnClickListener(v -> {
      prefs.edit().putString("draftRoutineName", name.getText().toString()).apply();
      if (routine.isEmpty()) {
        Toast.makeText(this, "Add at least one exercise first.", Toast.LENGTH_SHORT).show();
        return;
      }
      saveRoutineDraft();
      Toast.makeText(this, "Routine saved.", Toast.LENGTH_SHORT).show();
      showRoutineList(false);
    });
    body.addView(save, margins(-1, dp(54), 0, 18, 0, 8));

    if (!routine.isEmpty()) {
      Button start = button("Start Workout", darken(BLUE,55));
      start.setOnClickListener(v -> {
        prefs.edit().putString("draftRoutineName", name.getText().toString()).apply();
        if (!validRepRange()) {
          Toast.makeText(this, "Fix Sets & Reps settings before starting.", Toast.LENGTH_LONG).show();
          return;
        }
        prepareWorkout();
        showPreWorkout(true);
      });
      body.addView(start, margins(-1, dp(54), 0, 0, 0, 10));
    }

    if (existingScroll != null && "routine".equals(currentScreen)
        && existingScroll.getParent() != null) {
      // Preserve the visible viewport rather than replacing the entire Activity window.
      int preservedY = existingScroll.getScrollY();
      // Keep the same ScrollView and block child focus from moving the viewport.
      // The content replacement can temporarily clamp scrollY to zero during layout;
      // restore it BEFORE the first frame is drawn, not in a delayed post().
      final int oldFocusability = existingScroll.getDescendantFocusability();
      existingScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
      existingScroll.removeAllViews();
      existingScroll.addView(body);
      routineScrollView = existingScroll;
      existingScroll.scrollTo(0, preservedY);
      final ScrollView retainedScroll = existingScroll;
      retainedScroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw() {
          if (retainedScroll.getViewTreeObserver().isAlive())
            retainedScroll.getViewTreeObserver().removeOnPreDrawListener(this);
          int maxY = Math.max(0, body.getHeight() - retainedScroll.getHeight());
          retainedScroll.scrollTo(0, Math.min(preservedY, maxY));
          retainedScroll.setDescendantFocusability(oldFocusability);
          return true;
        }
      });
    } else {
      routineScrollView = scroll(body);
      attachKeyboardAwareRoutineScroll(routineScrollView);
      page.addView(routineScrollView, new LinearLayout.LayoutParams(-1, 0, 1));
      display("routine", page, push);
    }
  }

  void addRoutineExerciseCard(LinearLayout body, ExerciseData.Exercise e, final int occurrenceIndex) {
    LinearLayout card = vbox();
    card.setPadding(dp(14), dp(10), dp(14), dp(10));
    card.setBackground(shape(CARD, 18));
    final int liftedCardColor = android.graphics.Color.rgb(
        Math.min(255, android.graphics.Color.red(CARD)+18),
        Math.min(255, android.graphics.Color.green(CARD)+18),
        Math.min(255, android.graphics.Color.blue(CARD)+18));

    LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
    TextView drag=bold("☰",24);
    drag.setTextColor(MUTED);
    drag.setGravity(Gravity.CENTER);
    drag.setContentDescription("Hold and drag to reorder exercise");
    top.addView(drag,new LinearLayout.LayoutParams(dp(44),dp(64)));

    LinearLayout texts = vbox(); TextView exerciseName=bold(e.name,17); exerciseName.setSingleLine(false); exerciseName.setMaxLines(2); texts.addView(exerciseName); texts.addView(text(e.muscle+"  •  "+e.movement+"  •  "+e.equipment,12,MUTED));
    top.addView(texts,new LinearLayout.LayoutParams(0,dp(78),1));
    card.addView(top);
    card.setElevation(dp(5));
    SwipeDeleteLayout swipeWrap = new SwipeDeleteLayout(this);
    final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
    LinearLayout weightRow=new LinearLayout(this);weightRow.setOrientation(LinearLayout.HORIZONTAL);weightRow.setGravity(Gravity.CENTER_VERTICAL);
    String loadLabel=isBodyweightExercise(e)?"Estimated load":
        (e.equipment.toLowerCase(Locale.US).contains("dumbbell")?"Weight per dumbbell":
        (e.equipment.toLowerCase(Locale.US).contains("kettlebell")?"Kettlebell weight":"Weight used"));
    weightRow.addView(text(loadLabel,13,MUTED),new LinearLayout.LayoutParams(0,dp(48),1));

    if (isBodyweightExercise(e)) {
      double kg=effectiveBodyweightKg(e); String unit=prefs.getString("profileWeightUnit","kg"); double shown="lb".equals(unit)?kg/0.45359237:kg;
      setOccurrenceWeight(occurrenceIndex,shown);
      setOccurrenceUnit(occurrenceIndex,unit);
      TextView auto=bold(formatNumber(shown)+" "+unit,15); auto.setTextColor(GREEN); auto.setGravity(Gravity.CENTER); auto.setBackground(shape(BG,12));
      weightRow.addView(auto,new LinearLayout.LayoutParams(dp(106),dp(44)));
      TextView tag=text("auto",11,MUTED);tag.setGravity(Gravity.CENTER);weightRow.addView(tag,new LinearLayout.LayoutParams(dp(48),dp(44)));
    } else {
      EditText w=new EditText(this);w.setText(occurrenceWeightValue(occurrenceIndex));w.setHint("0");w.setTextColor(TEXT);w.setHintTextColor(MUTED);w.setTextSize(15);w.setGravity(Gravity.CENTER);w.setSingleLine();
      w.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);w.setBackground(shape(BG,12));
      w.setOnFocusChangeListener((view,hasFocus)->{
        if(hasFocus && routineScrollView!=null){
          view.postDelayed(()->scrollRoutineFieldAboveKeyboard(view),220);
        }
      });
      w.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
      w.setOnEditorActionListener((view,action,event)->{
        if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){
          hideKeyboard(view);
          view.clearFocus();
          if(routineScrollView!=null) routineScrollView.postDelayed(()->routineScrollView.smoothScrollTo(0,routineScrollBeforeKeyboard),180);
          return true;
        }
        return false;
      });
      w.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){setOccurrenceWeight(occurrenceIndex,parseDouble(s.toString(),0));}public void afterTextChanged(Editable s){}});
      weightRow.addView(w,new LinearLayout.LayoutParams(dp(76),dp(44)));
      Spinner units=new Spinner(this);String[] unitItems={"kg","lb"};units.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,unitItems));
      String savedUnit=occurrenceUnit(occurrenceIndex);
      units.setSelection("lb".equals(savedUnit)?1:0);
      units.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View v,int pos,long id){setOccurrenceUnit(occurrenceIndex,unitItems[pos]);}});
      weightRow.addView(units,new LinearLayout.LayoutParams(dp(76),dp(46)));
    }
    card.addView(weightRow);
    TextView deleteBg = bold("🗑",24);
    deleteBg.setGravity(Gravity.CENTER_VERTICAL);
    deleteBg.setPadding(dp(22),0,0,0);
    deleteBg.setTextColor(0xFFE8E8E8);
    deleteBg.setBackground(shape(0xFF641E24,18));
    swipeWrap.addView(deleteBg,new FrameLayout.LayoutParams(-1,-1));
    swipeWrap.addView(card,new FrameLayout.LayoutParams(-1,-2));
    swipeWrap.setTag("routine-reorder-card");
    body.addView(swipeWrap,margins(-1,-2,0,6,0,8));

    // Horizontal swipe/delete is owned by the outer wrapper.
        swipeWrap.setOnTouchListener((v,ev) -> {
            switch(ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getX() - swipeWrap.downX;
                    if(dx > 0) {
                        float swipeX = Math.min(dx, card.getWidth());
                        card.setTranslationX(swipeX);

                        // Make the delete state visibly "armed" at the same
                        // 50% threshold used when the finger is released.
                        boolean deleteArmed = swipeX > card.getWidth() * .50f;
                        deleteBg.setBackground(shape(
                            deleteArmed ? 0xFFD9364A : 0xFF641E24, 18));
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if(card.getTranslationX() > card.getWidth() * .50f) {
                        ensureOccurrenceStorage();
                        deletionHaptic(swipeWrap);
                        routine.remove(occurrenceIndex);
                        routineOccurrenceWeights.remove(occurrenceIndex);
                        routineOccurrenceUnits.remove(occurrenceIndex);
                        showRoutine(false, true);
                    } else {
                        deleteBg.setBackground(shape(0xFF641E24,18));
                        card.animate().translationX(0).setDuration(150).start();
                    }
                    swipeWrap.interceptingSwipe = false;
                    if(routineScrollView != null)
                        routineScrollView.requestDisallowInterceptTouchEvent(false);
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    deleteBg.setBackground(shape(0xFF641E24,18));
                    card.animate().translationX(0).setDuration(150).start();
                    swipeWrap.interceptingSwipe = false;
                    if(routineScrollView != null)
                        routineScrollView.requestDisallowInterceptTouchEvent(false);
                    return true;
            }
            return true;
        });

        // A single raised REAL card; no Android drag shadow or duplicate image.
    drag.setOnTouchListener(new View.OnTouchListener() {
      final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
      Runnable hold;
      boolean dragging=false, cancelledHold=false;
      float downRawX, downRawY;
      int targetIndex=occurrenceIndex;
      final float[] lastFingerY={0};
      float dragOffsetY=0;
      // Keep the raised card inside the exercise section and visible scroll viewport.
      void positionDraggedCard() {
        if(routineScrollView==null) return;
        int[] scrollLoc=new int[2]; routineScrollView.getLocationOnScreen(scrollLoc);
        float minTop=scrollLoc[1];
        float maxBottom=scrollLoc[1]+routineScrollView.getHeight();
        for(int j=0;j<body.getChildCount();j++) {
          View child=body.getChildAt(j);
          if(!"routine-reorder-card".equals(child.getTag())) continue;
          int[] childLoc=new int[2]; child.getLocationOnScreen(childLoc);
          float originalTop=childLoc[1]-child.getTranslationY();
          minTop=Math.max(minTop, j==firstExerciseCardIndex() ? originalTop : minTop);
          if(j==lastExerciseCardIndex()) maxBottom=Math.min(maxBottom,originalTop+child.getHeight());
        }
        int[] ownLoc=new int[2]; swipeWrap.getLocationOnScreen(ownLoc);
        float ownOriginalTop=ownLoc[1]-swipeWrap.getTranslationY();
        float desiredTop=ownOriginalTop+(lastFingerY[0]-downRawY)+dragOffsetY;
        float upper=Math.max(minTop,maxBottom-swipeWrap.getHeight());
        float boundedTop=Math.max(minTop,Math.min(upper,desiredTop));
        swipeWrap.setTranslationY(boundedTop-ownOriginalTop);
      }
      int firstExerciseCardIndex() {
        for(int j=0;j<body.getChildCount();j++) if("routine-reorder-card".equals(body.getChildAt(j).getTag())) return j;
        return -1;
      }
      int lastExerciseCardIndex() {
        for(int j=body.getChildCount()-1;j>=0;j--) if("routine-reorder-card".equals(body.getChildAt(j).getTag())) return j;
        return -1;
      }
      final Runnable edgeScroll = new Runnable() {
        @Override public void run() {
          if(!dragging || routineScrollView==null) return;
          int[] loc=new int[2]; routineScrollView.getLocationOnScreen(loc);
          float fingerY=lastFingerY[0]-loc[1];
          int step=0;
          if(fingerY<dp(85)) step=-dp(13);
          else if(fingerY>routineScrollView.getHeight()-dp(85)) step=dp(13);
          if(step!=0) {
            int before=routineScrollView.getScrollY();
            routineScrollView.scrollBy(0,step);
            int actual=routineScrollView.getScrollY()-before;
            dragOffsetY+=actual;
            positionDraggedCard();
            updateTarget();
          }
          handler.postDelayed(this,25);
        }
      };
      void updateTarget() {
        int[] draggedLoc=new int[2]; swipeWrap.getLocationOnScreen(draggedLoc);
        float top=draggedLoc[1];
        float bottom=top+swipeWrap.getHeight();
        int newTarget=occurrenceIndex;
        for(int j=0;j<body.getChildCount();j++) {
          View other=body.getChildAt(j);
          if(other==swipeWrap || !"routine-reorder-card".equals(other.getTag())) continue;
          int[] loc=new int[2]; other.getLocationOnScreen(loc);
          float mid=loc[1]-other.getTranslationY()+other.getHeight()/2f;
          if(j>body.indexOfChild(swipeWrap) && bottom>mid) newTarget++;
          if(j<body.indexOfChild(swipeWrap) && top<mid) newTarget--;
        }
        int previousTarget=targetIndex;
        targetIndex=Math.max(0,Math.min(routine.size()-1,newTarget));
        if(targetIndex==previousTarget) return;
        int count=0;
        for(int j=0;j<body.getChildCount();j++) {
          View other=body.getChildAt(j);
          if(!"routine-reorder-card".equals(other.getTag())) continue;
          if(other==swipeWrap) {count++;continue;}
          float shift=0;
          int itemIndex=count++;
          if(targetIndex>occurrenceIndex && itemIndex>occurrenceIndex && itemIndex<=targetIndex)
            shift=-swipeWrap.getHeight();
          if(targetIndex<occurrenceIndex && itemIndex>=targetIndex && itemIndex<occurrenceIndex)
            shift=swipeWrap.getHeight();
          other.animate().cancel();
          other.animate().translationY(shift).setDuration(100).start();
        }
      }
      @Override public boolean onTouch(View v,MotionEvent ev) {
        switch(ev.getActionMasked()) {
          case MotionEvent.ACTION_DOWN:
                    downRawX=ev.getRawX();
            downRawY=lastFingerY[0]=ev.getRawY();
            dragging=false; swipeWrap.reordering=false;cancelledHold=false;targetIndex=occurrenceIndex;dragOffsetY=0;
            hold=()->{
              if(cancelledHold) return;
              dragging=true; swipeWrap.reordering=true;
              swipeWrap.setTranslationZ(dp(20));
              card.setElevation(dp(18));
              card.setBackground(shape(liftedCardColor,18));
              card.animate().scaleX(1.025f).scaleY(1.025f).setDuration(120).start();
              v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
              if(routineScrollView!=null) routineScrollView.requestDisallowInterceptTouchEvent(true);
              handler.post(edgeScroll);
            };
            handler.postDelayed(hold,ViewConfiguration.getLongPressTimeout());
            return true;
          case MotionEvent.ACTION_MOVE:
            lastFingerY[0]=ev.getRawY();
            if(!dragging && (Math.abs(ev.getRawY()-downRawY)>slop || Math.abs(ev.getRawX()-downRawX)>slop)) {
              cancelledHold=true;handler.removeCallbacks(hold);
            }
            if(dragging) {
              positionDraggedCard();
              updateTarget();
            }
            return true;
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL:
                    swipeWrap.requestDisallowInterceptTouchEvent(false);
            cancelledHold=true;
            if(hold!=null) handler.removeCallbacks(hold);
            handler.removeCallbacks(edgeScroll);
            if(dragging) {
              if(ev.getActionMasked()==MotionEvent.ACTION_UP && targetIndex!=occurrenceIndex) {
                ensureOccurrenceStorage();
                ExerciseData.Exercise moved=routine.remove(occurrenceIndex);
                double weight=routineOccurrenceWeights.remove(occurrenceIndex);
                String unit=routineOccurrenceUnits.remove(occurrenceIndex);
                routine.add(targetIndex,moved);
                routineOccurrenceWeights.add(targetIndex,weight);
                routineOccurrenceUnits.add(targetIndex,unit);
              }
              card.setBackground(shape(CARD,18));
              dragging=false; swipeWrap.reordering=false;
                    swipeWrap.setTranslationY(0); swipeWrap.setTranslationZ(0); card.setElevation(0); card.setScaleX(1f); card.setScaleY(1f);
              showRoutine(false, true);
            }
            return true;
        }
        return true;
      }
    });
  }

  void ensureOccurrenceStorage() {
    while(routineOccurrenceWeights.size() < routine.size())
      routineOccurrenceWeights.add(0.0);
    while(routineOccurrenceUnits.size() < routine.size())
      routineOccurrenceUnits.add(prefs.getString("profileWeightUnit","kg"));

    while(routineOccurrenceWeights.size() > routine.size())
      routineOccurrenceWeights.remove(routineOccurrenceWeights.size()-1);
    while(routineOccurrenceUnits.size() > routine.size())
      routineOccurrenceUnits.remove(routineOccurrenceUnits.size()-1);
  }

  double occurrenceWeight(int index) {
    ensureOccurrenceStorage();
    if(index<0 || index>=routineOccurrenceWeights.size()) return 0;
    return routineOccurrenceWeights.get(index);
  }

  String occurrenceUnit(int index) {
    ensureOccurrenceStorage();
    if(index<0 || index>=routineOccurrenceUnits.size())
      return prefs.getString("profileWeightUnit","kg");
    return routineOccurrenceUnits.get(index);
  }

  void setOccurrenceWeight(int index,double value) {
    ensureOccurrenceStorage();
    if(index>=0 && index<routineOccurrenceWeights.size())
      routineOccurrenceWeights.set(index,value);
  }

  void setOccurrenceUnit(int index,String value) {
    ensureOccurrenceStorage();
    if(index>=0 && index<routineOccurrenceUnits.size())
      routineOccurrenceUnits.set(index,value);
  }

  String occurrenceWeightValue(int index) {
    double v=occurrenceWeight(index);
    if(Math.abs(v-Math.rint(v))<0.0001)
      return String.valueOf((int)Math.rint(v));
    return String.format(Locale.US,"%.1f",v);
  }

  String weightValue(String name) {
    double v = routineWeights.containsKey(name) ? routineWeights.get(name) : 0;
    if (Math.abs(v - Math.rint(v)) < 0.0001) return String.valueOf((int)Math.rint(v));
    return String.format(Locale.US, "%.1f", v);
  }

  boolean isBodyweightExercise(ExerciseData.Exercise e) {
    return "Bodyweight".equalsIgnoreCase(e.equipment);
  }

  double bodyweightFactor(ExerciseData.Exercise e) {
    String n=e.name.toLowerCase(Locale.US);
    // Approximate effective moving load, not the total weight supported by the body.
    // These are movement-specific modelling assumptions, not measured biomechanical constants.
    if (n.equals("back extension")) return 0.30;
    if (n.equals("bodyweight kickbacks") || n.equals("donkey kicks")) return 0.16;
    if (n.equals("bodyweight lunge") || n.equals("bodyweight reverse lunge")
        || n.equals("bodyweight side lunge")) return 0.55;
    if (n.equals("bodyweight squat") || n.equals("sumo squat")) return 0.65;
    if (n.equals("inverted row")) return 0.60; // feet pivot, roughly 30-45 degrees
    if (n.equals("lying leg raise") || n.equals("scissor kicks")) return 0.32;
    if (n.equals("single leg glute bridge")) return 0.48;
    if (n.equals("glute bridge")) return 0.40;
    if (n.equals("tricep dips on a bench") || n.equals("triceps dips on a bench")
        || n.equals("bench dips")) return 0.55;
    if (n.contains("incline push")) return 0.55;
    if (n.contains("decline push")) return 0.80;
    if (n.contains("push-up") || n.contains("push up")) return 0.72;
    if (n.contains("pull-up") || n.contains("chin-up") || n.contains("pull up") || n.contains("chin up")) return 0.95;
    if (n.contains("dip")) return 0.90;
    if (n.contains("glute bridge") || n.contains("hip thrust")) return 0.70;
    if (n.contains("mountain climber") || n.contains("bear crawl")) return 0.65;
    if (n.contains("crunch") || n.contains("sit-up") || n.contains("dead bug") || n.contains("bird dog") || n.contains("russian twist")) return 0.45;
    // V21 movement-specific effective bodyweight estimates.
    if (n.equals("ab wheel rollout")) return 0.65;
    if (n.equals("hanging knee raise")) return 0.25;
    if (n.equals("hanging leg raise")) return 0.30;
    if (n.equals("prone alternate back raise")) return 0.25;
    if (n.equals("superman")) return 0.40;
    if (n.equals("standing hip adduction")) return 0.16;
    if (n.equals("standing hip kickback")) return 0.16;
    if (n.equals("standing single leg deadlift")) return 0.35;
    if (n.equals("standing hip abduction")) return 0.16;
    if (n.equals("standing knee raise")) return 0.16;
    if (n.contains("leg raise") || n.contains("knee tuck") || n.contains("v-up") || n.contains("scissor")) return 0.60;
    return 1.00;
  }

  double effectiveBodyweightKg(ExerciseData.Exercise e) {
    return profileWeightKg() * bodyweightFactor(e);
  }

  void attachKeyboardAwareRoutineScroll(ScrollView sc) {
    final Rect visible = new Rect();
    sc.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
      if(windowRoot==null || currentScreen==null || !"routine".equals(currentScreen)) return;
      windowRoot.getWindowVisibleDisplayFrame(visible);
      int rootH = windowRoot.getRootView().getHeight();
      int keyboardH = rootH - visible.bottom;
      boolean nowVisible = keyboardH > dp(180);
      if(nowVisible && !routineKeyboardVisible){
        routineKeyboardVisible = true;
        routineScrollBeforeKeyboard = sc.getScrollY();
        View focus = getCurrentFocus();
        if(focus!=null) focus.postDelayed(() -> scrollRoutineFieldAboveKeyboard(focus), 80);
      } else if(!nowVisible && routineKeyboardVisible){
        routineKeyboardVisible = false;
        sc.postDelayed(() -> sc.smoothScrollTo(0, routineScrollBeforeKeyboard), 80);
      }
    });
  }

  void scrollRoutineFieldAboveKeyboard(View field) {
    if(routineScrollView==null || field==null) return;
    int[] loc = new int[2];
    field.getLocationOnScreen(loc);
    Rect visible = new Rect();
    windowRoot.getWindowVisibleDisplayFrame(visible);
    int desiredBottom = visible.bottom - dp(28);
    int fieldBottom = loc[1] + field.getHeight();
    if(fieldBottom > desiredBottom){
      routineScrollView.smoothScrollBy(0, fieldBottom - desiredBottom + dp(24));
    } else {
      int desiredTop = visible.top + dp(110);
      if(loc[1] < desiredTop) routineScrollView.smoothScrollBy(0, loc[1]-desiredTop);
    }
  }

  // ---------- Exercise library ----------
  void showLibrary(boolean push) {
    LinearLayout page = pageShell((selectingForRoutine || selectingForDaily100) ? "Add Exercise" : "Exercise List");
    LinearLayout body = contentColumn();

    EditText search = field("Search", libraryQuery, InputType.TYPE_CLASS_TEXT);
    body.addView(search);

    String[] equipmentFilter = {"All", "Bodyweight", "Weightlifting", "Cardio", "Barbell", "Dumbbell", "Cable", "Machine", "Kettlebell"};
    final String[] equipment = {libraryEquipment};
    final String[] muscle = {libraryMuscle};

    HorizontalScrollView equipScroll = new HorizontalScrollView(this);
    equipScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout newEquip = new LinearLayout(this);
    newEquip.setOrientation(LinearLayout.HORIZONTAL);
    for (String f : equipmentFilter) {
      Button chip = smallChip(f, f.equals(equipment[0]));
      chip.setOnClickListener(v -> {
        equipment[0] = f; libraryEquipment = f;
        styleChipRow(newEquip, f);
        libraryScrollY = 0;
        refreshLibrary((LinearLayout)((ViewGroup)search.getParent()).getChildAt(4),
            (TextView)((ViewGroup)search.getParent()).getChildAt(3), search.getText().toString(), equipment[0], muscle[0]);
      });
      LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(Math.max(72, f.length()*9+28)), dp(36));
      ep.setMargins(dp(3),0,dp(3),0);
      newEquip.addView(chip, ep);
    }
    equipScroll.addView(newEquip);
    body.addView(equipScroll, margins(-1, dp(40), 0, 4, 0, 4));

    HorizontalScrollView bodyPartsScroll = new HorizontalScrollView(this);
    bodyPartsScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout bodyParts = new LinearLayout(this);
    bodyParts.setOrientation(LinearLayout.HORIZONTAL);
    String[] muscles = {"All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core"};
    bodyPartsScroll.addView(bodyParts);
    body.addView(bodyPartsScroll, margins(-1, dp(44), 0, 0, 0, 6));

    TextView count = text("", 14, MUTED);
    body.addView(count);
    LinearLayout list = vbox();
    body.addView(list);

    Runnable refresh = () -> {
      libraryQuery = search.getText().toString();
      refreshLibrary(list, count, libraryQuery, equipment[0], muscle[0]);
    };

    for (String f : muscles) {
      Button chip = smallChip(f, f.equals(muscle[0]));
      chip.setOnClickListener(v -> {
        muscle[0] = f; libraryMuscle = f;
        styleChipRow(bodyParts, f); libraryScrollY = 0; refresh.run();
      });
      LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(76), dp(38));
      cp.setMargins(dp(3), 0, dp(3), 0);
      bodyParts.addView(chip, cp);
    }

    search.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
      public void onTextChanged(CharSequence s, int st, int before, int count) { libraryScrollY = 0; refresh.run(); }
      public void afterTextChanged(Editable s) {}
    });

    refresh.run();
    ScrollView sc = scroll(body);
    sc.setOnScrollChangeListener((v,sx,sy,ox,oy) -> {
      libraryScrollY=sy;
      if(sy!=oy) playScrollClick(sy);
    });
    sc.post(() -> {
      sc.scrollTo(0,libraryScrollY);
      resetScrollClickState(libraryScrollY);
    });
    page.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    display("library", page, push);
  }

  LinearLayout chipRow(String[] values, String[] selected, Runnable onChange) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    for (String f : values) {
      Button b = smallChip(f, f.equals(selected[0]));
      row.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
    }
    return row;
  }

  // V23: V14-inspired deep navy inactive chips; retain V22 active blue.
  // V27: raised blue selection with a light-blue rim, matching Create Routine.
  GradientDrawable chipFace(boolean selected) {
    int base = selected ? BLUE : Color.rgb(25,53,80);
    GradientDrawable g = new GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        new int[]{lighten(base,34),base,darken(base,24)});
    g.setCornerRadius(dp(18));
    g.setStroke(dp(1),lighten(selected ? BLUE : CARD_ALT,65));
    return g;
  }

  void setChipSelected(Button b, boolean selected) {
    b.setTag(Boolean.valueOf(selected));
    b.setBackground(chipFace(selected));
    b.setElevation(dp(6));
    b.setTranslationY(0);
    b.setScaleX(1f);
    b.setScaleY(1f);
  }

  Button smallChip(String label, boolean selected) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextColor(TEXT);
    b.setTextSize(12);
    b.setAllCaps(false);
    b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    b.setPadding(dp(5),0,dp(5),0);
    b.setMinHeight(0);
    b.setMinimumHeight(0);

    setChipSelected(b,selected);

    // Feedback on Profile unit selectors and Exercise List category filters.
    b.setOnTouchListener((v, event) -> {
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN
          && prefs.getBoolean("hapticsEnabled", true)) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
      }
      return false;
    });
    return b;
  }

  void styleChipRow(LinearLayout row, String selected) {
    for (int i = 0; i < row.getChildCount(); i++) {
      View v = row.getChildAt(i);
      if (v instanceof Button) {
        Button b = (Button)v;
        setChipSelected(
            b,
            selected.equals(b.getText().toString()));
      }
    }
  }

  void refreshLibrary(LinearLayout list, TextView count, String query,
                      String equipment, String muscleFilter) {
    list.setVisibility(View.INVISIBLE);
    list.removeAllViews();
    String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
    int shown = 0;

    for (ExerciseData.Exercise e : ExerciseData.ALL) {
      if (!isRepBased(e)) continue;
      if (isVariantDuplicate(e)) continue;

      String eq = e.equipment.toLowerCase(Locale.US);
      boolean equipmentOk = "All".equals(equipment)
          || ("Bodyweight".equals(equipment) && eq.equals("bodyweight"))
          || ("Weightlifting".equals(equipment) && !eq.equals("bodyweight"))
          || ("Cardio".equals(equipment) && ExerciseMeta.categories(e).contains("Cardio"))
          || ("Barbell".equals(equipment) && eq.contains("barbell"))
          || ("Dumbbell".equals(equipment) && eq.contains("dumbbell"))
          || ("Cable".equals(equipment) && eq.contains("cable"))
          || ("Machine".equals(equipment) && eq.contains("machine"))
          || ("Kettlebell".equals(equipment) && eq.contains("kettlebell"));

      boolean muscleOk = matchesMuscleFilter(e, muscleFilter);

      String haystack = normalizeSearch(e.name + " " + e.muscle + " " + e.equipment + " "
          + e.movement + " " + aliasesFor(e));
      String nq = normalizeSearch(q);
      boolean queryOk = true;
      if (nq.length() > 0) {
        for (String token : nq.split(" ")) {
          if (token.length() > 0 && !haystack.contains(token)) { queryOk = false; break; }
        }
      }

      if (equipmentOk && muscleOk && queryOk) {
        addExerciseRow(list, e);
        shown++;
      }
    }

    count.setText(shown + (shown == 1 ? " exercise" : " exercises"));
    if (shown == 0) {
      TextView empty = text("No rep-based exercises match those filters.", 15, MUTED);
      empty.setGravity(Gravity.CENTER);
      list.addView(empty, margins(-1, dp(90), 0, 12, 0, 0));
    }

    // V12: reveal the completed list only after it has been populated.
    list.setVisibility(View.VISIBLE);
    list.requestLayout();
  }

  String normalizeSearch(String raw) {
    if (raw == null) return "";
    String x = raw.toLowerCase(Locale.US).replace("-", " ").replace("_", " ");
    x = x.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    StringBuilder out = new StringBuilder();
    for (String token : x.split(" ")) {
      if (token.endsWith("ies") && token.length() > 4) token = token.substring(0, token.length()-3) + "y";
      else if (token.endsWith("es") && token.length() > 4) token = token.substring(0, token.length()-2);
      else if (token.endsWith("s") && token.length() > 3) token = token.substring(0, token.length()-1);
      if (out.length() > 0) out.append(' ');
      out.append(token);
    }
    return out.toString();
  }

  String aliasesFor(ExerciseData.Exercise e) {
    String n = e.name.toLowerCase(Locale.US);
    StringBuilder a = new StringBuilder();
    if (n.contains("push-up") || n.contains("push up")) a.append(" press up press-up ");
    if (n.contains("pull-up") || n.contains("pull up")) a.append(" pullup chin bar ");
    if (n.contains("chin-up") || n.contains("chin up")) a.append(" chinup underhand pull up ");
    if (n.contains("dip")) a.append(" tricep dip parallel bar dip ");
    if (n.contains("squat")) a.append(" squats knee bend ");
    if (n.contains("lunge")) a.append(" lunges split squat ");
    if (n.contains("calf raise")) a.append(" heel raise calf raises ");
    if (n.contains("romanian deadlift")) a.append(" rdl ");
    if (n.contains("rear delt fly")) a.append(" reverse fly ");
    if (n.contains("lat pulldown")) a.append(" lat pull down ");
    if (n.contains("triceps pushdown")) a.append(" tricep pressdown cable press down ");
    return a.toString();
  }

  boolean isVariantDuplicate(ExerciseData.Exercise e) {
    String n = e.name.toLowerCase(Locale.US);
    return n.startsWith("tempo ") || n.startsWith("pause ");
  }

  boolean isRepBased(ExerciseData.Exercise e) {
    String n = e.name.toLowerCase(Locale.US);
    String[] blocked = {
        "plank", "hold", "carry", "treadmill", "stationary bike",
        "rowing machine", "elliptical", "stair climber", "wall sit"
    };
    for (String x : blocked) if (n.contains(x)) return false;
    return true;
  }

  boolean matchesMuscleFilter(ExerciseData.Exercise e, String filter) {
    if (filter == null || "All".equals(filter)) return true;
    if ("Legs".equals(filter)) {
      return e.muscle.equals("Quads") || e.muscle.equals("Hamstrings")
          || e.muscle.equals("Glutes") || e.muscle.equals("Calves");
    }
    if ("Arms".equals(filter)) {
      return e.muscle.equals("Biceps") || e.muscle.equals("Triceps")
          || e.muscle.equals("Forearms");
    }
    return e.muscle.equals(filter);
  }

  void addExerciseRow(LinearLayout list, ExerciseData.Exercise e) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.HORIZONTAL);
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setPadding(dp(10), dp(8), dp(10), dp(8));
    card.setBackground(shape(CARD, 18));
    card.setElevation(dp(5));

    ImageView thumb = new ImageView(this);
    setExerciseImage(thumb, e, false);
    thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
    card.addView(thumb, new LinearLayout.LayoutParams(dp(76), dp(66)));

    LinearLayout words = vbox();
    TextView n = bold(e.name, 17);
    words.addView(n);
    card.addView(words, new LinearLayout.LayoutParams(0, dp(70), 1));

    if (selectingForRoutine || selectingForDaily100) {
      Button addExercise = button("Add Exercise", GREEN_DARK);
      addExercise.setTextSize(11);
      addExercise.setContentDescription("Add " + e.name + (selectingForDaily100 ? " to Daily 100" : " to routine"));
      addExercise.setOnClickListener(v -> {
        if (selectingForDaily100) {
          addDailyExerciseFromLibrary(e);
          return;
        }
        routine.add(e);
        routineOccurrenceWeights.add(0.0);
        routineOccurrenceUnits.add(prefs.getString("profileWeightUnit", "kg"));
        Toast.makeText(this, e.name + " added", Toast.LENGTH_SHORT).show();
        selectingForRoutine = false;
        libraryScrollY = 0;
        returnToScreen("routine");
      });
      card.addView(addExercise, new LinearLayout.LayoutParams(dp(108), dp(48)));
    } else {
      TextView arrow = text("›", 28, MUTED);
      arrow.setGravity(Gravity.CENTER);
      card.addView(arrow, new LinearLayout.LayoutParams(dp(42), dp(70)));
    }

    card.setOnClickListener(v -> {
      if (!selectingForRoutine && !selectingForDaily100) showDetail(e, true);
    });
    list.addView(card, margins(-1, dp(84), 0, 4, 0, 4));
  }

  int exerciseDrawable(ExerciseData.Exercise e, boolean midpoint) {
    int id = getResources().getIdentifier(e.photo, "drawable", getPackageName());
    return id;
  }

  void setExerciseImage(ImageView img, ExerciseData.Exercise e, boolean midpoint) {
    int id = exerciseDrawable(e, midpoint);
    if (id != 0) img.setImageResource(id);
    else img.setImageResource(android.R.drawable.ic_menu_gallery);
    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
  }

  void showDetail(ExerciseData.Exercise e, boolean push) {
    detailExercise=e; LinearLayout page=pageShell(e.name); LinearLayout body=contentColumn();
    ImageView img=new ImageView(this); img.setImageResource(exerciseDrawable(e,false)); img.setScaleType(ImageView.ScaleType.FIT_CENTER); img.setAdjustViewBounds(true); img.setBackground(shape(CARD_ALT,20)); img.setElevation(dp(5));
    body.addView(img,margins(-1,-2,0,6,0,10));
    body.addView(sectionTitle("Muscles worked"));
    LinearLayout muscles=vbox(); muscles.setPadding(dp(14),dp(12),dp(14),dp(12)); muscles.setBackground(shape(CARD,18)); muscles.setElevation(dp(4));
    muscles.addView(bold("Primary: "+ExerciseMeta.primary(e.name,e.muscle),15)); muscles.addView(text("Secondary: "+ExerciseMeta.secondary(e.name),14,MUTED)); body.addView(muscles,margins(-1,-2,0,4,0,10));
    body.addView(sectionTitle("Exercise Categories")); TextView cats=text(ExerciseMeta.categories(e),14,TEXT); cats.setPadding(dp(14),dp(12),dp(14),dp(12)); cats.setBackground(shape(BLUE,18)); cats.setElevation(dp(4)); body.addView(cats,margins(-1,-2,0,4,0,10));
    body.addView(sectionTitle("How to perform")); String exact=ExerciseInstructions.get(e.name); if(exact==null||exact.trim().isEmpty()) exact=instructionsFor(e); TextView how=text(exact,14,TEXT); how.setPadding(dp(16),dp(14),dp(16),dp(14)); how.setBackground(shape(CARD_ALT,18)); how.setElevation(dp(4)); body.addView(how,margins(-1,-2,0,4,0,10));
    page.addView(scroll(body),new LinearLayout.LayoutParams(-1,0,1)); display("detail",page,push);
  }

  LinearLayout imagePanel(ExerciseData.Exercise e, boolean midpoint, String label) {
    LinearLayout panel = vbox();
    panel.setBackground(shape(CARD, 16));
    ImageView img = new ImageView(this);
    setExerciseImage(img, e, midpoint);
    panel.addView(img, new LinearLayout.LayoutParams(-1, dp(180)));
    TextView cap = text(label, 11, GREEN);
    cap.setGravity(Gravity.CENTER);
    panel.addView(cap, new LinearLayout.LayoutParams(-1, dp(36)));
    return panel;
  }

  String instructionsFor(ExerciseData.Exercise e) {
    String exact = ExerciseInstructions.get(e.name);
    if (exact != null) return exact;
    String n = e.name.toLowerCase(Locale.US);
    if (n.contains("push-up") || n.contains("push up")) {
      String hand = n.contains("diamond") ? "Place your hands close together beneath the centre of your chest, thumbs and index fingers almost touching."
          : n.contains("wide") ? "Place your hands wider than shoulder width, fingers pointing mostly forward."
          : "Place your hands slightly wider than shoulder width with fingers spread and wrists beneath or just outside the shoulders.";
      String angle = n.contains("incline") ? "Keep hands on a stable raised surface and maintain a straight line from head to heels."
          : n.contains("decline") ? "Place the feet on a stable raised surface and keep a straight line from shoulders to heels."
          : "Set the feet about hip width apart and make a straight line from head through hips to heels.";
      return "1. " + hand + "\n2. " + angle + "\n3. Brace the abdomen and glutes; keep the neck neutral and elbows about 30–45° from the torso.\n4. Bend the elbows and lower the chest toward the support while the shoulders move slightly forward.\n5. Stop just before the chest touches, then press the floor away until the elbows are straight without shrugging.\n\nCommon mistakes: hips sagging or piking, elbows flaring straight sideways, shortening the range, and bouncing at the bottom.";
    }
    if (n.contains("pull-up") || n.contains("chin-up")) {
      String grip = n.contains("chin") ? "Use a shoulder-width underhand grip with palms facing you."
          : "Use an overhand grip a little wider than shoulder width.";
      return "1. " + grip + "\n2. Hang with straight elbows, ribs gently down and legs still; avoid swinging.\n3. Begin by drawing the shoulder blades down, then drive the elbows toward the ribs.\n4. Lift the chest toward the bar until the chin clears it without craning the neck.\n5. Lower under control until the elbows are straight and the shoulders are fully lengthened.\n\nCommon mistakes: kicking, half reps, shrugging into the ears, or using momentum.";
    }
    if (n.contains("dip")) {
      return "1. Support yourself on parallel bars with hands beside the hips, shoulders down and elbows straight.\n2. Keep the torso slightly forward for more chest emphasis or more upright for triceps emphasis.\n3. Bend the elbows and lower until the upper arms are about parallel with the floor, keeping forearms close to vertical.\n4. Press the bars down and straighten the elbows to return.\n\nCommon mistakes: shoulders rolling forward, dropping too deep, elbows flaring excessively, and bouncing.";
    }
    if (n.contains("bodyweight squat") || (n.contains("squat") && e.equipment.equalsIgnoreCase("Bodyweight"))) {
      return "1. Stand with feet about shoulder width apart; turn the toes slightly outward.\n2. Brace the abdomen, keep the chest tall and look forward.\n3. Sit the hips down and slightly back while bending the knees in the same direction as the toes.\n4. Keep the whole foot in contact with the floor and descend until the thighs are at least near parallel, or to a comfortable depth.\n5. Drive through the mid-foot and heel to stand, finishing with hips and knees straight.\n\nCommon mistakes: knees collapsing inward, heels lifting, rounding the lower back, and cutting the depth short.";
    }
    if (n.contains("lunge") || n.contains("split squat")) {
      return "1. Stand tall with feet hip width apart. Step far enough that both knees can bend without the front heel lifting.\n2. Keep the front knee tracking over the middle toes and the torso upright.\n3. Lower the back knee toward the floor while the front thigh moves toward parallel.\n4. Push through the whole front foot to return, keeping the pelvis level.\n\nCommon mistakes: a step that is too short, front knee collapsing inward, pushing only from the toes, and leaning excessively.";
    }
    if (n.contains("deadlift") || n.contains("romanian") || n.contains("good morning")) {
      return "1. Set the feet about hip width apart and brace the abdomen before moving.\n2. Push the hips backward while keeping the spine neutral and knees softly bent.\n3. Keep the load close to the legs and shoulders pulled down away from the ears.\n4. Continue until hamstring tension or the required start position is reached.\n5. Drive the floor away and extend the hips to stand tall without leaning backward.\n\nCommon mistakes: rounding the lower back, letting the load drift forward, squatting a hinge, and jerking from the floor.";
    }
    if (n.contains("bench press") || n.contains("chest press")) {
      return "1. Lie with eyes roughly under the bar or handles, shoulder blades pulled gently back and down, and feet planted firmly.\n2. Grip just wider than shoulder width and keep wrists stacked over elbows.\n3. Lower the load toward the mid-to-lower chest with elbows roughly 45–70° from the torso.\n4. Lightly touch or reach the machine's safe depth, then press up and slightly back until the elbows are straight.\n\nCommon mistakes: bouncing the load, wrists bending backward, shoulders lifting from the bench, and losing foot pressure.";
    }
    if (n.contains("inverted row")) {
      return "1. Set a sturdy bar around waist height. Lie beneath it and take an overhand grip slightly wider than shoulder width.\n2. Straighten the legs and brace the abdomen so your head, ribs, hips and heels form one line; bend the knees to make the exercise easier.\n3. Start with straight elbows and shoulders controlled, then pull the chest toward the bar by driving the elbows down and back.\n4. Squeeze the shoulder blades together at the top without shrugging.\n5. Lower under control until the elbows are fully straight again.\n\nCommon mistakes: hips sagging, chin jutting toward the bar, shrugging the shoulders, and shortening the bottom position.";
    }
    if (n.contains("row")) {
      return "1. Set the torso angle required by the variation and brace the spine in neutral.\n2. Begin with the arms long and shoulders reaching slightly forward without rounding the lower back.\n3. Pull the elbows back toward the hips or ribs, keeping wrists neutral.\n4. Pause when the upper arm reaches the torso, then extend the arms under control.\n\nCommon mistakes: twisting the torso, shrugging, jerking the weight, and shortening the stretch.";
    }
    if (n.contains("shoulder press") || n.contains("overhead press") || n.contains("arnold press")) {
      return "1. Stand or sit tall with feet stable, abdomen braced and ribs stacked over the pelvis.\n2. Start the load around shoulder level with wrists over elbows.\n3. Press upward while moving the head slightly back then through as the arms pass the face.\n4. Finish with elbows straight and the load balanced over the shoulders, then lower to shoulder level under control.\n\nCommon mistakes: excessive lower-back arch, flared ribs, wrists bent back, and pressing too far forward.";
    }
    if (n.contains("curl")) {
      return "1. Stand or sit tall with the upper arms close to the torso and wrists neutral.\n2. Begin with elbows almost straight without locking them aggressively.\n3. Bend the elbows and bring the hands toward the shoulders while keeping the upper arms still.\n4. Squeeze briefly, then lower until the biceps are lengthened.\n\nCommon mistakes: swinging the torso, elbows drifting forward, and dropping the weight quickly.";
    }
    if (n.contains("lateral raise") || n.contains("front raise") || n.contains("rear delt")) {
      return "1. Use a stable stance with a slight bend in the elbows and wrists neutral.\n2. Brace the trunk and keep the shoulders down away from the ears.\n3. Raise the arms in the direction specified by the exercise until roughly shoulder height.\n4. Lead with the elbows rather than the hands and lower slowly to the start.\n\nCommon mistakes: shrugging, swinging, turning the movement into a press, and lifting well above shoulder height.";
    }
    if (n.contains("crunch") || n.contains("sit-up") || n.contains("v-up") || n.contains("knee tuck")) {
      return "1. Set the pelvis and ribs so the lower back is controlled rather than excessively arched.\n2. Brace the abdomen before lifting.\n3. Curl the ribs toward the pelvis, or bring the knees and torso toward one another as the variation requires.\n4. Pause briefly at the shortened position and return slowly without dropping.\n\nCommon mistakes: pulling on the neck, using momentum, holding the breath, and losing control of the lower back.";
    }
    if (n.contains("leg raise") || n.contains("scissor")) {
      return "1. Lie or hang in the exercise's start position and brace the abdomen.\n2. Keep the pelvis slightly tucked so the lower back does not over-arch.\n3. Raise the legs by moving at the hips while keeping the knees as straight as comfortably possible.\n4. Lower slowly only as far as you can maintain trunk control.\n\nCommon mistakes: swinging, arching the lower back, dropping the legs, and using a shortened range.";
    }
    if (n.contains("bird dog")) {
      return "1. Start on hands and knees with hands under shoulders and knees under hips.\n2. Brace the abdomen and keep the spine neutral.\n3. Reach one arm forward and the opposite leg backward until both are roughly in line with the torso.\n4. Keep the hips square to the floor, return under control, then alternate sides.\n\nCommon mistakes: rotating the pelvis, over-arching the back, lifting the leg too high, and rushing.";
    }
    if (n.contains("mountain climber")) {
      return "1. Start in a high push-up position with hands under shoulders and body straight.\n2. Brace the abdomen and keep the shoulders stacked over the hands.\n3. Drive one knee forward toward the chest while the other leg stays extended.\n4. Return the foot and alternate sides, keeping the hips relatively low and stable.\n\nCommon mistakes: bouncing the hips high, shortening the knee drive, and letting the shoulders drift far behind the hands.";
    }
    if (n.contains("jumping jack")) {
      return "1. Stand tall with feet together and arms by the sides.\n2. Jump the feet out wider than shoulder width while sweeping the arms overhead.\n3. Land softly with knees tracking over the toes.\n4. Jump back to the start and repeat with a steady rhythm.\n\nCommon mistakes: stiff landings, knees collapsing inward, and losing posture as fatigue builds.";
    }
    if (n.contains("burpee")) {
      return "1. Stand with feet about hip width apart.\n2. Squat down and place the hands on the floor just outside the feet.\n3. Jump or step the feet back to a strong high-plank position.\n4. Return the feet beneath the hips, stand, and finish with the required jump if using the jumping version.\n\nCommon mistakes: sagging through the lower back, landing on straight knees, and letting technique collapse for speed.";
    }
    if (n.contains("calf raise")) {
      return "1. Place the balls of the feet securely on the floor or step with toes pointing forward and knees straight but not locked.\n2. Keep the trunk tall and use support only for balance.\n3. Press through the big-toe side of the forefoot and raise the heels as high as possible without rolling the ankles outward.\n4. Pause briefly, then lower the heels under control until the calves are fully lengthened.\n\nCommon mistakes: bouncing, turning the feet out excessively, shortening the bottom stretch, and letting the ankles roll outward.";
    }
    if (n.contains("hip thrust") || n.contains("glute bridge")) {
      return "1. Set the upper back on a bench for a hip thrust, or lie on the floor for a bridge. Place feet about hip width apart with shins close to vertical at the top.\n2. Brace the abdomen and keep the ribs down.\n3. Drive through the heels and squeeze the glutes to extend the hips until the torso and thighs form a straight line.\n4. Do not over-arch the lower back; lower the hips under control until the glutes are lengthened.\n\nCommon mistakes: feet too far away, pushing through the toes, overextending the spine, and failing to reach full hip extension.";
    }
    if (n.contains("leg press")) {
      return "1. Sit with the back and pelvis fully supported. Place feet about shoulder width on the platform with toes slightly outward.\n2. Release the safety and lower the platform by bending the hips and knees while keeping the heels down.\n3. Descend only as far as the pelvis stays against the pad and the knees track with the toes.\n4. Push through the whole foot to extend the knees and hips without forcefully locking the knees.\n\nCommon mistakes: knees collapsing inward, hips curling off the pad, heels lifting, and locking the knees hard.";
    }
    if (n.contains("leg extension")) {
      return "1. Adjust the machine so the knee joint lines up with the machine pivot and the pad rests above the ankles.\n2. Sit back firmly and hold the handles.\n3. Straighten the knees until the legs are nearly parallel with the floor, keeping the thighs against the pad.\n4. Pause briefly and lower under control to a comfortable knee bend.\n\nCommon mistakes: using momentum, lifting the hips, misaligning the knee with the pivot, and slamming the weight stack.";
    }
    if (n.contains("leg curl")) {
      return "1. Align the knees with the machine pivot and place the roller just above the heels.\n2. Keep the hips and torso firmly supported.\n3. Bend the knees to draw the heels toward the glutes without lifting the hips.\n4. Squeeze the hamstrings briefly, then extend the knees slowly to the start.\n\nCommon mistakes: arching the lower back, lifting the hips, jerking the pad, and stopping short of full extension.";
    }
    if (n.contains("fly") || n.contains("pec deck")) {
      return "1. Set the shoulders gently back and down with the chest lifted and elbows softly bent.\n2. Begin with the arms opened until the chest is stretched without forcing the shoulders behind the body.\n3. Sweep the arms in an arc toward one another, keeping the elbow angle almost unchanged.\n4. Stop when the hands or pads meet in front of the chest, then return slowly to the stretched position.\n\nCommon mistakes: turning the movement into a press, overstretching the shoulders, shrugging, and using momentum.";
    }
    if (n.contains("triceps pushdown") || n.contains("rope pushdown")) {
      return "1. Stand tall facing the cable with elbows tucked beside the ribs and forearms around parallel to the floor.\n2. Keep the upper arms still and wrists neutral.\n3. Extend the elbows to press the handle toward the thighs; with a rope, separate the ends slightly at the bottom.\n4. Return slowly until the forearms rise without letting the elbows drift forward.\n\nCommon mistakes: leaning heavily over the cable, swinging the shoulders, elbows flaring, and bending the wrists.";
    }
    if (n.contains("skull crusher") || n.contains("triceps extension")) {
      return "1. Position the upper arms so the elbows point mostly upward and remain about shoulder width apart.\n2. Keep the wrists neutral and the upper arms as still as possible.\n3. Bend the elbows to lower the load toward or slightly behind the head.\n4. Extend the elbows until the arms are straight without letting the shoulders take over.\n\nCommon mistakes: elbows spreading wide, moving the upper arms excessively, dropping the weight quickly, and over-arching the back.";
    }
    if (n.contains("wood chop")) {
      return "1. Stand side-on to the cable with feet wider than hip width and knees softly bent.\n2. Hold the handle with both hands and brace the trunk.\n3. Rotate through the ribcage and hips while drawing the hands diagonally across the body in the direction of the chop.\n4. Control the return and keep the knees tracking with the toes.\n\nCommon mistakes: pulling only with the arms, twisting the knees, rounding the back, and letting the cable snap back.";
    }
    if (n.contains("pallof")) {
      return "1. Stand side-on to the cable or band with feet stable and the handle held at the chest.\n2. Brace the abdomen and keep shoulders and hips square.\n3. Press the hands straight forward until the elbows are extended while resisting the pull to rotate.\n4. Bring the hands back to the chest under control.\n\nCommon mistakes: rotating toward the anchor, leaning sideways, flaring the ribs, and using a resistance too heavy to control.";
    }
    String mv = e.movement.toLowerCase(Locale.US);
    if (mv.contains("squat")) return "1. Set the feet securely and brace the trunk.\n2. Bend the hips and knees together while the knees track with the toes.\n3. Keep the full foot planted and reach a controlled depth.\n4. Drive through the floor to return to standing.\n\nCommon mistakes: knees collapsing inward, heels lifting, and rushing the bottom position.";
    if (mv.contains("push")) return "1. Establish a stable base and position the hands or handles so the wrists stay stacked with the forearms.\n2. Brace the torso and lower through the intended joint path under control.\n3. Press away smoothly without bouncing or losing shoulder position.\n4. Finish the rep without overextending the spine.\n\nCommon mistakes: flared joints, shortened range, excessive momentum, and losing trunk position.";
    if (mv.contains("pull")) return "1. Establish a stable base with the spine neutral and shoulders down.\n2. Begin with the working muscles lengthened.\n3. Pull by driving the elbows through the required path while keeping wrists neutral.\n4. Reach the contracted position, then return slowly to full length.\n\nCommon mistakes: shrugging, swinging, jerking the load, and shortening the range.";
    return "1. Set your feet, hands and body in the exercise's stable start position.\n2. Brace the abdomen and keep the working joints aligned.\n3. Move through the intended range slowly enough to control both directions.\n4. Reverse the movement without bouncing and return fully to the start.\n\nCommon mistakes: rushing, using momentum, holding the breath, and sacrificing range of motion.";
  }

  // ---------- Saved routines ----------
  void showRoutineList(boolean push) {
    LinearLayout page = pageShell("Routines");
    LinearLayout body = contentColumn();

    JSONArray arr = savedRoutines();
    if (arr.length() == 0) {
      TextView empty = text("No saved routines yet.", 16, MUTED);
      empty.setGravity(Gravity.CENTER);
      body.addView(empty, margins(-1, dp(90), 0, 16, 0, 12));
    }

    for (int i = 0; i < arr.length(); i++) {
      final int index = i;
      JSONObject r = arr.optJSONObject(i);
      if (r == null) continue;

      // V24 compact routine card: identity on the left, actions and estimates on the right.
      LinearLayout card = new LinearLayout(this);
      card.setOrientation(LinearLayout.HORIZONTAL);
      card.setGravity(Gravity.CENTER_VERTICAL);
      card.setPadding(dp(12), dp(12), dp(12), dp(12));
      card.setBackground(shape(CARD_ALT, 20));

      JSONArray ex = r.optJSONArray("exercises");
      int nEx = ex == null ? 0 : ex.length();
      LinearLayout identity = vbox();
      identity.setPadding(dp(6), dp(8), dp(6), dp(8));
      identity.setBackground(shape(BG, 0));
      TextView n = bold(r.optString("name", "Routine"), 18);
      n.setPadding(dp(3), dp(3), dp(3), dp(10));
      identity.addView(n);
      TextView detail = text(nEx + " exercises  •  " + r.optInt("sets", 5) + " sets", 13, MUTED);
      detail.setPadding(dp(3), dp(3), dp(3), dp(3));
      identity.addView(detail);
      card.addView(identity, new LinearLayout.LayoutParams(0, dp(126), 1));

      LinearLayout right = vbox();
      LinearLayout actions = new LinearLayout(this);
      actions.setOrientation(LinearLayout.HORIZONTAL);
      actions.setGravity(Gravity.CENTER);

      Button play = button("▶", CARD_ALT);
      play.setTextColor(Color.rgb(80,164,255));
      play.setTextSize(23);
      play.setOnClickListener(v -> {
        loadSavedRoutine(r);
        if (routine.isEmpty()) return;
        prepareWorkout();
        showPreWorkout(true);
      });
      actions.addView(play, new LinearLayout.LayoutParams(0, dp(54), 1));

      Button edit = button("✎", CARD_ALT);
      edit.setTextColor(TEXT);
      edit.setTextSize(22);
      edit.setOnClickListener(v -> {
        loadSavedRoutine(r);
        showRoutine(true);
      });
      actions.addView(edit, new LinearLayout.LayoutParams(0, dp(54), 1));

      Button del = button("▣", CARD_ALT);
      del.setTextColor(RED);
      del.setTextSize(20);
      del.setOnClickListener(v -> confirmDeleteRoutine(index));
      actions.addView(del, new LinearLayout.LayoutParams(0, dp(54), 1));
      right.addView(actions, new LinearLayout.LayoutParams(-1, dp(58)));

      TextView estimate = text(potentialEstimate(ex,r.optInt("sets",5),r.optInt("min",1),r.optInt("max",30)), 12, Color.rgb(90,170,255));
      estimate.setPadding(dp(7), dp(3), dp(3), dp(3));
      estimate.setMaxLines(3);
      estimate.setEllipsize(TextUtils.TruncateAt.END);
      estimate.setBackground(shape(BG, 0));
      right.addView(estimate, new LinearLayout.LayoutParams(-1, dp(64)));
      LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, dp(126), 1.35f);
      rightLp.leftMargin = dp(10);
      card.addView(right, rightLp);

      body.addView(card, margins(-1, -2, 0, 7, 0, 7));
    }

    Button create = button("＋  Create New Routine", darken(BLUE,55));
    create.setTextColor(TEXT);
    create.setOnClickListener(v -> {
      routine.clear();
      routineWeights.clear();
      routineWeightUnits.clear();
      routineOccurrenceWeights.clear();
      routineOccurrenceUnits.clear();
      prefs.edit().remove("draftRoutineName").apply();
      showRoutine(true);
    });
    body.addView(create, margins(-1, dp(52), 0, 16, 0, 12));

    ScrollView routineListScroll=scroll(body);
    routineListScroll.setOnScrollChangeListener((v,sx,sy,ox,oy) -> {
      if(sy!=oy) playScrollClick(sy);
    });
    routineListScroll.post(() -> resetScrollClickState(routineListScroll.getScrollY()));
    page.addView(routineListScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    display("routines", page, push);
  }

  JSONArray savedRoutines() {
    try { return new JSONArray(prefs.getString("savedRoutines", "[]")); }
    catch (Exception e) { return new JSONArray(); }
  }

  void confirmDeleteRoutine(int index) {
    new AlertDialog.Builder(this)
        .setTitle("Delete routine?")
        .setMessage("This removes the saved routine. Completed workout history is not affected.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Delete", (d, which) -> {
          JSONArray old = savedRoutines();
          JSONArray out = new JSONArray();
          for (int i = 0; i < old.length(); i++) if (i != index) out.put(old.opt(i));
          prefs.edit().putString("savedRoutines", out.toString()).apply();
          showRoutineList(false);
        }).show();
  }

  void saveRoutineDraft() {
    JSONArray arr = savedRoutines();
    JSONObject r = new JSONObject();
    try {
      r.put("name", routineName());
      r.put("sets", selectedSets);
      r.put("min", minReps);
      r.put("max", maxReps);
      JSONArray ex = new JSONArray();
      ensureOccurrenceStorage();
      for (int i=0;i<routine.size();i++) {
        ExerciseData.Exercise e=routine.get(i);
        JSONObject o = new JSONObject();
        o.put("name", e.name);
        o.put("weight", occurrenceWeight(i));
        o.put("unit", occurrenceUnit(i));
        ex.put(o);
      }
      r.put("exercises", ex);

      // Replace same-named routine rather than silently duplicating it.
      JSONArray out = new JSONArray();
      boolean replaced = false;
      for (int i = 0; i < arr.length(); i++) {
        JSONObject old = arr.optJSONObject(i);
        if (!replaced && old != null
            && old.optString("name").equalsIgnoreCase(routineName())) {
          out.put(r);
          replaced = true;
        } else {
          out.put(arr.opt(i));
        }
      }
      if (!replaced) out.put(r);
      prefs.edit().putString("savedRoutines", out.toString()).apply();
    } catch (Exception ignored) {}
  }

  void loadSavedRoutine(JSONObject r) {
    routine.clear();
    routineWeights.clear();
    routineWeightUnits.clear();
    routineOccurrenceWeights.clear();
    routineOccurrenceUnits.clear();

    selectedSets = r.optInt("sets", 5);
    minReps = r.optInt("min", 10);
    maxReps = r.optInt("max", 30);
    prefs.edit().putString("draftRoutineName", r.optString("name", "Routine")).apply();

    JSONArray ex = r.optJSONArray("exercises");
    if (ex != null) {
      for (int i = 0; i < ex.length(); i++) {
        JSONObject o = ex.optJSONObject(i);
        if (o == null) continue;
        ExerciseData.Exercise match = findExercise(o.optString("name"));
        if (match != null) {
          routine.add(match);
          routineOccurrenceWeights.add(o.optDouble("weight",0));
          routineOccurrenceUnits.add(
              o.optString("unit",prefs.getString("profileWeightUnit","kg")));
        }
      }
    }
  }

  ExerciseData.Exercise findExercise(String name) {
    if ("Walking Lunge".equals(name)) name="Dumbbell Lunges";
    if ("Bench Dip".equals(name) || "Chest Dip".equals(name)) name="Dips";
    for (ExerciseData.Exercise e : ExerciseData.ALL) if (e.name.equals(name)) return e;
    return null;
  }

  String routineName() {
    String s = prefs.getString("draftRoutineName", "").trim();
    return s.length() == 0 ? "100-Rep Routine" : s;
  }

  // ---------- Workout ----------
  void prepareWorkout() {
    currentExercise = 0;
    currentSet = 0;
    workoutSplits.clear();
    workoutSetDurations.clear();
    workoutRestIntervals.clear();
    workoutPhase = 0;
    workoutLastStop = 0;
    stopWorkoutTimer();

    for (int i=0;i<routine.size();i++) {
      ArrayList<Integer> split=randomSplit(selectedSets,minReps,maxReps);
      workoutSplits.add(split);

      ArrayList<Double> durations=new ArrayList<>();
      for(int j=0;j<split.size();j++) durations.add(0.0);
      workoutSetDurations.add(durations);
    }

    currentSplit = workoutSplits.isEmpty() ? new ArrayList<>() : workoutSplits.get(0);
    workoutStarted = 0;
    workoutSetStarted = 0;
  }

  void stopWorkoutTimer() {
    if (workoutTimerUpdate != null) workoutTimerHandler.removeCallbacks(workoutTimerUpdate);
    workoutTimerUpdate = null;
  }

  String elapsedClock(long milliseconds) {
    long sec = Math.max(0, milliseconds / 1000);
    return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
  }

  void showPreWorkout(boolean push) {
    LinearLayout page = pageShell("Prepare Workout");
    LinearLayout body = contentColumn();
    body.addView(bold(routineName(), 25));
    body.addView(text(selectedSets + " sets per exercise  •  " + minReps + "–" + maxReps
        + " reps per set  •  100 reps per exercise", 15, TEXT));
    TextView estimate = text(draftPotentialEstimate(), 15, GREEN);
    estimate.setPadding(dp(14), dp(14), dp(14), dp(14));
    estimate.setBackground(shape(CARD, 16));
    body.addView(estimate, margins(-1, -2, 0, 12, 0, 12));
    Button start = button("Start workout", GREEN);
    start.setOnClickListener(v -> {
      workoutStarted = System.currentTimeMillis();
      workoutLastStop = 0;
      workoutPhase = 0;
      showWorkout(true);
    });
    body.addView(start, margins(-1, dp(54), 0, 4, 0, 16));
    body.addView(sectionTitle("Exercise order"));
    for (int i = 0; i < routine.size(); i++) {
      ExerciseData.Exercise e = routine.get(i);
      LinearLayout card = vbox();
      card.setPadding(dp(14), dp(12), dp(14), dp(12));
      card.setBackground(shape(CARD, 14));
      card.addView(bold((i + 1) + ".  " + e.name, 17));
      card.addView(text("100 reps  •  " + selectedSets + " sets", 13, MUTED));
      body.addView(card, margins(-1, -2, 0, 0, 0, 8));
    }
    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
    display("preWorkout", page, push);
  }

  void showWorkout(boolean push) {
    if (routine.isEmpty()) { showRoutine(false); return; }
    stopWorkoutTimer();
    LinearLayout page = pageShell(routineName());
    LinearLayout body = contentColumn();
    ExerciseData.Exercise e = routine.get(currentExercise);
    TextView progress = text("Set " + (currentSet + 1) + " of " + selectedSets
        + "   •   Exercise " + (currentExercise + 1) + " of " + routine.size(), 14, BLUE);
    progress.setGravity(Gravity.CENTER);
    body.addView(progress);
    TextView name = bold(e.name, 28);
    name.setGravity(Gravity.CENTER);
    body.addView(name);
    ImageView workoutCard = new ImageView(this);
    workoutCard.setImageResource(exerciseDrawable(e, false));
    workoutCard.setScaleType(ImageView.ScaleType.FIT_CENTER);
    workoutCard.setAdjustViewBounds(true);
    body.addView(workoutCard, margins(-1, dp(180), 0, 4, 0, 4));
    currentSplit = workoutSplits.get(currentExercise);
    int reps = currentSplit.get(currentSet);
    LinearLayout stats = new LinearLayout(this);
    stats.setOrientation(LinearLayout.HORIZONTAL);
    stats.setGravity(Gravity.CENTER);
    LinearLayout repsCol = vbox();
    TextView big = bold(String.valueOf(reps), 60);
    big.setGravity(Gravity.CENTER);
    repsCol.addView(big);
    TextView repLabel = text("REPS THIS SET", 13, MUTED);
    repLabel.setGravity(Gravity.CENTER);
    repsCol.addView(repLabel);
    stats.addView(repsCol, new LinearLayout.LayoutParams(0, -2, 0.35f));
    LinearLayout timerCol = vbox();
    TextView timer = bold("00:00", 46);
    timer.setSingleLine(true);
    timer.setMaxLines(1);
    timer.setHorizontallyScrolling(true);
    timer.setIncludeFontPadding(false);
    timer.setAutoSizeTextTypeUniformWithConfiguration(30, 46, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
    timer.setGravity(Gravity.CENTER);
    timerCol.addView(timer);
    TextView timerLabel = text("SET TIMER", 13, MUTED);
    timerLabel.setGravity(Gravity.CENTER);
    timerCol.addView(timerLabel);
    stats.addView(timerCol, new LinearLayout.LayoutParams(0, -2, 0.65f));
    body.addView(stats);
    if (workoutPhase == 2) {
      double saved = workoutSetDurations.get(currentExercise).get(currentSet);
      timer.setText(elapsedClock(Math.round(saved * 1000)));
    }
    if (usesAdjustableLoad(e)) {
      final int occurrence = currentExercise;
      double weight = occurrenceWeight(occurrence);
      String unit = occurrenceUnit(occurrence);
      Button adjust = outlineButton(loadDescription(e, weight, unit) + "   ✎ Edit");
      adjust.setOnClickListener(v -> editWorkoutLoad(e, occurrenceWeight(occurrence),
          occurrenceUnit(occurrence), (newWeight, newUnit) -> {
        setOccurrenceWeight(occurrence, newWeight);
        setOccurrenceUnit(occurrence, newUnit);
        adjust.setText(loadDescription(e, newWeight, newUnit) + "   ✎ Edit");
      }));
      body.addView(adjust, margins(-1, dp(52), 0, 8, 0, 8));
    } else {
      double wt = occurrenceWeight(currentExercise);
      if (wt > 0) {
        TextView load = text("Estimated load: " + occurrenceWeightValue(currentExercise)
            + " " + occurrenceUnit(currentExercise), 15, GREEN);
        load.setGravity(Gravity.CENTER);
        body.addView(load);
      }
    }
    boolean last = currentSet == selectedSets - 1 && currentExercise == routine.size() - 1;
    // Pastel-green active state; original blue when paused or between sets.
    final int workoutActiveGreen = Color.rgb(133, 205, 155);
    Button next = button(workoutPhase == 0 ? "Start exercise"
        : workoutPhase == 1 ? "Stop exercise"
        : last ? "Workout Complete" : "Next", BLUE);
    next.setTag(workoutPhase == 1 ? workoutActiveGreen : BLUE);
    setWorkoutActionFace(next, workoutPhase == 1 ? workoutActiveGreen : BLUE);
    next.setTextColor(TEXT);
    next.setOnClickListener(v -> {
      long now = System.currentTimeMillis();
      if (workoutPhase == 0) {
        if (workoutLastStop > 0) workoutRestIntervals.add(Math.max(0, now - workoutLastStop) / 1000.0);
        workoutSetStarted = now;
        workoutPhase = 1;
        next.setText("Stop exercise");
        next.setTag(workoutActiveGreen);
        setWorkoutActionFace(next, workoutActiveGreen);
        timer.setText("00:00");
        workoutTimerUpdate = new Runnable() {
          @Override public void run() {
            if (workoutPhase != 1) return;
            timer.setText(elapsedClock(System.currentTimeMillis() - workoutSetStarted));
            workoutTimerHandler.postDelayed(this, 200);
          }
        };
        workoutTimerHandler.post(workoutTimerUpdate);
      } else if (workoutPhase == 1) {
        double seconds = Math.max(0.1, (now - workoutSetStarted) / 1000.0);
        workoutSetDurations.get(currentExercise).set(currentSet, seconds);
        timer.setText(elapsedClock(Math.round(seconds * 1000)));
        stopWorkoutTimer();
        workoutLastStop = now;
        workoutPhase = 2;
        next.setText(last ? "Workout Complete" : "Next");
        next.setTag(BLUE);
        setWorkoutActionFace(next, BLUE);
      } else if (last) {
        completeWorkout();
      } else {
        if (currentExercise < routine.size() - 1) currentExercise++;
        else { currentSet++; currentExercise = 0; }
        workoutPhase = 0;
        showWorkout(false);
      }
    });
    LinearLayout navRow = new LinearLayout(this);
    navRow.setOrientation(LinearLayout.HORIZONTAL);
    Button prev = button("Back", CARD_ALT);
    prev.setEnabled((currentSet > 0 || currentExercise > 0) && workoutPhase != 1);
    prev.setOnClickListener(v -> {
      if (workoutPhase == 1) return;
      if (currentExercise > 0) currentExercise--;
      else if (currentSet > 0) { currentSet--; currentExercise = routine.size() - 1; }
      workoutPhase = 2; // Previously completed set; avoid accidentally re-timing it.
      showWorkout(false);
    });
    navRow.addView(prev, new LinearLayout.LayoutParams(dp(92), dp(54)));
    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(54), 1);
    np.setMargins(dp(8), 0, 0, 0);
    navRow.addView(next, np);
    body.addView(navRow, margins(-1, dp(54), 0, 16, 0, 8));
    Button cancel = outlineButton("Cancel Routine");
    cancel.setTextColor(RED);
    cancel.setOnClickListener(v -> new AlertDialog.Builder(this)
        .setTitle("Cancel workout?")
        .setMessage("This workout will not be recorded as completed.")
        .setNegativeButton("Keep going", null)
        .setPositiveButton("Cancel workout", (d, w) -> { stopWorkoutTimer(); showHome(false); })
        .show());
    body.addView(cancel, margins(-1, dp(56), 0, 0, 0, 8));
    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
    display("workout", page, push);
  }

  // V26: check each measured set rather than only the total session duration.
  // A test is kept separately and never contributes to statistics or Health Connect.
  boolean workoutTimingImplausible() {
    for (int i=0; i<routine.size() && i<workoutSplits.size(); i++) {
      ExerciseData.Exercise e=routine.get(i);
      ArrayList<Integer> splits=workoutSplits.get(i);
      ArrayList<Double> durations=i<workoutSetDurations.size()
          ? workoutSetDurations.get(i) : new ArrayList<>();
      for (int j=0; j<splits.size(); j++) {
        int reps=splits.get(j);
        double seconds=j<durations.size()?durations.get(j):0;
        // Zero indicates unmeasured legacy data; do not accuse the user of a test.
        if (reps>0 && seconds>0 && seconds/reps < minimumSecondsPerRep(e)) return true;
      }
    }
    return false;
  }

  double minimumSecondsPerRep(ExerciseData.Exercise e) {
    String n=e.name.toLowerCase(Locale.US);
    if (n.contains("jumping jack") || n.contains("high knees")
        || n.contains("mountain climber")) return 0.35;
    if (n.contains("jump") || n.contains("swing")) return 0.65;
    if (n.contains("burpee") || n.contains("get-up")) return 1.20;
    return 0.80; // conservative; a warning, not proof of dishonesty
  }

  void completeWorkout() {
    stopWorkoutTimer();
    if (workoutTimingImplausible()) {
      new AlertDialog.Builder(this)
          .setTitle("Unrealistic workout duration")
          .setMessage("Some sets were completed faster than a plausible repetition pace. "
              + "Was this a test? Test and unverified sessions will not count towards "
              + "calorie totals or be sent to Health Connect.")
          .setPositiveButton("Yes - this was a test", (d,w) -> saveCompletedWorkout("test"))
          .setNegativeButton("No - real workout", (d,w) -> saveCompletedWorkout("unverified"))
          .setNeutralButton("Review workout", (d,w) -> showWorkout(false))
          .show();
      return;
    }
    saveCompletedWorkout("verified");
  }

  void saveCompletedWorkout(String status) {
    long now = System.currentTimeMillis();
    double total = 0;
    JSONArray exercises = new JSONArray();
    long durationSec = Math.max(1, (now - workoutStarted) / 1000);
    ensureOccurrenceStorage();
    for (int routineIndex=0; routineIndex<routine.size(); routineIndex++) {
      ExerciseData.Exercise e=routine.get(routineIndex);
      double w=occurrenceWeight(routineIndex);
      String unit=occurrenceUnit(routineIndex);
      double kcal=0.0;

      ArrayList<Integer> splits=
          routineIndex<workoutSplits.size()
              ? workoutSplits.get(routineIndex)
              : new ArrayList<>();

      ArrayList<Double> durations=
          routineIndex<workoutSetDurations.size()
              ? workoutSetDurations.get(routineIndex)
              : new ArrayList<>();

      JSONArray splitJson=new JSONArray();
      JSONArray durationJson=new JSONArray();

      for(int setIndex=0;setIndex<splits.size();setIndex++) {
        int setReps=splits.get(setIndex);
        double setSeconds=
            setIndex<durations.size()
                ? durations.get(setIndex)
                : 0.0;

        if(setSeconds<=0)
          setSeconds=setReps*secondsPerRep(e);

        // Parallel repetition model uses expected movement time, load and ROM.
        // Both models describe the same work, so blend rather than add.
        double timed=estimateCaloriesSet(e,setReps,w,unit,setSeconds);
        double repBased=estimateCaloriesSet(e,setReps,w,unit,
            setReps*secondsPerRep(e));
        kcal+=0.75*timed+0.25*repBased;
        try {
          splitJson.put(setReps);
          durationJson.put(round(setSeconds));
        } catch (Exception ignored) {}
      }

      total += kcal;
      try {
        JSONObject o = new JSONObject();
        o.put("name", e.name);
        o.put("reps", 100);
        o.put("weight", w);
        o.put("unit", unit);
        o.put("calories", round(kcal));
        o.put("repSplits",splitJson);
        o.put("setDurationsSec",durationJson);
        exercises.put(o);
      } catch (Exception ignored) {}
    }

    // Include observed rest/transition periods at the recovery MET intensity.
    double restSeconds = 0;
    for (double seconds : workoutRestIntervals) restSeconds += Math.max(0, seconds);
    double restNetMet = Math.max(0, correctedMet(1.5) - correctedMet(1.0));
    double restCalories = profileWeightKg() * restNetMet * restSeconds / 3600.0;
    total += restCalories;
    JSONArray restJson = new JSONArray();
    for (double seconds : workoutRestIntervals) restJson.put(Double.valueOf(round(seconds)));

    JSONObject rec = new JSONObject();
    try {
      rec.put("restDurationsSec", restJson);
      rec.put("restDurationSec", round(restSeconds));
      rec.put("restCalories", round(restCalories));
      rec.put("timestamp", now);
      rec.put("date", dateKey.format(new Date(now)));
      rec.put("dateTime", dateTime.format(new Date(now)));
      rec.put("routine", routineName());
      rec.put("durationSec", durationSec);
      rec.put("calories", round(total));
      rec.put("sets", selectedSets);
      rec.put("calorieModel", "V26 hybrid MET/repetition 75/25");
      rec.put("verificationStatus", status);
      rec.put("estimatedCaloriesBeforeVerification", round(total));
      rec.put("exercises", exercises);
    } catch (Exception ignored) {}

    if (!"verified".equals(status)) {
      // Separate store ensures existing history charts, streaks, and summaries
      // cannot accidentally include simulations or unverified workouts.
      try { rec.put("calories", 0.0); } catch (Exception ignored) {}
      JSONArray flagged;
      try { flagged=new JSONArray(prefs.getString("flaggedWorkoutRecords", "[]")); }
      catch (Exception ignored) { flagged=new JSONArray(); }
      flagged.put(rec);
      prefs.edit().putString("flaggedWorkoutRecords",flagged.toString()).apply();
      Toast.makeText(this, "Workout saved as " + status
          + "; excluded from statistics and Health Connect.", Toast.LENGTH_LONG).show();
      showHome(false);
      return;
    }
    JSONArray records = loadRecords();
    records.put(rec);
    prefs.edit().putString("records", records.toString()).apply();
    if (prefs.getBoolean("healthConnectEnabled", false)) {
      StringBuilder healthDetails = new StringBuilder();
      for (int i=0;i<exercises.length();i++) {
        JSONObject x=exercises.optJSONObject(i); if(x==null) continue;
        if(healthDetails.length()>0) healthDetails.append("; ");
        healthDetails.append(x.optString("name")).append(" — ").append(x.optInt("reps",100)).append(" reps");
      }
      final String exportId=String.valueOf(now);
      final String exportTitle=routineName();
      final String exportDetails=healthDetails.toString();
        final double exportTotal = total;
      HealthConnectBridge.INSTANCE.hasPermissions(this, ok -> {
        if(ok) HealthConnectBridge.INSTANCE.writeWorkout(this,exportId,exportTitle,now-durationSec*1000L,now,exportTotal,exportDetails,(success,msg)->{ if(!success) Toast.makeText(this,msg,Toast.LENGTH_LONG).show(); return kotlin.Unit.INSTANCE; });
        return kotlin.Unit.INSTANCE;
      });
    }
    showCompletion(total, exercises);
  }

  // ---------- V21 2024 Compendium set-level calorie engine ----------

  double estimateCalories(ExerciseData.Exercise e, int reps,
                           double exerciseWeight, String unit) {
    return estimateCaloriesSet(
        e,reps,exerciseWeight,unit,reps*secondsPerRep(e));
  }

  double estimateCaloriesSegmented(ExerciseData.Exercise e, int reps,
                                    double exerciseWeight, String unit,
                                    double segmentSeconds) {
    return estimateCaloriesSet(
        e,reps,exerciseWeight,unit,segmentSeconds);
  }

  double estimateCaloriesSet(ExerciseData.Exercise e, int reps,
                             double exerciseWeight, String unit,
                             double measuredSeconds) {
    double bodyKg=Math.max(1.0,profileWeightKg());

    // Rep count estimates active time. The measured interval is the
    // absolute ceiling, ensuring time can never be counted twice.
    double activeSeconds=Math.min(
        Math.max(0.0,measuredSeconds),
        Math.max(0,reps)*secondsPerRep(e));

    double recoverySeconds=Math.max(0.0,measuredSeconds-activeSeconds);

    double activeMet=correctedMet(compendiumMet(e));
    activeMet*=metabolicLoadAdjustment(e,exerciseWeight,unit);
    activeMet*=metabolicRomAdjustment(e);
    activeMet=Math.max(1.5,Math.min(12.0,activeMet));

    double recoveryMet=correctedMet(1.5);
    double restingMet=correctedMet(1.0);

    // Net exercise calories: energy above predicted resting expenditure.
    double activeNet=Math.max(0.0,activeMet-restingMet);
    double recoveryNet=Math.max(0.0,recoveryMet-restingMet);

    return Math.max(0.0,
        bodyKg*(
            activeNet*(activeSeconds/3600.0)
            + recoveryNet*(recoverySeconds/3600.0)
        ));
  }

  // Maps every exercise card to a published 2024 Compendium class.
  // Exact named activities take precedence; remaining cards fall back
  // to the appropriate resistance/bodyweight class rather than receiving
  // invented exercise-specific MET values.
  double compendiumMet(ExerciseData.Exercise e) {
    String n=e.name.toLowerCase(Locale.US);
    String eq=e.equipment.toLowerCase(Locale.US);
    String mv=e.movement.toLowerCase(Locale.US);

    // Kettlebell swing — 9.8 MET.
    if(n.contains("kettlebell swing")) return 9.8;

    // Vigorous HIIT examples: burpees, mountain climbers, squat jumps.
    if(n.contains("burpee")
        || n.contains("mountain climber")
        || n.contains("squat jump"))
      return 11.0;

    // Vigorous calisthenic/jumping activity.
    if(n.contains("box jump")
        || n.contains("tuck jump")
        || n.contains("forward-back jump")
        || n.contains("side-to-side jump")
        || n.contains("jumping jack")
        || n.contains("high knees"))
      return 7.5;

    // Vigorous kettlebell circuit-style movements.
    if(eq.contains("kettlebell")
        && (n.contains("clean")
            || n.contains("snatch")
            || n.contains("thruster")
            || n.contains("turkish get-up")))
      return 7.5;

    // Squat/deadlift resistance category.
    if(n.contains("deadlift")
        || n.contains("squat")
        || mv.contains("squat"))
      return 5.0;

    // Vigorous/power resistance.
    if(n.contains("snatch")
        || n.contains("thruster")
        || n.contains("clean and press")
        || n.contains("clean & press"))
      return 6.0;

    // Cards explicitly corrected to Bodyweight in V21.
    if(n.equals("standing hip adduction")
        || n.equals("standing hip kickback")
        || n.equals("standing single leg deadlift")
        || n.equals("standing hip abduction")
        || n.equals("standing knee raise"))
      return 3.0;

    // General bodyweight resistance.
    if(eq.contains("bodyweight"))
      return 3.0;

    // General varied resistance: barbell, dumbbell, cable and machines.
    return 3.5;
  }

  // 2024 Compendium corrected-MET approach using predicted resting
  // metabolic rate from the user's Personal Profile.
  double correctedMet(double standardMet) {
    double kg=profileWeightKg();
    double cm=storedHeightCm();

    int age=0;
    try {
      age=Integer.parseInt(prefs.getString("age","0"));
    } catch(Exception ignored) {}

    int sex=prefs.getInt("sex",1);

    if(kg<=0 || cm<=0 || age<=0)
      return standardMet;

    double rmrKcalDay;

    if(sex==1) {
      rmrKcalDay=
          66.4730
          + 5.0033*cm
          + 13.7516*kg
          - 6.7550*age;
    } else {
      rmrKcalDay=
          655.0955
          + 1.8496*cm
          + 9.5634*kg
          - 4.6756*age;
    }

    if(rmrKcalDay<=0) return standardMet;

    double rmrMlKgMin=
        (((rmrKcalDay/1440.0)/5.0)/kg)*1000.0;

    if(rmrMlKgMin<=0) return standardMet;

    return standardMet*(3.5/rmrMlKgMin);
  }

  double secondsPerRep(ExerciseData.Exercise e) {
    String n=e.name.toLowerCase(Locale.US);

    if(n.contains("mountain climber")
        || n.contains("jumping jack")
        || n.contains("high knees"))
      return 1.2;

    if(n.contains("kettlebell swing")
        || n.contains("burpee")
        || n.contains("jump"))
      return 2.0;

    return 3.0;
  }

  double metabolicLoadAdjustment(ExerciseData.Exercise e,
                                  double exerciseWeight,
                                  String unit) {
    double bodyKg=Math.max(1.0,profileWeightKg());
    double relativeLoad;

    if(isBodyweightExercise(e)) {
      relativeLoad=effectiveBodyweightKg(e)/bodyKg;
    } else {
      double enteredKg=
          "lb".equalsIgnoreCase(unit)
              ? exerciseWeight*0.45359237
              : exerciseWeight;

      relativeLoad=
          (Math.max(0.0,enteredKg)*loadMultiplier(e))/bodyKg;
    }

    // Load is a bounded modifier, not a second calorie calculation.
    return Math.max(0.90,Math.min(1.10,0.95+relativeLoad*0.15));
  }

  double metabolicRomAdjustment(ExerciseData.Exercise e) {
    double heightM=Math.max(1.3,storedHeightCm()/100.0);
    double reference=heightM*0.25;

    if(reference<=0) return 1.0;

    double ratio=repDistanceMeters(e)/reference;

    // ROM contribution deliberately limited to ±7.5%.
    return Math.max(0.925,Math.min(1.075,0.95+ratio*0.05));
  }

  double loadMultiplier(ExerciseData.Exercise e) {
    // In Reps100 a dumbbell entry means the weight of ONE dumbbell. Most listed dumbbell
    // movements use a pair, so the external load in the energy model is doubled.
    if (e.equipment.toLowerCase(Locale.US).contains("dumbbell")) return 2.0;
    // Kettlebell entries are treated as the actual kettlebell weight used and are not doubled.
    return 1.0;
  }

  double repDistanceMeters(ExerciseData.Exercise e) {
    String n=e.name.toLowerCase(Locale.US), move=e.movement.toLowerCase(Locale.US);
    double heightM=Math.max(1.3,storedHeightCm()/100.0);
    if(n.contains("calf raise")) return Math.min(0.16,heightM*0.08);
    if(n.contains("curl")) return Math.min(0.38,heightM*0.18);
    if(n.contains("lateral raise")||n.contains("front raise")||n.contains("fly")) return Math.min(0.48,heightM*0.23);
    if(n.contains("leg extension")||n.contains("leg curl")) return Math.min(0.42,heightM*0.20);
    if(n.contains("wood chop")||n.contains("russian")) return Math.min(0.55,heightM*0.27);
    if(n.contains("jump")||n.contains("burpee")) return Math.min(0.55,heightM*0.27);
    if(move.contains("squat")||move.contains("lunge")) return Math.min(0.52,heightM*0.26);
    if(move.contains("hinge")||n.contains("deadlift")||n.contains("good morning")) return Math.min(0.58,heightM*0.29);
    if(move.contains("push")) return Math.min(0.46,heightM*0.23);
    if(move.contains("pull")) return Math.min(0.48,heightM*0.24);
    if(move.contains("core")) return Math.min(0.36,heightM*0.18);
    if(move.contains("full")) return Math.min(0.60,heightM*0.30);
    return Math.min(0.40,heightM*0.20);
  }

  void showCompletion(double total, JSONArray exercises) {
    navigation.clear();
    LinearLayout page = pageShell("Workout Complete");
    LinearLayout body = contentColumn();

    TextView check = text("✓", 58, GREEN);
    check.setGravity(Gravity.CENTER);
    body.addView(check);

    TextView title = bold("WORKOUT COMPLETE!", 26);
    title.setGravity(Gravity.CENTER);
    body.addView(title);

    TextView kcal = bold(String.format(Locale.US, "%.1f kcal", total), 34);
    kcal.setTextColor(GREEN);
    kcal.setGravity(Gravity.CENTER);
    body.addView(kcal);

    TextView sub = text("Estimated calories burned", 15, MUTED);
    sub.setGravity(Gravity.CENTER);
    body.addView(sub);

    body.addView(sectionTitle("Workout breakdown"));
    for (int i = 0; i < exercises.length(); i++) {
      JSONObject o = exercises.optJSONObject(i);
      if (o == null) continue;
      LinearLayout row = vbox();
      row.setPadding(dp(14), dp(10), dp(14), dp(10));
      row.setBackground(shape(CARD, 16));
      row.addView(bold(o.optString("name"), 16));
      row.addView(text("100 reps  •  " + formatLoad(o) + "  •  "
          + String.format(Locale.US, "%.1f kcal", o.optDouble("calories", 0)), 13, MUTED));
      body.addView(row, margins(-1, -2, 0, 4, 0, 4));
    }

    Button done = button("Done", GREEN);
    done.setTextColor(TEXT);
    done.setOnClickListener(v -> {
      routine.clear();
      routineWeights.clear();
      routineWeightUnits.clear();
      routineOccurrenceWeights.clear();
      routineOccurrenceUnits.clear();
      prefs.edit().remove("draftRoutineName").apply();
      showHome(false);
    });
    body.addView(done, margins(-1, dp(54), 0, 18, 0, 8));

    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
    display("completion", page, false);
  }

  String formatLoad(JSONObject o) {
    double w = o.optDouble("weight", 0);
    String unit=o.optString("unit","kg");
    if (w <= 0) return "Bodyweight";
    String one=(Math.abs(w-Math.rint(w))<0.001?String.valueOf((int)Math.rint(w)):String.format(Locale.US,"%.1f",w));
    ExerciseData.Exercise e=findExercise(o.optString("name",""));
    if(e!=null && e.equipment.toLowerCase(Locale.US).contains("dumbbell")){
      return one+" "+unit+" each ("+formatNumber(w*2.0)+" "+unit+" pair)";
    }
    return one+" "+unit;
  }

  // ---------- Profile ----------
  void showProfile(boolean push) {
    LinearLayout page = pageShell("Profile");
    LinearLayout body = contentColumn();

    body.addView(text("These details improve Reps100's calorie estimates and are saved on this device. Check your stats regularly and amend them here as they change, so that Reps100 can provide you with the most accurate measurement for Calories burned, as well as your BMI.", 14, MUTED));

    String system = prefs.getString("measurementSystem", "metric");
    LinearLayout systemRow = new LinearLayout(this); systemRow.setOrientation(LinearLayout.HORIZONTAL);
    Button metric = smallChip("Metric", "metric".equals(system));
    Button imperial = smallChip("Imperial", "imperial".equals(system));
    LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, dp(42), 1); mlp.setMargins(0,0,dp(4),0);
    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(42), 1); ilp.setMargins(dp(4),0,0,0);
    systemRow.addView(metric, mlp); systemRow.addView(imperial, ilp); body.addView(systemRow, margins(-1, dp(46), 0, 8, 0, 12));
    final String[] activeSystem = {system};

    // V21 profile layout: core details beside the measurement figure,
    // with waist and hip measurements side-by-side underneath.
    LinearLayout profileTop = new LinearLayout(this);
    profileTop.setOrientation(LinearLayout.HORIZONTAL);
    profileTop.setGravity(Gravity.TOP);

    LinearLayout profileLeft = vbox();

    profileLeft.addView(profileLabel("Biological sex"));
    Spinner sex = new Spinner(this);
    String[] sexes = {"Male", "Female"};
    sex.setAdapter(new ArrayAdapter<>(this,
        android.R.layout.simple_spinner_dropdown_item, sexes));
    sex.setSelection(prefs.getInt("sex", 1) == 2 ? 1 : 0);
    sex.setBackground(outline(CARD_ALT,1,18));
    profileLeft.addView(sex, margins(-1, dp(52), 0, 0, 0, 8));

    profileLeft.addView(profileLabel("Age"));
    EditText age = field("", prefs.getString("age", ""),
        InputType.TYPE_CLASS_NUMBER);
    profileLeft.addView(age);

    LinearLayout measurementFields = vbox();
    profileLeft.addView(measurementFields,
        new LinearLayout.LayoutParams(-1,-2));

    final ImageView[] profileMeasureImg={new ImageView(this)};
    profileMeasureImg[0].setScaleType(ImageView.ScaleType.FIT_CENTER);
    profileMeasureImg[0].setAdjustViewBounds(true);
    profileMeasureImg[0].setImageResource(
        sex.getSelectedItemPosition()==0
            ? R.drawable.profile_measurement_male
            : R.drawable.profile_measurement_female);

    LinearLayout.LayoutParams leftLp =
        new LinearLayout.LayoutParams(0,-2,0.92f);
    leftLp.setMargins(0,0,dp(6),0);
    profileTop.addView(profileLeft,leftLp);

    LinearLayout.LayoutParams imageLp =
        new LinearLayout.LayoutParams(0,dp(390),1.28f);
    imageLp.setMargins(dp(6),0,0,0);
    profileTop.addView(profileMeasureImg[0],imageLp);

    body.addView(profileTop,new LinearLayout.LayoutParams(-1,-2));

    LinearLayout lowerMeasurements = new LinearLayout(this);
    lowerMeasurements.setOrientation(LinearLayout.HORIZONTAL);
    lowerMeasurements.setGravity(Gravity.TOP);

    LinearLayout waistBox=vbox();
    LinearLayout hipBox=vbox();

    LinearLayout.LayoutParams waistLp =
        new LinearLayout.LayoutParams(0,-2,1);
    waistLp.setMargins(0,0,dp(5),0);

    LinearLayout.LayoutParams hipLp =
        new LinearLayout.LayoutParams(0,-2,1);
    hipLp.setMargins(dp(5),0,0,0);

    lowerMeasurements.addView(waistBox,waistLp);
    lowerMeasurements.addView(hipBox,hipLp);
    body.addView(lowerMeasurements,margins(-1,-2,0,4,0,8));

    final EditText[] heightCm={null},heightFt={null},heightIn={null},
        weight={null},waist={null},hip={null};

    Runnable rebuildMeasurements=()->{
      measurementFields.removeAllViews();
      waistBox.removeAllViews();
      hipBox.removeAllViews();

      if("metric".equals(activeSystem[0])){
        measurementFields.addView(profileLabel("Weight (kg)"));
        weight[0]=field("",formatNumber(profileWeightKg()),
            InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        measurementFields.addView(weight[0]);

        measurementFields.addView(profileLabel("Height (cm)"));
        heightCm[0]=field("",formatNumber(storedHeightCm()),
            InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        measurementFields.addView(heightCm[0]);

        waistBox.addView(profileLabel("Waist (cm)"));
        waist[0]=field("",formatNumber(storedWaistCm()),
            InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        waistBox.addView(waist[0]);

        hipBox.addView(profileLabel("Hip circumference (cm)"));
        hip[0]=field("",formatNumber(storedHipCm()),
            InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        hipBox.addView(hip[0]);

      }else{
        measurementFields.addView(profileLabel("Weight (lb)"));
        weight[0]=field("",formatNumber(profileWeightKg()/0.45359237),
            InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        measurementFields.addView(weight[0]);

        measurementFields.addView(profileLabel("Height (ft / in)"));
        double total=storedHeightCm()/2.54;
        int ft=(int)Math.floor(total/12);
        double inch=total-ft*12;

        LinearLayout hr=new LinearLayout(this);
        hr.setOrientation(LinearLayout.HORIZONTAL);

        heightFt[0]=field("ft",String.valueOf(ft),
            InputType.TYPE_CLASS_NUMBER);
        heightIn[0]=field("in",formatNumber(inch),
            InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);

        LinearLayout.LayoutParams f1=
            new LinearLayout.LayoutParams(0,dp(58),1);
        f1.setMargins(0,0,dp(4),0);

        LinearLayout.LayoutParams f2=
            new LinearLayout.LayoutParams(0,dp(58),1);
        f2.setMargins(dp(4),0,0,0);

        hr.addView(heightFt[0],f1);
        hr.addView(heightIn[0],f2);
        measurementFields.addView(hr);

        waistBox.addView(profileLabel("Waist (in)"));
        waist[0]=field("",formatNumber(storedWaistCm()/2.54),
            InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        waistBox.addView(waist[0]);

        hipBox.addView(profileLabel("Hip circumference (in)"));
        hip[0]=field("",formatNumber(storedHipCm()/2.54),
            InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        hipBox.addView(hip[0]);
      }
    };

    metric.setOnClickListener(v->{
      activeSystem[0]="metric";
      styleTwo(metric,imperial,true);
      rebuildMeasurements.run();
    });

    imperial.setOnClickListener(v->{
      activeSystem[0]="imperial";
      styleTwo(metric,imperial,false);
      rebuildMeasurements.run();
    });

    rebuildMeasurements.run();

    Button save=button("Save Profile",GREEN_DARK);save.setOnClickListener(v->{
      double cm,kg,wc,hipCm;
      if("metric".equals(activeSystem[0])){cm=parseDouble(heightCm[0].getText().toString(),storedHeightCm());kg=parseDouble(weight[0].getText().toString(),profileWeightKg());wc=parseDouble(waist[0].getText().toString(),storedWaistCm());hipCm=parseDouble(hip[0].getText().toString(),storedHipCm());}
      else{double ft=parseDouble(heightFt[0].getText().toString(),0),in=parseDouble(heightIn[0].getText().toString(),0);cm=(ft*12+in)*2.54;kg=parseDouble(weight[0].getText().toString(),profileWeightKg()/0.45359237)*0.45359237;wc=parseDouble(waist[0].getText().toString(),storedWaistCm()/2.54)*2.54;hipCm=parseDouble(hip[0].getText().toString(),storedHipCm()/2.54)*2.54;}
      prefs.edit().putInt("sex",sex.getSelectedItemPosition()==0?1:2).putString("age",age.getText().toString()).putFloat("heightCm",(float)cm).putFloat("profileWeightKg",(float)kg).putFloat("waistCm",(float)wc).putFloat("hipCm",(float)hipCm).putString("measurementSystem",activeSystem[0]).putString("profileWeightUnit","metric".equals(activeSystem[0])?"kg":"lb").apply();
      Toast.makeText(this,"Profile saved.",Toast.LENGTH_SHORT).show();showProfile(false);
    });
    body.addView(save,margins(-1,dp(54),0,18,0,8));

    body.addView(sectionTitle("Measurement guide"));
    body.addView(text("Height: measure from the floor to the top of the head while standing upright.\n\nWaist: find the bottom of the ribs and top of the hips; measure midway between them after breathing out naturally.\n\nHip: wrap the tape around the fullest part of the hips and buttocks, keeping it level.",14,MUTED));

    body.addView(sectionTitle("Your results"));
    LinearLayout results=vbox(); results.setPadding(dp(14),dp(12),dp(14),dp(12)); results.setBackground(shape(CARD,18));
    double bmi=bmiValue(), whtr=waistHeightRatio(), whr=waistHipRatio();
    TextView bmiLine=bold("BMI  " + String.format(Locale.US,"%.1f",bmi) + "  •  " + bmiCategory(bmi),17); bmiLine.setTextColor(bmiColor(bmi)); results.addView(bmiLine);
    TextView whtrLine=text("Waist-to-height ratio  " + String.format(Locale.US,"%.2f",whtr) + "  •  " + whtrCategory(whtr),14,TEXT); results.addView(whtrLine);
    TextView whrLine=text("Waist-to-hip ratio  " + String.format(Locale.US,"%.2f",whr),14,TEXT); results.addView(whrLine);
    TextView risk=text(riskBandText(whtr,whr,sex.getSelectedItemPosition()==0),13,MUTED); results.addView(risk);
    body.addView(results,margins(-1,-2,0,4,0,8));

    body.addView(sectionTitle("Waist-to-hip ratio reference"));
    body.addView(waistHipRiskTable(), margins(-1,-2,0,2,0,10));

    body.addView(sectionTitle("Body composition guide"));
    PhotoBmiScaleView bmiScale = new PhotoBmiScaleView(this, sex.getSelectedItemPosition()==0);

    // Compact responsive Body Composition guide.
    // The artwork itself contains the complete figures; do not reserve
    // a large unused area underneath it.
    int bmiScreenWidth = getResources().getDisplayMetrics().widthPixels;
    int bmiGuideHeight = (int)(bmiScreenWidth * 0.66f);
    bmiGuideHeight = Math.max(dp(240), bmiGuideHeight);
    bmiGuideHeight = Math.min(dp(360), bmiGuideHeight);

    bmiScale.setPadding(dp(6), dp(4), dp(6), dp(4));
    

    LinearLayout.LayoutParams bmiGuideParams =
        new LinearLayout.LayoutParams(-1, bmiGuideHeight);
    bmiGuideParams.setMargins(0, dp(2), 0, 0);

    body.addView(bmiScale, bmiGuideParams);
    sex.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onNothingSelected(android.widget.AdapterView<?> parent){}
      public void onItemSelected(android.widget.AdapterView<?> parent,View v,int pos,long id){bmiScale.setMale(pos==0);profileMeasureImg[0].setImageResource(pos==0?R.drawable.profile_measurement_male:R.drawable.profile_measurement_female);}
    });
    body.addView(text("BMI is a screening measure, not a direct measurement of body fat. The scale below shows your calculated BMI position and the approximate body-composition figure/category it aligns with.",12,MUTED));


    body.addView(sectionTitle("Health integrations"));
    LinearLayout healthBox=vbox(); healthBox.setPadding(dp(14),dp(12),dp(14),dp(12)); healthBox.setBackground(shape(CARD,18));
    boolean healthEnabled=prefs.getBoolean("healthConnectEnabled",false);

    TextView hcStatus=text(
        healthEnabled ? "Health Connect: enabled" : "Health Connect: not enabled",
        14,
        healthEnabled ? GREEN_SOFT : MUTED);
    healthBox.addView(hcStatus);

    healthBox.addView(text(
        "When enabled, Reps100 writes completed strength-training sessions, exercise details and estimated calories burned to Health Connect after each completed routine.",
        12,MUTED));

    Button hc=button(
        healthEnabled ? "Disable Health Connect" : "Enable Health Connect",
        healthEnabled ? CARD_ALT : GREEN_DARK);

    hc.setOnClickListener(v -> {
      boolean currentlyEnabled=prefs.getBoolean("healthConnectEnabled",false);

      if(currentlyEnabled){
        new AlertDialog.Builder(this)
            .setTitle("Disable Health Connect?")
            .setMessage("Reps100 will stop exporting future completed workouts to Health Connect. Workouts already written to Health Connect are not deleted.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Disable",(dialog,which) -> {
              prefs.edit().putBoolean("healthConnectEnabled",false).apply();
              Toast.makeText(
                  this,
                  "Health Connect export disabled.",
                  Toast.LENGTH_SHORT).show();
              showProfile(false);
            })
            .show();
        return;
      }

      if(!HealthConnectBridge.INSTANCE.isAvailable(this)){
        if(HealthConnectBridge.INSTANCE.needsProviderUpdate(this)){
          Toast.makeText(
              this,
              "Health Connect needs to be installed or updated.",
              Toast.LENGTH_LONG).show();
          HealthConnectBridge.INSTANCE.openProvider(this);
        } else {
          Toast.makeText(
              this,
              "Health Connect is not supported on this Android device.",
              Toast.LENGTH_LONG).show();
        }
        return;
      }

      HealthConnectBridge.INSTANCE.hasPermissions(this, granted -> {
        if(granted){
          prefs.edit().putBoolean("healthConnectEnabled",true).apply();
          runOnUiThread(() -> {
            Toast.makeText(
                this,
                "Health Connect export enabled.",
                Toast.LENGTH_SHORT).show();
            showProfile(false);
          });
        } else {
          runOnUiThread(() -> {
            Toast.makeText(
                this,
                "Grant Health Connect permission to enable workout export.",
                Toast.LENGTH_LONG).show();
            healthPermissionLauncher.launch(HealthConnectBridge.INSTANCE.getPermissions());
          });
        }
        return kotlin.Unit.INSTANCE;
      });
    });

    healthBox.addView(hc,margins(-1,dp(50),0,10,0,4));

    healthBox.addView(text(
        healthEnabled
            ? "Reps100 is currently set to export completed workouts. You can disable future exports here at any time."
            : "Health Connect export is currently off. No completed workouts will be exported until you enable it.",
        12,MUTED));
    TextView garmin=bold("Garmin Connect: developer approval required",14); garmin.setTextColor(MUTED); healthBox.addView(garmin);
    healthBox.addView(text("The Garmin connection point is reserved in Reps100, but completed-activity upload remains disabled until Garmin approves the integration and supplies the permitted API/credentials. No Garmin password or secret is stored in the app source.",12,MUTED));
    body.addView(healthBox,margins(-1,-2,0,4,0,10));



    page.addView(scroll(body),new LinearLayout.LayoutParams(-1,0,1));display("profile",page,push);
  }

  class MuscleHighlightView extends View {
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); String muscle;
    MuscleHighlightView(Context c,String m){super(c);muscle=m;setBackground(shape(Color.argb(205,9,28,47),12));}
    protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Color.rgb(210,220,232));c.drawOval(new RectF(w*.36f,h*.08f,w*.64f,h*.28f),p);c.drawRoundRect(new RectF(w*.28f,h*.27f,w*.72f,h*.68f),w*.12f,w*.12f,p);c.drawLine(w*.38f,h*.68f,w*.32f,h*.94f,p);c.drawLine(w*.62f,h*.68f,w*.68f,h*.94f,p);p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(92,166,255));String m=muscle.toLowerCase(Locale.US);RectF r;if(m.contains("chest"))r=new RectF(w*.34f,h*.31f,w*.66f,h*.43f);else if(m.contains("shoulder"))r=new RectF(w*.24f,h*.28f,w*.76f,h*.38f);else if(m.contains("bicep")||m.contains("tricep")||m.contains("arm"))r=new RectF(w*.18f,h*.35f,w*.82f,h*.53f);else if(m.contains("back")||m.contains("trap"))r=new RectF(w*.34f,h*.29f,w*.66f,h*.55f);else if(m.contains("core"))r=new RectF(w*.38f,h*.43f,w*.62f,h*.65f);else if(m.contains("glute"))r=new RectF(w*.34f,h*.58f,w*.66f,h*.72f);else if(m.contains("quad"))r=new RectF(w*.30f,h*.66f,w*.70f,h*.83f);else if(m.contains("ham"))r=new RectF(w*.31f,h*.67f,w*.69f,h*.84f);else if(m.contains("calf"))r=new RectF(w*.28f,h*.80f,w*.72f,h*.94f);else r=new RectF(w*.34f,h*.34f,w*.66f,h*.70f);c.drawRoundRect(r,dp(5),dp(5),p);p.setColor(TEXT);p.setTextSize(dp(9));p.setTextAlign(Paint.Align.CENTER);c.drawText(muscle.toUpperCase(Locale.US),w/2,h-dp(3),p);}
  }

  LinearLayout measurementPortrait(int drawable,String label,boolean female){
    LinearLayout box=vbox();box.setPadding(dp(3),dp(3),dp(3),dp(3));
    FrameLayout frame=new FrameLayout(this);ImageView iv=new ImageView(this);iv.setImageResource(drawable);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setAdjustViewBounds(true);frame.addView(iv,new FrameLayout.LayoutParams(-1,-1));frame.addView(new MeasurementOverlay(this,female),new FrameLayout.LayoutParams(-1,-1));
    box.addView(frame,new LinearLayout.LayoutParams(-1,dp(340)));TextView cap=text(label,11,MUTED);cap.setGravity(Gravity.CENTER);box.addView(cap,new LinearLayout.LayoutParams(-1,dp(28)));return box;
  }

  class MeasurementOverlay extends View{
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    final boolean female;
    MeasurementOverlay(Context c, boolean female){super(c);this.female=female;setBackgroundColor(Color.TRANSPARENT);}
    protected void onDraw(Canvas c){
      float w=getWidth(),h=getHeight();
      p.setColor(GREEN); p.setStrokeWidth(dp(2)); p.setStyle(Paint.Style.STROKE);
      p.setPathEffect(new DashPathEffect(new float[]{dp(6),dp(5)},0));

      // Height: exact top-of-head to bottom-of-feet reference.
      float x=w*.08f, top=h*.035f, bottom=h*.965f;
      c.drawLine(x,top,x,bottom,p);
      c.drawLine(x-dp(5),top,x+dp(5),top,p);
      c.drawLine(x-dp(5),bottom,x+dp(5),bottom,p);

      // Dotted waist/hip guides only span the anatomical body width, never the arms.
      float waistY=female?h*.405f:h*.423f;
      float hipY=female?h*.505f:h*.505f;
      float waistLeft=female?w*.38f:w*.35f;
      float waistRight=female?w*.64f:w*.66f;
      float hipLeft=female?w*.31f:w*.31f;
      float hipRight=female?w*.70f:w*.70f;
      c.drawLine(waistLeft,waistY,waistRight,waistY,p);
      c.drawLine(hipLeft,hipY,hipRight,hipY,p);

      p.setPathEffect(null); p.setStyle(Paint.Style.FILL); p.setTextSize(dp(9));
      c.drawText("HEIGHT",dp(1),h*.53f,p);
      c.drawText("WAIST",Math.min(w-dp(42),waistRight+dp(4)),waistY-dp(4),p);
      c.drawText("HIP",Math.min(w-dp(24),hipRight+dp(4)),hipY-dp(4),p);
    }
  }

  View waistHipRiskTable() {
    LinearLayout table=vbox();
    table.setBackground(shape(CARD,16));
    table.setPadding(dp(2),dp(2),dp(2),dp(2));
    LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.HORIZONTAL);
    String[] hs={"MEN","WOMEN","RISK LEVEL"};
    for(String s:hs){TextView t=bold(s,11);t.setGravity(Gravity.CENTER);t.setBackground(shape(BAR,0));head.addView(t,new LinearLayout.LayoutParams(0,dp(38),1));}
    table.addView(head);
    addRiskTableRow(table,"Less than 0.90","Less than 0.85","Low risk of metabolic complications",Color.rgb(97,151,48));
    addRiskTableRow(table,"0.90–0.95","0.85–0.90","Moderate risk of metabolic complications",Color.rgb(224,112,16));
    addRiskTableRow(table,"Above 0.95","Above 0.90","High risk of metabolic complications",Color.rgb(185,35,35));
    TextView note=text("Waist-to-hip ratio = waist circumference ÷ hip circumference. This is a screening indicator, not a diagnosis.",11,MUTED);
    note.setPadding(dp(10),dp(9),dp(10),dp(9)); table.addView(note);
    return table;
  }

  void addRiskTableRow(LinearLayout table,String men,String women,String risk,int color){
    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setBackgroundColor(color);
    String[] cells={men,women,risk};
    for(int i=0;i<cells.length;i++){
      TextView t=text(cells[i],i==2?10:11,TEXT);t.setGravity(Gravity.CENTER);t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);t.setBackground(shape(color,0));t.setPadding(dp(5),dp(6),dp(5),dp(6));
      row.addView(t,new LinearLayout.LayoutParams(0,dp(72),1));
    }
    table.addView(row);
  }

  class PhotoBmiScaleView extends View {
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); boolean male;
    PhotoBmiScaleView(Context c, boolean male){super(c);this.male=male;setPadding(0,0,0,0);}
    void setMale(boolean value){male=value;invalidate();}
    protected void onDraw(Canvas c){ super.onDraw(c); int id=male?R.drawable.profile_bmi_male:R.drawable.profile_bmi_female; Bitmap b=BitmapFactory.decodeResource(getResources(),id); if(b==null)return;
      // Fit the complete source artwork above the BMI scale with a small
      // top inset so the real heads are never clipped.
      float topInset=dp(4);
      float scaleAreaH=getHeight()-dp(42);
      float scale=Math.min(
          (getWidth()-dp(8))/(float)b.getWidth(),
          scaleAreaH/(float)b.getHeight());
      float dw=b.getWidth()*scale, dh=b.getHeight()*scale;
      RectF dst=new RectF(
          (getWidth()-dw)/2f,
          topInset,
          (getWidth()+dw)/2f,
          topInset+dh);
      c.drawBitmap(b,new Rect(0,0,b.getWidth(),b.getHeight()),dst,p);

      // Use the heads already present in the source artwork. The previous
      // five coloured circles were overlays and caused the visible dots.
      float y=dst.bottom+dp(8), left=dp(12), right=getWidth()-dp(12); p.setStrokeWidth(dp(3)); p.setColor(Color.rgb(90,166,255)); c.drawLine(left,y,right,y,p);
      String[] labs={"<18.5\nUnderweight","18.5–24.9\nHealthy","25–29.9\nOverweight","30–34.9\nObesity I","35+\nObesity II+"}; p.setTextSize(dp(8));p.setTextAlign(Paint.Align.CENTER);p.setColor(TEXT); for(int i=0;i<5;i++){float x=left+(right-left)*(i+.5f)/5f;String[] a=labs[i].split("\n");c.drawText(a[0],x,y+dp(14),p);c.drawText(a[1],x,y+dp(25),p);}
      double bmi=bmiValue(); double clamped=Math.max(14,Math.min(40,bmi)); float x=left+(float)((clamped-14)/(40-14))*(right-left); p.setColor(GREEN);p.setStyle(Paint.Style.FILL);Path a=new Path();a.moveTo(x,y-dp(3));a.lineTo(x-dp(7),y-dp(15));a.lineTo(x+dp(7),y-dp(15));a.close();c.drawPath(a,p);p.setTextSize(dp(10));c.drawText(String.format(Locale.US,"BMI %.1f",bmi),x,y-dp(19),p);p.setTextAlign(Paint.Align.LEFT);
    }
  }

  class BmiScaleView extends View{
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);BmiScaleView(Context c){super(c);}
    protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();double bmi=bmiValue();int active=bmi<18.5?0:bmi<25?1:bmi<30?2:bmi<35?3:4;int[] cs={Color.rgb(78,166,90),Color.rgb(129,181,67),Color.rgb(231,196,54),Color.rgb(241,139,42),Color.rgb(224,70,55)};String[] labs={"<18.5","18.5–24.9","25–29.9","30–34.9","35+"};for(int i=0;i<5;i++){float cx=(i+.5f)*w/5f;p.setColor(cs[i]);p.setStyle(Paint.Style.FILL);float bodyW=w*.055f*(1+i*.18f);c.drawCircle(cx,h*.22f,w*.025f,p);c.drawRoundRect(new RectF(cx-bodyW,h*.32f,cx+bodyW,h*.72f),bodyW,bodyW,p);p.setStrokeWidth(w*.025f);c.drawLine(cx-bodyW*.45f,h*.70f,cx-bodyW*.55f,h*.90f,p);c.drawLine(cx+bodyW*.45f,h*.70f,cx+bodyW*.55f,h*.90f,p);if(i==active){p.setColor(GREEN);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));c.drawRect(i*w/5f+dp(3),dp(3),(i+1)*w/5f-dp(3),h-dp(3),p);}p.setStyle(Paint.Style.FILL);p.setColor(TEXT);p.setTextSize(dp(9));p.setTextAlign(Paint.Align.CENTER);c.drawText(labs[i],cx,h*.98f,p);}p.setTextAlign(Paint.Align.LEFT);}
  }

  double bmiValue(){double m=storedHeightCm()/100.0;return m>0?profileWeightKg()/(m*m):0;}
  String bmiCategory(double b){if(b<18.5)return "Underweight";if(b<25)return "Healthy weight";if(b<30)return "Overweight";if(b<35)return "Obesity class 1";if(b<40)return "Obesity class 2";return "Obesity class 3";}
  int bmiColor(double b){if(b>=18.5&&b<25)return Color.rgb(99,190,111);if(b<18.5)return Color.rgb(95,166,220);if(b<30)return Color.rgb(229,198,66);if(b<35)return Color.rgb(240,145,49);return Color.rgb(226,80,67);}
  double waistHeightRatio(){return storedHeightCm()>0?storedWaistCm()/storedHeightCm():0;}
  double waistHipRatio(){return storedHipCm()>0?storedWaistCm()/storedHipCm():0;}
  String whtrCategory(double r){if(r<0.4)return "Below reference range";if(r<0.5)return "Healthy central adiposity";if(r<0.6)return "Increased central adiposity";return "High central adiposity";}
  String riskBandText(double whtr,double whr,boolean male){String central=whtr<0.5?"No increased central-adiposity risk band":whtr<0.6?"Increased central-adiposity risk band":"Further increased central-adiposity risk band";String ratio=male?(whr<0.90?"waist-to-hip ratio below the commonly used 0.90 public-health threshold":"waist-to-hip ratio at or above the commonly used 0.90 public-health threshold"):(whr<0.85?"waist-to-hip ratio below the commonly used 0.85 public-health threshold":"waist-to-hip ratio at or above the commonly used 0.85 public-health threshold");return central+"; "+ratio+". These are population screening bands and do not predict an individual's heart attack or cardiovascular mortality.";}

  TextView profileLabel(String label) {
    TextView t = text(label, 13, MUTED);
    t.setPadding(dp(6), dp(6), dp(6), dp(2));
    return t;
  }

  LinearLayout profilePortrait(int drawable, String label) {
    LinearLayout box = vbox();
    box.setPadding(dp(5),dp(5),dp(5),dp(5));
    ImageView iv = new ImageView(this); iv.setImageResource(drawable); iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
    box.addView(iv, new LinearLayout.LayoutParams(-1, dp(180)));
    TextView cap=text(label,11,MUTED);cap.setGravity(Gravity.CENTER);box.addView(cap,new LinearLayout.LayoutParams(-1,dp(28)));
    return box;
  }

  void styleTwo(Button a, Button b, boolean first) {
    setChipSelected(a, first);
    setChipSelected(b, !first);
  }

  class BodyMeasurementView extends View {
    final boolean female;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    BodyMeasurementView(Context c, boolean female) {
      super(c);
      this.female = female;
      setBackgroundColor(BG);
    }

    @Override protected void onDraw(Canvas c) {
      super.onDraw(c);
      float w = getWidth(), h = getHeight();
      float cx = w * 0.54f;
      float headY = h * 0.16f;
      float footY = h * 0.88f;

      p.setStyle(Paint.Style.FILL);
      p.setColor(Color.rgb(215, 221, 216));

      // Head
      c.drawCircle(cx, headY, w * 0.075f, p);

      // Neck
      RectF neck = new RectF(cx - w*0.035f, headY + w*0.07f,
          cx + w*0.035f, headY + w*0.11f);
      c.drawRoundRect(neck, dp(8), dp(8), p);

      // Torso is deliberately body-shaped rather than a stick figure.
      Path torso = new Path();
      float shoulder = female ? w*0.18f : w*0.22f;
      float waist = female ? w*0.105f : w*0.13f;
      float hip = female ? w*0.18f : w*0.15f;
      float yTop = h*0.30f, yWaist = h*0.52f, yHip = h*0.63f;
      torso.moveTo(cx - shoulder, yTop);
      torso.quadTo(cx - shoulder*0.8f, h*0.39f, cx - waist, yWaist);
      torso.quadTo(cx - hip, h*0.59f, cx - hip, yHip);
      torso.lineTo(cx + hip, yHip);
      torso.quadTo(cx + hip, h*0.59f, cx + waist, yWaist);
      torso.quadTo(cx + shoulder*0.8f, h*0.39f, cx + shoulder, yTop);
      torso.close();
      c.drawPath(torso, p);

      // Arms
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeWidth(w*0.075f);
      c.drawLine(cx - shoulder*0.95f, h*0.34f, cx - w*0.27f, h*0.59f, p);
      c.drawLine(cx + shoulder*0.95f, h*0.34f, cx + w*0.27f, h*0.59f, p);

      // Legs
      p.setStrokeWidth(w*0.095f);
      c.drawLine(cx - hip*0.45f, yHip, cx - w*0.12f, footY, p);
      c.drawLine(cx + hip*0.45f, yHip, cx + w*0.12f, footY, p);

      // Measurement lines
      p.setColor(GREEN);
      p.setStrokeWidth(dp(2));
      p.setStyle(Paint.Style.STROKE);

      float xHeight = w*0.13f;
      c.drawLine(xHeight, headY - w*0.08f, xHeight, footY + w*0.02f, p);
      c.drawLine(xHeight-dp(7), headY-w*0.08f, xHeight+dp(7), headY-w*0.08f, p);
      c.drawLine(xHeight-dp(7), footY+w*0.02f, xHeight+dp(7), footY+w*0.02f, p);

      float yMeasure = yWaist;
      c.drawLine(cx - hip - dp(7), yMeasure, cx + hip + dp(7), yMeasure, p);
      c.drawLine(cx - hip - dp(7), yMeasure, cx - hip + dp(1), yMeasure-dp(5), p);
      c.drawLine(cx - hip - dp(7), yMeasure, cx - hip + dp(1), yMeasure+dp(5), p);
      c.drawLine(cx + hip + dp(7), yMeasure, cx + hip - dp(1), yMeasure-dp(5), p);
      c.drawLine(cx + hip + dp(7), yMeasure, cx + hip - dp(1), yMeasure+dp(5), p);

      p.setStyle(Paint.Style.FILL);
      p.setTextSize(dp(10));
      c.drawText("HEIGHT", dp(4), h*0.55f, p);
      c.drawText("WAIST", cx - dp(20), yMeasure - dp(9), p);

      p.setTextSize(dp(11));
      p.setColor(MUTED);
      c.drawText(female ? "FEMALE" : "MALE", cx - dp(21), h*0.97f, p);
    }
  }

  double storedHeightCm() {
    if (prefs.contains("heightCm")) return prefs.getFloat("heightCm", 170f);
    return parseDouble(prefs.getString("height", ""), 170);
  }

  double storedWaistCm() {
    if (prefs.contains("waistCm")) return prefs.getFloat("waistCm", 80f);
    return parseDouble(prefs.getString("waist", ""), 80);
  }

  double storedHipCm() {
    return prefs.contains("hipCm") ? prefs.getFloat("hipCm", 95f) : 95.0;
  }

  double profileWeightKg() {
    if (prefs.contains("profileWeightKg")) return prefs.getFloat("profileWeightKg", 70f);
    double old = prefs.getFloat("profileWeight", 70f);
    String oldUnit = prefs.getString("profileWeightUnit", "kg");
    return "lb".equals(oldUnit) ? old * 0.45359237 : old;
  }

  String formatNumber(double d) {
    if (Math.abs(d - Math.rint(d)) < 0.05) return String.valueOf((int)Math.rint(d));
    return String.format(Locale.US, "%.1f", d);
  }

  // ---------- Completed routines ----------
  JSONArray loadRecords() {
    try { return new JSONArray(prefs.getString("records", "[]")); }
    catch (Exception e) { return new JSONArray(); }
  }

  // Completed Daily 100 challenges share the Completed Workouts calendar and totals.
  // Their data remains in v28DailyRecords, so the history cards are not duplicated.
  ArrayList<JSONObject> completedDailyRecordsOn(String key) {
    ArrayList<JSONObject> out = new ArrayList<>();
    JSONObject day = dailyRecords().optJSONObject(key);
    if (day == null) return out;
    Iterator<String> names = day.keys();
    while (names.hasNext()) {
      String name = names.next();
      JSONObject record = day.optJSONObject(name);
      if (record == null || dailyTotal(record) < 100) continue;
      JSONObject summary = new JSONObject();
      try {
        summary.put("date", key);
        summary.put("routine", "Daily 100 • " + name);
        summary.put("calories", dailyRecordedCalories(name, record));
        summary.put("durationSec", dailyDurationSeconds(record));
        summary.put("timestamp", record.optLong("completedAt", 0));
      } catch (JSONException ignored) {}
      out.add(summary);
    }
    return out;
  }

  long dailyDurationSeconds(JSONObject record) {
    JSONArray durations = record == null ? null : record.optJSONArray("setDurations");
    if (durations == null) return 0;
    double total = 0;
    for (int i = 0; i < durations.length(); i++) total += Math.max(0, durations.optDouble(i, 0));
    return Math.round(total);
  }

  boolean hasWorkoutOn(String key) {
    if (!completedDailyRecordsOn(key).isEmpty()) return true;
    JSONArray a = loadRecords();
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (o != null && key.equals(o.optString("date"))) return true;
    }
    return false;
  }

  double caloriesOn(String key) {
    double sum = 0;
    JSONArray a = loadRecords();
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (o != null && key.equals(o.optString("date"))) sum += o.optDouble("calories", 0);
    }
    for (JSONObject daily : completedDailyRecordsOn(key)) sum += daily.optDouble("calories", 0);
    return sum;
  }

  ArrayList<JSONObject> recordsOn(String key) {
    ArrayList<JSONObject> out = new ArrayList<>();
    JSONArray a = loadRecords();
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (o != null && key.equals(o.optString("date"))) out.add(o);
    }
    out.addAll(completedDailyRecordsOn(key));
    return out;
  }
  int routineColor(int index){int[] colors={Color.rgb(62,142,230),Color.rgb(80,184,120),Color.rgb(232,169,55),Color.rgb(190,102,220),Color.rgb(231,96,110),Color.rgb(64,190,194)};return colors[Math.abs(index)%colors.length];}

  int currentStreak() {
    Calendar c = Calendar.getInstance();
    String today = dateKey.format(c.getTime());

    // If today has not been completed yet, yesterday may still be the live streak.
    if (!hasWorkoutOn(today)) c.add(Calendar.DATE, -1);

    int n = 0;
    while (hasWorkoutOn(dateKey.format(c.getTime()))) {
      n++;
      c.add(Calendar.DATE, -1);
    }
    return n;
  }

  double currentStreakCalories() {
    Calendar c=Calendar.getInstance(); if(!hasWorkoutOn(dateKey.format(c.getTime()))) c.add(Calendar.DATE,-1);
    double total=0; while(hasWorkoutOn(dateKey.format(c.getTime()))){total+=caloriesOn(dateKey.format(c.getTime()));c.add(Calendar.DATE,-1);} return total;
  }

  double bestStreakCalories() {
    String install=prefs.getString("installDate",dateKey.format(new Date()));
    try{Date start=dateKey.parse(install);Calendar c=Calendar.getInstance();c.setTime(start);Calendar end=Calendar.getInstance();int run=0,best=0;double runCal=0,bestCal=0;while(!c.after(end)){String k=dateKey.format(c.getTime());if(hasWorkoutOn(k)){run++;runCal+=caloriesOn(k);if(run>best){best=run;bestCal=runCal;}else if(run==best&&runCal>bestCal){bestCal=runCal;}}else{run=0;runCal=0;}c.add(Calendar.DATE,1);}return bestCal;}catch(Exception e){return 0;}
  }

  int bestStreak() {
    String install = prefs.getString("installDate", dateKey.format(new Date()));
    try {
      Date start = dateKey.parse(install);
      Calendar c = Calendar.getInstance();
      c.setTime(start);
      Calendar end = Calendar.getInstance();
      int best = 0, run = 0;
      while (!c.after(end)) {
        if (hasWorkoutOn(dateKey.format(c.getTime()))) {
          run++;
          best = Math.max(best, run);
        } else run = 0;
        c.add(Calendar.DATE, 1);
      }
      return best;
    } catch (Exception e) {
      return 0;
    }
  }

  void showHistory(boolean push) {
    LinearLayout page = pageShell("Completed");
    LinearLayout body = contentColumn();

    final Calendar[] anchor = {Calendar.getInstance()};
    midnight(anchor[0]);

    LinearLayout nav = new LinearLayout(this);
    nav.setOrientation(LinearLayout.HORIZONTAL); nav.setGravity(Gravity.CENTER_VERTICAL);
    Button prev = button("‹", CARD); prev.setTextSize(24);
    Button next = button("›", CARD); next.setTextSize(24);
    TextView weekTitle = text("", 17, TEXT); weekTitle.setGravity(Gravity.CENTER);
    nav.addView(prev, new LinearLayout.LayoutParams(dp(46), dp(46)));
    nav.addView(weekTitle, new LinearLayout.LayoutParams(0, dp(46), 1));
    nav.addView(next, new LinearLayout.LayoutParams(dp(46), dp(46)));
    body.addView(nav);

    TextView yearTitle = bold("", 17); yearTitle.setTextColor(GREEN); yearTitle.setGravity(Gravity.CENTER);
    body.addView(yearTitle, new LinearLayout.LayoutParams(-1, dp(34)));

    HorizontalScrollView calendarScroll = new HorizontalScrollView(this);
    calendarScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout days = new LinearLayout(this); days.setOrientation(LinearLayout.HORIZONTAL);
    calendarScroll.addView(days); body.addView(calendarScroll, margins(-1, dp(80), 0, 4, 0, 8));

    body.addView(sectionTitle("Calories burned"));

    LinearLayout chartOuter = new LinearLayout(this); chartOuter.setOrientation(LinearLayout.HORIZONTAL);
    chartOuter.setPadding(dp(4),dp(6),dp(4),dp(4)); chartOuter.setBackground(shape(CARD, 18));
    LinearLayout leftScale = scaleColumn();
    LinearLayout chart = new LinearLayout(this); chart.setOrientation(LinearLayout.HORIZONTAL); chart.setGravity(Gravity.BOTTOM); chart.setPadding(dp(4),dp(6),dp(4),dp(4));
    LinearLayout rightScale = scaleColumn();
    chartOuter.addView(leftScale,new LinearLayout.LayoutParams(dp(36),-1));
    chartOuter.addView(chart,new LinearLayout.LayoutParams(0,-1,1));
    chartOuter.addView(rightScale,new LinearLayout.LayoutParams(dp(36),-1));
    body.addView(chartOuter, margins(-1, dp(242), 0, 0, 0, 12));

    TextView selectedTitle = sectionTitle(""); body.addView(selectedTitle);
    LinearLayout details = vbox(); body.addView(details);

    final String[] selectedDate = {dateKey.format(anchor[0].getTime())};
    final Runnable[] render = new Runnable[1];
    render[0] = () -> {
      days.removeAllViews(); chart.removeAllViews();

      Calendar first = (Calendar)anchor[0].clone(); first.add(Calendar.DATE, -6);
      SimpleDateFormat dateWithYear = new SimpleDateFormat("d MMM yyyy", Locale.US);
      weekTitle.setText(shortDate.format(first.getTime()) + " – " + shortDate.format(anchor[0].getTime()));
      yearTitle.setText(new SimpleDateFormat("yyyy", Locale.US).format(anchor[0].getTime()));

      String install = prefs.getString("installDate", dateKey.format(new Date()));
      for (int i = -6; i <= 0; i++) {
        Calendar d = (Calendar)anchor[0].clone(); d.add(Calendar.DATE, i);
        String key = dateKey.format(d.getTime()); boolean allowed = key.compareTo(install) >= 0; boolean selected = key.equals(selectedDate[0]);

        double dayKcal = allowed ? caloriesOn(key) : 0;
        TextView day = text(dayLabel.format(d.getTime()) + "\n" + new SimpleDateFormat("d", Locale.US).format(d.getTime()), 12, allowed ? TEXT : MUTED);
        day.setGravity(Gravity.CENTER);
        int dayColor = dayKcal > 0 ? tieredBarColor((float)Math.min(1000.0,dayKcal)) : CARD;
        if(selected) dayColor = dayKcal > 0 ? dayColor : GREEN_DARK;
        day.setBackground(shape(dayColor, 14));
        if (allowed) day.setOnClickListener(v -> { selectedDate[0]=key; renderHistoryDay(key,details,selectedTitle); render[0].run(); });
        days.addView(day, margins(dp(64), dp(70), 3, 0, 3, 0));

        LinearLayout slot = vbox(); slot.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); slot.setBackgroundColor(Color.TRANSPARENT);
        double kcal = allowed ? caloriesOn(key) : 0; double clipped = Math.min(1000.0, Math.max(0, kcal));
        int barH=(int)Math.max(dp(4),dp(160)*clipped/1000.0);
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.VERTICAL);bar.setGravity(Gravity.BOTTOM);ArrayList<JSONObject> dayRecords=recordsOn(key);double dayTotal=Math.max(1.0,kcal);for(int ri=0;ri<dayRecords.size();ri++){double rk=dayRecords.get(ri).optDouble("calories",0);View seg=new View(this);seg.setBackground(shape(routineColor(ri),4));int sh=Math.max(dp(3),(int)(barH*(rk/dayTotal)));bar.addView(seg,new LinearLayout.LayoutParams(dp(28),sh));}if(dayRecords.isEmpty()){View emptyBar=new View(this);emptyBar.setBackground(shape(CARD_ALT,4));bar.addView(emptyBar,new LinearLayout.LayoutParams(dp(28),dp(4)));}
        if (allowed) bar.setOnClickListener(v -> { selectedDate[0]=key; renderHistoryDay(key,details,selectedTitle); render[0].run(); });
        slot.addView(bar,new LinearLayout.LayoutParams(dp(28),barH));
        TextView dl=text(dayLabel.format(d.getTime()).substring(0,1)+"\n"+new SimpleDateFormat("d",Locale.US).format(d.getTime()),10,MUTED);dl.setGravity(Gravity.CENTER);
        slot.addView(dl,new LinearLayout.LayoutParams(dp(36),dp(46)));
        chart.addView(slot,new LinearLayout.LayoutParams(0,-1,1));
      }
      renderHistoryDay(selectedDate[0],details,selectedTitle);
      prev.setEnabled(canMoveWeek(anchor[0],-7)); next.setEnabled(canMoveWeek(anchor[0],7));
      next.setAlpha(next.isEnabled()?1f:.35f); prev.setAlpha(prev.isEnabled()?1f:.35f);
    };

    prev.setOnClickListener(v->{anchor[0].add(Calendar.DATE,-7);selectedDate[0]=dateKey.format(anchor[0].getTime());render[0].run();});
    next.setOnClickListener(v->{anchor[0].add(Calendar.DATE,7);if(anchor[0].after(Calendar.getInstance())){anchor[0].setTime(new Date());midnight(anchor[0]);}selectedDate[0]=dateKey.format(anchor[0].getTime());render[0].run();});
    render[0].run();

    page.addView(scroll(body), new LinearLayout.LayoutParams(-1,0,1)); display("history",page,push);
  }

  LinearLayout scaleColumn() {
    LinearLayout col=vbox(); col.setGravity(Gravity.CENTER_HORIZONTAL); int[] vals={1000,800,600,400,200,0};
    for(int v:vals){TextView t=text(String.valueOf(v),9,MUTED);t.setGravity(Gravity.CENTER);col.addView(t,new LinearLayout.LayoutParams(-1,0,1));}
    return col;
  }

  int tieredBarColor(float kcal) {
    if (kcal >= 850) return Color.rgb(239,72,72);
    if (kcal >= 700) return Color.rgb(245,126,45);
    if (kcal >= 550) return Color.rgb(245,179,42);
    if (kcal >= 400) return Color.rgb(213,197,52);
    if (kcal >= 200) return Color.rgb(84,176,94);
    return Color.rgb(60,142,190);
  }

  GradientDrawable tieredBar(float kcal) {
    return shape(tieredBarColor(kcal),8);
  }

  void midnight(Calendar c) {
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
  }

  boolean canMoveWeek(Calendar anchor, int delta) {
    Calendar n = (Calendar)anchor.clone();
    n.add(Calendar.DATE, delta);
    String install = prefs.getString("installDate", dateKey.format(new Date()));
    String today = dateKey.format(new Date());
    String end = dateKey.format(n.getTime());
    if (delta < 0) {
      Calendar first = (Calendar)n.clone();
      first.add(Calendar.DATE, -6);
      return dateKey.format(n.getTime()).compareTo(install) >= 0
          || dateKey.format(first.getTime()).compareTo(install) >= 0;
    }
    return end.compareTo(today) <= 0;
  }

  void renderHistoryDay(String key, LinearLayout details, TextView title) {
    details.removeAllViews();
    double total = caloriesOn(key);
    title.setText("Completed on " + displayDate(key) + "\nCalories burned - " + String.format(Locale.US, "%.1f kcal", total));

    JSONArray records = loadRecords();
    int found = 0;
    for (int i = records.length() - 1; i >= 0; i--) {
      JSONObject o = records.optJSONObject(i);
      if (o == null || !key.equals(o.optString("date"))) continue;
      found++;

      LinearLayout card = vbox();
      card.setPadding(dp(16), dp(12), dp(16), dp(12));
      card.setBackground(shape(CARD_ALT, 20));

      card.addView(bold(o.optString("routine", "100-Rep Routine"), 18));
      long secs = o.optLong("durationSec", 0);
      card.addView(text(displayDateTime(o.optLong("timestamp",0), o.optString("dateTime", "")) + "  •  "
          + durationString(secs) + "  •  "
          + String.format(Locale.US, "%.1f kcal", o.optDouble("calories", 0)),
          13, MUTED));

      JSONArray ex = o.optJSONArray("exercises");
      if (ex != null) {
        for (int j = 0; j < ex.length(); j++) {
          JSONObject x = ex.optJSONObject(j);
          if (x == null) continue;
          card.addView(text("• " + x.optString("name")
              + " — " + x.optInt("reps", 100) + " reps"
              + " — " + formatLoad(x)
              + " — " + String.format(Locale.US, "%.1f kcal", x.optDouble("calories", 0)),
              12, TEXT));
        }
      }

      Button del = outlineButton("Delete Record");
      del.setTextColor(RED);
      final int recordIndex = i;
      del.setOnClickListener(v -> confirmDeleteRecord(recordIndex));
      LinearLayout.LayoutParams deleteParams = margins(-2, dp(48), 0, 8, 0, 0);
      deleteParams.gravity = Gravity.CENTER_HORIZONTAL;
      del.setMinWidth(0);
      del.setMinimumWidth(0);
      del.setPadding(dp(20), 0, dp(20), 0);
      card.addView(del, deleteParams);

      details.addView(card, margins(-1, -2, 0, 5, 0, 8));
    }

    // Completed Daily 100 exercises appear in the same history without duplicating records.
    JSONObject dailyDay = dailyRecords().optJSONObject(key);
    if (dailyDay != null) {
      Iterator<String> dailyNames = dailyDay.keys();
      while (dailyNames.hasNext()) {
        String exerciseName = dailyNames.next();
        JSONObject dailyExercise = dailyDay.optJSONObject(exerciseName);
        if (dailyTotal(dailyExercise) < 100) continue;
        found++;
        LinearLayout card = vbox();
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(shape(CARD_ALT, 20));
        card.addView(bold("Daily 100 • " + exerciseName, 18));
        JSONArray sets = dailyExercise.optJSONArray("sets");
        int setCount = sets == null ? 0 : sets.length();
        long finishedAt = dailyExercise.optLong("completedAt", 0);
        String completedWhen = finishedAt > 0
            ? displayDateTime(finishedAt, "") : displayDate(key);
        card.addView(text(completedWhen + "  •  " + dailyTotal(dailyExercise)
            + " reps  •  " + setCount + " sets", 13, MUTED));
        long activeSeconds = dailyDurationSeconds(dailyExercise);
        if (activeSeconds > 0) card.addView(text("Active exercise time: " + durationString(activeSeconds), 12, TEXT));
        card.addView(text("Estimated calories burned: "+String.format(Locale.US,"%.1f kcal",dailyRecordedCalories(exerciseName,dailyExercise)),14,GREEN));
        card.addView(text("Daily 100 challenge completed", 12, TEXT));
        Button del = outlineButton("Delete Record");
        del.setTextColor(RED);
        final String recordDate = key;
        final String recordExercise = exerciseName;
        del.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Delete completed Daily 100?")
            .setMessage("This permanently removes " + recordExercise
                + " and its recorded sets from " + displayDate(recordDate)
                + ". Challenge and achievement totals will also update.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", (dialog, which) -> {
              JSONObject all = dailyRecords();
              JSONObject day = all.optJSONObject(recordDate);
              if (day != null) {
                day.remove(recordExercise);
                try {
                  if (day.length() == 0) all.remove(recordDate);
                  else all.put(recordDate, day);
                } catch (JSONException ignored) {}
                saveDailyRecords(all);
              }
              showHistory(false);
            }).show());
        LinearLayout.LayoutParams deleteParams = margins(-2, dp(48), 0, 8, 0, 0);
      deleteParams.gravity = Gravity.CENTER_HORIZONTAL;
      del.setMinWidth(0);
      del.setMinimumWidth(0);
      del.setPadding(dp(20), 0, dp(20), 0);
      card.addView(del, deleteParams);
        details.addView(card, margins(-1, -2, 0, 5, 0, 8));
      }
    }
    if (found == 0) {
      details.addView(text("No completed workouts on this date.", 15, MUTED));
    }
  }

  String displayDate(String key) {
    try { return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(dateKey.parse(key)); }
    catch(Exception e){ return key; }
  }
  String displayDateTime(long timestamp, String fallback) {
    if(timestamp>0) return new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.US).format(new Date(timestamp));
    try { return new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.US).format(dateTime.parse(fallback)); }
    catch(Exception e){ return fallback; }
  }

  String durationString(long sec) {
    long h = sec / 3600;
    long m = (sec % 3600) / 60;
    long s = sec % 60;
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
  }

  void confirmDeleteRecord(int index) {
    new AlertDialog.Builder(this)
        .setTitle("Delete completed routine?")
        .setMessage("This removes the workout from your history and calorie chart.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Delete", (d, which) -> {
          JSONArray old = loadRecords();
          JSONArray out = new JSONArray();
          for (int i = 0; i < old.length(); i++) if (i != index) out.put(old.opt(i));
          prefs.edit().putString("records", out.toString()).apply();
          showHistory(false);
        }).show();
  }

  // ---------- Parsing ----------
  double parseDouble(String s, double fallback) {
    try { return Double.parseDouble(s.trim()); }
    catch (Exception e) { return fallback; }
  }

  double round(double x) { return Math.round(x * 10.0) / 10.0; }
}
