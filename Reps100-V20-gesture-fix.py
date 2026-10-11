#!/usr/bin/env python3
"""Create V20-source from V17-source and replace broken routine-card gestures.
Run in /workspaces/Reps100. Does not modify the existing V17/V19 build folder.
"""
from pathlib import Path
import shutil,re,sys
root=Path('/workspaces/Reps100'); source=root/'V17-source'; dest=root/'V20-source'
main=source/'app/src/main/java/com/reps100/app/MainActivity.java'
if not main.is_file(): sys.exit('ERROR: V17-source/MainActivity.java not found')
if dest.exists(): sys.exit('ERROR: V20-source exists already; refusing to overwrite')
shutil.copytree(source,dest,ignore=shutil.ignore_patterns('build','.gradle','*.apk','*.idsig','*.log','*.keystore','*.jks'))
target=dest/'app/src/main/java/com/reps100/app/MainActivity.java'
s=target.read_text()
start=s.index('  void addRoutineExerciseCard(')
end=s.index('\n  void ensureOccurrenceStorage()',start)
old=s[start:end]
assert 'new View.DragShadowBuilder(card)' in old and 'drag.setOnTouchListener' in old
# Remove old conflicting drag/drop and handle swipe listeners.
a=old.index('    card.setOnDragListener('); b=old.index('    LinearLayout texts = vbox();',a)
old=old[:a]+old[b:]
a=old.index('        FrameLayout swipeWrap=new FrameLayout(this);');b=old.index('    LinearLayout weightRow=',a)
old=old[:a]+'''    FrameLayout swipeWrap = new FrameLayout(this);
    final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
    final float[] downX = {0}, downY = {0};
    final boolean[] swipeActive = {false};
''' +old[b:]
a=old.index('        TextView deleteBg=bold("🗑",24);');b=len(old)
# Preserve the method's closing brace, replace the old swipe wrapper construction.
old=old[:a]+'''    TextView deleteBg = bold("🗑",24);
    deleteBg.setGravity(Gravity.CENTER_VERTICAL);
    deleteBg.setPadding(dp(22),0,0,0);
    deleteBg.setTextColor(0xFFE8E8E8);
    deleteBg.setBackground(shape(0xFF641E24,18));
    swipeWrap.addView(deleteBg,new FrameLayout.LayoutParams(-1,-1));
    swipeWrap.addView(card,new FrameLayout.LayoutParams(-1,-2));
    swipeWrap.setTag("routine-reorder-card");
    body.addView(swipeWrap,margins(-1,-2,0,6,0,8));

    // Horizontal delete belongs to the card, never to the reorder handle.
    card.setOnTouchListener((v,ev) -> {
      switch(ev.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downX[0]=ev.getRawX(); downY[0]=ev.getRawY(); swipeActive[0]=false;
          return true;
        case MotionEvent.ACTION_MOVE:
          float dx=ev.getRawX()-downX[0], dy=ev.getRawY()-downY[0];
          if(!swipeActive[0] && Math.abs(dy)>slop && Math.abs(dy)>Math.abs(dx))
            return false; // Let the ScrollView take over vertical scrolling.
          if(dx>slop && Math.abs(dx)>Math.abs(dy)) {
            swipeActive[0]=true;
            if(routineScrollView!=null) routineScrollView.requestDisallowInterceptTouchEvent(true);
            card.setTranslationX(Math.min(dx,card.getWidth()));
          }
          return true;
        case MotionEvent.ACTION_UP:
          if(swipeActive[0] && card.getTranslationX()>card.getWidth()*.50f) {
            ensureOccurrenceStorage();
            routine.remove(occurrenceIndex);
            routineOccurrenceWeights.remove(occurrenceIndex);
            routineOccurrenceUnits.remove(occurrenceIndex);
            showRoutine(false);
          } else card.animate().translationX(0).setDuration(150).start();
          return true;
        case MotionEvent.ACTION_CANCEL:
          card.animate().translationX(0).setDuration(150).start();
          return true;
      }
      return false;
    });

    // A single raised REAL card; no Android drag shadow or duplicate image.
    drag.setOnTouchListener(new View.OnTouchListener() {
      final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
      Runnable hold;
      boolean dragging=false, cancelledHold=false;
      float downRawY;
      int targetIndex=occurrenceIndex;
      final float[] lastFingerY={0};
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
            swipeWrap.setTranslationY(swipeWrap.getTranslationY()+actual);
            updateTarget();
          }
          handler.postDelayed(this,25);
        }
      };
      void updateTarget() {
        int[] draggedLoc=new int[2]; swipeWrap.getLocationOnScreen(draggedLoc);
        float center=draggedLoc[1]+swipeWrap.getHeight()/2f;
        int newTarget=occurrenceIndex;
        for(int j=0;j<body.getChildCount();j++) {
          View other=body.getChildAt(j);
          if(other==swipeWrap || !"routine-reorder-card".equals(other.getTag())) continue;
          int[] loc=new int[2]; other.getLocationOnScreen(loc);
          float mid=loc[1]-other.getTranslationY()+other.getHeight()/2f;
          if(j>body.indexOfChild(swipeWrap) && center>mid) newTarget++;
          if(j<body.indexOfChild(swipeWrap) && center<mid) newTarget--;
        }
        targetIndex=Math.max(0,Math.min(routine.size()-1,newTarget));
        int original=body.indexOfChild(swipeWrap);
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
          other.animate().translationY(shift).setDuration(100).start();
        }
      }
      @Override public boolean onTouch(View v,MotionEvent ev) {
        switch(ev.getActionMasked()) {
          case MotionEvent.ACTION_DOWN:
            downRawY=lastFingerY[0]=ev.getRawY();
            dragging=false;cancelledHold=false;targetIndex=occurrenceIndex;
            hold=()->{
              if(cancelledHold) return;
              dragging=true;
              swipeWrap.bringToFront();
              card.setElevation(dp(18));
              card.animate().scaleX(1.025f).scaleY(1.025f).setDuration(120).start();
              v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
              if(routineScrollView!=null) routineScrollView.requestDisallowInterceptTouchEvent(true);
              handler.post(edgeScroll);
            };
            handler.postDelayed(hold,ViewConfiguration.getLongPressTimeout());
            return true;
          case MotionEvent.ACTION_MOVE:
            lastFingerY[0]=ev.getRawY();
            if(!dragging && Math.abs(ev.getRawY()-downRawY)>slop) {
              cancelledHold=true;handler.removeCallbacks(hold);
            }
            if(dragging) {
              swipeWrap.setTranslationY(ev.getRawY()-downRawY);
              updateTarget();
            }
            return true;
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL:
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
              dragging=false;
              showRoutine(false);
            }
            return true;
        }
        return true;
      }
    });
  }
'''
s=s[:start]+old+s[end:]
# Increase version for a test build.
gradle=dest/'app/build.gradle.kts'
g=gradle.read_text()
g,n=re.subn(r'\bversionCode\s*=\s*\d+','versionCode = 20',g,count=1)
g,m=re.subn(r'\bversionName\s*=\s*"[^"]+"','versionName = "20.0"',g,count=1)
if n!=1 or m!=1: sys.exit('ERROR: Could not find versionCode/versionName in Gradle file')
gradle.write_text(g)
target.write_text(s)
print('Created V20-source (versionCode 20, versionName 20.0).')
print('V17-source and signed V19 APK were NOT changed.')
print('NEXT: cd /workspaces/Reps100/V20-source && ./gradlew :app:compileReleaseJavaWithJavac --no-daemon --max-workers=1 --no-parallel')
