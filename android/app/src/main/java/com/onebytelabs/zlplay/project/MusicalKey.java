package com.onebytelabs.zlplay.project;
import java.util.regex.*;
public final class MusicalKey {
 public static final String[] FLATS={"C","Db","D","Eb","E","F","Gb","G","Ab","A","Bb","B"};
 private MusicalKey(){}
 public static int parse(String text){if(text==null)return -1;String s=text.trim().replace("♭","b").replace("♯","#");if(!s.matches("(?i)[A-G](?:#|b)?(?:m|maj|min)?"))return -1;int n;switch(Character.toUpperCase(s.charAt(0))){case 'C':n=0;break;case 'D':n=2;break;case 'E':n=4;break;case 'F':n=5;break;case 'G':n=7;break;case 'A':n=9;break;default:n=11;}if(s.length()>1){if(s.charAt(1)=='#')n++;else if(s.charAt(1)=='b'||s.charAt(1)=='B')n--;}return(n+12)%12;}
 public static int filename(String name){String s=name.replace('\\','/');s=s.substring(s.lastIndexOf('/')+1).replaceFirst("(?i)\\.(wav|mp3|rpp|zlplay)$","");String note="([A-G](?:#|b|♯|♭)?(?:m|maj|min)?)";for(String expression:new String[]{"(?i)Original\\s*\\("+note+"\\)","(?i)\\("+note+"\\)","(?i)(?:^|[ _-])Tom[ _:-]*"+note+"(?:$|[ _-])","(?i)(?:^|[ _-])"+note+"$"}){Matcher m=Pattern.compile(expression).matcher(s);int result=-1;while(m.find())result=parse(m.group(1));if(result>=0)return result;}return parse(s);}
 public static int padFilename(String name){int exact=filename(name);if(exact>=0)return exact;String clean=name.replaceFirst("(?i)\\.(wav|mp3)$","");int found=-1;for(String token:clean.split("[ _().\\-]+")){int note=parse(token);if(note>=0){if(found>=0&&found!=note)return -1;found=note;}}return found;}
 public static int delta(int from,int to){return((to-from+18)%12)-6;}
}
