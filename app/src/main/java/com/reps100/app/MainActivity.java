package com.reps100.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.text.*;
import android.text.method.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.*;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {

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
  int libraryScrollY = 0;
  String libraryQuery = "";
  String libraryEquipment = "All";
  String libraryMuscle = "All";

  ArrayList<ExerciseData.Exercise> routine = new ArrayList<>();
  HashMap<String, Double> routineWeights = new HashMap<>();
  HashMap<String, String> routineWeightUnits = new HashMap<>();

  int selectedSets = 5;
  int minReps = 10;
  int maxReps = 30;

  int currentExercise = 0;
  int currentSet = 0;
  ArrayList<Integer> currentSplit = new ArrayList<>();
  long workoutStarted = 0;

  SharedPreferences prefs;
  SimpleDateFormat dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
  SimpleDateFormat dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
  SimpleDateFormat dayLabel = new SimpleDateFormat("EEE", Locale.US);
  SimpleDateFormat shortDate = new SimpleDateFormat("d MMM", Locale.US);

  int dp(float v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(BAR);
    getWindow().setNavigationBarColor(Color.BLACK);
    if (Build.VERSION.SDK_INT >= 30) getWindow().setStatusBarColor(BAR);

    prefs = getSharedPreferences("reps100", 0);
    selectedSets = prefs.getInt("sets", 5);
    minReps = prefs.getInt("minReps", 10);
    maxReps = prefs.getInt("maxReps", 30);

    if (!prefs.contains("installDate")) {
      long firstInstall = System.currentTimeMillis();
      try {
        firstInstall = getPackageManager().getPackageInfo(getPackageName(), 0).firstInstallTime;
      } catch (Exception ignored) {}
      prefs.edit().putString("installDate", dateKey.format(new Date(firstInstall))).apply();
    }

    if (Build.VERSION.SDK_INT >= 33) {
      getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
          android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
    }

    showHome(false);
  }

  @Override public void onBackPressed() { goBack(); }

  void goBack() {
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
      case "workout": showWorkout(false); break;
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

  Button button(String label, int color) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextColor(TEXT);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    b.setBackground(shape(color, 18));
    b.setPadding(dp(10), 0, dp(10), 0);
    b.setMinHeight(0);
    b.setMinimumHeight(0);
    return b;
  }

  Button outlineButton(String label) {
    Button b = button(label, Color.TRANSPARENT);
    b.setBackground(outline(CARD_ALT, 1, 18));
    return b;
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
    back.setOnClickListener(v -> goBack());
    bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(64)));

    TextView h = text(title, title.length() > 20 ? 21 : 25, TEXT);
    h.setGravity(Gravity.CENTER);
    h.setSingleLine(false);
    bar.addView(h, new LinearLayout.LayoutParams(0, dp(64), 1));

    Space balance = new Space(this);
    bar.addView(balance, new LinearLayout.LayoutParams(dp(52), dp(64)));
    return bar;
  }

  LinearLayout pageShell(String title) {
    LinearLayout page = vbox();
    page.addView(titleBar(title), new LinearLayout.LayoutParams(-1, dp(78)));
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

    TextView brand = text("Reps100", 32, TEXT);
    brand.setGravity(Gravity.CENTER);
    brand.setPadding(dp(8), dp(12), dp(8), 0);
    all.addView(brand);

    TextView strap = text("100 reps to a more conditioned body. With a unique rep randomiser that keeps your sets interesting, which you can tailor to match your level.", 14, GREEN);
    strap.setGravity(Gravity.CENTER);
    strap.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    strap.setPadding(dp(18), dp(6), dp(18), dp(10));
    all.addView(strap);

    addStreakCard(all);
    all.addView(space(8));

    addMenuRow(all,
        menuCard("sliders", "Sets & Reps", v -> showSetsManager(true)),
        menuCard("plus", "Create Routine", v -> showRoutine(true)));

    addMenuRow(all,
        menuCard("list", "Routines", v -> showRoutineList(true)),
        menuCard("dumbbell", "Exercises", v -> showLibrary(true)));

    addMenuRow(all,
        menuCard("check", "Completed", v -> showHistory(true)),
        menuCard("smile", "Profile", v -> showProfile(true)));

    TextView offline = text("OFFLINE FIRST  •  YOUR DATA STAYS ON DEVICE", 12, MUTED);
    offline.setGravity(Gravity.CENTER);
    offline.setPadding(dp(8), dp(18), dp(8), dp(8));
    all.addView(offline);

    display("home", scroll(all), push);
  }

  View menuCard(String icon, String label, View.OnClickListener listener) {
    LinearLayout c = vbox();
    c.setGravity(Gravity.CENTER);
    c.setPadding(dp(8), dp(10), dp(8), dp(10));
    c.setBackground(shape(GRID_CARD, 20));
    c.setOnClickListener(listener);
    c.setClickable(true);
    c.setFocusable(true);

    MenuIconView circle = new MenuIconView(this, icon);
    circle.setBackground(shape(GREEN_DARK, 999));
    c.addView(circle, new LinearLayout.LayoutParams(dp(82), dp(82)));

    TextView lab = bold(label, 14);
    lab.setGravity(Gravity.CENTER);
    lab.setPadding(dp(4), dp(8), dp(4), 0);
    c.addView(lab, new LinearLayout.LayoutParams(-1, dp(36)));
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
      paint.setColor(GREEN_SOFT); paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(3)); paint.setStrokeCap(Paint.Cap.ROUND);
      if ("plus".equals(type)) {
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

  void addStreakCard(LinearLayout p) {
    int current = currentStreak();
    int best = bestStreak();
    LinearLayout card = vbox();
    card.setPadding(dp(16), dp(12), dp(16), dp(12));
    card.setBackground(shape(GREEN_DARK, 18));

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

    p.addView(card, margins(-1, -2, 0, 14, 0, 4));
  }

  // ---------- Sets and reps ----------
  void showSetsManager(boolean push) {
    LinearLayout page = pageShell("Sets and Reps Manager");
    LinearLayout body = contentColumn();

    TextView intro = text("Choose how each exercise's 100 reps are distributed.", 15, MUTED);
    intro.setGravity(Gravity.CENTER);
    body.addView(intro);

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

    Button save = button("Save", GREEN);
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
    card.addView(seek, new LinearLayout.LayoutParams(-1, dp(54)));
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
    LinearLayout page = pageShell("Create New Routine");
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

    Button add = outlineButton("＋  Add Exercise");
    add.setTextColor(GREEN);
    add.setOnClickListener(v -> {
      prefs.edit().putString("draftRoutineName", name.getText().toString()).apply();
      selectingForRoutine = true;
      showLibrary(true);
    });
    body.addView(add, margins(-1, dp(52), 0, 6, 0, 12));

    body.addView(sectionTitle("Exercises in this routine (" + routine.size() + ")"));

    if (routine.isEmpty()) {
      TextView empty = text("No exercises added yet. Tap “Add Exercise” to build your routine.",
          15, TEXT);
      empty.setPadding(dp(18), dp(16), dp(18), dp(16));
      empty.setBackground(shape(CARD_ALT, 18));
      body.addView(empty, margins(-1, -2, 0, 4, 0, 14));
    } else {
      for (ExerciseData.Exercise e : new ArrayList<>(routine)) addRoutineExerciseCard(body, e);
    }

    Button save = button("Save Routine", GREEN);
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
      Button start = button("Start 100-Rep Workout", BLUE);
      start.setOnClickListener(v -> {
        prefs.edit().putString("draftRoutineName", name.getText().toString()).apply();
        if (!validRepRange()) {
          Toast.makeText(this, "Fix Sets & Reps settings before starting.", Toast.LENGTH_LONG).show();
          return;
        }
        prepareWorkout();
        showWorkout(true);
      });
      body.addView(start, margins(-1, dp(54), 0, 0, 0, 10));
    }

    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
    display("routine", page, push);
  }

  void addRoutineExerciseCard(LinearLayout body, ExerciseData.Exercise e) {
    LinearLayout card = vbox();
    card.setPadding(dp(14), dp(10), dp(14), dp(10));
    card.setBackground(shape(CARD, 18));

    LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout texts = vbox(); texts.addView(bold(e.name,17)); texts.addView(text(e.muscle+"  •  "+e.movement+"  •  "+e.equipment,12,MUTED));
    top.addView(texts,new LinearLayout.LayoutParams(0,dp(58),1));
    Button remove=button("×",CARD_ALT);remove.setTextColor(RED);remove.setTextSize(20);
    remove.setOnClickListener(v->{routine.remove(e);routineWeights.remove(e.name);routineWeightUnits.remove(e.name);showRoutine(false);});
    top.addView(remove,new LinearLayout.LayoutParams(dp(44),dp(44))); card.addView(top);

    LinearLayout weightRow=new LinearLayout(this);weightRow.setOrientation(LinearLayout.HORIZONTAL);weightRow.setGravity(Gravity.CENTER_VERTICAL);
    weightRow.addView(text(isBodyweightExercise(e)?"Estimated load":"Weight used",13,MUTED),new LinearLayout.LayoutParams(0,dp(48),1));

    if (isBodyweightExercise(e)) {
      double kg=effectiveBodyweightKg(e); String unit=prefs.getString("profileWeightUnit","kg"); double shown="lb".equals(unit)?kg/0.45359237:kg;
      routineWeights.put(e.name,shown); routineWeightUnits.put(e.name,unit);
      TextView auto=bold(formatNumber(shown)+" "+unit,15); auto.setTextColor(GREEN); auto.setGravity(Gravity.CENTER); auto.setBackground(shape(BG,12));
      weightRow.addView(auto,new LinearLayout.LayoutParams(dp(106),dp(44)));
      TextView tag=text("auto",11,MUTED);tag.setGravity(Gravity.CENTER);weightRow.addView(tag,new LinearLayout.LayoutParams(dp(48),dp(44)));
    } else {
      EditText w=new EditText(this);w.setText(weightValue(e.name));w.setHint("0");w.setTextColor(TEXT);w.setHintTextColor(MUTED);w.setTextSize(15);w.setGravity(Gravity.CENTER);w.setSingleLine();
      w.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);w.setBackground(shape(BG,12));
      w.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){routineWeights.put(e.name,parseDouble(s.toString(),0));}public void afterTextChanged(Editable s){}});
      weightRow.addView(w,new LinearLayout.LayoutParams(dp(76),dp(44)));
      Spinner units=new Spinner(this);String[] unitItems={"kg","lb"};units.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,unitItems));
      String savedUnit=routineWeightUnits.containsKey(e.name)?routineWeightUnits.get(e.name):prefs.getString("profileWeightUnit","kg");
      units.setSelection("lb".equals(savedUnit)?1:0);
      units.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View v,int pos,long id){routineWeightUnits.put(e.name,unitItems[pos]);}});
      weightRow.addView(units,new LinearLayout.LayoutParams(dp(76),dp(46)));
    }
    card.addView(weightRow);
    body.addView(card,margins(-1,-2,0,6,0,8));
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
    if (n.contains("incline push")) return 0.55;
    if (n.contains("decline push")) return 0.80;
    if (n.contains("push-up") || n.contains("push up")) return 0.72;
    if (n.contains("pull-up") || n.contains("chin-up") || n.contains("pull up") || n.contains("chin up")) return 0.95;
    if (n.contains("dip")) return 0.90;
    if (n.contains("glute bridge") || n.contains("hip thrust")) return 0.70;
    if (n.contains("mountain climber") || n.contains("bear crawl")) return 0.65;
    if (n.contains("crunch") || n.contains("sit-up") || n.contains("dead bug") || n.contains("bird dog") || n.contains("russian twist")) return 0.45;
    if (n.contains("leg raise") || n.contains("knee tuck") || n.contains("v-up") || n.contains("scissor")) return 0.60;
    return 1.00;
  }

  double effectiveBodyweightKg(ExerciseData.Exercise e) {
    return profileWeightKg() * bodyweightFactor(e);
  }

  // ---------- Exercise library ----------
  void showLibrary(boolean push) {
    LinearLayout page = pageShell(selectingForRoutine ? "Add Exercise" : "Exercise List");
    LinearLayout body = contentColumn();

    EditText search = field("Search", libraryQuery, InputType.TYPE_CLASS_TEXT);
    body.addView(search);

    String[] equipmentFilter = {"All", "Bodyweight", "Weightlifting"};
    final String[] equipment = {libraryEquipment};
    final String[] muscle = {libraryMuscle};

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
      LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(38), 1);
      ep.setMargins(dp(3),0,dp(3),0);
      newEquip.addView(chip, ep);
    }
    body.addView(newEquip, margins(-1, dp(42), 0, 4, 0, 4));

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
    sc.setOnScrollChangeListener((v, sx, sy, ox, oy) -> libraryScrollY = sy);
    sc.post(() -> sc.scrollTo(0, libraryScrollY));
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

  Button smallChip(String label, boolean selected) {
    Button b = button(label, selected ? GREEN_DARK : Color.TRANSPARENT);
    if (!selected) b.setBackground(outline(CARD_ALT, 1, 14));
    b.setTextSize(12);
    b.setPadding(dp(5),0,dp(5),0);
    return b;
  }

  void styleChipRow(LinearLayout row, String selected) {
    for (int i = 0; i < row.getChildCount(); i++) {
      View v = row.getChildAt(i);
      if (v instanceof Button) {
        Button b = (Button)v;
        boolean active = selected.equals(b.getText().toString());
        b.setBackground(active ? shape(GREEN_DARK, 16) : outline(CARD_ALT, 1, 16));
      }
    }
  }

  void refreshLibrary(LinearLayout list, TextView count, String query,
                      String equipment, String muscleFilter) {
    list.removeAllViews();
    String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
    int shown = 0;

    for (ExerciseData.Exercise e : ExerciseData.ALL) {
      if (!isRepBased(e)) continue;
      if (isVariantDuplicate(e)) continue;

      boolean equipmentOk = "All".equals(equipment)
          || ("Bodyweight".equals(equipment) && "Bodyweight".equalsIgnoreCase(e.equipment))
          || ("Weightlifting".equals(equipment) && !"Bodyweight".equalsIgnoreCase(e.equipment));

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

    ImageView thumb = new ImageView(this);
    setExerciseImage(thumb, e, false);
    thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
    card.addView(thumb, new LinearLayout.LayoutParams(dp(76), dp(66)));

    LinearLayout words = vbox();
    TextView n = bold(e.name, 17);
    TextView meta = text(e.muscle + "  •  " + e.movement, 12, MUTED);
    words.addView(n);
    words.addView(meta);
    card.addView(words, new LinearLayout.LayoutParams(0, dp(70), 1));

    TextView arrow = text("›", 28, MUTED);
    arrow.setGravity(Gravity.CENTER);
    card.addView(arrow, new LinearLayout.LayoutParams(dp(42), dp(70)));

    card.setOnClickListener(v -> showDetail(e, true));
    list.addView(card, margins(-1, dp(84), 0, 4, 0, 4));
  }

  int exerciseDrawable(ExerciseData.Exercise e, boolean midpoint) {
    String suffix = midpoint ? "_mid" : "_start";
    int id = getResources().getIdentifier("reps_photo_" + e.photo + suffix,
        "drawable", getPackageName());
    if (id == 0) {
      id = getResources().getIdentifier("reps_photo_" + e.photo,
          "drawable", getPackageName());
    }
    return id;
  }

  void setExerciseImage(ImageView img, ExerciseData.Exercise e, boolean midpoint) {
    int id = exerciseDrawable(e, midpoint);
    if (id != 0) img.setImageResource(id);
    else img.setImageResource(android.R.drawable.ic_menu_gallery);
    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
  }

  void showDetail(ExerciseData.Exercise e, boolean push) {
    detailExercise = e;
    LinearLayout page = pageShell(e.name);
    LinearLayout body = contentColumn();

    LinearLayout photos = new LinearLayout(this);
    photos.setOrientation(LinearLayout.HORIZONTAL);

    LinearLayout left = imagePanel(e, false, "START / EXTENDED");
    LinearLayout right = imagePanel(e, true, "CONTRACTED / MIDPOINT");
    photos.addView(left, margins(0, -2, 0, 0, 4, 0));
    ((LinearLayout.LayoutParams)left.getLayoutParams()).weight = 1;
    photos.addView(right, margins(0, -2, 4, 0, 0, 0));
    ((LinearLayout.LayoutParams)right.getLayoutParams()).weight = 1;
    body.addView(photos);

    TextView chips = text(e.muscle.toUpperCase(Locale.US) + "  •  "
        + e.equipment.toUpperCase(Locale.US) + "  •  "
        + e.difficulty.toUpperCase(Locale.US), 12, BLUE);
    body.addView(chips);

    body.addView(sectionTitle("Muscles worked"));
    body.addView(text("Primary: " + e.muscle + "\nMovement: " + e.movement, 15, MUTED));

    body.addView(sectionTitle("How to perform"));
    body.addView(text(instructionsFor(e), 15, MUTED));

    if (isBodyweightExercise(e)) {
      String unit=prefs.getString("profileWeightUnit","kg");
      double kg=effectiveBodyweightKg(e);
      double shown="lb".equals(unit)?kg/0.45359237:kg;
      TextView estimate=text("Estimated effective bodyweight load:  "+formatNumber(shown)+" "+unit+"\nCalculated from your profile using an exercise-specific bodyweight factor. This is an approximation, not an external scale weight.",13,TEXT);
      estimate.setPadding(dp(14),dp(12),dp(14),dp(12)); estimate.setBackground(shape(CARD,16));
      body.addView(estimate,margins(-1,-2,0,12,0,8));
    }

    if (selectingForRoutine) {
      Button add = button(routine.contains(e) ? "✓  Already in Routine" : "＋  Add to Routine", BLUE);
      add.setOnClickListener(v -> {
        if (!routine.contains(e)) {
          routine.add(e);
          routineWeights.put(e.name, 0.0);
          routineWeightUnits.put(e.name, prefs.getString("profileWeightUnit", "kg"));
        }
        Toast.makeText(this, "Added to routine.", Toast.LENGTH_SHORT).show();
        selectingForRoutine = false;
        returnToScreen("routine");
      });
      body.addView(add, margins(-1, dp(52), 0, 16, 0, 10));
    }

    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
    display("detail", page, push);
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
    LinearLayout page = pageShell("Routines Library");
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

      LinearLayout card = new LinearLayout(this);
      card.setOrientation(LinearLayout.HORIZONTAL);
      card.setGravity(Gravity.CENTER_VERTICAL);
      card.setPadding(dp(16), dp(12), dp(12), dp(12));
      card.setBackground(shape(CARD_ALT, 20));

      LinearLayout words = vbox();
      TextView n = bold(r.optString("name", "Routine"), 18);
      JSONArray ex = r.optJSONArray("exercises");
      int nEx = ex == null ? 0 : ex.length();
      words.addView(n);
      words.addView(text(nEx + " exercises  •  " + r.optInt("sets", 5) + " sets", 13, MUTED));
      card.addView(words, new LinearLayout.LayoutParams(0, dp(72), 1));

      Button play = button("▶", Color.TRANSPARENT);
      play.setTextColor(GREEN);
      play.setTextSize(24);
      play.setOnClickListener(v -> {
        loadSavedRoutine(r);
        if (routine.isEmpty()) return;
        prepareWorkout();
        showWorkout(true);
      });
      card.addView(play, new LinearLayout.LayoutParams(dp(54), dp(54)));

      Button edit = button("✎", Color.TRANSPARENT);
      edit.setTextColor(TEXT);
      edit.setTextSize(20);
      edit.setOnClickListener(v -> {
        loadSavedRoutine(r);
        showRoutine(true);
      });
      card.addView(edit, new LinearLayout.LayoutParams(dp(54), dp(54)));

      Button del = button("▣", Color.TRANSPARENT);
      del.setTextColor(RED);
      del.setTextSize(20);
      del.setOnClickListener(v -> confirmDeleteRoutine(index));
      card.addView(del, new LinearLayout.LayoutParams(dp(54), dp(54)));

      body.addView(card, margins(-1, dp(100), 0, 7, 0, 7));
    }

    Button create = outlineButton("＋  Create New Routine");
    create.setTextColor(GREEN);
    create.setOnClickListener(v -> {
      routine.clear();
      routineWeights.clear();
      routineWeightUnits.clear();
      prefs.edit().remove("draftRoutineName").apply();
      showRoutine(true);
    });
    body.addView(create, margins(-1, dp(52), 0, 16, 0, 12));

    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
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
      for (ExerciseData.Exercise e : routine) {
        JSONObject o = new JSONObject();
        o.put("name", e.name);
        o.put("weight", routineWeights.containsKey(e.name) ? routineWeights.get(e.name) : 0);
        o.put("unit", routineWeightUnits.containsKey(e.name)
            ? routineWeightUnits.get(e.name)
            : prefs.getString("profileWeightUnit", "kg"));
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
          routineWeights.put(match.name, o.optDouble("weight", 0));
          routineWeightUnits.put(match.name,
              o.optString("unit", prefs.getString("profileWeightUnit", "kg")));
        }
      }
    }
  }

  ExerciseData.Exercise findExercise(String name) {
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
    currentSplit = randomSplit(selectedSets, minReps, maxReps);
    workoutStarted = System.currentTimeMillis();
  }

  void showWorkout(boolean push) {
    if (routine.isEmpty()) {
      showRoutine(false);
      return;
    }

    LinearLayout page = pageShell("100-Rep Workout");
    LinearLayout body = contentColumn();

    ExerciseData.Exercise e = routine.get(currentExercise);
    TextView progress = text("Exercise " + (currentExercise + 1) + " of " + routine.size()
        + "   •   Set " + (currentSet + 1) + " of " + selectedSets, 14, BLUE);
    progress.setGravity(Gravity.CENTER);
    body.addView(progress);

    TextView name = bold(e.name, 28);
    name.setGravity(Gravity.CENTER);
    body.addView(name);

    LinearLayout photos = new LinearLayout(this);
    photos.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout a = imagePanel(e, false, "START");
    LinearLayout b = imagePanel(e, true, "MIDPOINT");
    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1);
    ap.setMargins(0, 0, dp(4), 0);
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1);
    bp.setMargins(dp(4), 0, 0, 0);
    photos.addView(a, ap);
    photos.addView(b, bp);
    body.addView(photos, margins(-1, -2, 0, 10, 0, 8));

    int reps = currentSplit.get(currentSet);
    TextView big = bold(String.valueOf(reps), 60);
    big.setGravity(Gravity.CENTER);
    body.addView(big);

    TextView repLabel = text("REPS THIS SET", 15, MUTED);
    repLabel.setGravity(Gravity.CENTER);
    body.addView(repLabel);

    double wt = routineWeights.containsKey(e.name) ? routineWeights.get(e.name) : 0;
    String unit = routineWeightUnits.containsKey(e.name) ? routineWeightUnits.get(e.name) : "kg";
    if (wt > 0) {
      TextView load = text("Load: " + weightValue(e.name) + " " + unit, 15, GREEN);
      load.setGravity(Gravity.CENTER);
      body.addView(load);
    }

    Button next = button(
        currentSet == selectedSets - 1 && currentExercise == routine.size() - 1
            ? "Finish Workout"
            : "Next",
        GREEN);
    next.setTextColor(TEXT);
    next.setOnClickListener(v -> {
      if (currentSet < selectedSets - 1) {
        currentSet++;
        showWorkout(false);
      } else if (currentExercise < routine.size() - 1) {
        currentExercise++;
        currentSet = 0;
        currentSplit = randomSplit(selectedSets, minReps, maxReps);
        showWorkout(false);
      } else {
        completeWorkout();
      }
    });
    body.addView(next, margins(-1, dp(54), 0, 16, 0, 8));

    Button cancel = outlineButton("Cancel Routine");
    cancel.setTextColor(RED);
    cancel.setOnClickListener(v -> new AlertDialog.Builder(this)
        .setTitle("Cancel workout?")
        .setMessage("This workout will not be recorded as completed.")
        .setNegativeButton("Keep going", null)
        .setPositiveButton("Cancel workout", (d, w) -> showHome(false))
        .show());
    body.addView(cancel, margins(-1, dp(56), 0, 0, 0, 8));

    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
    display("workout", page, push);
  }

  void completeWorkout() {
    long now = System.currentTimeMillis();
    double total = 0;
    JSONArray exercises = new JSONArray();

    for (ExerciseData.Exercise e : routine) {
      double w = routineWeights.containsKey(e.name) ? routineWeights.get(e.name) : 0;
      String unit = routineWeightUnits.containsKey(e.name)
          ? routineWeightUnits.get(e.name) : "kg";
      double kcal = estimateCalories(e, 100, w, unit);
      total += kcal;
      try {
        JSONObject o = new JSONObject();
        o.put("name", e.name);
        o.put("reps", 100);
        o.put("weight", w);
        o.put("unit", unit);
        o.put("calories", round(kcal));
        exercises.put(o);
      } catch (Exception ignored) {}
    }

    JSONObject rec = new JSONObject();
    try {
      rec.put("timestamp", now);
      rec.put("date", dateKey.format(new Date(now)));
      rec.put("dateTime", dateTime.format(new Date(now)));
      rec.put("routine", routineName());
      rec.put("durationSec", Math.max(1, (now - workoutStarted) / 1000));
      rec.put("calories", round(total));
      rec.put("sets", selectedSets);
      rec.put("exercises", exercises);
    } catch (Exception ignored) {}

    JSONArray records = loadRecords();
    records.put(rec);
    prefs.edit().putString("records", records.toString()).apply();
    showCompletion(total, exercises);
  }

  double estimateCalories(ExerciseData.Exercise e, int reps, double exerciseWeight, String unit) {
    double bodyKg = profileWeightKg();
    double loadKg = "lb".equals(unit) ? exerciseWeight * 0.45359237 : exerciseWeight;

    double base = "Bodyweight".equalsIgnoreCase(e.equipment) ? 0.34 : 0.44;
    String move = e.movement.toLowerCase(Locale.US);
    if (move.contains("squat") || move.contains("hinge") || move.contains("full")) base += 0.09;
    if (move.contains("pull") || move.contains("push")) base += 0.03;

    int sex = prefs.getInt("sex", 0);
    double age = parseDouble(prefs.getString("age", ""), 35);
    double heightCm = storedHeightCm();
    double bmr = (sex == 2 ? -161 : 5) + 10 * bodyKg + 6.25 * heightCm - 5 * age;
    double physiologyScale = Math.max(0.82, Math.min(1.18, bmr / 1650.0));
    double externalLoad = Math.max(0, loadKg) * 0.018;

    return Math.max(1, bodyKg * base * reps / 100.0 * physiologyScale
        + externalLoad * reps / 100.0);
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
      prefs.edit().remove("draftRoutineName").apply();
      showHome(false);
    });
    body.addView(done, margins(-1, dp(54), 0, 18, 0, 8));

    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
    display("completion", page, false);
  }

  String formatLoad(JSONObject o) {
    double w = o.optDouble("weight", 0);
    if (w <= 0) return "Bodyweight";
    return (Math.abs(w - Math.rint(w)) < 0.001 ? String.valueOf((int)Math.rint(w))
        : String.format(Locale.US, "%.1f", w)) + " " + o.optString("unit", "kg");
  }

  // ---------- Profile ----------
  void showProfile(boolean push) {
    LinearLayout page = pageShell("Personal Profile");
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

    body.addView(profileLabel("Biological sex"));
    Spinner sex = new Spinner(this); String[] sexes = {"Male", "Female"};
    sex.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sexes));
    sex.setSelection(prefs.getInt("sex", 1) == 2 ? 1 : 0); sex.setBackground(outline(CARD_ALT,1,18));
    body.addView(sex, margins(-1, dp(52), 0, 0, 0, 8));

    body.addView(profileLabel("Age"));
    EditText age = field("", prefs.getString("age", ""), InputType.TYPE_CLASS_NUMBER); body.addView(age);

    LinearLayout measurementFields=vbox(); body.addView(measurementFields);
    final EditText[] heightCm={null},heightFt={null},heightIn={null},weight={null},waist={null},hip={null};
    Runnable rebuildMeasurements=()->{
      measurementFields.removeAllViews();
      if("metric".equals(activeSystem[0])){
        measurementFields.addView(profileLabel("Weight (kg)")); weight[0]=field("",formatNumber(profileWeightKg()),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);measurementFields.addView(weight[0]);
        measurementFields.addView(profileLabel("Height (cm)")); heightCm[0]=field("",formatNumber(storedHeightCm()),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);measurementFields.addView(heightCm[0]);
        measurementFields.addView(profileLabel("Waist (cm)")); waist[0]=field("",formatNumber(storedWaistCm()),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);measurementFields.addView(waist[0]);
        measurementFields.addView(profileLabel("Hip circumference (cm)")); hip[0]=field("",formatNumber(storedHipCm()),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);measurementFields.addView(hip[0]);
      }else{
        measurementFields.addView(profileLabel("Weight (lb)")); weight[0]=field("",formatNumber(profileWeightKg()/0.45359237),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);measurementFields.addView(weight[0]);
        measurementFields.addView(profileLabel("Height (ft / in)")); double total=storedHeightCm()/2.54; int ft=(int)Math.floor(total/12); double inch=total-ft*12;
        LinearLayout hr=new LinearLayout(this);hr.setOrientation(LinearLayout.HORIZONTAL);heightFt[0]=field("ft",String.valueOf(ft),InputType.TYPE_CLASS_NUMBER);heightIn[0]=field("in",formatNumber(inch),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams f1=new LinearLayout.LayoutParams(0,dp(58),1);f1.setMargins(0,0,dp(4),0);LinearLayout.LayoutParams f2=new LinearLayout.LayoutParams(0,dp(58),1);f2.setMargins(dp(4),0,0,0);hr.addView(heightFt[0],f1);hr.addView(heightIn[0],f2);measurementFields.addView(hr);
        measurementFields.addView(profileLabel("Waist (in)")); waist[0]=field("",formatNumber(storedWaistCm()/2.54),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);measurementFields.addView(waist[0]);
        measurementFields.addView(profileLabel("Hip circumference (in)")); hip[0]=field("",formatNumber(storedHipCm()/2.54),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);measurementFields.addView(hip[0]);
      }
    };
    metric.setOnClickListener(v->{activeSystem[0]="metric";styleTwo(metric,imperial,true);rebuildMeasurements.run();});
    imperial.setOnClickListener(v->{activeSystem[0]="imperial";styleTwo(metric,imperial,false);rebuildMeasurements.run();});
    rebuildMeasurements.run();

    body.addView(sectionTitle("Your results"));
    LinearLayout results=vbox(); results.setPadding(dp(14),dp(12),dp(14),dp(12)); results.setBackground(shape(CARD,18));
    double bmi=bmiValue(), whtr=waistHeightRatio(), whr=waistHipRatio();
    TextView bmiLine=bold("BMI  " + String.format(Locale.US,"%.1f",bmi) + "  •  " + bmiCategory(bmi),17); bmiLine.setTextColor(bmiColor(bmi)); results.addView(bmiLine);
    TextView whtrLine=text("Waist-to-height ratio  " + String.format(Locale.US,"%.2f",whtr) + "  •  " + whtrCategory(whtr),14,TEXT); results.addView(whtrLine);
    TextView whrLine=text("Waist-to-hip ratio  " + String.format(Locale.US,"%.2f",whr),14,TEXT); results.addView(whrLine);
    TextView risk=text(riskBandText(whtr,whr,sex.getSelectedItemPosition()==0),13,MUTED); results.addView(risk);
    body.addView(results,margins(-1,-2,0,4,0,8));

    body.addView(sectionTitle("Body composition guide"));
    body.addView(new BmiScaleView(this), new LinearLayout.LayoutParams(-1,dp(120)));
    body.addView(text("BMI is a screening measure, not a direct measurement of body fat. The highlighted figure is only a visual category guide.",12,MUTED));

    body.addView(sectionTitle("Measurement guide"));
    LinearLayout portraits=new LinearLayout(this);portraits.setOrientation(LinearLayout.HORIZONTAL);portraits.setGravity(Gravity.CENTER);
    portraits.addView(measurementPortrait(R.drawable.profile_male_full,"MALE",false),new LinearLayout.LayoutParams(0,dp(330),1));
    portraits.addView(measurementPortrait(R.drawable.profile_female_full,"FEMALE",true),new LinearLayout.LayoutParams(0,dp(330),1));
    body.addView(portraits);
    body.addView(text("Height: measure from the floor to the top of the head while standing upright.\n\nWaist: find the bottom of the ribs and top of the hips; measure midway between them after breathing out naturally.\n\nHip: wrap the tape around the fullest part of the hips and buttocks, keeping it level.\n\nThese BMI and ratio results are screening estimates, not a diagnosis or an individual prediction of heart attack or cardiovascular mortality.",14,MUTED));

    Button save=button("Save Profile",GREEN_DARK);save.setOnClickListener(v->{
      double cm,kg,wc,hc;
      if("metric".equals(activeSystem[0])){cm=parseDouble(heightCm[0].getText().toString(),storedHeightCm());kg=parseDouble(weight[0].getText().toString(),profileWeightKg());wc=parseDouble(waist[0].getText().toString(),storedWaistCm());hc=parseDouble(hip[0].getText().toString(),storedHipCm());}
      else{double ft=parseDouble(heightFt[0].getText().toString(),0),in=parseDouble(heightIn[0].getText().toString(),0);cm=(ft*12+in)*2.54;kg=parseDouble(weight[0].getText().toString(),profileWeightKg()/0.45359237)*0.45359237;wc=parseDouble(waist[0].getText().toString(),storedWaistCm()/2.54)*2.54;hc=parseDouble(hip[0].getText().toString(),storedHipCm()/2.54)*2.54;}
      prefs.edit().putInt("sex",sex.getSelectedItemPosition()==0?1:2).putString("age",age.getText().toString()).putFloat("heightCm",(float)cm).putFloat("profileWeightKg",(float)kg).putFloat("waistCm",(float)wc).putFloat("hipCm",(float)hc).putString("measurementSystem",activeSystem[0]).putString("profileWeightUnit","metric".equals(activeSystem[0])?"kg":"lb").apply();
      Toast.makeText(this,"Profile saved.",Toast.LENGTH_SHORT).show();showProfile(false);
    }); body.addView(save,margins(-1,dp(54),0,18,0,8));

    page.addView(scroll(body),new LinearLayout.LayoutParams(-1,0,1));display("profile",page,push);
  }

  LinearLayout measurementPortrait(int drawable,String label,boolean female){
    LinearLayout box=vbox();box.setPadding(dp(3),dp(3),dp(3),dp(3));
    FrameLayout frame=new FrameLayout(this);ImageView iv=new ImageView(this);iv.setImageResource(drawable);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(iv,new FrameLayout.LayoutParams(-1,-1));frame.addView(new MeasurementOverlay(this),new FrameLayout.LayoutParams(-1,-1));
    box.addView(frame,new LinearLayout.LayoutParams(-1,dp(290)));TextView cap=text(label,11,MUTED);cap.setGravity(Gravity.CENTER);box.addView(cap,new LinearLayout.LayoutParams(-1,dp(28)));return box;
  }

  class MeasurementOverlay extends View{
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);MeasurementOverlay(Context c){super(c);setBackgroundColor(Color.TRANSPARENT);}
    protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();p.setColor(GREEN);p.setStrokeWidth(dp(2));p.setStyle(Paint.Style.STROKE);float x=dp(12);c.drawLine(x,dp(12),x,h-dp(12),p);c.drawLine(x-dp(5),dp(12),x+dp(5),dp(12),p);c.drawLine(x-dp(5),h-dp(12),x+dp(5),h-dp(12),p);float wy=h*.43f,hy=h*.58f;c.drawLine(w*.26f,wy,w*.86f,wy,p);c.drawLine(w*.22f,hy,w*.90f,hy,p);p.setStyle(Paint.Style.FILL);p.setTextSize(dp(9));c.drawText("HEIGHT",dp(2),h*.52f,p);c.drawText("WAIST",w*.62f,wy-dp(5),p);c.drawText("HIP",w*.68f,hy-dp(5),p);}
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
    a.setBackground(first ? shape(GREEN_DARK, 16) : outline(CARD_ALT, 1, 16));
    b.setBackground(!first ? shape(GREEN_DARK, 16) : outline(CARD_ALT, 1, 16));
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

  boolean hasWorkoutOn(String key) {
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
    return sum;
  }

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
    LinearLayout page = pageShell("Completed Routines");
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

        TextView day = text(dayLabel.format(d.getTime()) + "\n" + new SimpleDateFormat("d", Locale.US).format(d.getTime()), 12, allowed ? TEXT : MUTED);
        day.setGravity(Gravity.CENTER); day.setBackground(shape(selected ? GREEN_DARK : CARD, 14));
        if (allowed) day.setOnClickListener(v -> { selectedDate[0]=key; renderHistoryDay(key,details,selectedTitle); render[0].run(); });
        days.addView(day, margins(dp(64), dp(70), 3, 0, 3, 0));

        LinearLayout slot = vbox(); slot.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); slot.setBackgroundColor(Color.TRANSPARENT);
        double kcal = allowed ? caloriesOn(key) : 0; double clipped = Math.min(1200.0, Math.max(0, kcal));
        int barH=(int)Math.max(dp(4),dp(160)*clipped/1200.0);
        View bar = new View(this); bar.setBackground(tieredBar((float)clipped));
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
    LinearLayout col=vbox(); col.setGravity(Gravity.CENTER_HORIZONTAL); int[] vals={1200,1000,800,600,400,200,0};
    for(int v:vals){TextView t=text(String.valueOf(v),9,MUTED);t.setGravity(Gravity.CENTER);col.addView(t,new LinearLayout.LayoutParams(-1,0,1));}
    return col;
  }

  GradientDrawable tieredBar(float kcal) {
    int c;
    if (kcal >= 1000) c=Color.rgb(239,72,72);
    else if (kcal >= 800) c=Color.rgb(245,126,45);
    else if (kcal >= 600) c=Color.rgb(245,179,42);
    else if (kcal >= 400) c=Color.rgb(213,197,52);
    else if (kcal >= 200) c=Color.rgb(84,176,94);
    else c=Color.rgb(60,142,190);
    return shape(c,8);
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
      card.addView(del, margins(-1, dp(48), 0, 8, 0, 0));

      details.addView(card, margins(-1, -2, 0, 5, 0, 8));
    }

    if (found == 0) {
      details.addView(text("No completed routines on this date.", 15, MUTED));
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
