package com.reps100.app;
import java.util.*;

/** V11 display metadata. Primary/secondary muscles are based on the movement's main movers and major assistants/stabilisers. */
public final class ExerciseMeta {
  private ExerciseMeta(){}
  public static String categories(ExerciseData.Exercise e){
    LinkedHashSet<String> c=new LinkedHashSet<>(); String n=e.name.toLowerCase(Locale.US), eq=e.equipment.toLowerCase(Locale.US), mv=e.movement.toLowerCase(Locale.US);
    if(eq.contains("bodyweight")) c.add("Bodyweight"); else c.add("Weightlifting");
    if(n.contains("jump")||n.contains("burpee")||n.contains("mountain climber")||n.contains("high knees")||n.contains("jumping jack")||n.contains("swing")) c.add("Cardio");
    if(eq.contains("barbell")) c.add("Barbell"); if(eq.contains("dumbbell")) c.add("Dumbbell"); if(eq.contains("cable")) c.add("Cable"); if(eq.contains("machine")) c.add("Machine"); if(eq.contains("kettlebell")) c.add("Kettlebell");
    if(mv.contains("push")||n.contains("press")||n.contains("push-up")||n.contains("dip")||n.contains("thruster")||n.contains("snatch")) c.add("Push");
    if(mv.contains("pull")||n.contains("row")||n.contains("curl")||n.contains("pulldown")||n.contains("pull-up")||n.contains("chin")||n.contains("shrug")||n.contains("deadlift")||n.contains("clean")||n.contains("swing")) c.add("Pull");
    String p=primary(e.name,e.muscle).toLowerCase(Locale.US), sec=secondary(e.name).toLowerCase(Locale.US);
    if(p.contains("pectoral")||p.contains("chest")) c.add("Chest"); if(p.contains("lat")||p.contains("back")||p.contains("erector")||sec.contains("back")) c.add("Back");
    if(p.matches(".*(quad|hamstring|glute|calf|adductor).*" )||sec.matches(".*(quad|hamstring|glute|calf|adductor).*")) c.add("Legs");
    if(p.contains("deltoid")||p.contains("trap")||sec.contains("deltoid")) c.add("Shoulders"); if(p.contains("biceps")||p.contains("triceps")||sec.contains("biceps")||sec.contains("triceps")||p.contains("forearm")) c.add("Arms");
    if(p.contains("abdom")||p.contains("oblique")||sec.contains("core")||n.contains("bird dog")||n.contains("dead bug")||n.contains("get-up")) c.add("Core");
    return String.join("  •  ",c);
  }
  public static String primary(String name,String fallback){String n=name.toLowerCase(Locale.US);
    if(n.contains("calf")) return "Gastrocnemius, soleus"; if(n.contains("adduction")) return "Hip adductors"; if(n.contains("abduction")) return "Gluteus medius, gluteus minimus";
    if(n.contains("kickback")||n.contains("donkey")||n.contains("glute bridge")||n.contains("hip thrust")) return "Gluteus maximus";
    if(n.contains("leg curl")) return "Hamstrings"; if(n.contains("leg extension")) return "Quadriceps";
    if(n.contains("squat")||n.contains("lunge")||n.contains("step-up")||n.contains("leg press")||n.contains("box jump")||n.contains("tuck jump")||n.contains("side-to-side")||n.contains("forward-back")) return "Quadriceps, gluteus maximus";
    if(n.contains("deadlift")||n.contains("good morning")||n.contains("single leg deadlift")) return "Hamstrings, gluteus maximus, spinal erectors";
    if(n.contains("bench press")||n.contains("chest press")||n.contains("pec deck")||n.contains("fly")||n.contains("push-up")||n.equals("dips")) return "Pectoralis major";
    if(n.contains("tricep")||n.contains("skull crusher")) return "Triceps brachii"; if(n.contains("curl")) return n.contains("reverse")?"Brachioradialis, brachialis":"Biceps brachii";
    if(n.contains("lateral raise")) return "Lateral deltoid"; if(n.contains("front raise")) return "Anterior deltoid"; if(n.contains("rear delt")||n.contains("face pull")) return "Posterior deltoid";
    if(n.contains("shoulder press")||n.contains("overhead press")||n.contains("arnold")||n.contains("one arm press")||n.contains("v push")) return "Deltoids";
    if(n.contains("shrug")) return "Upper trapezius"; if(n.contains("upright row")) return "Lateral deltoid, upper trapezius";
    if(n.contains("pulldown")||n.contains("pull-up")||n.contains("chin-up")||n.contains("row")) return "Latissimus dorsi, middle back";
    if(n.contains("crunch")||n.contains("sit-up")||n.contains("leg raise")||n.contains("knee raise")||n.contains("knee tuck")||n.contains("v-up")||n.contains("scissor")||n.contains("dead bug")) return "Rectus abdominis";
    if(n.contains("russian")||n.contains("wood chop")) return "Obliques"; if(n.contains("ab wheel")) return "Rectus abdominis, transverse abdominis";
    if(n.contains("bird dog")||n.contains("superman")||n.contains("back raise")||n.contains("back extension")) return "Spinal erectors";
    if(n.contains("snatch")||n.contains("thruster")||n.contains("clean")||n.contains("swing")||n.contains("get-up")||n.contains("burpee")) return "Full-body compound: glutes, quadriceps, shoulders";
    if(n.contains("mountain climber")||n.contains("high knees")) return "Hip flexors, quadriceps, abdominals"; if(n.contains("jumping jack")) return "Hip abductors, calves, deltoids";
    return fallback==null||fallback.isEmpty()?"Primary movers":""+fallback;
  }
  public static String secondary(String name){String n=name.toLowerCase(Locale.US);
    if(n.contains("calf")) return "Tibialis posterior and intrinsic foot/ankle stabilisers; core for balance";
    if(n.contains("squat")||n.contains("lunge")||n.contains("step-up")||n.contains("leg press")||n.contains("jump")) return "Hamstrings, calves, hip adductors and core stabilisers";
    if(n.contains("deadlift")||n.contains("good morning")) return "Quadriceps, latissimus dorsi, trapezius, forearms and deep core";
    if(n.contains("press")||n.contains("push-up")||n.contains("dip")) return "Triceps brachii, anterior deltoids, serratus anterior and core stabilisers";
    if(n.contains("fly")||n.contains("pec deck")) return "Anterior deltoids, biceps and serratus anterior";
    if(n.contains("row")||n.contains("pulldown")||n.contains("pull-up")||n.contains("chin-up")) return "Biceps, brachialis, posterior deltoids, trapezius and rhomboids";
    if(n.contains("curl")) return "Brachialis, brachioradialis and forearm flexors/stabilisers";
    if(n.contains("tricep")||n.contains("skull")) return "Anconeus, deltoids and core/shoulder stabilisers";
    if(n.contains("raise")||n.contains("shoulder")||n.contains("overhead")||n.contains("arnold")||n.contains("upright")) return "Trapezius, rotator cuff, serratus anterior and core stabilisers";
    if(n.contains("crunch")||n.contains("sit-up")||n.contains("leg raise")||n.contains("knee")||n.contains("v-up")||n.contains("scissor")||n.contains("dead bug")||n.contains("wood chop")||n.contains("russian")||n.contains("ab wheel")) return "Obliques, transverse abdominis, hip flexors and deep spinal stabilisers";
    if(n.contains("glute")||n.contains("kickback")||n.contains("donkey")||n.contains("abduction")||n.contains("adduction")||n.contains("hip thrust")) return "Hamstrings, gluteus medius/minimus or adductors as appropriate, plus core stabilisers";
    if(n.contains("back")||n.contains("bird dog")||n.contains("superman")) return "Gluteus maximus, hamstrings, multifidus, trapezius and shoulder stabilisers";
    return "Core and joint stabilisers; assisting muscles appropriate to the movement";
  }
}
