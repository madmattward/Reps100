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
  final int BG = Color.rgb(8, 15, 10);
  final int BAR = Color.rgb(18, 27, 20);
  final int CARD = Color.rgb(27, 39, 30);
  final int CARD_ALT = Color.rgb(70, 82, 74);
  final int TEXT = Color.rgb(242, 246, 242);
  final int MUTED = Color.rgb(190, 199, 192);
  final int GREEN = Color.rgb(91, 196, 145);
  final int GREEN_DARK = Color.rgb(0, 112, 62);
  final int GREEN_SOFT = Color.rgb(188, 246, 217);
  final int BLUE = Color.rgb(68, 139, 241);
  final int RED = Color.rgb(236, 133, 133);
  final int GRID_CARD = Color.rgb(24, 38, 28);

  // ---------- State ----------
  FrameLayout windowRoot;
  String currentScreen = "";
  ArrayDeque<String> navigation = new ArrayDeque<>();
  ExerciseData.Exercise detailExercise;
  boolean selectingForRoutine = false;

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
    return t;
  }

  TextView bold(String s, float sp) {
    TextView t = text(s, sp, TEXT);
    t.setTypeface(null, Typeface.BOLD);
    return t;
  }

  Button button(String label, int color) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextColor(TEXT);
    b.setTextSize(15);
    b.setAllCaps(false);
    b.setTypeface(null, Typeface.BOLD);
    b.setBackground(shape(color, 18));
    b.setPadding(dp(12), 0, dp(12), 0);
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

    TextView h = text(title, 25, TEXT);
    h.setGravity(Gravity.CENTER);
    h.setSingleLine(false);
    bar.addView(h, new LinearLayout.LayoutParams(0, dp(64), 1));

    Space balance = new Space(this);
    bar.addView(balance, new LinearLayout.LayoutParams(dp(52), dp(64)));
    return bar;
  }

  LinearLayout pageShell(String title) {
    LinearLayout page = vbox();
    page.addView(titleBar(title), new LinearLayout.LayoutParams(-1, dp(72)));
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

    TextView strap = text("100 reps. One exercise at a time.", 15, GREEN);
    strap.setGravity(Gravity.CENTER);
    all.addView(strap);

    addStreakCard(all);
    all.addView(space(8));

    addMenuRow(all,
        menuCard("＋", "Create Routine", v -> showRoutine(true)),
        menuCard("☷", "Sets & Reps", v -> showSetsManager(true)));

    addMenuRow(all,
        menuCard("≡", "Routines", v -> showRoutineList(true)),
        menuCard("🏋", "Exercises", v -> showLibrary(true)));

    addMenuRow(all,
        menuCard("✓", "Completed", v -> showHistory(true)),
        menuCard("●", "Profile", v -> showProfile(true)));

    TextView offline = text("OFFLINE FIRST  •  YOUR DATA STAYS ON DEVICE", 12, MUTED);
    offline.setGravity(Gravity.CENTER);
    offline.setPadding(dp(8), dp(20), dp(8), dp(10));
    all.addView(offline);

    display("home", scroll(all), push);
  }

  View menuCard(String icon, String label, View.OnClickListener listener) {
    LinearLayout c = vbox();
    c.setGravity(Gravity.CENTER);
    c.setPadding(dp(10), dp(14), dp(10), dp(14));
    c.setBackground(shape(GRID_CARD, 24));
    c.setOnClickListener(listener);
    c.setClickable(true);
    c.setFocusable(true);

    TextView circle = text(icon, 34, GREEN_SOFT);
    circle.setGravity(Gravity.CENTER);
    circle.setTypeface(null, Typeface.BOLD);
    circle.setBackground(shape(GREEN_DARK, 999));
    c.addView(circle, new LinearLayout.LayoutParams(dp(104), dp(104)));

    TextView lab = bold(label, 15);
    lab.setGravity(Gravity.CENTER);
    lab.setPadding(dp(4), dp(10), dp(4), 0);
    c.addView(lab, new LinearLayout.LayoutParams(-1, dp(42)));
    return c;
  }

  void addMenuRow(LinearLayout parent, View a, View b) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams ca = new LinearLayout.LayoutParams(0, dp(174), 1);
    ca.setMargins(dp(4), dp(6), dp(6), dp(6));
    LinearLayout.LayoutParams cb = new LinearLayout.LayoutParams(0, dp(174), 1);
    cb.setMargins(dp(6), dp(6), dp(4), dp(6));
    row.addView(a, ca);
    row.addView(b, cb);
    parent.addView(row, new LinearLayout.LayoutParams(-1, dp(186)));
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

    TextView stats = text("Current: " + current + " days     •     Record: " + best + " days", 13, TEXT);
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
    save.setTextColor(Color.rgb(20, 52, 34));
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
    body.addView(save, margins(-1, dp(62), 0, 8, 0, 12));

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
    body.addView(add, margins(-1, dp(60), 0, 6, 0, 12));

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
    save.setTextColor(Color.rgb(20, 52, 34));
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
    body.addView(save, margins(-1, dp(62), 0, 18, 0, 8));

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
      body.addView(start, margins(-1, dp(62), 0, 0, 0, 10));
    }

    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
    display("routine", page, push);
  }

  void addRoutineExerciseCard(LinearLayout body, ExerciseData.Exercise e) {
    LinearLayout card = vbox();
    card.setPadding(dp(14), dp(12), dp(14), dp(12));
    card.setBackground(shape(CARD, 20));

    LinearLayout top = new LinearLayout(this);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    TextView name = bold(e.name, 17);
    TextView meta = text(e.muscle + "  •  " + e.movement, 12, MUTED);
    LinearLayout texts = vbox();
    texts.addView(name);
    texts.addView(meta);
    top.addView(texts, new LinearLayout.LayoutParams(0, dp(62), 1));

    Button remove = button("×", CARD_ALT);
    remove.setTextColor(RED);
    remove.setTextSize(22);
    remove.setOnClickListener(v -> {
      routine.remove(e);
      routineWeights.remove(e.name);
      routineWeightUnits.remove(e.name);
      showRoutine(false);
    });
    top.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
    card.addView(top);

    LinearLayout weightRow = new LinearLayout(this);
    weightRow.setOrientation(LinearLayout.HORIZONTAL);
    weightRow.setGravity(Gravity.CENTER_VERTICAL);
    weightRow.addView(text("Weight used", 14, MUTED), new LinearLayout.LayoutParams(0, dp(52), 1));

    EditText w = new EditText(this);
    w.setText(weightValue(e.name));
    w.setHint("0");
    w.setTextColor(TEXT);
    w.setHintTextColor(MUTED);
    w.setTextSize(16);
    w.setGravity(Gravity.CENTER);
    w.setSingleLine();
    w.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    w.setBackground(shape(BG, 14));
    w.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
      public void onTextChanged(CharSequence s, int st, int before, int count) {
        routineWeights.put(e.name, parseDouble(s.toString(), 0));
      }
      public void afterTextChanged(Editable s) {}
    });
    weightRow.addView(w, new LinearLayout.LayoutParams(dp(82), dp(48)));

    Spinner units = new Spinner(this);
    String[] unitItems = {"kg", "lb"};
    ArrayAdapter<String> ua = new ArrayAdapter<>(this,
        android.R.layout.simple_spinner_dropdown_item, unitItems);
    units.setAdapter(ua);
    String savedUnit = routineWeightUnits.containsKey(e.name)
        ? routineWeightUnits.get(e.name)
        : prefs.getString("profileWeightUnit", "kg");
    units.setSelection("lb".equals(savedUnit) ? 1 : 0);
    units.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      public void onNothingSelected(android.widget.AdapterView<?> parent) {}
      public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
        routineWeightUnits.put(e.name, unitItems[pos]);
      }
    });
    weightRow.addView(units, new LinearLayout.LayoutParams(dp(82), dp(50)));
    card.addView(weightRow);

    body.addView(card, margins(-1, -2, 0, 6, 0, 8));
  }

  String weightValue(String name) {
    double v = routineWeights.containsKey(name) ? routineWeights.get(name) : 0;
    if (Math.abs(v - Math.rint(v)) < 0.0001) return String.valueOf((int)Math.rint(v));
    return String.format(Locale.US, "%.1f", v);
  }

  // ---------- Exercise library ----------
  void showLibrary(boolean push) {
    LinearLayout page = pageShell(selectingForRoutine ? "Add Exercise" : "Exercise List");
    LinearLayout body = contentColumn();

    EditText search = field("Search by name, body part, push or pull", "",
        InputType.TYPE_CLASS_TEXT);
    body.addView(search);

    String[] equipmentFilter = {"All", "Bodyweight", "Weightlifting"};
    final String[] equipment = {"All"};
    final String[] muscle = {"All"};

    LinearLayout equipRow = chipRow(equipmentFilter, equipment, null);
    body.addView(equipRow, margins(-1, dp(54), 0, 4, 0, 4));

    HorizontalScrollView bodyPartsScroll = new HorizontalScrollView(this);
    bodyPartsScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout bodyParts = new LinearLayout(this);
    bodyParts.setOrientation(LinearLayout.HORIZONTAL);
    String[] muscles = {"All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core"};
    bodyPartsScroll.addView(bodyParts);
    body.addView(bodyPartsScroll, margins(-1, dp(56), 0, 0, 0, 6));

    TextView count = text("", 14, MUTED);
    body.addView(count);

    LinearLayout list = vbox();
    body.addView(list);

    Runnable refresh = () -> refreshLibrary(list, count, search.getText().toString(),
        equipment[0], muscle[0]);

    // Rebuild first chip row with listeners tied to refresh.
    body.removeView(equipRow);
    LinearLayout newEquip = new LinearLayout(this);
    newEquip.setOrientation(LinearLayout.HORIZONTAL);
    for (String f : equipmentFilter) {
      Button chip = smallChip(f, "All".equals(f));
      chip.setOnClickListener(v -> {
        equipment[0] = f;
        styleChipRow(newEquip, f);
        refresh.run();
      });
      newEquip.addView(chip, new LinearLayout.LayoutParams(0, dp(48), 1));
    }
    body.addView(newEquip, 1);

    for (String f : muscles) {
      Button chip = smallChip(f, "All".equals(f));
      chip.setOnClickListener(v -> {
        muscle[0] = f;
        styleChipRow(bodyParts, f);
        refresh.run();
      });
      LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(96), dp(48));
      cp.setMargins(dp(3), 0, dp(3), 0);
      bodyParts.addView(chip, cp);
    }

    search.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
      public void onTextChanged(CharSequence s, int st, int before, int count) { refresh.run(); }
      public void afterTextChanged(Editable s) {}
    });

    refresh.run();
    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
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
    if (!selected) b.setBackground(outline(CARD_ALT, 1, 16));
    b.setTextSize(14);
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

      String haystack = (e.name + " " + e.muscle + " " + e.equipment + " "
          + e.movement).toLowerCase(Locale.US);
      boolean queryOk = q.length() == 0 || haystack.contains(q);

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
      body.addView(add, margins(-1, dp(60), 0, 16, 0, 10));
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
    String mv = e.movement.toLowerCase(Locale.US);
    if (mv.contains("push")) {
      return "1. Set your body and joints in a stable starting position.\n"
          + "2. Lower or load the movement under control.\n"
          + "3. Press smoothly through the working muscles without bouncing.\n"
          + "4. Return to the start position under control.\n\n"
          + "Common mistakes: flared joints, excessive momentum, and shortening the range.";
    }
    if (mv.contains("pull")) {
      return "1. Begin with the working muscles lengthened and your torso braced.\n"
          + "2. Pull by driving the elbows through the intended path.\n"
          + "3. Briefly reach the contracted position without shrugging.\n"
          + "4. Return slowly to full control.\n\n"
          + "Common mistakes: swinging, jerking the load, and losing spinal position.";
    }
    if (mv.contains("squat") || mv.contains("lunge")) {
      return "1. Set your feet securely and brace your trunk.\n"
          + "2. Bend through the hips and knees while keeping balance through the feet.\n"
          + "3. Reach a comfortable controlled depth.\n"
          + "4. Drive back to standing without letting the knees collapse inward.\n\n"
          + "Common mistakes: rushing the bottom position and losing balance.";
    }
    if (mv.contains("hinge") || e.name.toLowerCase(Locale.US).contains("deadlift")) {
      return "1. Brace your trunk with the load close to the body.\n"
          + "2. Hinge at the hips while keeping a neutral spine.\n"
          + "3. Drive the floor away and extend the hips smoothly.\n"
          + "4. Lower the load under control.\n\n"
          + "Common mistakes: rounding the back and letting the load drift away.";
    }
    return "1. Set up in a stable position with a neutral spine.\n"
        + "2. Move through a comfortable controlled range.\n"
        + "3. Keep the working joints aligned and breathe steadily.\n"
        + "4. Return smoothly to the start position.\n\n"
        + "Common mistakes: rushing reps, losing posture, and using momentum.";
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
    body.addView(create, margins(-1, dp(60), 0, 16, 0, 12));

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
    next.setTextColor(Color.rgb(20, 52, 34));
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
    body.addView(next, margins(-1, dp(62), 0, 16, 0, 8));

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
    done.setTextColor(Color.rgb(20, 52, 34));
    done.setOnClickListener(v -> {
      routine.clear();
      routineWeights.clear();
      routineWeightUnits.clear();
      prefs.edit().remove("draftRoutineName").apply();
      showHome(false);
    });
    body.addView(done, margins(-1, dp(62), 0, 18, 0, 8));

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

    body.addView(text("These details improve calorie estimates and stay on this device.",
        14, MUTED));

    // Measurement system switch.
    String system = prefs.getString("measurementSystem", "metric");
    LinearLayout systemRow = new LinearLayout(this);
    systemRow.setOrientation(LinearLayout.HORIZONTAL);
    Button metric = smallChip("Metric", "metric".equals(system));
    Button imperial = smallChip("Imperial", "imperial".equals(system));
    systemRow.addView(metric, new LinearLayout.LayoutParams(0, dp(50), 1));
    systemRow.addView(imperial, new LinearLayout.LayoutParams(0, dp(50), 1));
    body.addView(systemRow, margins(-1, dp(54), 0, 8, 0, 8));

    final String[] activeSystem = {system};

    Spinner sex = new Spinner(this);
    String[] sexes = {"Biological sex", "Male", "Female"};
    sex.setAdapter(new ArrayAdapter<>(this,
        android.R.layout.simple_spinner_dropdown_item, sexes));
    sex.setSelection(prefs.getInt("sex", 0));
    body.addView(sex, margins(-1, dp(56), 0, 4, 0, 4));

    EditText age = field("Age", prefs.getString("age", ""), InputType.TYPE_CLASS_NUMBER);
    body.addView(age);

    LinearLayout measurementFields = vbox();
    body.addView(measurementFields);

    final EditText[] heightCm = new EditText[1];
    final EditText[] heightFt = new EditText[1];
    final EditText[] heightIn = new EditText[1];
    final EditText[] weight = new EditText[1];
    final EditText[] waist = new EditText[1];

    Runnable rebuildMeasurements = () -> {
      measurementFields.removeAllViews();
      if ("metric".equals(activeSystem[0])) {
        heightCm[0] = field("Height (cm)", formatNumber(storedHeightCm()),
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weight[0] = field("Weight (kg)", formatNumber(profileWeightKg()),
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        waist[0] = field("Waist (cm)", formatNumber(storedWaistCm()),
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        measurementFields.addView(heightCm[0]);
        measurementFields.addView(weight[0]);
        measurementFields.addView(waist[0]);
      } else {
        double inchesTotal = storedHeightCm() / 2.54;
        int ft = (int)Math.floor(inchesTotal / 12.0);
        double inch = inchesTotal - ft * 12;

        LinearLayout hrow = new LinearLayout(this);
        hrow.setOrientation(LinearLayout.HORIZONTAL);
        heightFt[0] = field("Height (ft)", ft > 0 ? String.valueOf(ft) : "",
            InputType.TYPE_CLASS_NUMBER);
        heightIn[0] = field("Height (in)", inch > 0 ? formatNumber(inch) : "",
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        hrow.addView(heightFt[0], new LinearLayout.LayoutParams(0, dp(62), 1));
        hrow.addView(heightIn[0], new LinearLayout.LayoutParams(0, dp(62), 1));
        measurementFields.addView(hrow);

        weight[0] = field("Weight (lb)", formatNumber(profileWeightKg() / 0.45359237),
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        waist[0] = field("Waist (in)", formatNumber(storedWaistCm() / 2.54),
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        measurementFields.addView(weight[0]);
        measurementFields.addView(waist[0]);
      }
    };

    metric.setOnClickListener(v -> {
      activeSystem[0] = "metric";
      styleTwo(metric, imperial, true);
      rebuildMeasurements.run();
    });
    imperial.setOnClickListener(v -> {
      activeSystem[0] = "imperial";
      styleTwo(metric, imperial, false);
      rebuildMeasurements.run();
    });

    rebuildMeasurements.run();

    TextView guideTitle = sectionTitle("Measurement guide");
    body.addView(guideTitle);

    LinearLayout figures = new LinearLayout(this);
    figures.setOrientation(LinearLayout.HORIZONTAL);
    figures.addView(new BodyMeasurementView(this, false),
        new LinearLayout.LayoutParams(0, dp(300), 1));
    figures.addView(new BodyMeasurementView(this, true),
        new LinearLayout.LayoutParams(0, dp(300), 1));
    body.addView(figures);

    body.addView(text(
        "Height: measure vertically from the floor to the top of the head while standing upright.\n\n"
        + "Waist: measure horizontally around the natural waist, keeping the tape level around the body.\n\n"
        + "Weight: enter the reading from your scale. Calorie figures are estimates, not medical measurements.",
        14, MUTED));

    Button save = button("Save Profile", GREEN);
    save.setTextColor(Color.rgb(20, 52, 34));
    save.setOnClickListener(v -> {
      double cm;
      double kg;
      double waistCm;

      if ("metric".equals(activeSystem[0])) {
        cm = parseDouble(heightCm[0].getText().toString(), storedHeightCm());
        kg = parseDouble(weight[0].getText().toString(), profileWeightKg());
        waistCm = parseDouble(waist[0].getText().toString(), storedWaistCm());
      } else {
        double ft = parseDouble(heightFt[0].getText().toString(), 0);
        double in = parseDouble(heightIn[0].getText().toString(), 0);
        cm = (ft * 12.0 + in) * 2.54;
        kg = parseDouble(weight[0].getText().toString(), profileWeightKg() / 0.45359237)
            * 0.45359237;
        waistCm = parseDouble(waist[0].getText().toString(), storedWaistCm() / 2.54) * 2.54;
      }

      prefs.edit()
          .putInt("sex", sex.getSelectedItemPosition())
          .putString("age", age.getText().toString())
          .putFloat("heightCm", (float)cm)
          .putFloat("profileWeightKg", (float)kg)
          .putFloat("waistCm", (float)waistCm)
          .putString("measurementSystem", activeSystem[0])
          .putString("profileWeightUnit", "metric".equals(activeSystem[0]) ? "kg" : "lb")
          .apply();

      Toast.makeText(this, "Profile saved.", Toast.LENGTH_SHORT).show();
      showHome(false);
    });
    body.addView(save, margins(-1, dp(62), 0, 18, 0, 8));

    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
    display("profile", page, push);
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
    nav.setOrientation(LinearLayout.HORIZONTAL);
    nav.setGravity(Gravity.CENTER_VERTICAL);

    Button prev = button("‹", CARD);
    prev.setTextSize(28);
    Button next = button("›", CARD);
    next.setTextSize(28);
    TextView weekTitle = text("", 18, TEXT);
    weekTitle.setGravity(Gravity.CENTER);
    nav.addView(prev, new LinearLayout.LayoutParams(dp(50), dp(50)));
    nav.addView(weekTitle, new LinearLayout.LayoutParams(0, dp(50), 1));
    nav.addView(next, new LinearLayout.LayoutParams(dp(50), dp(50)));
    body.addView(nav);

    HorizontalScrollView calendarScroll = new HorizontalScrollView(this);
    calendarScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout days = new LinearLayout(this);
    days.setOrientation(LinearLayout.HORIZONTAL);
    calendarScroll.addView(days);
    body.addView(calendarScroll, margins(-1, dp(86), 0, 6, 0, 8));

    body.addView(sectionTitle("Calories burned"));

    LinearLayout chart = new LinearLayout(this);
    chart.setOrientation(LinearLayout.HORIZONTAL);
    chart.setGravity(Gravity.BOTTOM);
    chart.setPadding(dp(8), dp(6), dp(8), dp(4));
    chart.setBackground(shape(CARD, 20));
    body.addView(chart, margins(-1, dp(190), 0, 0, 0, 12));

    TextView selectedTitle = sectionTitle("");
    body.addView(selectedTitle);
    LinearLayout details = vbox();
    body.addView(details);

    final String[] selectedDate = {dateKey.format(anchor[0].getTime())};

    final Runnable[] render = new Runnable[1];
    render[0] = () -> {
      days.removeAllViews();
      chart.removeAllViews();

      Calendar first = (Calendar)anchor[0].clone();
      first.add(Calendar.DATE, -6);
      weekTitle.setText(shortDate.format(first.getTime()) + " – " + shortDate.format(anchor[0].getTime()));

      double max = 1;
      for (int i = -6; i <= 0; i++) {
        Calendar d = (Calendar)anchor[0].clone();
        d.add(Calendar.DATE, i);
        max = Math.max(max, caloriesOn(dateKey.format(d.getTime())));
      }

      String install = prefs.getString("installDate", dateKey.format(new Date()));
      for (int i = -6; i <= 0; i++) {
        Calendar d = (Calendar)anchor[0].clone();
        d.add(Calendar.DATE, i);
        String key = dateKey.format(d.getTime());
        boolean allowed = key.compareTo(install) >= 0;
        boolean selected = key.equals(selectedDate[0]);

        TextView day = text(dayLabel.format(d.getTime()) + "\n"
            + new SimpleDateFormat("d", Locale.US).format(d.getTime()), 13,
            allowed ? TEXT : MUTED);
        day.setGravity(Gravity.CENTER);
        day.setBackground(shape(selected ? GREEN_DARK : CARD, 14));
        if (allowed) {
          day.setOnClickListener(v -> {
            selectedDate[0] = key;
            renderHistoryDay(key, details, selectedTitle);
            render[0].run();
          });
        }
        days.addView(day, margins(dp(70), dp(76), 3, 0, 3, 0));

        LinearLayout slot = vbox();
        slot.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        double kcal = allowed ? caloriesOn(key) : 0;
        int barH = (int)Math.max(dp(8), Math.min(dp(128), dp(128) * kcal / max));
        TextView bar = text(kcal > 0 ? String.format(Locale.US, "%.0f", kcal) : "", 10, TEXT);
        bar.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        bar.setBackground(shape(kcal > 0 ? GREEN_DARK : BAR, 9));
        slot.addView(bar, new LinearLayout.LayoutParams(dp(30), barH));
        TextView dl = text(dayLabel.format(d.getTime()).substring(0, 1), 11, MUTED);
        dl.setGravity(Gravity.CENTER);
        slot.addView(dl, new LinearLayout.LayoutParams(dp(38), dp(26)));
        chart.addView(slot, new LinearLayout.LayoutParams(0, -1, 1));
      }

      renderHistoryDay(selectedDate[0], details, selectedTitle);
      prev.setEnabled(canMoveWeek(anchor[0], -7));
      next.setEnabled(canMoveWeek(anchor[0], 7));
      next.setAlpha(next.isEnabled() ? 1f : .35f);
      prev.setAlpha(prev.isEnabled() ? 1f : .35f);
    };

    prev.setOnClickListener(v -> {
      anchor[0].add(Calendar.DATE, -7);
      selectedDate[0] = dateKey.format(anchor[0].getTime());
      render[0].run();
    });

    next.setOnClickListener(v -> {
      anchor[0].add(Calendar.DATE, 7);
      if (anchor[0].after(Calendar.getInstance())) {
        anchor[0].setTime(new Date());
        midnight(anchor[0]);
      }
      selectedDate[0] = dateKey.format(anchor[0].getTime());
      render[0].run();
    });

    render[0].run();

    page.addView(scroll(body), new LinearLayout.LayoutParams(-1, 0, 1));
    display("history", page, push);
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
    title.setText("Completed on " + key + "  •  " + String.format(Locale.US, "%.1f kcal", total));

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
      card.addView(text(o.optString("dateTime", "") + "  •  "
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
