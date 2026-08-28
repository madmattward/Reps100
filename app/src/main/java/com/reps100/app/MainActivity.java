package com.reps100.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.content.*;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
  final int BG=Color.rgb(10,15,11), BAR=Color.rgb(18,25,19), CARD=Color.rgb(29,39,31), CARD2=Color.rgb(61,72,65), TEXT=Color.rgb(239,244,239), MUTED=Color.rgb(180,190,183), GREEN=Color.rgb(89,195,143), GREEN_DARK=Color.rgb(0,103,58), BLUE=Color.rgb(63,139,244), RED=Color.rgb(225,82,82);
  LinearLayout root;
  ArrayDeque<View> stack=new ArrayDeque<>();
  ArrayList<ExerciseData.Exercise> routine=new ArrayList<>();
  HashMap<String,Double> routineWeights=new HashMap<>();
  String weightUnit="kg";
  int selectedSets=5, minReps=10, maxReps=30, currentExercise=0, currentSet=0;
  ArrayList<Integer> currentSplit=new ArrayList<>();
  long workoutStarted=0;
  SharedPreferences prefs;
  SimpleDateFormat dateKey=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
  SimpleDateFormat dateTime=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(BAR); getWindow().setNavigationBarColor(Color.BLACK);
    prefs=getSharedPreferences("reps100",0);
    if(!prefs.contains("installDate")){
      long firstInstall=System.currentTimeMillis();
      try{firstInstall=getPackageManager().getPackageInfo(getPackageName(),0).firstInstallTime;}catch(Exception ignored){}
      prefs.edit().putString("installDate",dateKey.format(new Date(firstInstall))).apply();
    }
    if(Build.VERSION.SDK_INT>=33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,this::goBack);
    showHome(false);
  }
  @Override public void onBackPressed(){goBack();}
  void goBack(){
    if(!stack.isEmpty()){ View previous=stack.pop(); root.removeAllViews(); root.addView(previous,new LinearLayout.LayoutParams(-1,-1)); }
    else showHome(false);
  }
  void show(View v, boolean push){
    if(push && root!=null && root.getChildCount()>0) stack.push(root.getChildAt(0));
    root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
    root.addView(v,new LinearLayout.LayoutParams(-1,-1)); setContentView(root);
  }
  TextView tv(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(18,8,18,8);return t;}
  LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(16,12,16,18);l.setBackgroundColor(BG);return l;}
  ScrollView scroll(View v){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(v);return s;}
  GradientDrawable bg(int color,float r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(r);return g;}
  Button button(String label,int color){Button b=new Button(this);b.setText(label);b.setTextColor(TEXT);b.setTextSize(14);b.setAllCaps(false);b.setBackground(bg(color,22));b.setPadding(14,4,14,4);return b;}
  LinearLayout page(String title){
    LinearLayout p=col(); LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(0,0,0,6);
    Button back=button("‹",BAR);back.setTextSize(30);back.setContentDescription("Back");back.setOnClickListener(v->goBack());bar.addView(back,new LinearLayout.LayoutParams(56,56));
    TextView h=tv(title,23,TEXT);h.setTypeface(null,Typeface.BOLD);bar.addView(h,new LinearLayout.LayoutParams(0,56,1));p.addView(bar);return p;
  }
  TextView heading(String s){TextView t=tv(s,20,TEXT);t.setTypeface(null,Typeface.BOLD);t.setPadding(8,18,8,8);return t;}
  void addCard(LinearLayout p,String icon,String title,String desc,View.OnClickListener l){
    LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(12,10,12,10);c.setBackground(bg(CARD,24));
    TextView ic=tv(icon,28,GREEN);ic.setGravity(Gravity.CENTER);c.addView(ic,new LinearLayout.LayoutParams(64,64));
    LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);TextView a=tv(title,18,TEXT);a.setTypeface(null,Typeface.BOLD);texts.addView(a);texts.addView(tv(desc,13,MUTED));c.addView(texts,new LinearLayout.LayoutParams(0,76,1));c.setOnClickListener(l);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,92);lp.setMargins(0,7,0,7);p.addView(c,lp);
  }
  void showHome(boolean push){
    stack.clear(); LinearLayout p=col();
    TextView brand=tv("Reps100",34,TEXT);brand.setTypeface(null,Typeface.BOLD);brand.setPadding(8,12,8,2);p.addView(brand);
    p.addView(tv("100 reps. One exercise at a time.",15,GREEN));
    addStreakCard(p);
    addCard(p,"＋","Create Routine","Build a 100-rep workout",v->showRoutine(true));
    addCard(p,"☷","Exercise Library","Search, filter and learn exercises",v->showLibrary(true));
    addCard(p,"≡","My Routines","Saved routines and quick starts",v->showRoutineList(true));
    addCard(p,"▣","Completed Routines","Calendar, calories and workout history",v->showHistory(true));
    addCard(p,"●","Personal Profile","Sex, age, height, weight and waist",v->showProfile(true));
    TextView off=tv("OFFLINE FIRST  •  YOUR DATA STAYS ON DEVICE",12,MUTED);off.setGravity(Gravity.CENTER);off.setPadding(8,18,8,8);p.addView(off);
    show(scroll(p),push);
  }
  void addStreakCard(LinearLayout p){
    int current=streakForDate(new Date()); int best=bestStreak();
    LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(18,14,18,14);c.setBackground(bg(GREEN_DARK,22));
    String status=current>0?"🔥 "+current+" day streak":"Start a new streak today";
    TextView h=tv(status,20,TEXT);h.setTypeface(null,Typeface.BOLD);c.addView(h);
    c.addView(tv("Current streak: "+current+" days    •    Record: "+best+" days",13,TEXT));
    c.addView(tv(current>0?"Keep going — your streak is alive.":"Complete a 100-rep routine to begin your streak.",13,TEXT));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,98);lp.setMargins(0,14,0,8);p.addView(c,lp);
  }
  void showLibrary(boolean push){
    LinearLayout p=page("Exercise Library");
    EditText search=new EditText(this);search.setHint("Search by name, body part, push or pull");search.setHintTextColor(MUTED);search.setTextColor(TEXT);search.setSingleLine();search.setPadding(18,0,18,0);search.setBackground(bg(BG,20));p.addView(search,new LinearLayout.LayoutParams(-1,62));
    HorizontalScrollView hs=new HorizontalScrollView(this);LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);String[] filters={"All","Chest","Back","Legs","Shoulders","Arms","Core","Cardio"};
    for(String f:filters){Button b=button(f,f.equals("All")?GREEN_DARK:CARD2);b.setOnClickListener(v->{search.setTag(f);refreshLibrary(p,search,f);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,50);lp.setMargins(3,8,3,8);chips.addView(b,lp);}hs.addView(chips);p.addView(hs,new LinearLayout.LayoutParams(-1,66));
    TextView count=tv("",14,MUTED);p.addView(count);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);ScrollView sv=scroll(list);p.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){refreshLibrary(p,search,(String)search.getTag());}public void afterTextChanged(Editable e){}});
    search.setTag("All");search.setHint("Search by name, body part, push or pull");refreshLibrary(p,search,"All");show(p,push);
  }
  boolean isRepBased(ExerciseData.Exercise e){
    String n=e.name.toLowerCase(Locale.US);
    String[] blocked={"plank","hold","carry","treadmill","stationary bike","rowing machine","elliptical","stair climber"};
    for(String x:blocked)if(n.contains(x))return false; return true;
  }
  boolean matchesFilter(ExerciseData.Exercise e,String filter){
    if(!isRepBased(e))return false; if(filter==null||filter.equals("All"))return true;
    if(filter.equals("Legs"))return e.muscle.equals("Quads")||e.muscle.equals("Hamstrings")||e.muscle.equals("Glutes")||e.muscle.equals("Calves");
    if(filter.equals("Arms"))return e.muscle.equals("Biceps")||e.muscle.equals("Triceps");
    return e.muscle.equals(filter);
  }
  void refreshLibrary(LinearLayout page,EditText search,String filter){
    if(page.getChildCount()<5)return;ScrollView sv=(ScrollView)page.getChildAt(4);LinearLayout list=(LinearLayout)sv.getChildAt(0);list.removeAllViews();String q=search.getText().toString().toLowerCase(Locale.US);int shown=0;
    for(ExerciseData.Exercise e:ExerciseData.ALL){boolean fq=e.name.toLowerCase(Locale.US).contains(q)||e.muscle.toLowerCase(Locale.US).contains(q)||e.equipment.toLowerCase(Locale.US).contains(q)||e.movement.toLowerCase(Locale.US).contains(q);if(matchesFilter(e,filter)&&fq){addExerciseRow(list,e);shown++;if(shown>=120)break;}}
    if(shown==0)list.addView(tv("No rep-based exercises found",16,MUTED));
  }
  void addExerciseRow(LinearLayout list,ExerciseData.Exercise e){
    LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(10,8,10,8);r.setBackground(bg(CARD,18));
    ImageView thumb=new ImageView(this);setExerciseImage(thumb,e.photo);r.addView(thumb,new LinearLayout.LayoutParams(72,60));
    LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView n=tv(e.name,16,TEXT);n.setTypeface(null,Typeface.BOLD);text.addView(n);text.addView(tv(e.muscle+"  •  "+e.equipment+"  •  "+e.difficulty,12,MUTED));r.addView(text,new LinearLayout.LayoutParams(0,68,1));r.setOnClickListener(v->showDetail(e));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,78);lp.setMargins(0,4,0,4);list.addView(r,lp);
  }
  void setExerciseImage(ImageView img,String photo){int id=getResources().getIdentifier("reps_photo_"+photo,"drawable",getPackageName());if(id!=0){img.setImageResource(id);img.setScaleType(ImageView.ScaleType.CENTER_CROP);}else img.setImageResource(android.R.drawable.ic_menu_gallery);}
  void showDetail(ExerciseData.Exercise e){
    LinearLayout p=page(e.name);LinearLayout imgs=new LinearLayout(this);imgs.setOrientation(LinearLayout.HORIZONTAL);ImageView a=new ImageView(this),b=new ImageView(this);setExerciseImage(a,e.photo);setExerciseImage(b,e.photo);a.setScaleType(ImageView.ScaleType.CENTER_CROP);b.setScaleType(ImageView.ScaleType.CENTER_CROP);imgs.addView(a,new LinearLayout.LayoutParams(0,210,1));imgs.addView(b,new LinearLayout.LayoutParams(0,210,1));p.addView(imgs);
    LinearLayout labels=new LinearLayout(this);TextView l1=tv("START / EXTENDED",11,GREEN);TextView l2=tv("CONTRACTED / MIDPOINT",11,GREEN);labels.addView(l1,new LinearLayout.LayoutParams(0,38,1));labels.addView(l2,new LinearLayout.LayoutParams(0,38,1));p.addView(labels);
    p.addView(tv(e.muscle.toUpperCase()+"   •   "+e.equipment.toUpperCase()+"   •   "+e.difficulty.toUpperCase(),12,BLUE));p.addView(heading("Muscles worked"));p.addView(tv("Primary: "+e.muscle+"\nMovement: "+e.movement,14,MUTED));p.addView(heading("How to perform"));p.addView(tv("1. Set up with a stable stance and neutral spine.\n2. Control the movement through a comfortable range.\n3. Keep your core braced and breathe steadily.\n4. Return smoothly to the start position.\n\nCommon mistakes: rushing reps, losing posture, and using momentum.",14,MUTED));
    Button add=button(routine.contains(e)?"✓  IN ROUTINE":"＋  ADD TO ROUTINE",BLUE);add.setOnClickListener(v->{if(!routine.contains(e)){routine.add(e);routineWeights.put(e.name,0.0);}Toast.makeText(this,"Added to routine",Toast.LENGTH_SHORT).show();});p.addView(add,new LinearLayout.LayoutParams(-1,58));show(scroll(p),true);
  }
  void showRoutine(boolean push){
    LinearLayout p=page("Create Routine");TextView total=tv("100 TOTAL REPS",28,TEXT);total.setGravity(Gravity.CENTER);total.setTypeface(null,Typeface.BOLD);p.addView(total,new LinearLayout.LayoutParams(-1,72));p.addView(tv("Each selected exercise totals exactly 100 reps.",14,MUTED));
    EditText routineName=field("Routine name",prefs.getString("draftRoutineName",""),InputType.TYPE_CLASS_TEXT);p.addView(routineName);
    LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setGravity(Gravity.CENTER_VERTICAL);TextView sets=tv("Sets: "+selectedSets+"   •   "+minReps+"–"+maxReps+" reps",15,TEXT);Button edit=button("SETS & REPS",GREEN_DARK);edit.setOnClickListener(v->showSetsManager(true));controls.addView(sets,new LinearLayout.LayoutParams(0,56,1));controls.addView(edit,new LinearLayout.LayoutParams(130,52));p.addView(controls);
    p.addView(heading("Exercises & weights"));LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);for(ExerciseData.Exercise e:routine)addRoutineExerciseRow(list,e);ScrollView sv=scroll(list);p.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    Button lib=button("＋  ADD EXERCISE",CARD2);lib.setOnClickListener(v->{prefs.edit().putString("draftRoutineName",routineName.getText().toString()).apply();showLibrary(true);});p.addView(lib,new LinearLayout.LayoutParams(-1,56));
    Button save=button("SAVE ROUTINE",GREEN_DARK);save.setOnClickListener(v->{prefs.edit().putString("draftRoutineName",routineName.getText().toString()).apply();saveRoutineDraft();Toast.makeText(this,"Routine saved",Toast.LENGTH_SHORT).show();});p.addView(save,new LinearLayout.LayoutParams(-1,56));
    Button start=button("START 100-REP WORKOUT",BLUE);start.setOnClickListener(v->{if(routine.isEmpty()){Toast.makeText(this,"Add at least one exercise",Toast.LENGTH_SHORT).show();return;}prefs.edit().putString("draftRoutineName",routineName.getText().toString()).apply();saveRoutineDraft();prepareWorkout();showWorkout(true);});p.addView(start,new LinearLayout.LayoutParams(-1,60));show(p,push);
  }
  void addRoutineExerciseRow(LinearLayout list,ExerciseData.Exercise e){
    LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(14,8,14,8);c.setBackground(bg(CARD,18));
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView n=tv(e.name,16,TEXT);n.setTypeface(null,Typeface.BOLD);top.addView(n,new LinearLayout.LayoutParams(0,48,1));Button x=button("×",CARD2);x.setOnClickListener(v->{routine.remove(e);routineWeights.remove(e.name);showRoutine(false);});top.addView(x,new LinearLayout.LayoutParams(52,48));c.addView(top);
    LinearLayout weight=new LinearLayout(this);weight.setGravity(Gravity.CENTER_VERTICAL);TextView w=tv("Weight used",13,MUTED);weight.addView(w,new LinearLayout.LayoutParams(0,48,1));EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setText(weightValue(e.name));input.setTextColor(TEXT);input.setHint("0");input.setHintTextColor(MUTED);input.setSingleLine();input.setGravity(Gravity.CENTER);input.setBackground(bg(BG,14));input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){try{routineWeights.put(e.name,Double.parseDouble(s.toString()));}catch(Exception ex){routineWeights.put(e.name,0.0);}}public void afterTextChanged(Editable e){}});weight.addView(input,new LinearLayout.LayoutParams(90,48));Spinner sp=new Spinner(this);String[] units={"kg","lb"};ArrayAdapter<String> ad=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,units);sp.setAdapter(ad);sp.setSelection(weightUnit.equals("lb")?1:0);sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){weightUnit=units[pos];prefs.edit().putString("weightUnit",weightUnit).apply();}});weight.addView(sp,new LinearLayout.LayoutParams(78,48));c.addView(weight);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,108);lp.setMargins(0,5,0,5);list.addView(c,lp);
  }
  String weightValue(String name){return String.valueOf(routineWeights.containsKey(name)?routineWeights.get(name):0.0).replace(".0","");}
  void showSetsManager(boolean push){
    LinearLayout p=page("Sets & Reps Manager");p.addView(tv("Choose how 100 reps are distributed across each exercise.",15,MUTED));
    addSeek(p,"Number of sets",selectedSets,1,10,v->{selectedSets=v;});addSeek(p,"Minimum reps per set",minReps,1,50,v->{minReps=v;});addSeek(p,"Maximum reps per set",maxReps,1,100,v->{maxReps=v;});
    TextView note=tv("Every exercise in a routine will total exactly 100 reps, with every set kept within the selected minimum and maximum.",15,TEXT);note.setPadding(18,20,18,20);note.setBackground(bg(GREEN_DARK,22));p.addView(note);Button save=button("SAVE",GREEN);save.setOnClickListener(v->{if(!validRepRange()){Toast.makeText(this,"Those limits cannot make 100 reps with "+selectedSets+" sets.",Toast.LENGTH_LONG).show();return;}showRoutine(false);});p.addView(save,new LinearLayout.LayoutParams(-1,58));show(p,push);
  }
  boolean validRepRange(){return selectedSets*minReps<=100 && selectedSets*maxReps>=100;}
  void addSeek(LinearLayout p,String label,int value,int min,int max,final IntSetter setter){
    LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(18,12,18,10);c.setBackground(bg(CARD2,22));TextView l=tv(label+"    "+value,17,TEXT);l.setTypeface(null,Typeface.BOLD);c.addView(l);SeekBar sb=new SeekBar(this);sb.setMax(max-min);sb.setProgress(value-min);sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int pr,boolean from){int val=min+pr;l.setText(label+"    "+val);setter.set(val);}});c.addView(sb);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,120);lp.setMargins(0,6,0,6);p.addView(c,lp);
  }
  interface IntSetter{void set(int v);}
  void prepareWorkout(){currentExercise=0;currentSet=0;currentSplit=randomSplit(selectedSets,minReps,maxReps);workoutStarted=System.currentTimeMillis();}
  ArrayList<Integer> randomSplit(int sets,int min,int max){
    ArrayList<Integer> a=new ArrayList<>();int remaining=100;Random r=new Random();for(int i=0;i<sets;i++){int left=sets-i-1;int low=Math.max(min,remaining-left*max);int high=Math.min(max,remaining-left*min);int v=low+(high>low?r.nextInt(high-low+1):0);a.add(v);remaining-=v;}return a;
  }
  void showWorkout(boolean push){
    LinearLayout p=page("100-Rep Workout");ExerciseData.Exercise e=routine.get(currentExercise);p.addView(tv("Exercise "+(currentExercise+1)+" of "+routine.size()+"   •   Set "+(currentSet+1)+" of "+selectedSets,13,BLUE));TextView name=tv(e.name,28,TEXT);name.setGravity(Gravity.CENTER);name.setTypeface(null,Typeface.BOLD);p.addView(name,new LinearLayout.LayoutParams(-1,72));
    ImageView img=new ImageView(this);setExerciseImage(img,e.photo);img.setScaleType(ImageView.ScaleType.CENTER_CROP);p.addView(img,new LinearLayout.LayoutParams(-1,180));
    int reps=currentSplit.get(currentSet);TextView big=tv(String.valueOf(reps),60,TEXT);big.setGravity(Gravity.CENTER);p.addView(big,new LinearLayout.LayoutParams(-1,90));p.addView(tv("REPS THIS SET   •   100 TOTAL FOR THIS EXERCISE",14,MUTED));
    Button done=button(currentSet==selectedSets-1&&currentExercise==routine.size()-1?"✓  COMPLETE WORKOUT":"✓  SET COMPLETE",GREEN);done.setOnClickListener(v->{if(currentSet<selectedSets-1){currentSet++;showWorkout(false);}else if(currentExercise<routine.size()-1){currentExercise++;currentSet=0;currentSplit=randomSplit(selectedSets,minReps,maxReps);showWorkout(false);}else completeWorkout();});p.addView(done,new LinearLayout.LayoutParams(-1,60));show(scroll(p),push);
  }
  void completeWorkout(){
    long now=System.currentTimeMillis();double total=0;JSONArray exercises=new JSONArray();
    for(int i=0;i<routine.size();i++){ExerciseData.Exercise e=routine.get(i);double w=routineWeights.containsKey(e.name)?routineWeights.get(e.name):0;double kg=weightKg();double kcal=estimateCalories(e,100,w);total+=kcal;try{JSONObject o=new JSONObject();o.put("name",e.name);o.put("reps",100);o.put("weight",w);o.put("unit",weightUnit);o.put("calories",round(kcal));exercises.put(o);}catch(Exception ignored){}}
    JSONObject rec=new JSONObject();try{rec.put("timestamp",now);rec.put("date",dateKey.format(new Date(now)));rec.put("dateTime",dateTime.format(new Date(now)));rec.put("routine",routineName());rec.put("durationSec",Math.max(1,(now-workoutStarted)/1000));rec.put("calories",round(total));rec.put("exercises",exercises);}catch(Exception ignored){}
    JSONArray all=loadRecords();all.put(rec);prefs.edit().putString("records",all.toString()).apply();showCompletion(total,exercises);
  }
  String routineName(){String s=prefs.getString("draftRoutineName","");return s.length()>0?s:"100-rep routine";}
  double estimateCalories(ExerciseData.Exercise e,int reps,double exerciseWeight){
    double body=weightKg();
    double factor=e.equipment.equalsIgnoreCase("Bodyweight")?0.34:0.46;
    if(e.movement.equals("Squat")||e.movement.equals("Hinge")||e.movement.equals("Full Body"))factor+=0.08;
    String sex=prefs.getString("sex","0");double age=parseDouble(prefs.getString("age",""),30);double height=parseDouble(prefs.getString("height",""),170);
    double bmr=(sex.equals("2")?161:5)+(10*body)+(6.25*height)-(5*age);
    double bmrScale=Math.max(0.85,Math.min(1.15,bmr/1650.0));
    double load=exerciseWeight>0?exerciseWeight*0.02:0;
    return Math.max(1,body*factor*reps/100.0*bmrScale + load*reps/100.0);
  }
  double parseDouble(String s,double d){try{return Double.parseDouble(s);}catch(Exception e){return d;}}
  double weightKg(){double w=prefs.getFloat("profileWeight",70f);String u=prefs.getString("profileWeightUnit","kg");return u.equals("lb")?w*0.45359237:w;}
  double round(double x){return Math.round(x*10.0)/10.0;}
  void showCompletion(double total,JSONArray exercises){
    LinearLayout p=page("Workout Complete");TextView ok=tv("✓",64,GREEN);ok.setGravity(Gravity.CENTER);p.addView(ok,new LinearLayout.LayoutParams(-1,90));TextView h=tv("WORKOUT COMPLETE!",26,TEXT);h.setGravity(Gravity.CENTER);h.setTypeface(null,Typeface.BOLD);p.addView(h);TextView cal=tv(String.format(Locale.US,"%.1f kcal burned",total),28,GREEN);cal.setGravity(Gravity.CENTER);cal.setTypeface(null,Typeface.BOLD);p.addView(cal);p.addView(tv("100 reps completed for each exercise",16,MUTED));p.addView(heading("Calories by exercise"));for(int i=0;i<exercises.length();i++){try{JSONObject o=exercises.getJSONObject(i);p.addView(tv(o.getString("name")+"  •  "+o.getInt("reps")+" reps  •  "+o.getDouble("calories")+" kcal",14,TEXT));}catch(Exception ignored){}}Button done=button("DONE",BLUE);done.setOnClickListener(v->{routine.clear();routineWeights.clear();showHome(false);});p.addView(done,new LinearLayout.LayoutParams(-1,58));show(scroll(p),true);
  }
  void saveRoutineDraft(){
    JSONArray arr=new JSONArray();try{arr=new JSONArray(prefs.getString("savedRoutines","[]"));}catch(Exception ignored){}JSONObject r=new JSONObject();try{r.put("name",routineName());r.put("sets",selectedSets);r.put("min",minReps);r.put("max",maxReps);JSONArray ex=new JSONArray();for(ExerciseData.Exercise e:routine){JSONObject o=new JSONObject();o.put("name",e.name);o.put("weight",routineWeights.containsKey(e.name)?routineWeights.get(e.name):0);o.put("unit",weightUnit);ex.put(o);}r.put("exercises",ex);arr.put(r);prefs.edit().putString("savedRoutines",arr.toString()).apply();}catch(Exception ignored){}
  }
  void loadSavedRoutine(JSONObject r){routine.clear();routineWeights.clear();selectedSets=r.optInt("sets",5);minReps=r.optInt("min",10);maxReps=r.optInt("max",30);weightUnit=r.optString("unit",prefs.getString("weightUnit","kg"));JSONArray ex=r.optJSONArray("exercises");if(ex!=null)for(int i=0;i<ex.length();i++)try{JSONObject o=ex.getJSONObject(i);for(ExerciseData.Exercise e:ExerciseData.ALL)if(e.name.equals(o.getString("name"))){routine.add(e);routineWeights.put(e.name,o.optDouble("weight",0));break;}}catch(Exception ignored){}prefs.edit().putString("draftRoutineName",r.optString("name","Routine")).apply();showRoutine(true);}
  void showRoutineList(boolean push){LinearLayout p=page("My Routines");p.addView(tv("Saved routines are stored locally. Tap one to load it.",14,MUTED));Button create=button("＋  CREATE NEW ROUTINE",BLUE);create.setOnClickListener(v->{routine.clear();routineWeights.clear();showRoutine(true);});p.addView(create,new LinearLayout.LayoutParams(-1,58));JSONArray arr;try{arr=new JSONArray(prefs.getString("savedRoutines","[]"));}catch(Exception e){arr=new JSONArray();}for(int i=0;i<arr.length();i++)try{JSONObject r=arr.getJSONObject(i);Button b=button(r.optString("name","Routine")+"   •   "+r.optInt("sets",5)+" sets",CARD);b.setOnClickListener(v->loadSavedRoutine(r));p.addView(b,new LinearLayout.LayoutParams(-1,60));}catch(Exception ignored){}show(scroll(p),push);}
  class MeasurementFigure extends View{boolean female;PaintHolder ph=new PaintHolder();MeasurementFigure(Context c,boolean f){super(c);female=f;}protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f;android.graphics.Paint p=ph.p;p.setAntiAlias(true);p.setStrokeWidth(7);p.setStyle(android.graphics.Paint.Style.STROKE);p.setColor(TEXT);c.drawCircle(cx,55,27,p);c.drawLine(cx,82,cx,190,p);c.drawLine(cx,105,cx-(female?42:48),145,p);c.drawLine(cx,105,cx+(female?42:48),145,p);c.drawLine(cx,190,cx-(female?34:42),270,p);c.drawLine(cx,190,cx+(female?34:42),270,p);p.setStrokeWidth(3);p.setColor(GREEN);c.drawLine(cx-70,82,cx-70,190,p);c.drawLine(cx-76,82,cx-64,82,p);c.drawLine(cx-76,190,cx-64,190,p);c.drawLine(cx+65,145,cx+65,175,p);c.drawLine(cx+59,145,cx+71,145,p);c.drawLine(cx+59,175,cx+71,175,p);p.setStyle(android.graphics.Paint.Style.FILL);p.setTextSize(13);c.drawText("HEIGHT",cx-125,138,p);c.drawText("WAIST",cx+76,164,p);} }
  class PaintHolder{android.graphics.Paint p=new android.graphics.Paint();}
  void showProfile(boolean push){
    LinearLayout p=page("Personal Profile");p.addView(tv("These details improve calorie estimates. They stay on this device.",14,MUTED));
    Spinner sex=new Spinner(this);String[] sexes={"Biological sex","Male","Female"};sex.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,sexes));sex.setSelection(prefs.getInt("sex",0));sex.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){prefs.edit().putInt("sex",pos).apply();}});p.addView(sex,new LinearLayout.LayoutParams(-1,56));
    EditText age=field("Age",prefs.getString("age",""),InputType.TYPE_CLASS_NUMBER);EditText height=field("Height (cm)",prefs.getString("height",""),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText weight=field("Weight",String.valueOf(prefs.getFloat("profileWeight",70f)).replace(".0",""),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText waist=field("Waist (cm)",prefs.getString("waist",""),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
    p.addView(age);p.addView(height);p.addView(weight);p.addView(waist);Spinner unit=new Spinner(this);String[] units={"kg","lb"};unit.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,units));unit.setSelection(prefs.getString("profileWeightUnit","kg").equals("lb")?1:0);p.addView(unit,new LinearLayout.LayoutParams(-1,56));
    Button save=button("SAVE PROFILE",GREEN);save.setOnClickListener(v->{prefs.edit().putString("age",age.getText().toString()).putString("height",height.getText().toString()).putFloat("profileWeight",parseFloat(weight.getText().toString(),70)).putString("waist",waist.getText().toString()).putString("profileWeightUnit",unit.getSelectedItem().toString()).apply();Toast.makeText(this,"Profile saved",Toast.LENGTH_SHORT).show();showHome(false);});p.addView(save,new LinearLayout.LayoutParams(-1,58));
    LinearLayout figures=new LinearLayout(this);figures.setOrientation(LinearLayout.HORIZONTAL);MeasurementFigure male=new MeasurementFigure(this,false),female=new MeasurementFigure(this,true);figures.addView(male,new LinearLayout.LayoutParams(0,290,1));figures.addView(female,new LinearLayout.LayoutParams(0,290,1));p.addView(figures);p.addView(heading("Measurement guide"));p.addView(tv("Height: stand upright and measure floor to the top of the head.\n\nWaist: measure around the natural waist, without pulling the tape tight.\n\nWeight: enter the value from your scale. Calorie figures are estimates, not medical measurements.",14,MUTED));show(scroll(p),push);
  }
  EditText field(String hint,String value,int type){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setSingleLine();e.setInputType(type);e.setPadding(18,0,18,0);e.setBackground(bg(CARD,16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,56);lp.setMargins(0,6,0,6);e.setLayoutParams(lp);return e;}
  float parseFloat(String s,float d){try{return Float.parseFloat(s);}catch(Exception e){return d;}}
  JSONArray loadRecords(){try{return new JSONArray(prefs.getString("records","[]"));}catch(Exception e){return new JSONArray();}}
  boolean hasWorkoutOn(String key){JSONArray a=loadRecords();for(int i=0;i<a.length();i++)try{if(key.equals(a.getJSONObject(i).getString("date")))return true;}catch(Exception ignored){}return false;}
  double caloriesOn(String key){double sum=0;JSONArray a=loadRecords();for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);if(key.equals(o.getString("date")))sum+=o.optDouble("calories",0);}catch(Exception ignored){}return sum;}
  int streakForDate(Date date){Calendar c=Calendar.getInstance();c.setTime(date);int n=0;while(true){String k=dateKey.format(c.getTime());if(!hasWorkoutOn(k))break;n++;c.add(Calendar.DATE,-1);}return n;}
  int bestStreak(){String install=prefs.getString("installDate",dateKey.format(new Date()));try{Date start=dateKey.parse(install);Calendar c=Calendar.getInstance();c.setTime(start);Calendar end=Calendar.getInstance();int best=0,run=0;while(!c.after(end)){if(hasWorkoutOn(dateKey.format(c.getTime()))){run++;best=Math.max(best,run);}else run=0;c.add(Calendar.DATE,1);}return best;}catch(Exception e){return 0;}}
  void showHistory(boolean push){
    LinearLayout p=page("Completed Routines");Date today=new Date();Calendar anchor=Calendar.getInstance();anchor.setTime(today);anchor.set(Calendar.HOUR_OF_DAY,0);anchor.set(Calendar.MINUTE,0);anchor.set(Calendar.SECOND,0);anchor.set(Calendar.MILLISECOND,0);
    LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);Button prev=button("‹",CARD2),next=button("›",CARD2);TextView title=tv("",18,TEXT);title.setGravity(Gravity.CENTER);nav.addView(prev,new LinearLayout.LayoutParams(54,54));nav.addView(title,new LinearLayout.LayoutParams(0,54,1));nav.addView(next,new LinearLayout.LayoutParams(54,54));p.addView(nav);
    LinearLayout week=new LinearLayout(this);week.setOrientation(LinearLayout.HORIZONTAL);HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.addView(week);p.addView(hsv,new LinearLayout.LayoutParams(-1,104));
    LinearLayout bars=new LinearLayout(this);bars.setOrientation(LinearLayout.HORIZONTAL);bars.setGravity(Gravity.BOTTOM);p.addView(bars,new LinearLayout.LayoutParams(-1,180));
    TextView selected=heading("Selected day");p.addView(selected);LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);p.addView(scroll(details),new LinearLayout.LayoutParams(-1,0,1));
    final Calendar[] cursor={anchor};
    Runnable render=()->{Calendar base=(Calendar)cursor[0].clone();String endLabel=dateKey.format(base.getTime());title.setText("Week ending "+endLabel);week.removeAllViews();bars.removeAllViews();details.removeAllViews();String install=prefs.getString("installDate",endLabel);String selectedKey=dateKey.format(base.getTime());for(int i=-6;i<=0;i++){Calendar d=(Calendar)base.clone();d.add(Calendar.DATE,i);String key=dateKey.format(d.getTime());boolean allowed=key.compareTo(install)>=0;TextView day=tv(new SimpleDateFormat("EEE",Locale.US).format(d.getTime())+"\n"+new SimpleDateFormat("d",Locale.US).format(d.getTime())+"\n"+(allowed?String.format(Locale.US,"%.0f",caloriesOn(key)):"—"),12,allowed?TEXT:MUTED);day.setGravity(Gravity.CENTER);day.setBackground(bg(key.equals(selectedKey)?GREEN_DARK:CARD,16));day.setOnClickListener(v->{showDayDetails(key,details,selected);});week.addView(day,new LinearLayout.LayoutParams(82,90));double kcal=allowed?caloriesOn(key):0;TextView bar=tv(kcal>0?String.format(Locale.US,"%.0f",kcal):"",10,TEXT);bar.setGravity(Gravity.CENTER);bar.setBackground(bg(kcal>0?GREEN_DARK:CARD,12));LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(40,(int)Math.min(140,Math.max(12,kcal*2)));bl.setMargins(5,0,5,0);bars.addView(bar,bl);}showDayDetails(selectedKey,details,selected);prev.setEnabled(canMoveWeek(cursor[0],-7));next.setEnabled(canMoveWeek(cursor[0],7));};
    prev.setOnClickListener(v->{cursor[0].add(Calendar.DATE,-7);render.run();});next.setOnClickListener(v->{cursor[0].add(Calendar.DATE,7);render.run();});render.run();show(p,push);
  }
  boolean canMoveWeek(Calendar c,int delta){Calendar n=(Calendar)c.clone();n.add(Calendar.DATE,delta);String install=prefs.getString("installDate",dateKey.format(new Date()));String today=dateKey.format(new Date());String end=dateKey.format(n.getTime());return end.compareTo(install)>=0&&end.compareTo(today)<=0;}
  void showDayDetails(String key,LinearLayout details,TextView selected){details.removeAllViews();selected.setText("Completed on "+key+"    •    "+String.format(Locale.US,"%.1f kcal",caloriesOn(key)));JSONArray a=loadRecords();int found=0;for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);if(key.equals(o.getString("date"))){found++;LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(14,10,14,10);c.setBackground(bg(CARD,18));c.addView(tv(o.optString("routine","100-rep routine"),17,TEXT));c.addView(tv(o.optString("dateTime","")+"  •  "+o.optDouble("calories",0)+" kcal  •  "+o.optLong("durationSec",0)/60+" min",13,MUTED));JSONArray ex=o.optJSONArray("exercises");if(ex!=null)for(int j=0;j<ex.length();j++){JSONObject x=ex.getJSONObject(j);c.addView(tv("• "+x.getString("name")+" — "+x.getInt("reps")+" reps — "+x.getDouble("weight")+" "+x.getString("unit")+" — "+x.getDouble("calories")+" kcal",12,TEXT));}details.addView(c,new LinearLayout.LayoutParams(-1,-2));}}catch(Exception ignored){}if(found==0)details.addView(tv("No completed routines on this date.",15,MUTED));}
}
